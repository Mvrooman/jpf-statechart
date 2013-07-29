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
package gov.nasa.jpf.vm;

import gov.nasa.jpf.Config;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.jvm.ChoiceGenerator;
import gov.nasa.jpf.jvm.ClassInfo;
import gov.nasa.jpf.jvm.ElementInfo;
import gov.nasa.jpf.jvm.FieldInfo;
import gov.nasa.jpf.jvm.JVM;
import gov.nasa.jpf.jvm.SystemState;
import gov.nasa.jpf.jvm.ThreadInfo;
import gov.nasa.jpf.jvm.choice.sc.SCEventGenerator;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.util.DynamicObjectArray;

/**
 * StateMachine native peer.
 *
 * this is merely a dispatcher from JPF StateMachine objects to
 * host VM NativeStateMachine instances. Not sure if we really need
 * this for KC interfacing, but it helps if we can keep (some) state on
 * the native side w/o resorting to static (global) fields
 */
public class JPF_gov_nasa_jpf_sc_StateMachine extends NativePeer {

  static Logger log = JPF.getLogger("gov.nasa.jpf.sc");

  static DynamicObjectArray<NativeStateMachine> machines;

  static class Listener extends ListenerAdapter {
    boolean isSCEventState(Search search) {
      JVM vm = search.getVM();
      SystemState ss = vm.getSystemState();
      ChoiceGenerator<?> cg = ss.getNextChoiceGenerator();
      return (cg instanceof SCEventGenerator);
    }

    @Override
    public void stateAdvanced (Search search) {
      if (isSCEventState(search)) {
        if (search.isVisitedState()) {
          log.info("visited: #" + search.getStateId());
        } else {
          log.info("new: #" + search.getStateId());
        }
      }
    }

    @Override
    public void stateBacktracked (Search search) {
      if (isSCEventState(search)) {
        // <2do> should also show the StateMachine 'step' count
        log.info("backtracked to: " + search.getStateId());
      }
    }

    public void searchFinished (Search search) {
      // here we could check for "all path" properties like
      // global visited sets
    }
  }

  //------------- internal helpers

  static ClassInfo ciStateMachine;
  static FieldInfo fiId;

  static NativeStateMachine getMachine (MJIEnv env, int objRef) {

    // the optimized version. NOTE: this only works with homogenous StateMachine
    // instances (all of the same type)
    ElementInfo ei = env.getElementInfo(objRef);
    assert (ei.getClassInfo() == ciStateMachine);

    // that would be the generic (slow) version
    //int idx = env.getIntField(objRef, "id");

    int idx = ei.getIntField(fiId);
    NativeStateMachine machine = machines.get(idx);
    if (machine == null) {
      machine = createNativeStateMachine(env.getConfig());
      machines.set(idx, machine);
    }

    return machine;
  }

  static NativeStateMachine createNativeStateMachine (Config conf){
    NativeStateMachine machine = conf.getInstance("sc.native_machine.class", NativeStateMachine.class);
    if (machine == null){
      machine = new NativeStateMachine();
    }
    
    return machine;
  }


  //------------ exported native methods

  @MJI
  public void $clinit (MJIEnv env, int clsObjRef) {
    if (log.isLoggable(Level.INFO)){
      env.getJPF().addSearchListener(new Listener());
    }

    machines = new DynamicObjectArray<NativeStateMachine>();

    ciStateMachine = env.getReferredClassInfo(clsObjRef);
    fiId = ciStateMachine.getInstanceField("id");
  }

  @MJI
  public boolean initialize___3Ljava_lang_String_2__Z (MJIEnv env, int objRef, int argRef) {
    return getMachine(env,objRef).initialize(env,objRef, argRef);
  }

  // we have to postpone our property init until here since we need the state map
  @MJI
  public void startRun____ (MJIEnv env, int objRef) {
    getMachine(env,objRef).startRun(env,objRef);
  }

  @MJI
  public void visitState__Lgov_nasa_jpf_sc_State_2__ (MJIEnv env, int objRef, int stateRef) {
    getMachine(env,objRef).visitState(env,objRef,stateRef);
  }

  @MJI
  public void checkTermination____ (MJIEnv env, int objRef) {
    getMachine(env,objRef).checkTermination(env,objRef);
  }

  @MJI
  public void checkStep__I__ (MJIEnv env, int objRef, int nFired) {
    getMachine(env,objRef).checkStep(env,objRef,nFired);
  }

  @MJI
  public int getEventPriority (MJIEnv env, int objRef,
                                      int senderRef, int receiverRef,
                                      int eventNameRef,
                                      int argsRef) {
    return getMachine(env,objRef).getEventPriority(env,objRef,
                                                   senderRef, receiverRef,
                                                   eventNameRef, argsRef);
  }

  /**
   * set the CG that returns the events to try in the next StateMachine step
   * this is where we break JPF transitions
   */
  @MJI
  public boolean getEnablingEvent____Z (MJIEnv env, int objRef) {
    return getMachine(env,objRef).getEnablingEvent(env,objRef);
  }

  @MJI
  public void setEnablingEventProcessed____ (MJIEnv env, int objRef) {
    getMachine(env,objRef).setEnablingEventProcessed(env,objRef);
  }
  
  @MJI
  public void triggerFired__Lgov_nasa_jpf_sc_State_2Lgov_nasa_jpf_sc_State_2__ (MJIEnv env, int objRef,
                                                                int srcStateRef, int tgtStateRef) {
    getMachine(env,objRef).triggerFired(env,objRef,srcStateRef,tgtStateRef);
  }

  @MJI
  public void executeEntryAction__Lgov_nasa_jpf_sc_State_2__ (MJIEnv env, int objRef, int stateRef) {
    getMachine(env,objRef).executeEntryAction(env,objRef,stateRef);
  }

  @MJI
  public void executeExitAction__Lgov_nasa_jpf_sc_State_2__ (MJIEnv env, int objRef, int stateRef) {
    getMachine(env,objRef).executeExitAction(env,objRef,stateRef);
  }

  @MJI
  public void executeDoAction__Lgov_nasa_jpf_sc_State_2__ (MJIEnv env, int objRef, int stateRef) {
    getMachine(env,objRef).executeDoAction(env,objRef,stateRef);
  }

  /**
   * check if there is a method that could fire on the currently tested signal.
   * answer true if we whould look at the parent state in case we
   */
  @MJI
  public void executeTrigger__Lgov_nasa_jpf_sc_State_2__ (MJIEnv env, int objRef, int stateRef) {
    getMachine(env,objRef).executeTrigger(env,objRef,stateRef);
  }

  @MJI
  public int getEventId____Ljava_lang_String_2 (MJIEnv env, int objRef){
    return getMachine(env,objRef).getEventId(env,objRef);
  }

  @MJI
  public int getEventArguments_____3Ljava_lang_Object_2 (MJIEnv env, int objRef){
    return getMachine(env,objRef).getEventArguments(env,objRef);
  }

  // just here to save us some JPF insn execution that pollutes the traces
  @MJI
  public boolean updateActiveStates____Z (MJIEnv env, int objRef) {
    return getMachine(env,objRef).updateActiveStates(env,objRef);
  }

  @MJI
  public boolean supportsSendEvent____Z (MJIEnv env, int objRef) {
    return getMachine(env,objRef).supportsSendEvent(env,objRef);
  }


  @MJI
  public void log__Ljava_lang_String_2__ (MJIEnv env, int objref, int r1) {
    String s1 = env.getStringObject(r1);
    log.info(s1);
  }

  // these are mostly here to avoid dynamic object allocation during logging
  @MJI
  public void log__Ljava_lang_String_2Ljava_lang_String_2__ (MJIEnv env, int objref, int r1, int r2) {
    String s1 = env.getStringObject(r1);
    String s2 = env.getStringObject(r2);
    log.info(s1+s2);
  }

  @MJI
  public void log__Ljava_lang_String_2Ljava_lang_String_2Ljava_lang_String_2__
                           (MJIEnv env, int objref, int r1, int r2, int r3) {
    String s1 = env.getStringObject(r1);
    String s2 = env.getStringObject(r2);
    String s3 = env.getStringObject(r3);
    log.info(s1+s2+s3);
  }

  @MJI
  public void log__Ljava_lang_String_2Ljava_lang_String_2Ljava_lang_String_2Ljava_lang_String_2__
                               (MJIEnv env, int objref, int r1, int r2, int r3, int r4) {
    String s1 = env.getStringObject(r1);
    String s2 = env.getStringObject(r2);
    String s3 = env.getStringObject(r3);
    String s4 = env.getStringObject(r4);
    log.info(s1+s2+s3+s4);
  }

  
  // accumulative per-threadinfo log buffers for log records with iteration groups (like parameters)
  HashMap<String,StringBuilder> logBuffers = new HashMap<String,StringBuilder>(); 
  ThreadInfo tiLast;
  StringBuilder lastBuffer;

  StringBuilder getLogBuffer (ThreadInfo ti) {
    StringBuilder buffer = null;
    if (ti == tiLast) {
      buffer = lastBuffer;
    } else {
      String tName = ti.getName();
      buffer = logBuffers.get(tName);
      if (buffer == null) {
        buffer = new StringBuilder();
        logBuffers.put(tName, buffer);
      }
      tiLast = ti;
      lastBuffer = buffer;
    }

    return buffer;
  }
  
  @MJI
  public void logAppend__Ljava_lang_String_2__ (MJIEnv env, int objRef, int msgRef) {
    StringBuilder buffer = getLogBuffer(env.getThreadInfo());
    String s = env.getStringObject(msgRef);
    buffer.append(s);
  }

  @MJI
  public void log____(MJIEnv env, int objRef) {
    StringBuilder buffer = getLogBuffer(env.getThreadInfo());    
    if (buffer != null && (buffer.length() > 0)) {
      log.info(buffer.toString());
      buffer.setLength(0);
    }
  }
}
