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

package jpfESAS;

import java.util.EnumSet;


/**
 * class modeling failures of the spacecraft
 * 
 * 'Failures' represents an example of a controlled object
 */
public class Failures {
  enum Type { EARTH_SENSOR, LAS_CNTRL, CM_MASS, CM_RCS };

  EnumSet<Type> pending;
  ErrorLog errors;
  
  
  public Failures(ErrorLog errors) {
    pending = EnumSet.noneOf(Type.class);
    this.errors = errors;
  }
  
  //--- actions
  
  public void setLAS_CNTRLfailure() {
    pending.add(Type.LAS_CNTRL);
  }
  
  public void setCM_RCSfailure() {
    pending.add(Type.CM_RCS);
  }
  
  //--- assertions
  
  public boolean noLAS_CNTRLfailure () {
    return !pending.contains(Type.LAS_CNTRL);
  }
  
  public boolean noEARTH_SENSORfailure() {
    return !pending.contains(Type.EARTH_SENSOR);
  }
}
