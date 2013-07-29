//
// Copyright (C) 2007 United States Government as represented by the
// Administrator of the National Aeronautics and Space Administration
// (NASA).  All Rights Reserved.
// 
// This software is distributed under the NASA Open Source Agreement
// (NOSA), version 1.3.  The NOSA has been approved by the Open Source
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

//
// DISCLAIMER - this file is part of the 'ESAS' demonstration project. As
// such, it is only intended for demonstration purposes, does not contain
// or refer to actual NASA flight software, and is solely derived from
// publicly available information. For further details, please refer to the
// README-ESAS file that is included in this distribution.
//

package visualize;

import java.util.EnumSet;

/**
 * class modeling behavior and configuration of the spacecraft
 * 
 * 'Spacecraft' is an example of a controlled object that is referenced from
 * the state chart
 */
public class Spacecraft {
  
  Failures failures;
  ErrorLog errors;
  
  enum Component { STAGE1, STAGE2, LAS, CM, SM, EDS, LSAM_ASCENT, LSAM_DESCENT };
  EnumSet<Component> configuration;

  public Spacecraft (Failures failures, ErrorLog errors) {
    configuration = EnumSet.of( Component.STAGE1,
                                Component.STAGE2,
                                Component.LAS,
                                Component.CM,
                                Component.SM);
    
    this.failures = failures;
    this.errors = errors;
  }
  
  
  //--- actions
  
  public void doStage1Separation () {
    configuration.remove(Component.STAGE1);
  }
  
  // that's nominal, if the LAS is not required anymore
  public void doLASjettison () {
    configuration.remove(Component.LAS);
  }
    
  public void doStage2Separation () {
    configuration.remove(Component.STAGE2);
  }
  
  public void doLSAMrendezvous () {
    configuration.add(Component.LSAM_ASCENT);
    configuration.add(Component.LSAM_DESCENT);
    configuration.add(Component.EDS);
  }

  public void doEDSseparation () {
    configuration.remove(Component.EDS);    
  }
  
  public void doLSAMascentBurn () {
    configuration.remove(Component.LSAM_DESCENT);
  }
  
  public void doLSAMascentRendezvous () {
    configuration.remove(Component.LSAM_ASCENT);    
  }
  
  public void doSMseparation () {
    configuration.remove(Component.SM);   
  }
  
  public void doEiBurn (boolean hasCMimbalance, boolean hasRCSfailure){
    if (!hasRCSfailure) {
      failures.setCM_RCSfailure();
    }
  }
  
  //--- off nominal
  
  public void doStage1Abort (int altitude, boolean lasControlMotorFired){
    if (!lasControlMotorFired){
      failures.setLAS_CNTRLfailure();
    }
  }
  
  public void doLowActiveAbort () {
    configuration.remove(Component.LAS);
    configuration.remove(Component.SM);
    configuration.remove(Component.STAGE1);
    configuration.remove(Component.STAGE2);
  }

  public void doLowPassiveAbort () {
    configuration.remove(Component.LAS);
    configuration.remove(Component.SM);
    configuration.remove(Component.STAGE1);
    configuration.remove(Component.STAGE2);
  }
  
  public void doStage2Abort (boolean lasControlMotorFired){
    if (!lasControlMotorFired){
      failures.setLAS_CNTRLfailure();
    }
  }

  
  //--- assertions
  
  public boolean readyForLSAMrendezvous() {
    if (configuration.contains(Component.LAS)){
      errors.log("lsamRendezvous with LAS attached");
      return false;
    } else {
      return true;
    }
  }

  public boolean readyForDeorbit () {
    if ( configuration.contains(Component.LAS) ||   
         configuration.contains(Component.SM) ||
         configuration.contains(Component.LSAM_ASCENT) ||
         configuration.contains(Component.EDS)){
      errors.log("deorbit with docked components: " + configuration);
      return false;
    } else {
      return true;
    }
  }

  public boolean readyForTliBurn () {
    if (!configuration.contains(Component.EDS)){
      errors.log("tliBurn without EDS");
      return false;
    } else {
      return true;
    }
  }
  
  public boolean readyForTeiBurn () {
    if (configuration.contains(Component.LSAM_ASCENT) ||
        configuration.contains(Component.LSAM_DESCENT)){
      errors.log("teiBurn with LSAM components docked");
      return false;
    } else {
      return true;
    }
  }
  
  public boolean readyForEiBurn () {
    if (configuration.contains(Component.CM) && (configuration.size() == 1)){
      return true;
    } else {
      errors.log("eiBurn with components docked to CM: " + configuration);
      return false;
    }
  }
  
  public boolean readyForChuteSequence() {
    if (configuration.contains(Component.CM) && (configuration.size() == 1)){
      return true;
    } else {
      errors.log("chute sequence with components docked to CM: " + configuration);
      return false;
    }
  }
}
