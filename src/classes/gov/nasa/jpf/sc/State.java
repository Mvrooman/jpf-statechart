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

package gov.nasa.jpf.sc;

import gov.nasa.jpf.annotation.FilterField;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * this class is the root for all concrete states, no matter if composite
 * or simple. Normally, we would have a lot of functionality that now resides in
 * Environment implemented here in this class, but State is also used when
 * we execute/check under JPF, so we don't want any expensive computation,
 * additional (temporary) JPF-state, or leaking of host VM types (like SCEvent)
 *
 * this is library/framework code
 */
public abstract class State {

  /**
   * this marks a reference to a non- statemachine object that is controlled by it
   */
  @Retention(RetentionPolicy.RUNTIME)
  public @interface NoSubState {
  }

  /**
   * Annotation to specify parameter ranges for trigger methods in an regular
   * expression like syntax (see gov.nasa.jpf.util.script.StringExpander)
   */
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Params {
      String value();
  }


  public static Class[] NO_ARGUMENTS = new Class[0];
  public static final String COMPLETION_TRIGGER_MTH = "completion";
  public static final String ENTRY_ACTION_MTH = "entryAction";
  public static final String DO_ACTION_MTH = "doAction";
  public static final String EXIT_ACTION_MTH = "exitAction";

  @FilterField public static int nStates;

  //--- all the following fields are only set during system init, and never at runtime

  // set during machine registration (a consecutive number per machine, to
  // abstract away from the reference value, which is not backtracking-invariant)
  int id;

  // these are all set during instantiation and never touched again
  protected State superState; // our parent (not really required for nested class objects
                              // but we want to refer to it anonymously (i.e. w/o
                              // explicit type name)

  State[] initStates; // in case we have initial substates
  State[] subStates;  // a list of our subs, initialized via concrete State subclass fields
  StateMachine machine;    // that's our execution environment, which controls
                      // the trigger activation and keeps track of active states

  // those are used to identify state instances (e.g. for event lookup)
  // both are fully qualified, i.e. preserve the whole hierarchy
  String typeName;
  String fieldName;

  Method completionTrigger;
  Method entryActions;
  Method doActions;
  Method exitActions;

  @FilterField State next; // to build queues of states (like active and nextActive sets)

  //--- runtime - we have to be careful about these so that they don't interfere with state matching
  @FilterField boolean isEndState = false;

  // this is what we use for state matching - not the StateMachine 'activeStates'
  // 'isActive' is somewhat redundant to the 'activeStates'/'next' list, but:
  //  (1) WE ALSO SET PARENT STATES AS ACTIVE (activeStates only contains the leaf-states)
  //  (2) we don't want the order of states in 'activeStates' to be matching relevant per se
  //  (3) we also use the 'next' field for the 'nextActives' list
  //  (4) MOST IMPORTANT: this is updated strictly sequential in order of setNextState() execution,
  //      it reflects IF THE STATE WILL BE PROCESSED IN THE REMAINDER OF THIS STEP OR THE NEXT STEP 
  boolean isActive = false;

  // we actually want this in the state space so that we don't have to restore explicitly upon backtrack
  @FilterField int visited;

  // did we re-enter this state?
  @FilterField boolean isReEntered = false;

  //////////////////////////////////////////////// internal methods


  /**
   * has to be a default ctor so that we don't need to define ctors in concrete State classes
   */
  protected State () {
    // NOTE - we can't call initialize from here, because it uses reflection
    // on fields which are set in the concrete ctors (e.g. "this$.." for the
    // superState, but also all the sub states). This is the drawback of not
    // having ctors in the mdc's, but we don't want to proliferate the
    // model, assuming that dynamic state creation is far more unusual

    // field init has to be postponed until we set the machine

    id = nStates++;
  }

  // better make sure there is nothing in here that could screw state matching, since
  // it only gets called before executing trigger methods
  public void reset () {
    isEndState = false;
    isReEntered = false;
  }

  // don't call this in the ctor, it would fail miserably because the concrete
  // State fields have not been set yet
  void initialize() {

    setStateFields();
    setSpecialMethods();

    // at this point, the state machine should have been set already
    if (machine != null){
    	machine.registerState(this);
    }
  }

  /**
   * this uses reflection to init our generic fields from the concrete State class
   * (it's native for JPF execution)
   */
  void setStateFields () {
    Class<?> cls = getClass();

    typeName = cls.getName().replace('$', '.');

    ArrayList<State> list = new ArrayList<State>();

    // NOTE - we need to explicitly loop through the class hierarchy
    // because we might have a user defined state class hierarchy with potentially
    // masked State fields (masked states would otherwise not be initialized)
    for (; cls != State.class; cls = cls.getSuperclass()) {
      for (Field f : cls.getDeclaredFields()) {

        // somebody might store states in fields that do not denote sub-states
        if (f.getAnnotation(NoSubState.class) == null){
          Class<?> fType = f.getType();

          if (State.class.isAssignableFrom(fType) ) {   // 'State' instances
            f.setAccessible(true); // we don't want IllegalAccessExceptions
            try {
              State s = (State) f.get(this);
              String fName = f.getName();
              if (fName.startsWith("this$")) { // our super state
                superState = s;

              } else { // a sub state
                String fn = fieldName != null ? fieldName + '.' + fName : fName;
                s.fieldName = fn;
                list.add(s);
              }
            } catch (IllegalAccessException x) {} // Duhh
          }

          else if (fType.isArray()) {                            // a 'State' array
            Class<?> cType = fType.getComponentType();
            if (State.class.isAssignableFrom(cType)) {
              String fName = f.getName();
              String fn = fieldName != null ? fieldName + '.' + fName : fName;
              f.setAccessible(true); // we don't want IllegalAccessExceptions
              try {
                Object array = f.get(this);
                int len = Array.getLength(array);
                for (int i=0; i<len; i++) {
                  State s = (State) Array.get(array, i);
                  s.fieldName = fn + '[' + i + ']';
                  list.add(s);
                }

              } catch (IllegalAccessException x) {}
            }
          }
        }
      }
    }

    if (list.size() > 0){
      subStates = list.toArray(new State[list.size()]);
    }
  }

  // this we probably just shortcut in a NativePeer
  void setSpecialMethods () {
    try {
      completionTrigger = getClass().getDeclaredMethod(COMPLETION_TRIGGER_MTH, NO_ARGUMENTS);
      completionTrigger.setAccessible(true);
    } catch (NoSuchMethodException nsmx) {}
    try {
      entryActions = getClass().getDeclaredMethod(ENTRY_ACTION_MTH, NO_ARGUMENTS);
      entryActions.setAccessible(true);
    } catch (NoSuchMethodException nsmx) {}
    try {
      exitActions = getClass().getDeclaredMethod(EXIT_ACTION_MTH, NO_ARGUMENTS);
      exitActions.setAccessible(true);
    } catch (NoSuchMethodException nsmx) {}
    try {
      doActions = getClass().getDeclaredMethod(DO_ACTION_MTH, NO_ARGUMENTS);
      doActions.setAccessible(true);
    } catch (NoSuchMethodException nsmx) {}
  }


  public void setStateMachine (StateMachine machine) {
    this.machine = machine;
    initialize(); // this sets the subStates, superState and initStates fields

    if (subStates != null) {
      for (int i=0; i<subStates.length; i++){
        State sub = subStates[i];
        sub.setStateMachine(machine);  // here we get recursive
        
        
        // NOTE - we allow field init from state classes of different compile modules,
        // BUT only to toplevel classes (i.e. no nested classes)
        if (sub.superState == null) {
          sub.superState = this;
          
        } else if (sub.superState != this) {
          throw new RuntimeException("ERROR: super state inconsistency of: " + sub + ", field: " + this + ", class nesting: " + sub.superState);
        }
      }
    }
  }

  public State getMasterState() {
    return machine.getMasterState(); // there can be only one
  }

  public void terminate() {
    setEndState(machine.getMasterState());
  }

  // NativePeer candidate
  public void executeEntryAction () throws InvocationTargetException, IllegalAccessException {
    if (entryActions != null) {
      machine.log(typeName, ".entryAction()");
      entryActions.invoke(this);
    }
  }

  // NativePeer candidate
  public void executeDoAction () throws InvocationTargetException, IllegalAccessException {
    if (doActions != null) {
      machine.log(typeName, ".doAction()");
      doActions.invoke(this);
    }
  }

  // NativePeer candidate
  public void executeExitAction () throws InvocationTargetException, IllegalAccessException {
    if (exitActions != null) {
      machine.log(typeName, ".exitAction()");
      exitActions.invoke(this);
    }
  }

  // NativePeer candidate
  public void executeCompletionTrigger () throws InvocationTargetException, IllegalAccessException {
    if (completionTrigger != null) {
      completionTrigger.invoke(this);
    }
  }

  public boolean isEndState () {
    return isEndState;
  }

  public boolean isActive() {
    return isActive;
  }

  public State getNext() {
    return next;
  }

  public String getName() {
    if (fieldName == null) { // the masterState
      return getTypeName();
    } else {
      return fieldName;
    }
  }

  public String getTypeName() {
    return typeName;
  }

  public String getFieldName() {
    return fieldName;
  }

  public State getSuperState() {
    return superState;
  }

  public State[] getSubStates() {
    return subStates;
  }

  // that's kind of dangerous - the model shouldn't rely on any
  // particular StateMachine representation. The idea is to have the model
  // invariant (no policy)
  public StateMachine getMachine() {
    return machine;
  }

  public String toString() {
    return typeName;
  }

  State getCommonParent (State other) {
    if (other != null) {
      for (State s=this.superState; s != null; s=s.superState) {
        for (State o=other.superState; o != null; o=o.superState) {
          if (s == o) {
            return s;
          }
        }
      }      
    } else {
      if (machine != null) {
        return machine.getMasterState();
      }
    }
    
    return null;  // that might actually be an assertion
  }

  void exitState (State commonParent) {
    for (State s=this; s!=commonParent; s=s.superState) {
      machine.executeExitAction(s);
      s.isActive = false;
    }
  }

  
  // execute entry actions top down from commonParent
  void enterParent () {
    if (superState != null) { // we have to do this top down
      superState.enterParent();
    }
    
    isEndState = false;

    if (!isActive) {
      setVisited();
      isActive = true;
      machine.executeEntryAction(this);
    }    
  }

  void enter () {
    isEndState = false;

    setVisited();
    isActive = true;
    // we have to execute this no matter if we already were active
    // (might be a self-transition)
    machine.executeEntryAction(this);

    if (initStates == null) { // simple state

      // if this is the masterstate, and not even the entry action
      // did set anything active, there is nothing to do
      if (superState != null) {
        // this gets executed after the entryAction, during which
        // some weirdo state might decide it's an end state
        if (!isEndState){
          machine.addNextActiveState(this);
        }
      }

    } else {  // has init sub state(s), recurse down until we find a simple state
      for (int i=0; i<initStates.length;i++) {
        initStates[i].enter();
      }
    }
  }
  
  void enterState (State srcState) {
    if (superState != null) {
      superState.enterParent();
    }
    enter();
  }

  //--- for property checks
  void setVisited () {
    visited++;

    if (machine != null) {
      machine.visitState(this);
    } else {
      assert false : "cannot visit unregistered state: " + getClass().getName() + " : " + fieldName;
    }
  }

  public boolean wasVisited () {
    return (visited > 0);
  }

  public void setReEntered() {
    isReEntered = true;
  }

  public boolean isReEntered() {
    return isReEntered;
  }

  public boolean hasNextState () {
    return machine.hasNextState();
  }

  ////////////////////////////////////////////////// public API (used in translated code)

  public <T extends State>  T makeInitial (T s) {
    if (initStates == null) {
      initStates = new State[1];
      initStates[0] = s;
    } else {
      State[] newInits = new State[initStates.length+1];
      System.arraycopy(initStates,0,newInits,0,initStates.length);
      newInits[initStates.length] = s;
      initStates = newInits;
    }

    return s;
  }

  /**
   * our normal transition within the same region from
   * the currently processed activeState to the specified nextState.
   * There cannot be more than one setNextState() executed for each trigger method call
   */
  public void setNextState (State nextState) {
    exitState(getCommonParent(nextState));

    machine.setNextState(nextState);
    // don't do the entry actions here because we still might have transition actions
  }

  /**
   * a special transition that effectively creates orthogonal regions. Can
   * be called multiple times from within the same trigger method
   */
  public void setNextOrthogonalState (State nextState){
    // <2do> that's not complete if the target states are at different
    // hierarchy levels
    if (!machine.hasNextState()){
      exitState(getCommonParent(nextState));
    }

    machine.setNextOrthogonalState(nextState);
  }

  public void setEndState () {
    setEndState(this);
  }

  public void setEndState (State parent) {
    machine.setEndState (parent);
    for (State s = this; s != parent.superState; s = s.superState) {
      if (!s.isEndState){
        s.isEndState = true;
        machine.executeExitAction(s);
      }
    }
  }


  //--------------------------------- explicit sent events section (for executable UML)
  /*
   *  THIS IS A MESS! there is a lot here that pollutes the state space, and
   *  nobody is going to understand complex FSMs that drive themselves through
   *  prioritized queues of explicitly sent events. If you use this feature
   *  extensively, you can basically forget about statement traces
   */

  // this is where we store explicitly sent events. It is set in sendEvent(), and
  // reset in the machine (that implements the policy of how to use the pendingEvents)
  // NOTE: this has to be in the JPF state space because we need it for state matching,
  // and we are not even allowed to ignore the order because it can matter with our
  // favorite "executable UML" dialect
  PendingEventQueue pendingEvents = new PendingEventQueue();

  // we can't use StringBuilders because that would modify the heap
  void logEvent (String msg, State tgtState, int priority, String eventName, Object... args){
    
    logAppend(msg);
    
    logAppend(eventName);
    logAppend("/");
    logAppend(Integer.toString(priority));
    
    logAppend("(");
    if (args != null){
      for (int i=0; i<args.length; i++){
        if (i>0) logAppend(",");
        logAppend(args[i].toString());
      }
    }
    logAppend(")");
    
    logAppend(" to ");
    if (tgtState.fieldName == null) {
      logAppend("ALL");
    } else {
      logAppend(tgtState.fieldName);
    }
    
    log();
  }

  public void sendEvent (State tgtState, int priority, String eventName, Object... args){
    String msg;
    if (machine.supportsSendEvent()) {
      tgtState.pendingEvents.add(eventName,args,priority);
      msg = "send ";
    } else {
      msg = "send (ignored) ";
    }

    logEvent(msg, tgtState, priority, eventName, args);
  }

  public void sendEvent (State tgtState, String eventName, Object... args){
    int priority = machine.getEventPriority(this,tgtState,eventName,args);
    sendEvent(tgtState,priority,eventName,args);
  }

  /**
   * anonymously sent events are sent to all
   */
  public void sendEvent (String eventName, Object... args){
    State tgtState = machine.getMasterState();
    int priority = machine.getEventPriority(this,tgtState,eventName,args);
    sendEvent(tgtState,priority,eventName,args);
  }

  public EventSpec getPendingEvent () {
    return pendingEvents.getPendingEvent();
  }

  public PendingEventQueue getPendingEventQueue() {
    return pendingEvents;
  }

  //--------------------------------- end send event section


  //--------------------------------- receive event section
  /*
   * almost equally messy - this represents a set of events we can wait for.
   * it it is non-empty, a state doesn't process any trigger until we get an
   * enabling event we wait for.
   * events are removed from the statemachine upon executeTrigger
   */
  EventSpec waitEvent;

  public EventSpec getWaitEvent() {
    return waitEvent;
  }

  public void removeWaitEvent() {
    if (waitEvent != null) {
      waitEvent = waitEvent.next;
    } else {
      waitEvent = null;
    }
  }


  public void receiveEvent (String eventName, String...argTypes) {
    for (EventSpec e = waitEvent; e != null; e = e.next) {
      if (e.name.equals(eventName)) {
        return;
      }
    }

    waitEvent = new EventSpec(eventName, argTypes, waitEvent);
    log("state ", fieldName, " waits for event: ", eventName);
  }

  //--------------------------------- end receive event section


  public String getEventId() {
    return machine.getEventId();
  }

  public Object[] getEventArguments() {
    return machine.getEventArguments();
  }


  //--- misc utility stuff

  // those are forwarded to the machine (we have overloaded versions
  // here to avoid dynamic message composition, which would make it harder
  // for the model checker)
  public void log (String msg) {
    machine.log(msg);
  }
  public void log (String s1, String s2) {
    machine.log(s1,s2);
  }
  public void log (String s1, String s2, String s3) {
    machine.log(s1,s2,s3);
  }
  public void log (String s1, String s2, String s3, String s4) {
    machine.log(s1,s2,s3,s4);
  }

  // it doesn't help to have a log(String...args) because that
  // would cause the compiler to create a dynamic String[] argument list object
  
  // these are for logging with iteration groups - message parts
  // are accumulated until a log() call is received, which prints the
  // log record and resets the message buffer
  public void logAppend(String msg) {
    machine.logAppend(msg);
  }
  public void log () {
    machine.log();
  }
  
  

  public State[] getInitStates() {
    return initStates;
  }

}

