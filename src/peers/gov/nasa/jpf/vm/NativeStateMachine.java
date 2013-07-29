//
// Copyright  (C) 2007 United States Government as represented by the
// Administrator of the National Aeronautics and Space Administration
//  (NASA).  All Rights Reserved.
//
// This software is distributed under the NASA Open Source Agreement
//  (NOSA), version 1.3.  The NOSA has been approved by the Open Source
// Initiative.  See the file NOSA-1.3-JPF at the top of the distribution
// directory tree for the complete NOSA document.
//
// THE SUBJECT SOFTWARE IS PROVIDED "AS IS" WITHOUT ANY WARRANTY OF ANY
// KIND, EITHER EXPRESSED, IMPLIED, OR STATUTORY, INCLUDING, BUT NOT
// LIMITED TO, ANY WARRANTY THAT THE SUBJECT SOFTWARE WILL CONFORM TO
// SPECIFICATIONS, ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR
// A PARTICULAR PURPOSE, OR FREEDOM FROM INFRINGEMENT, ANY WARRANTY THAT
// THE SUBJECT SOFTWARE WILL BE ERROR FREE, OR ANY WARRANTY THAT
// DOCUMENTATION, IF PROVIDED, WILL CONFORM TO THE SUBJECT SOFTWARE.
//
package gov.nasa.jpf.vm;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.vm.JPF_gov_nasa_jpf_sc_State;
import gov.nasa.jpf.jvm.ChoiceGenerator;
import gov.nasa.jpf.jvm.ClassInfo;
import gov.nasa.jpf.jvm.DirectCallStackFrame;
import gov.nasa.jpf.jvm.MJIEnv;
import gov.nasa.jpf.jvm.MethodInfo;
import gov.nasa.jpf.jvm.StackFrame;
import gov.nasa.jpf.jvm.SystemState;
import gov.nasa.jpf.jvm.ThreadInfo;
import gov.nasa.jpf.jvm.bytecode.Instruction;
import gov.nasa.jpf.jvm.choice.sc.SCEvent;
import gov.nasa.jpf.jvm.choice.sc.SCEventFromSet;
import gov.nasa.jpf.jvm.choice.sc.SCEventGenerator;
import gov.nasa.jpf.jvm.choice.sc.SCEventSingleChoice;
import gov.nasa.jpf.jvm.choice.sc.SCScriptEnvironment;
import gov.nasa.jpf.jvm.choice.sc.NativeSentSCEvent;
import gov.nasa.jpf.util.DynamicObjectArray;
import gov.nasa.jpf.util.StringSetMatcher;
import gov.nasa.jpf.util.script.ESParser;
import gov.nasa.jpf.util.script.Event;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is basically the environment part that should not be in the JPF state space.
 * The main function is to analyze states via reflection (trigger methods, actions),
 * provide events, and to execute trigger methods and actions.
 *
 * We don't implement this directly in the native peer so that the peer can handle
 * any number of state machine executions (even several instances of the same
 * State master class)
 */
public class NativeStateMachine {

  static Logger log = JPF.getLogger("gov.nasa.jpf.sc");

  SCScriptEnvironment scriptEnv;

  // this is where we can keep track of tried events per state, to
  // control JPF state matching
  DynamicObjectArray<HashSet<SCEvent>> events;

  // the currently dispatched event
  SCEvent curEvent;


  // property check data
  static int[] neverVisit;
  static int[] alwaysVisit;
  static boolean checkNoActiveStates;
  static boolean checkNoUnhandledEvents;

  static boolean sendQueue; // do we try only the first sent event
  static boolean selfPriority; // queued self sends have priority (KC behavior)
  static boolean sendSuper; // do we also try events sent to super states
  
  // how do we handle priorities of explicitly sent events?
  static boolean localPriorities; // use the highest priority of each active state hierarchy
  static boolean topPriority;   // all events of all actives states with the same top priority
  static boolean totalOrder; // use only the first event of the highest priority of all active states 

  static boolean showMachine; // print structure of statemachine
  
  static int maxSteps;

  //--- internal stuff

  // a little helper to avoid using State objects in Script section lookup
  // (since we don't recycle states, we can rely on the ref invariance)
  String[] getStateNames (MJIEnv env, int activeStatesRef) {
    ArrayList<String> names = new ArrayList<String>();

    for (int stateRef = activeStatesRef; stateRef != MJIEnv.NULL;
                          stateRef = env.getReferenceField(stateRef, "next")) {

      // type based lookup
      //ClassInfo ci = env.getClassInfo(stateRef);
      //String name = ci.getName().replace('$', '.');

      // field based lookup (it's already in dot notation)
      int fnameRef = env.getReferenceField(stateRef, "fieldName");
      String name = env.getStringObject(fnameRef);

      names.add(name);
    }

    return names.toArray(new String[names.size()]);
  }

  BitSet getReEnteredStates (MJIEnv env, int activeStatesRef) {
    BitSet reEntered = new BitSet();
    int i = 0;

    for (int stateRef = activeStatesRef; stateRef != MJIEnv.NULL;
                       stateRef = env.getReferenceField(stateRef, "next")) {
      reEntered.set(i++, env.getBooleanField(stateRef, "isReEntered"));
    }

    return reEntered;
  }


  boolean isMatchingReceiver (MJIEnv env, int stateRef, SCEvent e){
    String[] rc = e.getReceiverConstraints();
    if (rc != null) {
      StringSetMatcher sm = new StringSetMatcher(rc);
      String sName = getStateName(env,stateRef);
      return sm.matchesAny(sName);
    }

    return true;
  }

  boolean isMatchingMethod (MethodInfo mi, SCEvent e) {
    return mi.getUniqueName().equals(e.getUniqueMethodName());
  }


  int[] getCheckReferences (Config conf, String key) {
    String[] stateNames = conf.getStringArray(key);
    if (stateNames != null) {
      ArrayList<Integer> refList = new ArrayList<Integer>();
      for (int i=0; i<stateNames.length; i++) {
        Integer ref = JPF_gov_nasa_jpf_sc_State.map.get(stateNames[i]);
        if (ref != null) {
          refList.add(ref);
        } else {
          // don't know about this state, maybe we should warn
          log.warning("unknown state name in property spec: " + stateNames[i]);
        }
      }
      int n = refList.size();
      if (n > 0) {
        int[] a = new int[n];
        for (int i=0; i<n; i++) {
          a[i] = refList.get(i).intValue();
        }
        return a;
      }
    }

    return null;
  }

  String getStateName (MJIEnv env, int stateRef) {
    int nameRef = env.getReferenceField(stateRef, "fieldName");
    return env.getStringObject(nameRef);
  }

  void executeStateMethod (MJIEnv env, int objRef, int stateRef, String methodName, boolean logIt) {
    ThreadInfo ti = env.getThreadInfo();
    Instruction insn = ti.getPC();
    DirectCallStackFrame frame = (DirectCallStackFrame) ti.getReturnedDirectCall();

    if (frame == null) {
      ClassInfo ci = env.getClassInfo(stateRef);
      MethodInfo mi = ci.getMethod( methodName, false);
      if (mi != null){
        if (logIt){
          log.info( getStateName(env,stateRef) + '.' + mi.getName());
        }

        frame = ci.createDirectCallStackFrame(ti, mi, 0);        
        frame.pushRef( stateRef);
        ti.pushFrame(frame);

        env.repeatInvocation();
      }
    }
  }

  void pushArgs (MJIEnv env, StackFrame frame, Object[] args) {
    if (args != null) {
      for (Object a : args) {
        if (a instanceof Integer) {
          frame.push((Integer)a);
        } else if (a instanceof Boolean) {
          frame.push((Boolean)a ? 1 : 0, false);
        } else if (a instanceof Double) {
          frame.pushDouble((Double)a);
        } else if (a instanceof String) {
          int sref = env.newString((String)a);
          frame.pushRef(sref);
        }
      }
    }
  }


  //--- logging support

  protected void logActive (MJIEnv env, int activeStatesRef) {

    if (log.isLoggable(Level.INFO)) {
      for (int stateRef = activeStatesRef; stateRef != MJIEnv.NULL;
                      stateRef = env.getReferenceField(stateRef, "next")) {
        int fnameRef = env.getReferenceField(stateRef, "fieldName");
        String fname = env.getStringObject(fnameRef);

        StringBuilder sb = new StringBuilder(100);
        sb.append("active state ");
        sb.append(fname);

        sb.append(", pending {");
        for (int sRef = stateRef; sRef != MJIEnv.NULL; sRef = env.getReferenceField(sRef, "superState")){

          int qRef = env.getReferenceField(sRef, "pendingEvents");
          if (qRef != MJIEnv.NULL) {
            int eRef = env.getReferenceField(qRef, "head");
            boolean first=true;
            while (eRef != MJIEnv.NULL) {
              int r = env.getReferenceField(eRef, "name");
              String eName = env.getStringObject(r);
              if (!first) {
                sb.append(',');
              } else {
                first = false;
                if (sRef != stateRef) {
                  int nRef = env.getReferenceField(sRef, "fieldName");
                  String sName = (nRef == MJIEnv.NULL) ? "ALL" : env.getStringObject(nRef);
                  sb.append(", ");
                  sb.append( sName);
                  sb.append(":");
                }
              }
              sb.append(eName);
              sb.append('/');
              sb.append(env.getIntField(eRef, "priority"));
              
              eRef = env.getReferenceField(eRef, "next");
            }
          }
        }

        sb.append("}, wait {");
        int eRef = env.getReferenceField(stateRef, "waitEvent");
        boolean first=true;
        while (eRef != MJIEnv.NULL) {
          int r = env.getReferenceField(eRef, "name");
          String eName = env.getStringObject(r);
          if (!first) {
            sb.append(',');
          } else {
            first = false;
          }
          sb.append(eName);
          eRef = env.getReferenceField(eRef, "next");
        }
        sb.append('}');

        log.info(sb.toString());
      }
    }
  }

  protected void logEvent (SCEvent e) {
    if (log.isLoggable(Level.INFO)) {
      StringBuilder sb = new StringBuilder("event: ");
      sb.append(e.toString());
      log.info(sb.toString());
    }
  }

  protected void logEventProcessed (SCEvent e) {
    if (log.isLoggable(Level.INFO)) {
      StringBuilder sb = new StringBuilder("event processed: ");
      sb.append(e.toString());
      log.info(sb.toString());
    }    
  }
  
  protected void logTrigger (MJIEnv env, int stateRef, MethodInfo mi) {
    if (log.isLoggable(Level.INFO)) {
      int fnRef = env.getReferenceField(stateRef, "fieldName");
      String stateName = env.getStringObject(fnRef);

      StringBuilder sb = new StringBuilder("state ");
      sb.append(stateName);
      sb.append(" executes trigger: ");
      sb.append(mi.getUniqueName());
      log.info(sb.toString());
    }
  }

  //--- various helpers for expansion of wildcard events (== active alphabet)

  ArrayList<MethodInfo> getAlphabet (MJIEnv env, ClassInfo ci){
    ArrayList<MethodInfo> alphabet = new ArrayList<MethodInfo>();
    for (MethodInfo mi : ci.getDeclaredMethodInfos()) {
      if (mi.isPublic() && !mi.isStatic()) {
        // filter out action methods and completion, but leave in timeouts

        // we could use the State constants here, but we don't want to
        // depend on a model class in the classpath
        String mName = mi.getName();
        if (mName.equals("completion") ||  // that's handled separately
            mName.equals("entryAction") ||
            mName.equals("exitAction") ||
            mName.equals("doAction") ||
            mName.equals("<init>")) {
          continue;
        }

        alphabet.add(mi);
      }
    }
    return alphabet;
  }

  ArrayList<SCEvent> getActiveAlphabet (MJIEnv env, int activeStatesRef,
                                        StringSetMatcher receiverMatcher, StringSetMatcher idMatcher) {
    String fName = null;
    ArrayList<SCEvent> alphabet = new ArrayList<SCEvent>();

    for (int sRef = activeStatesRef; sRef != MJIEnv.NULL; sRef = env.getReferenceField(sRef, "next")) {

      if (receiverMatcher != null) {
        int fNameRef = env.getReferenceField(sRef, "fieldName");
        fName = env.getStringObject(fNameRef);
        if (!receiverMatcher.matchesAny(fName)) {
          continue;
        }
      }

      for (int stateRef=sRef; stateRef != MJIEnv.NULL; stateRef = env.getReferenceField(stateRef,"superState")) {
        for (MethodInfo mi : getAlphabet(env,env.getClassInfo(stateRef))) {

          String mName = mi.getName();
          String[] argTypes = mi.getArgumentTypeNames();

          if (idMatcher != null) {
            if (!idMatcher.matchesAny(mName)) {
              continue;
            }
          }

          String rc = receiverMatcher != null ? fName : null;

          if (argTypes.length == 0) { // no args, the simple case
            SCEvent e = new SCEvent(mName);
            e.addUniqueTo(alphabet, rc);

          } else { // we have args, now we need values
            String[] paramValues = getParamValuesFromAnnotation(env,mi);
            if (paramValues != null) {
              Event ev = new Event(null, mName, paramValues, 0); // <2do> lineno would be nice
              for (Event e : ev.expand()) {
                // maybe we should do some type checking here
                SCEvent sce = new SCEvent(e);
                sce.addUniqueTo( alphabet, rc);
              }

            } else if (onlyBooleanArguments(argTypes)){
              for (Object[] args : Event.getBooleanArgVariations(argTypes.length)){
                SCEvent e = new SCEvent(mName, args);
                e.addUniqueTo(alphabet, rc);
              }

            } else {
              // nothing we can do, we need parameter values
              log.warning("no argument values for: " + mName + ", skipping");
            }
          }
        }
      }
    }

    return alphabet;
  }

  boolean onlyBooleanArguments (String[] argTypes){
    for (int i=0; i<argTypes.length; i++){
      if (!argTypes[i].equals("boolean")) {
        return false;
      }
    }
    return true;
  }

  String[] getParamValuesFromAnnotation (MJIEnv env, MethodInfo mi) {
    AnnotationInfo ann = mi.getAnnotation("gov.nasa.jpf.sc.State$Params");
    if (ann != null){
      String v = ann.valueAsString();
      if (v != null && v.length() > 0){
        String[] values= v.split(","); // can't break on blanks (String literals might contain them)
        for (int i=0; i<values.length; i++){
          values[i] = values[i].trim();
        }
        return values;
      }
    }

    return null;
  }

  boolean wasGuardedCompletionStep(MJIEnv env, int objRef) {
    return env.getBooleanField(objRef, "wasGuardedCompletionStep");
  }

  boolean checkEvent (MJIEnv env, int stateRef, SCEvent event, boolean add) {
    int idx = env.getIntField(stateRef, "id");
    HashSet<SCEvent> seen = events.get(idx);

    if (seen == null){
      seen = new HashSet<SCEvent>();
      events.set(idx,seen);
    } else {
      if (seen.contains(event)){
        return true;
      }
    }

    if (add){
      seen.add(event);
    }
    return false;
  }

  boolean hasCompletionTrigger (MJIEnv env, int activeStatesRef){

    for (int stateRef = activeStatesRef; stateRef != MJIEnv.NULL;
                    stateRef = env.getReferenceField(stateRef, "next")) {
      ClassInfo ci = env.getClassInfo(stateRef);
      SCEvent ev = SCEvent.COMPLETION_EVENT;
      if (ci.getMethod( ev.getUniqueMethodName(), false) != null){
        return true;
      }
    }

    return false;
  }

  boolean hasTimeTrigger (MJIEnv env, int activeStatesRef){
    // this is recursive
    for (int sRef = activeStatesRef; sRef != MJIEnv.NULL;
                            sRef = env.getReferenceField(sRef, "next")) {

      for (int stateRef=sRef; stateRef != MJIEnv.NULL; stateRef = env.getReferenceField(stateRef, "superState")) {
        ClassInfo ci = env.getClassInfo(stateRef);
        if (ci.getMethod( SCEvent.TIMEOUT_EVENT.getUniqueMethodName(), false) != null){
          return true;
        }
      }
    }

    return false;
  }

  SCEventGenerator createCGFromEvents (String id, List<SCEvent> events){
    int n = events.size();

    if (n == 0) {
      return null;
    } else if (n == 1) {
      return new SCEventSingleChoice(id, events.get(0));
    } else {
      return new SCEventFromSet(id, events);
    }
  }

  SCEvent createSCEventFromPending (MJIEnv env, int eRef, int stateRef){
    // NOTE - we can't recycle the EventSpec here (put it back into the
    // pendingEvents.processedEvent pool) because this is called before
    // state matching (top half of getEnablingEvent()), and we need the
    // pendingEvents queue to be part of the program state space, otherwise
    // we can get premature state matching
    
    String eventName = env.getStringObject(env.getReferenceField(eRef, "name"));
    Object[] args = env.getArgumentArray(env.getReferenceField(eRef, "args"));
    String receiverName = getStateName(env,stateRef);

    // since this is an explicitly sent event, we have to encode the receiver
    // in the SCEvent id. This is kind of braindead since that info is
    // implicitly in the State's PendingEvent queue, but the receiver filter
    // is implemented by means of using the SCEvent accessors

    NativeSentSCEvent event = new NativeSentSCEvent(stateRef, eRef, receiverName, eventName, args);

    return event;
  }

  //--- lots of pending event processing policies to follow
  
  /**
   * this is a questionable policy, since it doesn't leave us any choices. It
   * indicates a procedural model
   */
  SCEvent getFirstTopPriorityPendingEvent (MJIEnv env, int activeStatesRef) {
    int topPriority = Integer.MIN_VALUE;
    int eRefTop = MJIEnv.NULL;
    int stateRefTop = MJIEnv.NULL;
    
    // for same top priority, the activeState order counts (= makeInitial(..) order) 
    for (int activeStateRef = activeStatesRef; activeStateRef != MJIEnv.NULL;
         activeStateRef = env.getReferenceField(activeStateRef, "next")){

      for (int stateRef=activeStateRef; stateRef != MJIEnv.NULL; stateRef = env.getReferenceField(stateRef,"superState")){
        int qRef = env.getReferenceField(stateRef, "pendingEvents"); // the pending queue of the state
        int eRef = env.getReferenceField(qRef, "head"); // the first EventSpec entry of the queue

        if (eRef != MJIEnv.NULL) {
          int prio = env.getIntField(eRef, "priority");
          if (prio > topPriority) { // use substate if priority is the same 
            topPriority = prio;
            eRefTop = eRef;
            stateRefTop = stateRef;
          }
        }
        
        if (!sendSuper) {
          break;
        }
      }
    }
    
    if (eRefTop != MJIEnv.NULL) {
      SCEvent sce = createSCEventFromPending(env,eRefTop,stateRefTop);
      return sce;
    } else {
      return null;
    }
  }

  ArrayList<SCEvent> getAllTopPriorityPendingEvents (MJIEnv env, int activeStatesRef) {
    ArrayList<SCEvent> list = new ArrayList<SCEvent>();
    int topPriority = Integer.MIN_VALUE;

    for (int activeStateRef = activeStatesRef; activeStateRef != MJIEnv.NULL;
             activeStateRef = env.getReferenceField(activeStateRef, "next")){

      for (int stateRef=activeStateRef; stateRef != MJIEnv.NULL; stateRef = env.getReferenceField(stateRef,"superState")){
        int qRef = env.getReferenceField(stateRef, "pendingEvents"); // the pending queue of the state
        int eRef = env.getReferenceField(qRef, "head"); // the first EventSpec entry of the queue

        if (eRef != MJIEnv.NULL) {
          int prio = env.getIntField(eRef, "priority");

          if (prio > topPriority) { // we have a new top 
            topPriority = prio;
            list.clear();
            SCEvent sce = createSCEventFromPending(env,eRef,stateRef);
            list.add( sce);
            
          } else if (prio == topPriority) {
            SCEvent sce = createSCEventFromPending(env,eRef,stateRef);
            if (!list.contains(sce)) { // don't add twice
              list.add( sce);
            }
          }

          if (!sendSuper) {
            break;
          }
        }
      }
    }

    return list;
  }

  
  //--- those are the per-active-state policies (that actually might give us choices)
  
  /**
   * add the highest prioritized pending event for a single active state and all it's parents
   */
  void addTopPriorityPendingEvent (MJIEnv env, ArrayList<SCEvent> list, int activeStateRef) {
    int topPriority = Integer.MIN_VALUE;
    int eRefTop = MJIEnv.NULL;
    int stateRefTop = MJIEnv.NULL;

    for (int stateRef=activeStateRef; stateRef != MJIEnv.NULL; stateRef = env.getReferenceField(stateRef,"superState")){
      int qRef = env.getReferenceField(stateRef, "pendingEvents"); // the pending queue of the state
      int eRef = env.getReferenceField(qRef, "head"); // the first EventSpec entry of the queue

      if (eRef != MJIEnv.NULL) {
        int prio = env.getIntField(eRef, "priority");
        if (prio > topPriority) { // use substate if priority is the same 
          topPriority = prio;
          eRefTop = eRef;
          stateRefTop = stateRef;
        }
      }
    }

    if (eRefTop != MJIEnv.NULL) {
      SCEvent sce = createSCEventFromPending(env,eRefTop,stateRefTop);
      if (!list.contains(sce)) { // don't add twice
        list.add( sce);
      }            
    }    
  }

  /**
   * add the first pending event we can find for an active state and all it's parents
   */
  void addFirstParentChainPendingEvent (MJIEnv env, ArrayList<SCEvent> list, int activeStateRef) {
    for (int stateRef=activeStateRef; stateRef != MJIEnv.NULL; stateRef = env.getReferenceField(stateRef,"superState")){
      int qRef = env.getReferenceField(stateRef, "pendingEvents"); // the pending queue of the state
      int eRef = env.getReferenceField(qRef, "head"); // the first EventSpec entry of the queue

      if (eRef != MJIEnv.NULL) {
        SCEvent sce = createSCEventFromPending(env,eRef,stateRef);
        if (!list.contains(sce)) { // don't add twice
          list.add( sce);
        }
        break;
      }
    }    
  }
  
  /**
   * add all pending events for the an active state and all it's parents
   */
  void addParentChainPendingEventSets (MJIEnv env, ArrayList<SCEvent> list, int activeStateRef) {
    for (int stateRef=activeStateRef; stateRef != MJIEnv.NULL; stateRef = env.getReferenceField(stateRef,"superState")){

      int qRef = env.getReferenceField(stateRef, "pendingEvents"); // the pending queue of the state
      int eRef = env.getReferenceField(qRef, "head"); // the first EventSpec entry of the queue
      
      while (eRef != MJIEnv.NULL){
        SCEvent sce = createSCEventFromPending(env,eRef,activeStateRef);
        if (!list.contains(sce)) { // don't add twice
          list.add( sce);
        }
        eRef = env.getReferenceField(eRef, "next");
      }
    }
  }

  /**
   * add the first pending event (if any) of a given active state
   */
  void addPendingEvent (MJIEnv env, ArrayList<SCEvent> list, int activeStateRef) {
    int qRef = env.getReferenceField(activeStateRef, "pendingEvents"); // the pending queue of the state
    int eRef = env.getReferenceField(qRef, "head"); // the first EventSpec entry of the queue

    if (eRef != MJIEnv.NULL) {
      SCEvent sce = createSCEventFromPending(env,eRef,activeStateRef);
      if (!list.contains(sce)) { // don't add twice
        list.add( sce);
      }            
    }
  }
  
  /**
   * add all pending events (if any) of a given active state
   */
  void addPendingEventSet (MJIEnv env, ArrayList<SCEvent> list, int activeStateRef) {
    int qRef = env.getReferenceField(activeStateRef, "pendingEvents"); // the pending queue of the state
    int eRef = env.getReferenceField(qRef, "head"); // the first EventSpec entry of the queue

    while (eRef != MJIEnv.NULL){
      SCEvent sce = createSCEventFromPending(env,eRef,activeStateRef);
      if (!list.contains(sce)) { // don't add twice
        list.add( sce);
      }
      eRef = env.getReferenceField(eRef, "next");
    }          
  }
  
  
  /**
   * the main policy method for processing of explicitly sent events
   * NOTE - this has nothing to do with UML
   * NOTE - this is a mess, policies have to be cleaned up
   */
  SCEventGenerator createCGFromPendingEvents (String id, MJIEnv env, int activeStatesRef) {
    
    if (totalOrder) { // this is not a real choice, it only returns one event max
      SCEvent e = getFirstTopPriorityPendingEvent(env, activeStatesRef);
      if (e == null) {
        return null;
      } else {
        return new SCEventSingleChoice(id, e);
      }
      
    } else if (topPriority) { // all events with the same top priority (this could be a choice)
      ArrayList<SCEvent> list = getAllTopPriorityPendingEvents(env, activeStatesRef);
      return createCGFromEvents(id, list);
      
    } else { // all other policies might have events per active state, i.e. more than one choice 
      ArrayList<SCEvent> list = new ArrayList<SCEvent>();

      // process all active states
      for (int activeStateRef = activeStatesRef; activeStateRef != MJIEnv.NULL;
               activeStateRef = env.getReferenceField(activeStateRef, "next")){

        if (sendSuper) { // process the state and all it's parentStates
          if (sendQueue) { // add only the first pending event
            if (localPriorities) { // add only the top priority event in the parent chain
              addTopPriorityPendingEvent(env, list, activeStateRef);            
            } else { // add first event we can find in hierarchy
              addFirstParentChainPendingEvent(env, list, activeStateRef);
            }

          } else { // add all pending events at this level (sendQueue==false)
            addParentChainPendingEventSets(env, list, activeStateRef);
          }

        } else { // process only the active state itself, no parents (sendSuper==false)

          if (sendQueue) { // add only the first pending event (if any)
            addPendingEvent(env,list,activeStateRef);
          } else { // add *all* pending events of this state
            addPendingEventSet(env,list,activeStateRef);
          }
        }
      }  

      return createCGFromEvents(id, list);
    }
  }

  // <2do> not so good - this is redundant to PendingEvent
  public static final int PRIO_MAX = 10;
  public static final int PRIO_NORM = 5;
  public static final int PRIO_MIN = 0;

  public int getEventPriority (MJIEnv env, int objRef,
                               int senderRef, int receiverRef,
                               int eventNameRef,
                               int argsRef) {

    if (sendQueue && selfPriority){
      if (senderRef == receiverRef){
        return PRIO_MAX;
      }
    }

    return PRIO_NORM;
  }


  SCEventGenerator createCGFromAlphabet(String id, MJIEnv env, int activeStatesRef){
    ArrayList<SCEvent> events = getActiveAlphabet(env, activeStatesRef, null, null);
    return createCGFromEvents(id, events);
  }

  SCEventGenerator createCGFromAlphabet(String id, MJIEnv env, int activeStatesRef,
                                        StringSetMatcher receiverMatcher, StringSetMatcher idMatcher){
    ArrayList<SCEvent> events = getActiveAlphabet(env, activeStatesRef, receiverMatcher, idMatcher);
    return createCGFromEvents(id, events);
  }


  //--- the model StateMachine interface

  public boolean initialize (MJIEnv env, int objRef, int argRef) {
    Config conf = env.getConfig();

    String scriptName = null;
    scriptName = conf.getString("sc.script");

    events = new DynamicObjectArray<HashSet<SCEvent>>();

    if (scriptName != null) {
      try {
        scriptEnv = new SCScriptEnvironment(scriptName);
        scriptEnv.registerListener(env.getJPF());
        scriptEnv.parseScript();

        return true;
      } catch (FileNotFoundException fnfx) {
        log.severe("script file not found: " + scriptName);
        return false;
      } catch (ESParser.Exception e) {
        log.severe(e.toString());
        return false;
      }
    } else {
      // script-less (unguided) model checking
      return true;
    }
  }

  // we have to postpone our property init until here since we need the state map
  public void startRun (MJIEnv env, int objRef) {
    Config conf = env.getConfig();

    // note this only works because we don't ever collect any State objects and backtrack past were
    // they were created
    alwaysVisit = getCheckReferences(conf, "sc.always");
    neverVisit = getCheckReferences(conf, "sc.never");

    checkNoActiveStates = conf.getBoolean("sc.no_active");
    checkNoUnhandledEvents = conf.getBoolean("sc.no_unhandled");

    // send event policies
    sendQueue = conf.getBoolean("sc.send_queue", true);
    sendSuper = conf.getBoolean("sc.send_super", true);
    selfPriority = conf.getBoolean("sc.self_priority", true);
    
    String priorityBase = conf.getString("sc.priority_base");
    if ("local".equals(priorityBase)) {
      localPriorities = true;
    } else if ("top".equals(priorityBase)) {
      topPriority = true;
    } else if ("total".equals(priorityBase)) {
      totalOrder = true;
    } else {
      // default is state pendingEvents queue order
    }

    showMachine = conf.getBoolean("sc.show_machine", false);
    if (showMachine) {
      showMachine(env, objRef);
    }
    
    maxSteps = conf.getInt("sc.max_steps", -1);
  }

  // <2do> this isn't yet thought out, but we should have some way to
  // print the composition before we get a gazillion of related log messages
  void showState (MJIEnv env, int stateRef, int level) {
    StringBuilder sb = new StringBuilder();

    boolean isActive = env.getBooleanField(stateRef, "isActive");
    sb.append(isActive ? '*' : ' ');

    for (int i=0; i<level; i++) {
      sb.append(". ");
    }

    int tnRef = env.getReferenceField(stateRef, "typeName");
    String typeName = env.getStringObject(tnRef);
    typeName = typeName.substring(typeName.lastIndexOf('.')+1);
    sb.append(typeName);

    int fnRef = env.getReferenceField(stateRef, "fieldName");
    if (fnRef != MJIEnv.NULL) {
      String fieldName = env.getStringObject(fnRef);
      fieldName = fieldName.substring(fieldName.lastIndexOf('.')+1);
      sb.append( " : ");
      sb.append(fieldName);
    }

    log.info(sb.toString());

    int subArrayRef = env.getReferenceField(stateRef, "subStates");
    if (subArrayRef != MJIEnv.NULL) {
      int nSub = env.getArrayLength(subArrayRef);
      for (int i=0; i<nSub; i++) {
        int subRef = env.getReferenceArrayElement(subArrayRef, i);
        showState(env, subRef, level+1);
      }
    }
  }

  void showMachine (MJIEnv env, int objRef) {
    int masterRef = env.getReferenceField(objRef, "masterState");
    showState(env,masterRef, 0);
  }


  public void visitState (MJIEnv env, int objRef, int stateRef) {
    if (neverVisit != null) {
      for (int i=0; i<neverVisit.length; i++){
        if (neverVisit[i] == stateRef){ // we got a property violation
          int rName = env.getReferenceField(stateRef, "fieldName");
          String stateName = env.getStringObject(rName);
          env.throwAssertion("visited forbidden state: " + stateName);
        }
      }
    }
  }

  public void checkTermination (MJIEnv env, int objRef) {

    if (checkNoActiveStates) {
      int nActive = env.getIntField(objRef, "nActive");
      if (nActive > 0){
        int aRef = env.getReferenceField(objRef, "activeStates");
        String[] states = getStateNames(env,aRef);
        StringBuilder sb = new StringBuilder("active states upon termination: ");
        for (int i=0; i<states.length; i++){
          if (i>0) sb.append(',');
          sb.append(states[i]);
        }
        env.throwAssertion(sb.toString());
      }
    }

    // check if all the mandatory states have been visited
    if (alwaysVisit != null) {
      for (int i=0; i<alwaysVisit.length; i++){
        int stateRef = alwaysVisit[i];
        int visited = env.getIntField(stateRef,"visited");
        if (visited == 0){
          int rName = env.getReferenceField(stateRef, "fieldName");
          String stateName = env.getStringObject(rName);
          env.throwAssertion("unvisited mandatory state: " + stateName);
        }
      }
    }
  }

  // property checks upon step completion
  public void checkStep (MJIEnv env, int objRef, int nFired) {
    SystemState ss = env.getSystemState();

    if (nFired == 0) {

      SCEventGenerator cg = ss.getLastChoiceGeneratorOfType(SCEventGenerator.class);
      SCEvent event = cg.getNextChoice();
      if (event.isCompletion()){ // try signals
        log.info("completion did not fire, retry signal");
        env.setBooleanField(objRef,"wasGuardedCompletionStep", true);
      } else {
        if (checkNoUnhandledEvents){
          env.throwAssertion("unhandled event: " + event);
        } else {
          log.info("signal did not fire");
        }
      }
    }
  }

  static final String CG_ID = "getEnablingEvent";

  /**
   * set the CG that returns the events to try in the next StateMachine step
   * this is where we break JPF transitions
   */
  public boolean getEnablingEvent (MJIEnv env, int objRef) {
    SCEventGenerator cg = null;
    ThreadInfo ti = env.getThreadInfo();
    SystemState ss = env.getSystemState();

    int steps = env.getIntField(objRef, "step");
    int activeStatesRef = env.getReferenceField(objRef, "activeStates");

    if (maxSteps >= 0) {
      if (steps >= maxSteps) {
        // <2do> Hmm, maybe it should be a warning
        log.info("max step count reached, terminating");
        return false;
      }
    }
    
    if (!ti.isFirstStepInsn()) { // first time around, get the next SCEvent CG

      if (!wasGuardedCompletionStep(env,objRef) && hasCompletionTrigger(env,activeStatesRef)){
        // we don't need to bother the cgFactory - completion always has precedence
        cg = new SCEventSingleChoice( CG_ID, SCEvent.getCompletionEvent());

      } else { // no completion, so we need events

        if (scriptEnv != null) { // guided model checking - we run with a script

          // POLICY - we could also use this to override alphabet expansion
          // but it would conform less with the scriptless mode ("explore all
          // events the system can handle in its current active state(s)")
          cg = createCGFromPendingEvents( CG_ID, env,activeStatesRef); // try explicitly sent events first

          if (cg == null) { // no explicitly sent events pending, consult factory
            String[] stateNames = getStateNames(env,activeStatesRef);
            BitSet reEntered = getReEnteredStates(env,activeStatesRef);
            cg = scriptEnv.getNext( CG_ID, stateNames, reEntered);

            if (cg != null) {
              if (cg.hasWildcard()){
                cg = createCGFromAlphabet( CG_ID,env,activeStatesRef, cg.getReceiverMatcher(), cg.getIdMatcher());
              } else {
                if (hasTimeTrigger(env,activeStatesRef)){
                  cg = cg.add( SCEvent.getTimeoutEvent());
                }
              }

            } else {
              if (hasTimeTrigger(env,activeStatesRef)){
                cg = new SCEventSingleChoice( CG_ID, SCEvent.getTimeoutEvent());
              }
            }
          }

        } else { // scriptless - get the cg from the current active state alphabet
          // we don't need the pending events here, because all triggers are tried anyways
          // (we actually ignore sends if we run scriptless, so that they don't interfere with state matching)
          cg = createCGFromAlphabet(CG_ID,env, activeStatesRef);
        }
      }

      if (cg != null) {
        // to identify CG stream positions for state matching
        env.setIntField(objRef,"cgSequenceNumber", cg.getSequenceNumber());

        ss.setNextChoiceGenerator(cg);
        //ti.skipInstructionLogging();
        env.repeatInvocation();
        return true; // doesn't really matter, we get re-executed

      } else {
        log.info("no more events");
        return false;
      }

    } else { // re-executed, this is the beginning of the next transition

      // we already have a cg (with at least one choice left), retrieve it
      cg = env.getSystemState().getCurrentChoiceGenerator(CG_ID,SCEventGenerator.class);

      assert cg != null : "current CG no SCEventGenerator: " + env.getChoiceGenerator();

      if (log.isLoggable(Level.INFO)) {
        log.info("-------- next step: " + env.getStateId() + 
                  " (event depth : " + steps + ')');
        logActive(env,activeStatesRef);
        log.info("processing event: " + cg);
      }

      env.setBooleanField(objRef,"forceNewState",false);
      env.setBooleanField(objRef, "wasGuardedCompletionStep", false);

      // EventSpec recycling (pendingEvents.head -> pendingEvents.processedEvent)
      // normally this would be done in setEnablingEventProcessed(), but we
      // might have an activeState sending the same event during this step, which
      // would cause a new EventSpec object to be generated if we haven't
      // returned the currently processing one to the pool yet. It's still
      // correctly state matching in case of event loops, but might cause some
      // additional states because of the changed heap
      SCEvent sce = cg.getNextChoice();
      if (sce != null) {
        sce.setProcessed();
      }
    }

    return true;
  }
  
  public void setEnablingEventProcessed (MJIEnv env, int objRef) {
    // we can't remove sent events from their respective queues before
    // we get here, because it might cause premature state matching. Since
    // the event itself isn't in the state space, we have to keep the
    // EventSpec/target state in there until we're done with matching
    SCEventGenerator cg = null; // <2do> replace this once SystemState provides a better API
    for (ChoiceGenerator<?> c = env.getSystemState().getChoiceGenerator(); c != null; c = c.getPreviousChoiceGenerator()){
      if (CG_ID.equals(c.getId()) && c instanceof SCEventGenerator){
        cg = (SCEventGenerator)c;
        break;
      }
    }

    assert cg != null; // there had to be one

    SCEvent sce = cg.getNextChoice();
    if (sce != null) {
      // see getEnablingEvent() bottom half why we don't recycle the currently processing
      // event (EventSpec) here
      // sce.setProcessed();      
      logEventProcessed(sce);
    }
  }

  
  public void triggerFired (MJIEnv env, int objRef, int srcStateRef, int tgtStateRef) {
    // ..still nothing
  }

  public void executeEntryAction (MJIEnv env, int objRef, int stateRef) {
    executeStateMethod(env,objRef,stateRef, "entryAction()V", true);
  }

  public void executeExitAction (MJIEnv env, int objRef, int stateRef) {
    executeStateMethod(env,objRef,stateRef, "exitAction()V", true);
  }

  public void executeDoAction (MJIEnv env, int objRef, int stateRef) {
    executeStateMethod(env,objRef,stateRef, "doAction()V", true);
  }

  /**
   * check if the method name is in the wait event list. If it is, remove
   * it.
   *
   * <2do> so far, we only check for event names, i.e. don't deal with
   * parameter types yet
   *
   * @return true if the wait list is empty
   */
  boolean checkUnBlocked (MJIEnv env, int stateRef, MethodInfo mi) {
    String mname = mi.getName();
    int eRef = env.getReferenceField(stateRef, "waitEvent");
    int prev = MJIEnv.NULL;

    while (eRef != MJIEnv.NULL) {
      int nameRef = env.getReferenceField(eRef, "name");
      ElementInfo eiName = env.getElementInfo(nameRef);
      if (eiName.equalsString(mname)) {
        int next = env.getReferenceField(eRef, "next");
        if (prev == MJIEnv.NULL) { // first one
          env.setReferenceField(stateRef, "waitEvent", next);
          return (next == MJIEnv.NULL);
        } else {
          env.setReferenceField(prev, "next", next);
          return false; // at least the previous waitEvent remains
        }
      }

      prev = eRef;
      eRef = env.getReferenceField(eRef, "next");
    }

    return true; // nothing to wait for
  }

  static final String TRIGGER_ACTION = "[TriggerAction]";

  /**
   * check if there is a method that could fire on the currently tested signal.
   * answer true if we would look at the parent state in case we
   */
  public void executeTrigger (MJIEnv env, int objRef, int stateRef) {

    ThreadInfo ti = env.getThreadInfo();
    Instruction insn = ti.getPC();
    DirectCallStackFrame frame = ti.getReturnedDirectCall();

    if (frame == null) {

      // note the cg was actually set by the last getEnablingEvents call, not here.
      SCEventGenerator cg = env.getSystemState().getLastChoiceGeneratorOfType(SCEventGenerator.class);
      assert cg != null;

      SCEvent e = cg.getNextChoice();
      if (e == null) {
        return; // cg might be an empty SCEventFromAlphabet
      }

      if (!e.isCompletion()){
        // completion events are kind of artificial, so only set
        // curEvent if it was a real one
        curEvent = e;
      }

      if (isMatchingReceiver(env,stateRef,e)){

        while (stateRef != -1) { // we recursively look this up on both the enclosing and class hierarchy
          ClassInfo ci = env.getClassInfo(stateRef);

          if ((scriptEnv != null) && !checkEvent(env,stateRef,e,true)){
            // we only need to force this if we have imperative event sequences
            // that do not have ANYs, i.e. where we could end up cutting a sequence
            // short because it gets state matched. Always playing a sequence to the end
            // doesn't work either, because that doesn't take care of cycles
            env.setBooleanField(objRef,"forceNewState",true);
          }

          // we have to iterate explicitly because we don't know about the return type (signature)
          for (MethodInfo mi : ci) {
            if (isMatchingMethod(mi,e)) {

              // the checkUnBlocked is down here because if we ever want to deal
              // with argument types, we better make sure we don't unblock unless
              // we really execute a corresponding trigger method
              if (checkUnBlocked(env, stateRef, mi)) {
                logTrigger(env,stateRef,mi);

                e.setConsumed(true);

                frame = ci.createDirectCallStackFrame(ti, mi, 0);

                frame.pushRef(stateRef);
                pushArgs(env, frame, e.getArguments());
                ti.pushFrame(frame);

                env.repeatInvocation();
              }
              return;
            }
          }

          if (e.isCompletion()){
            return; // no recursive superState lookup
          } else {
            stateRef = env.getReferenceField(stateRef, "superState");
          }
        }
      }
    }
  }

  public int getEventId (MJIEnv env, int objRef){
    if (curEvent == null){
      return MJIEnv.NULL;
    } else {
      return env.newString(curEvent.getId());
    }
  }

  public int getEventArguments (MJIEnv env, int objRef){
    if (curEvent == null){
      return MJIEnv.NULL;
    } else {
      Object[] args = curEvent.getArguments();
      if (args == null){
        return env.newObjectArray("java.lang.Object",0);
      } else {
        int aref = env.newObjectArray("java.lang.Object",args.length);
        for (int i=0; i<args.length; i++){
          Object a = args[i];
          if (a instanceof Boolean){
            env.setReferenceArrayElement(aref,i, env.newBoolean(((Boolean)a).booleanValue()));
          } else if (a instanceof Integer) {
            env.setReferenceArrayElement(aref,i, env.newInteger(((Integer)a).intValue()));
          } else if (a instanceof Double) {
            env.setReferenceArrayElement(aref,i, env.newDouble(((Double)a).doubleValue()));
          } else if (a instanceof String) {
            env.setReferenceArrayElement(aref,i, env.newString((String)a));
          }
        }
        return aref;
      }
    }
  }

  /*
   * NOTE: this has to revert the order of 'nextActives', which is LIFO
   */
  public boolean updateActiveStates (MJIEnv env, int objRef) {
    int sNext;
    int nextActiveRef = env.getReferenceField(objRef, "nextActives");
    int activeRef = MJIEnv.NULL;

    for (; nextActiveRef != MJIEnv.NULL; nextActiveRef = sNext) {
      sNext = env.getReferenceField( nextActiveRef, "next");
      env.setReferenceField(nextActiveRef, "next", activeRef);
      activeRef = nextActiveRef;
    }

    env.setReferenceField(objRef, "activeStates", activeRef);
    env.setReferenceField(objRef, "nextActives", MJIEnv.NULL);

    return (activeRef != MJIEnv.NULL);
  }

  public boolean supportsSendEvent (MJIEnv env, int objRef) {
    // if we run scriptless, there's no point in sending events - we try everything
    // that's handled anyways
    return (scriptEnv != null);
  }

}
