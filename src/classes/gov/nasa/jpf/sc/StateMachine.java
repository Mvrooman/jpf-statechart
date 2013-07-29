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

/**
 * the common model and sim root class for state chart excution. This includes
 * all policy and esp. the main driver loop
 */
public class StateMachine implements Runnable {

  static final int N_STATE_TABLE=256;  // initial state table size
  static final int INC_STATE_TABLE=64; // state table size increment

  @FilterField public static int nMachines; // a counter of the machines created so far

  protected int id; // this identifies the machine instance

  protected State masterState;
  
  // those are the arguments starting with the master state (which is arguments[0])
  static protected String[] arguments;

  // a flat list of all states handled by this machine. This is useful
  // for traversal from a native peer or listener, so that we don't have
  // to create & backtrack external maps for coverage metrics etc.
  // (that's also the reason why we don't use a Collection)
  // <2do> - get rid of the size limit
  @FilterField int nStates;
  @FilterField State[] states = new State[N_STATE_TABLE];

  // note that we don't match on these queue fields, but on the 'isActive' state attributes
  @FilterField protected State activeStates; // this is 'in-order'
  @FilterField protected State nextActives; // this is in reverse order

  // this is used to identify positions in the CG stream. Only if we have the same active
  // states AND have seen the event sequence before can we state match
  int cgSequenceNumber;               // <FIXME> - that is broken with the new SCEnvironment

  // this can be used by the environment to tell JPF no to match this state
  boolean forceNewState;
  
  // the last step was a completion that did not fire
  boolean wasGuardedCompletionStep;

  // a set nextState means the last trigger did fire
  @FilterField State nextState;

  @FilterField protected int step;    // how many events have we executed in the current path
  
  static {
    // we just need a hook for static peer initialization
  }

  protected StateMachine () {
	  id = nMachines++;
  }

  //----------- these are our NativePeer methods (to be overridden by simulator)
  protected boolean initialize (String[] args) {
    return true;
  }

  protected boolean getEnablingEvent() {
    // could be also in a native peer, so it can't be abstract here
    throw new RuntimeException("no getEnablingEvents() implementation");
  }

  protected void executeTrigger(State s) {
    // intercepted by native peer, or overridden by derived class
    throw new RuntimeException("no executeTrigger() implementation");
  }
  
  protected void setEnablingEventProcessed () {
    // intercepted by native peer, or overridden by derived class    
  }
  
  protected void startRun() {
    // to be called before we start to execute steps, after states have been created
  }

  public void visitState (State state) {
    // property check upon state entry
  }

  protected void checkTermination () {
    // property checks upon termination
  }

  protected void checkStep (int nFired) {
    // property checks upon step completion
  }

  //----------- still subject to overriding, but can be used explicitly in model
  // (intercepted by native peer when model checking. we have explict, overloaded
  // versions so that we don't have to use StringBuilders to dynamicallly compose
  // messages, which would make it harder for the model checker)
  public void log (String s) {
  }

  public void log (String s1, String s2) {
  }

  public void log (String s1, String s2, String s3) {
  }

  public void log (String s1, String s2, String s3, String s4){
  }
  
  public void logAppend (String msg) {
  }

  public void log() {
  }

  //----------- end NativePeer methods

  // NOTE - this is called by JPF with adjusted args
  public static void main (String[] args) {

    StateMachine machine = new StateMachine();
    if (!machine.initialize(args)) {
      System.err.println("StateMachine system did not initialize");
      return;
    }

    if (args.length < 1) {
      printUsage();
      return;
    }

    State master = createMasterState(args[0]);
    if (master == null) {
      System.err.println("masterState did not instantiate");
      return;
    }

    // we only get the args starting with the masterstate from JPF
    arguments = args;
    
    machine.setMasterState(master);
    machine.run();
  }

  static public int getArgsLength() {
	  return arguments.length;	  
  }
  
  static public String getArg(int index) {
	  if (index < arguments.length) 
		  return arguments[index];
	  else
		  return null;
  }
  
  
  static protected void printUsage() {
    System.err.print("usage: StateMachine <masterState> [<eventScriptFile>]");
  }

  /**
   * all the action execution has to be routed through the machine, because
   * this is where we need to do exception handling (possibly different
   * for model checking and simulation)
   */
  protected void executeEntryActions (State src, State tgt) {
    tgt.enterState(src);
  }

  protected void executeEntryAction (State s){
    // native peer or derived class
    throw new RuntimeException("no executeEntryAction() implementation");
  }

  protected void executeDoAction (State s){
    // native peer or derived class
    throw new RuntimeException("no executeDoAction() implementation");
  }

  protected void executeExitAction (State s) {
    // native peer or derived class
    throw new RuntimeException("no executeExitActions() implementation");
  }

  void triggerFired (State src, State tgt) {
    // nothing to do
  }

  // we could have those as locals, but we want to filter
  @FilterField int nFired;
  @FilterField State curState;
  // maybe we should also have a curTrigger

  protected void initialize () {
    startRun();
    masterState.enterState(null); // no parent state
  }

  /**
   * the workhorse - perform one step
   * @return true if there are more steps, false if done
   */
  protected boolean step () {
    // no active state termination conditions
    if (!updateActiveStates()) {
      log("done - no more active states");
      return false;
    }

    // 'getEnablingEvents' is the main state matching/storage point (creates the CGs), so we better
    // reset everything that might interfere with state matching before we call it
    nextState = null;
    nFired = 0;
    State s = null, sNext = null;

    if (getEnablingEvent()) { // this is where the real action is (transition break / backtrack point)
      
      step++;

      for (s = activeStates; s != null; s = sNext) {  //---------------------- active state loop
        assert s.isActive : "processing inactive state: " + s;
        
        curState = s;
        sNext = s.next; // store it, we might need to link this into nextActive
        s.next = null;

        s.reset();

        // this can be recursively upwards
        // NOTE this assumes that we don't have to check if a trigger actually
        // fired to terminate the lookup, but it would be very counter-intuitive
        // to have the same trigger hold in a sub and fire in a superState
        // (leave alone the 'timeout' case)
        executeTrigger(s);

        // if the enabling event fired a transition, it sets 'nextState'
        // NOTE this is not really UML, but with this slight modification we
        // could even have transitions from entry actions. Those should rather
        // go into completion triggers, but it might be handy for some of those
        // "executable UML" dialects
        if (nextState != null) {  // Ok, we got a transition (might be also an 'end')

          if (nextState == s) {
            s.setReEntered();
          }

          do {
            State ns = nextState;
            nextState = null;

            nFired++;
            triggerFired(s,ns);
            if (ns.isEndState) { // nextState == s
              log("signal fired: ", s.getName(), " => end");
              break;
            } else {
              log("signal fired: ", s.getName(), " => ", ns.getName());

              // this also updates the 'nextActive' set (via tgt.enterState())
              // which needs to be there because we don't know here what the
              // actual active state(s) will be (could be hierarchically descending into ortho regions)
              executeEntryActions(s,ns); // maybe this sets 'nextState' again (shouldn't)
            }
          } while (nextState != null);

        } else {   // didn't fire, re-add to list of active states
          addNextActiveState(s);
        }

        // end state treatment is here because it depends on the active set
        // (which is unknown to State)
        // <2do> this looks like there is a level of recursion missing - check
        if (s.isEndState) {
          while ((s.superState != null) && s.superState.isEndState) {
            // we might jump to an end state several levels up
            s = s.superState;
          }
          log("end ", s.getName());

          // if this was the last active child, re-activate the parent again
          // (but only if it isn't the masterState, or otherwise we never finish)
          if ((s != masterState) && (s.superState != masterState) && !hasActivePeers(s)) {
            addNextActiveState(s.superState);
          }
        }
      } //------------------------------------------------ end activeStates loop
      
      setEnablingEventProcessed();
      checkStep(nFired);

    } else { // no more events
      log("done - no more events");
      return false;
    }

    return true;
  }

  /**
   * this is the main driver loop for state machine execution
   */
  public void run () {
    initialize();
    while (step());
    checkTermination();
  }


  protected String getStateNameList (State first) {
    StringBuilder sb = new StringBuilder();
    int i=0;

    for (State s = first; s!= null; s = s.next){
      if (i++ > 0){
        sb.append(',');
      }
      sb.append(s);
    }

    return sb.toString();
  }
  
  public State getCurrentState() {
	  return(curState);
  }

  /**
   * check if there still is an active non-end state left that has
   * the same superState like 'state'
   */
  boolean hasActivePeers (State state) {
    State parent = state.superState;

    // that's bad - we have to walk the whole state list since activeStates is already modified

    for (int i=0; i<nStates; i++) {
      State s = states[i];
      if ((s != state) && s.isActive && !s.isEndState && (s.superState == parent)){
        return true;
      }
    }

    return false;
  }

  /*
   * NOTE this builds the activeStates list in REVERSE order of the
   * nextActives list (which is created in LIFO order)
   */
  boolean updateActiveStates () {
    State s, sNext;

    activeStates = null;

    for (s = nextActives; s != null; s = sNext) {
      sNext = s.next;

      s.next = activeStates;
      activeStates = s;
    }

    nextActives = null;
    return (activeStates != null);
  }

  public void setMasterState (State masterState) {
    this.masterState = masterState;
    masterState.setStateMachine(this);
  }

  void growStateTable () {
    State[] a = new State[states.length + INC_STATE_TABLE];
    System.arraycopy(states, 0, a, 0, states.length);
    states = a;
  }

  /**
   * this is a callback from the traversal of the state graph when we
   * set the machine (setMasterState). It can be used to register
   * the now fully initialized State objects, e.g. to get a map
   * of all states per machine etc.
   */
  public void registerState (State state){
    if (nStates >= states.length){
      growStateTable();
    }

    states[nStates++] = state;
  }

  /*
   * NOTE: this builds the nextActives list in LIFO order
   * (it gets reverted again to FIFO when copying nextActives to activeStates)
   */
  public void addNextActiveState (State state) {

    for (State s = nextActives; s != null; s = s.next) {
      if (s == state) {
        return;
      }
    }

    assert state.isActive : "adding a non-active state to the 'nextActives' list";
    
    state.next = nextActives;
    nextActives = state;
  }

  public void setNextState (State state) {
    assert nextState == null :
      "ambiguous transitions in: " + curState.getName() +
           "\n\t\tprocessing event: " + getEventId() + printArgs() +
           "\n\t\ttarget-state 1:   " + state.getName() +
           "\n\t\ttarget-state 2:   " + nextState.getName();
    nextState = state;
  }

  public void setNextOrthogonalState (State state) {
    // like setNextState, only that it doesn't choke
    // on being called multiple times from within the same trigger method.
    // Used to transition to arbitrary initial states inside orthogonal regions

    if (nextState == null){
      setNextState(state);
    } else {
      // <2do> store it, still needs to be done..
    }
  }

  public void setEndState (State state) {
    nextState = state; // kind of mis-used, to flag a transition
  }

  public boolean hasNextState () {
    return (nextState != null);
  }

  /**
   * answers if the provided state is processed in the current step
   * (i.e. is still in the 'activeStates' list)
   * this is different from State.isActive()
   * 
   * NOTE: step() updates 'activeStates' for each processed state, and
   * the state already might have been processed in this step  
   */
  public boolean isInActiveStates (State state) {
    for (State s = activeStates; s != null; s = s.next) {
      if (s == state) {
        return true;
      }
    }
    
    return false;
  }
  
  public boolean supportsSendEvent () {
    return true;
  }

  /**
   * this might get intercepted by our native peer so that we can turn it into
   * a choice point ("undefined" order of events)
   *
   * which might be required since our favorite "executable UML" dialect says
   * that events received from different state machines "..may be processed
   * in any order..". Now that makes a fine point to check with JPF, which we
   * could simply do by using an IntIntervalGenerator for the returned priority
   */
  public int getEventPriority (State sender, State receiver, String eventName, Object[] args){
    // this is KC-ism
    if (sender == receiver){
      return EventSpec.PRIO_MAX;
    } else {
      return EventSpec.PRIO_NORM;
    }
  }

  /*
   * these two refer to the currently processed event, e.g. to find out
   * how we got into the currently processing state, and to check parameter
   * values from inside entry-actions. Again, motivated by some restricted
   * "executable UML" dialects
   */
  public String getEventId (){
    // intercepted or overridden
    return null;
  }

  public Object[] getEventArguments (){
    // intercepted or overridden
    return null;
  }

  public State getMasterState (){
    return masterState;
  }

  public State getActiveStates () {
    return activeStates;
  }
  
  protected String printArgs () {
    Object[] a = getEventArguments();
    if (a == null || a.length == 0){
      return "()";
    } else {
      StringBuilder sb = new StringBuilder();
      sb.append('(');
      for (int i=0; i<a.length; i++){
        Object o = a[i];
        if (i > 0) {
          sb.append(',');
        }
        if (o instanceof String){
          sb.append('"');
          sb.append(o);
          sb.append('"');
        } else if (o instanceof Boolean){
          sb.append(((Boolean)o).booleanValue());
        } else if (o instanceof Integer){
          sb.append(((Integer)o).intValue());
        } else if (o instanceof Double){
          sb.append(((Double)o).doubleValue());
        }
      }
      sb.append(')');
      return sb.toString();
    }
  }

  static protected State createMasterState (String clsName) {
    try {
      Class cls = Class.forName(clsName);
      Object s = cls.newInstance();
      return (State) s;

    } catch (ClassNotFoundException cnfx) {
      System.err.print("class not found: ");
    } catch (IllegalAccessException iax) {
      System.err.print("ctor not accessible: ");
    } catch (InstantiationException ix) {
      System.err.print("instantiation failed: ");
    }
    System.err.println(clsName);
    return null;
  }

  public void showState (State s, int level) {

    if (s.isActive()) {
      System.out.print("* ");
    } else {
      System.out.print("  ");
    }

    for (int i=0; i<level; i++) {
      System.out.print("  ");
    }

    String tName = s.getTypeName();
    int idx = tName.lastIndexOf('.');
    System.out.print(tName.substring(idx+1));
    System.out.print(" : ");

    String fName = s.getFieldName();
    if (fName != null) {
      idx = fName.lastIndexOf('.');
      System.out.print(fName.substring(idx+1));
    }
    System.out.println();

    if (s.subStates != null) {
      level++;
      for (State c : s.subStates) {
        showState(c, level);
      }
    }
  }

  public void showMachine () {
    System.out.println("===================================== statemachine composition: ");
    showState(masterState, 0);
    System.out.println();
  }
}
