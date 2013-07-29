//
// Copyright  (C) 2008 United States Government as represented by the
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
package gov.nasa.jpf.jvm.choice.sc;

import gov.nasa.jpf.jvm.JVM;
import gov.nasa.jpf.jvm.MJIEnv;
import gov.nasa.jpf.jvm.ThreadInfo;

/**
 * explicitly sent event (not environment/script generated)
 */
public class NativeSentSCEvent extends SCEvent {

  int stateRef; // the receiver state reference (where the corresponding PendingEvent is stored)
  int eventSpecRef; // the PendingEvent reference this instance was created from

  public NativeSentSCEvent (int stateRef, int eventSpecRef,
                      String receiver, String eventName, Object... args) {
    super(receiver, eventName, args);

    this.stateRef = stateRef;
    this.eventSpecRef = eventSpecRef;
  }

  void storeProcessedEvent (MJIEnv env, int qRef, int eRef) {    
    int eProcessedRef = env.getReferenceField(qRef, "processedEvent");
    env.setReferenceField(eRef, "next", eProcessedRef);
    env.setReferenceField(qRef, "processedEvent", eRef);    
  }

  public void setProcessed () {
    // that's bad
    JVM vm = JVM.getVM();
    ThreadInfo ti = vm.getCurrentThread();
    MJIEnv env = ti.getEnv();
        
    int qRef = env.getReferenceField(stateRef, "pendingEvents");
    int eRef = env.getReferenceField(qRef, "head");

    // update pending events queue
    if (eRef != MJIEnv.NULL){

      if (eRef == eventSpecRef) { // event was queue head
        env.setReferenceField(qRef, "head", env.getReferenceField(eRef, "next"));
        storeProcessedEvent(env, qRef, eRef);
        
      } else {
        for (int eRefNext = env.getReferenceField(eRef, "next"); eRefNext != MJIEnv.NULL; 
                 eRef=eRefNext, eRefNext = env.getReferenceField(eRef, "next")) {
          if (eRefNext == eventSpecRef) {
            env.setReferenceField(eRef, "next", env.getReferenceField(eRefNext, "next"));
            storeProcessedEvent(env, qRef, eRefNext);
            break;
          }
        }
      }
    }
  }
}
