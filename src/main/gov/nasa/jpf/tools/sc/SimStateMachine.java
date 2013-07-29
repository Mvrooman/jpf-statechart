//
// Copyright  (C) 2006 United States Government as represented by the
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
package gov.nasa.jpf.tools.sc;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.JPFShell;
import gov.nasa.jpf.jvm.MJIEnv;
import gov.nasa.jpf.jvm.choice.sc.SCEvent;
import gov.nasa.jpf.jvm.choice.sc.SCEventFromSet;
import gov.nasa.jpf.jvm.choice.sc.SCEventGenerator;
import gov.nasa.jpf.jvm.choice.sc.SCEventSingleChoice;
import gov.nasa.jpf.jvm.choice.sc.SCScriptEnvironment;
import gov.nasa.jpf.jvm.choice.sc.SentSCEvent;
import gov.nasa.jpf.sc.EventSpec;
import gov.nasa.jpf.sc.PendingEventQueue;
import gov.nasa.jpf.sc.State;
import gov.nasa.jpf.sc.StateMachine;
import gov.nasa.jpf.util.StringSetMatcher;
import gov.nasa.jpf.util.script.ESParser;
import gov.nasa.jpf.util.script.Event;

/**
 * standalone state machine simulator
 * SimStateMachine uses the same model classes like JPF, so make sure
 * you have the model classes in the classpath when you run it. There
 * is an interactive mode that lets you choose the next event on every step
 */
public class SimStateMachine extends StateMachine implements JPFShell {

  static final String ALL = "<all>";

  static Random random = new Random(42);

  // send event policies
  static boolean sendQueue; // do we try only the first sent event
  static boolean selfPriority; // queued self sends have priority (KC behavior)
  static boolean sendSuper; // do we also try events sent to super states

  // how do we handle priorities of explicitly sent events?
  static boolean localPriorities; // use the highest priority of each active state hierarchy
  static boolean topPriority;   // all events of all actives states with the same top priority
  static boolean totalOrder; // use only the first event of the highest priority of all active states 
  
  static boolean showMachine; // show the structure of the machine

  boolean runInteractive = false; // query for user input when there are choices (otherwise it picks choices randomly)
  boolean autoSingleChoice = false; // only query user input if there is more than one choice
  
  BufferedReader in; // for input commands
  PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out)); // for simplistic logging

  // some ad hoc property checks
  boolean checkNoActiveStates;
  boolean checkNoUnhandledEvents;
  HashSet<String> neverVisit;
  HashSet<String> alwaysVisit;
  int maxSteps;
  
  // this is where we get our events from (usually scripted)
  SCScriptEnvironment scriptEnv;


  SCEventGenerator curCG;
  SCEvent curEvent;

  boolean guardedCompletion;
  
  public SimStateMachine (Config config){
    if (!initialize( config)){
      throw new RuntimeException("statemachine did not initialize");
    }
  }
  
  public void start(String[] args){
    arguments = args;
    run();
  }
  
  public static void main (String[] args) {
    Config config = JPF.createConfig(args);
    SimStateMachine machine = new SimStateMachine(config);
    machine.start( args);
  }

  // the StateMachine NativePeer interface, this time pure Java
  protected boolean initialize (Config conf) {
    int i=0;
    String[] args = conf.getTargetArgs();
    
    if (args.length >= 1) {
      
      State master = createMasterState(args[i]);
      if (master == null) {
        log("masterState did not instantiate: " + args[i]);
        return false;
      }
      setMasterState(master);

      String scriptFileName = conf.getString("sc.script");
      
      if (scriptFileName != null){
        
        String mode = conf.getString("sc.sim_mode", "interactive");
        if ("interactive".equalsIgnoreCase(mode)) {
          runInteractive = true;        
        } else if ("auto".equalsIgnoreCase(mode)) {
          runInteractive = true;
          autoSingleChoice = true;
        }
        
        try {
          scriptEnv = new SCScriptEnvironment(scriptFileName);
          scriptEnv.parseScript();

        } catch (FileNotFoundException fnfx) {
          log("script file not found: " + scriptFileName);
          return false;
        } catch (ESParser.Exception e) {
          log("error while parsing script file: " + scriptFileName + " : " + e.getMessage());
          return false;
        }
      } else {
        runInteractive = true;
      }

      //-- initialize property data
      String[] set = conf.getStringArray("sc.always");
      if (set != null) {
        alwaysVisit = new HashSet<String>();
        for (String s: set) alwaysVisit.add(s);
      }

      set = conf.getStringArray("sc.never");
      if (set != null) {
        neverVisit = new HashSet<String>();
        for (String s: set) neverVisit.add(s);
      }

      checkNoActiveStates = conf.getBoolean("sc.no_active");
      checkNoUnhandledEvents = conf.getBoolean("sc.no_unhandled");

      // send event policies
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
      
      maxSteps = conf.getInt("sc.max_steps", -1);

      showMachine = conf.getBoolean("sc.show_machine", false);

      if (runInteractive) { // <2do> abstract so that we can use it in a UI
        in = new BufferedReader(new InputStreamReader(System.in));
      }

      return true;

    } else { // no target class specified
      printUsage();
      return false;
    }
  }

  protected void startRun() {
    if (showMachine) {
      showMachine();
    }
  }

  String readInput() {
    try {
      String input =  in.readLine();
      return input;
    } catch (IOException x) {
      return null;
    }
  }


  int getNextEvent (SCEventGenerator cg) {
    int n = cg.getTotalNumberOfChoices();
    int r=0;

    if (runInteractive) {
      
      if ((cg.getTotalNumberOfChoices() < 2) && autoSingleChoice) {
        return 0; // automatically return the only choice
      }
      
      out.print("[SC] enter command: " + cg.toShortString() + " >");
      out.flush();

      while (true) {
        String input = readInput();
        if (input.length() > 0) {

          if ("q".equals(input) || ".".equals(input) || "x".equals(input)) {
            log("quitting..");
            System.exit(0);
          }

          try {
            r = Integer.parseInt(input);
          } catch (NumberFormatException x) {
            r = -1;
          }
          
          if (r <= 0 || r > n) {
            out.print("[SC] illegal input (valid numbers [1.." + n + "] or 'q' for quit), try again:");
            out.flush();
          } else {
            return r-1;
          }
        } else {
          // <cr> means we do a random selection
          break;
        }
      }
    }

    if (n > 0) {
      return random.nextInt(n);
    } else {
      return -1;
    }
  }

  public String getEventId (){
    if (curEvent != null){
      return curEvent.getId();
    } else {
      return null;
    }
  }

  public Object[] getEventArguments (){
    if (curEvent != null){
      Object[] args = curEvent.getArguments();
      if (args != null){
        return args;
      } else {
        return new Object[0];
      }
    } else {
      return null;
    }
  }

  ArrayList<SCEvent> getAlphabet (State first,
                                  StringSetMatcher receiverMatcher, StringSetMatcher idMatcher){
    ArrayList<State> states = new ArrayList<State>();

    for (State s = first; s!= null; s = s.getNext()) {
      states.add(s);
    }

    return getAlphabet(states, receiverMatcher, idMatcher);
  }

  ArrayList<SCEvent> getAlphabet (ArrayList<State> states,
                                  StringSetMatcher receiverMatcher, StringSetMatcher idMatcher){
    String fName = null;
    ArrayList<SCEvent> alphabet = new ArrayList<SCEvent>();

    for (State s : states) {
      fName = s.getFieldName();

      if (receiverMatcher != null) {
        if (!receiverMatcher.matchesAny(fName)) {
          continue;
        }
      }

      Class<?> cls = s.getClass();
      for (Method m : cls.getDeclaredMethods()) {
        int mod = m.getModifiers();
        if ((mod & (Modifier.PUBLIC | Modifier.STATIC)) == Modifier.PUBLIC) {
          String mName = m.getName();

          if (mName.equals("completion") ||
              mName.equals("entryAction") ||
              mName.equals("exitAction") ||
              mName.equals("doAction")) {
            continue;
          }

          if (idMatcher != null) {
            if (!idMatcher.matchesAny(mName)) {
              continue;
            }
          }

          String rc = (receiverMatcher != null) ? fName : null;

          Class<?>[] paramTypes = m.getParameterTypes();
          if (paramTypes.length > 0) {
            boolean allBoolean = true;
            for (int j=0; allBoolean && (j<paramTypes.length); j++){
              allBoolean = (paramTypes[j] == boolean.class);
            }

            String[] paramValues = getParamValuesFromAnnotation(m);
            if (paramValues != null){
              Event ev = new Event(null, mName, paramValues, 0); // <2do> lineno would be nice
              for (Event e : ev.expand()) {
                // maybe we should do some type checking here
                SCEvent sce = new SCEvent(e);
                sce.addUniqueTo(alphabet, rc);
              }

            } else if (allBoolean) {
              for (Object[] args : Event.getBooleanArgVariations(paramTypes.length)){
                SCEvent e = new SCEvent(mName, args);
                e.addUniqueTo(alphabet, rc);
              }

            } else {  // no annotations, we're lost here, no way to guess parameters generically
              continue;
            }

          } else { // no method parameters, no need to go through the expansion hassle
            SCEvent e = new SCEvent(mName);
            e.addUniqueTo(alphabet, rc);
          }
        }
      }
    }

    return alphabet;
  }

  String[] getParamValuesFromAnnotation (Method m) {
    State.Params ann = m.getAnnotation(State.Params.class);
    if (ann != null){
      String v = ann.value();
      if ((v != null) && (v.length() > 0)){
        String[] values= v.split(","); // can't break on blanks (String literals might contain them)
        for (int i=0; i<values.length; i++){
          values[i] = values[i].trim();
        }
        return values;
      }
    }

    return null;
  }

  boolean hasCompletionTrigger() {
    for (State s = activeStates; s!=null; s=s.getNext()) {
      Class<?> cls = s.getClass();
      try {
        if (cls.getDeclaredMethod(SCEvent.COMPLETION) != null){
          return true;
        }
      } catch (NoSuchMethodException x) {}
    }
    return false;
  }

  boolean hasTimeTrigger() {
    for (State s = activeStates; s!=null; s=s.getNext()) {
      for (State state = s; state!= null; state = state.getSuperState()) {
        Class<?> cls = state.getClass();
        try {
          if (cls.getDeclaredMethod(SCEvent.TIMEOUT) != null){
            return true;
          }
        } catch (NoSuchMethodException x) {}
      }
    }
    return false;
  }

  SCEventGenerator createCGFromAlphabet (String id, StringSetMatcher receiverMatcher, StringSetMatcher idMatcher) {
    ArrayList<SCEvent> events = getAlphabet(activeStates, receiverMatcher, idMatcher);

    int n = events.size();
    if (n == 0) {
      return null;
    } else if (n == 1) {
      return new SCEventSingleChoice(id, events.get(0));
    } else {
      return new SCEventFromSet(id, events);
    }
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

  //--- lots of pending event processing policies to follow
  // NOTE: this is not just a mess, but also terribly redundant to NativeStateMachine
  
  SentSCEvent createSCEvent( State state, EventSpec e) {
    return new SentSCEvent(state, e, state.getFieldName(), e.getName(), e.getArgs());
  }
  
  /**
   * this is a questionable policy, since it doesn't leave us any choices. It
   * indicates a procedural model
   */
  void addFirstTopPriorityPendingEvent (ArrayList<SCEvent> list) {
    int topPriority = Integer.MIN_VALUE;
    EventSpec eTop = null;
    State stateTop = null;
    
    // for same top priority, the activeState order counts (= makeInitial(..) order) 
    for (State activeState = activeStates; activeState != null; activeState = activeState.getNext()) {

      for (State state=activeState; state != null; state = state.getSuperState()){
        EventSpec e = state.getPendingEventQueue().peekFirst();

        if (e != null) {
          int prio = e.getPriority();
          if (prio > topPriority) { // use substate if priority is the same 
            topPriority = prio;
            eTop = e;
            stateTop = state;
          }
        }
        
        if (!sendSuper) {
          break;
        }
      }
    }
    
    if (eTop != null) {
      list.add(createSCEvent(stateTop, eTop));
    }
  }

  void addAllTopPriorityPendingEvents (ArrayList<SCEvent> list) {
    int topPriority = Integer.MIN_VALUE;

    for (State activeState = activeStates; activeState != null; activeState = activeState.getNext()) {

      for (State state=activeState; state != null; state = state.getSuperState()){
        EventSpec e = state.getPendingEventQueue().peekFirst();

        if (e != null) {
          int prio = e.getPriority();

          if (prio > topPriority) { // we have a new top 
            topPriority = prio;
            list.clear();
            list.add( createSCEvent(state,e));
            
          } else if (prio == topPriority) {
            SentSCEvent sce = createSCEvent(state,e);
            if (!list.contains(sce)) { // don't add twice (sent to same parent)
              list.add( sce);
            }
          }

          if (!sendSuper) {
            break;
          }
        }
      }
    }
  }

  
  //--- those are the per-active-state policies (that actually might give us choices)
  
  /**
   * add the highest prioritized pending event for a single active state and all it's parents
   */
  void addTopPriorityPendingEvent (ArrayList<SCEvent> list, State activeState) {
    int topPriority = Integer.MIN_VALUE;
    EventSpec eTop = null;
    State stateTop = null;

    for (State state=activeState; state != null; state = state.getSuperState()){
      EventSpec e = state.getPendingEventQueue().peekFirst();

      if (e != null) {
        int prio = e.getPriority();
        if (prio > topPriority) { // use substate if priority is the same 
          topPriority = prio;
          eTop = e;
          stateTop = state;
        }
      }
    }

    if (eTop != null) {
      SentSCEvent sce = createSCEvent(stateTop, eTop);
      if (!list.contains(sce)) { // don't add twice
        list.add( sce);
      }            
    }    
  }

  /**
   * add the first pending event we can find for an active state and all it's parents
   */
  void addFirstParentChainPendingEvent (ArrayList<SCEvent> list, State activeState) {
    for (State state=activeState; state != null; state = state.getSuperState()){
      EventSpec e = state.getPendingEventQueue().peekFirst();

      if (e != null) {
        SentSCEvent sce = createSCEvent(state, e);
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
  void addParentChainPendingEventSets (ArrayList<SCEvent> list, State activeState) {
    for (State state=activeState; state != null; state = state.getSuperState()){
      EventSpec e = state.getPendingEventQueue().peekFirst();

      while (e != null) {
        SentSCEvent sce = createSCEvent(state, e);
        if (!list.contains(sce)) { // don't add twice
          list.add( sce);
        }
        e = e.getNext();
      }
    }
  }

  /**
   * add the first pending event (if any) of a given active state
   */
  void addPendingEvent (ArrayList<SCEvent> list, State state) {
    EventSpec e = state.getPendingEventQueue().peekFirst();

    if (e != null) {
      SentSCEvent sce = createSCEvent(state, e);
      if (!list.contains(sce)) { // don't add twice
        list.add( sce);
      }            
    }
  }
  
  /**
   * add all pending events (if any) of a given active state
   */
  void addPendingEventSet (ArrayList<SCEvent> list, State state) {
    EventSpec e = state.getPendingEventQueue().peekFirst();

    while (e != null) {
      SentSCEvent sce = createSCEvent(state, e);
      if (!list.contains(sce)) { // don't add twice
        list.add( sce);
      }
      e = e.getNext();
    }
  }

  
  /**
   * the main policy method for processing of explicitly sent events
   * NOTE - this has nothing to do with UML
   * NOTE - this is a mess, policies have to be cleaned up
   */  
  SCEventGenerator createCGFromPendingEvents (String id) {
    ArrayList<SCEvent> list = new ArrayList<SCEvent>();
    
    if (totalOrder) { // this is not a real choice, it only returns one event max
      addFirstTopPriorityPendingEvent(list);
      
    } else if (topPriority) { // all events with the same top priority (this could be a choice)
      addAllTopPriorityPendingEvents(list);
      
    } else { // all other policies might have events per active state, i.e. more than one choice 

      // process all active states
      for (State activeState = activeStates; activeState != null; activeState = activeState.getNext()) {

        if (sendSuper) { // process the state and all it's parentStates
          if (sendQueue) { // add only the first pending event
            if (localPriorities) { // add only the top priority event in the parent chain
              addTopPriorityPendingEvent(list, activeState);            
            } else { // add first event we can find in hierarchy
              addFirstParentChainPendingEvent(list, activeState);
            }

          } else { // add all pending events at this level (sendQueue==false)
            addParentChainPendingEventSets(list, activeState);
          }

        } else { // process only the active state itself, no parents (sendSuper==false)

          if (sendQueue) { // add only the first pending event (if any)
            addPendingEvent(list, activeState);
          } else { // add *all* pending events of this state
            addPendingEventSet(list, activeState);
          }
        }
      }  
    }

    return createCGFromEvents(id,list);
  }

  
  // we only do the logging here
  public void sendEvent (State target, String eventName, Object[] args){
    StringBuilder sb = new StringBuilder();
    sb.append("send ");
    sb.append(eventName);
    sb.append('(');
    if (args != null){
      for (int i=0; i<args.length; i++){
        if (i > 0) sb.append(',');
        sb.append(args[i]);
      }
    }
    sb.append(')');
    sb.append(" to: ");
    sb.append(target.getName() != null ? target.getName() : "ALL");

    log(sb.toString());
  }


  static final String CG_ID = "getEnablingEvent";

  protected boolean getEnablingEvent() {
    SCEventGenerator cg = null;
    String[] stateNames = getStateNames(activeStates);
    BitSet isReEntered = getReEnteredStates(activeStates);
    logActive();

    if ((maxSteps >= 0) && (step >= maxSteps)) {
      log("max step count reached, terminating");
      return false;
    }
    
    if (!guardedCompletion && hasCompletionTrigger()) {
      cg = new SCEventSingleChoice(CG_ID, SCEvent.COMPLETION_EVENT);

    } else { // no completion - need events
      guardedCompletion = false;

      if (scriptEnv != null) { // we run guided by a script

        // POLICY - see NativeStateMachine for discussion
        cg = createCGFromPendingEvents(CG_ID);

        if (cg == null) { // no explicitly sent events, consult factory (script)
          cg = (SCEventGenerator) scriptEnv.getNext(CG_ID,stateNames,isReEntered);

          if (cg != null) {
            if (cg.hasWildcard()){
              cg = createCGFromAlphabet(CG_ID,cg.getReceiverMatcher(), cg.getIdMatcher());
            } else {
              if (hasTimeTrigger()){
                cg = cg.add( SCEvent.getTimeoutEvent());
              }
            }
          } else {
            if (hasTimeTrigger()){
              cg = new SCEventSingleChoice( CG_ID,SCEvent.getTimeoutEvent());
            }
          }
        }

      } else { // scriptless
        cg = createCGFromAlphabet(CG_ID,null, null);
      }
    }

    curCG = cg;
    if (cg != null) {

      int r = getNextEvent(cg);
      if (r >=0 ){
        cg.reset();
        cg.select(r+1);
        log("processing event: " + cg.getNextChoice());
      }

      return true;
    } else {
      return false;
    }
  }

  protected void executeEntryAction (State state) {
    try {
      state.executeEntryAction();
    } catch (Throwable t) {
      executionError(t);
    }
  }

  protected void executeExitAction (State state) {
    try {
      state.executeExitAction();
    } catch (Throwable t) {
      executionError(t);
    }
  }

  protected void executeDoAction (State state) {
    try {
      state.executeDoAction();
    } catch (Throwable t) {
      executionError(t);
    }
  }

  /**
   * check if the method name is in the wait event list. If it is, remove
   * it.
   *
   * @return true if the wait list is empty
   */

  boolean checkUnBlocked (State s, Method m) {
    EventSpec e = s.getWaitEvent();
    EventSpec prev = null;

    while (e != null) {
      if (e.matches(m)) {
        if (prev == null) {
          s.removeWaitEvent();
          return (s.getWaitEvent() == null);
        } else {
          prev.setNext(e.getNext());
          return false;
        }
      }

      e = e.getNext();
    }

    return true;
  }

  /**
   * returns true if the current event has a matching signal trigger, which does not
   * necessarily mean the guards hold and we have a transition
   */
  protected void executeTrigger(State state) {

    assert curCG != null;
    SCEvent event = curCG.getNextChoice();
    curEvent = event;

    if (event != null) {
      if (isMatchingReceiver(state,event)) {

        while (state != null) {
          // we can't look this up directly because we don't know about the return type
          // note that we look this up in the enclosing *and* the class hierarchy
          for (Method m : state.getClass().getMethods()) {
            if (isMatchingMethod(m, event)) {

              if (checkUnBlocked(state, m)) {
                try {
                  log("state ", state.getFieldName(), " executes trigger: ", m.toString());

                  m.setAccessible(true); // we don't want IllegalAccessExceptions here
                  // we don't have to convert anything here (autoboxing)
                  m.invoke(state, event.getArguments());
                  return;
                } catch (Throwable t) {
                  executionError (t);
                }
              }
            }
          }

          if (event.isCompletion()) {
            return; // no recursive lookup
          } else {
            state = state.getSuperState();
          }
        }
      }
    }
  }
  
  protected void setEnablingEventProcessed( ) {
    if (curEvent != null) {
      curEvent.setProcessed();
      log("event processed: " + curEvent);
    }
  }
  
  void executionError (Throwable t) {
    if (t instanceof InvocationTargetException) {
      t = t.getCause();
    }

    t.printStackTrace();
    System.exit(1);
  }

  //--- our ad hoc property checks
  public void visitState (State state) {
    if (neverVisit != null) {
      assert !neverVisit.contains(state.getName()) : "visited forbidden state: " + state.getName();
    }
  }

  protected void checkTermination () {
    if (checkNoActiveStates) {
      assert (activeStates == null) : "active states at end of run: " + getStateNameList(activeStates);
    }

    if (alwaysVisit != null) {
      checkVisited(masterState);
    }
  }

  protected void checkStep (int nFired) {
    if (nFired == 0){
      SCEvent e = curCG.getNextChoice();
      if (e.isCompletion()){ // we had a guarded completion, try signals
        guardedCompletion=true;
      }

      if (checkNoUnhandledEvents) {
        assert true : "event not consumed";
      }
    }
  }

  void checkVisited (State state) {
    if (alwaysVisit != null){
      if (alwaysVisit.contains(state.getName())){
        assert (state.wasVisited()) : "unvisited mandatory state: " + state.getName();
      }
    }

    for (Field f : state.getClass().getDeclaredFields()) {
      Class<?> fType = f.getType();
      if (State.class.isAssignableFrom(fType) && !f.getName().startsWith("this$")) {
        f.setAccessible(true); // we don't want IllegalAccessExceptions
        try {
          State s = (State) f.get(state);
          checkVisited(s);
        } catch (IllegalAccessException iax) {} // Duhh
      }
    }
  }

  //----- helper methods

  boolean isMatchingMethod (Method m, SCEvent e) {

    if (m.getName().equals(e.getMethodName())){
      Type[] argTypes = m.getGenericParameterTypes();
      Object[] args = e.getArguments();
      if (args == null) {
        if (argTypes.length != 0){
          return false;
        } else {
          return true;
        }
      }
      if (args.length != argTypes.length) {
        return false;
      }
      for (int i=0; i<argTypes.length; i++) {

        if (args[i].getClass().isInstance(argTypes[i])) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  boolean isMatchingReceiver (State s, SCEvent e) {
    String[] rc = e.getReceiverConstraints();
    if (rc != null) {
      StringSetMatcher sm = new StringSetMatcher(rc);
      String sName = s.getFieldName();
      return sm.matchesAny(sName);
    }

    return true;
  }

  String[] getStateNames (State activeStates) {
    ArrayList<String> names = new ArrayList<String>();

    for (State s = activeStates; s!=null; s=s.getNext()) {
      names.add( s.getFieldName());
    }

    return names.toArray(new String[names.size()]);
  }

  BitSet getReEnteredStates (State activeStates) {
    BitSet isReEntered = new BitSet();
    int i=0;

    for (State s = activeStates; s!=null; s=s.getNext()) {
      isReEntered.set(i++, s.isReEntered());
    }

    return isReEntered;
  }

  //--- some minimal logging
  
  private static final ThreadLocal<StringBuilder> logBuffer = new ThreadLocal<StringBuilder>() {
    protected StringBuilder initialValue() {
      return new StringBuilder();
    }
  };
  
  public void logAppend (String msg) {
    StringBuilder buffer = logBuffer.get();
    buffer.append(msg);
  }
  
  public void log () {
    StringBuilder buffer = logBuffer.get();
    if (buffer.length() > 0) {
      log(buffer.toString());
      buffer.setLength(0);
    }
  }
  
  public void log (String message) {
    out.print("[SC] ");
    out.println(message);
    out.flush();
  }

  public void log (String s1, String s2) {
    log(s1 + s2);
  }

  public void log (String s1, String s2, String s3) {
    log(s1 + s2 + s3);
  }

  public void log (String s1, String s2, String s3, String s4) {
    log(s1 + s2 + s3 + s4);
  }

  void logActive () {
    log("------------ next step: " + step);

    for (State activeState = activeStates; activeState != null; activeState = activeState.getNext()) {
      StringBuilder sb = new StringBuilder(100);
      sb.append("active state ");
      sb.append(activeState.getFieldName());

      sb.append(" pending {");
      
      for (State s = activeState; s != null; s = s.getSuperState()) {
        PendingEventQueue q = s.getPendingEventQueue();
        if (q != null) {
          EventSpec e = q.peekFirst();
          boolean first = true;
          while (e != null) {
            if (!first) {
              sb.append(',');
            } else {
              first = false;

              if (s != activeState) {
                String sName = s.getFieldName();
                if (sName == null) {
                  sName = "ALL";
                }
                sb.append(", ");
                sb.append( sName);
                sb.append(":");
              }

            }
            sb.append(e.getName());
            sb.append('/');
            sb.append(e.getPriority());
            e = e.getNext();
          }
        }
      }
      sb.append("}, wait {");
      EventSpec e = activeState.getWaitEvent();
      boolean first = true;
      while (e != null) {
        if (!first) {
          sb.append(',');
        } else {
          first = false;
        }
        sb.append(e.getName());
        e = e.getNext();
      }
      sb.append('}');

      log(sb.toString());
    }
  }

  void logEvent (SCEvent e) {
    StringBuilder sb = new StringBuilder();
    sb.append("event: ");
    sb.append(e.toString());
    log(sb.toString());
  }

}
