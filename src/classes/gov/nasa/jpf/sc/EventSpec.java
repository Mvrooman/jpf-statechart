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

package gov.nasa.jpf.sc;

import java.lang.reflect.Method;

import gov.nasa.jpf.annotation.FilterField;


/**
 *  represents an event specification in model space, e.g. to explicitly
 *  send or wait for events from triggers and actions (used for "executable UML" dialects)
 *
 *  the trick is that we should store only internalized instances in State
 *  objects, so that sending the same event repeatedly doesn't result in
 *  different EventSpec objects, and hence screws the JPF state matching
 *
 *  <2do> this still doesn't completely deal with different event order
 */
public class EventSpec {

  public static final int PRIO_MAX = 10;
  public static final int PRIO_NORM = 5;
  public static final int PRIO_MIN = 0;

  String name;
  Object[] args;

  @FilterField int priority; // optional, we only need this for queue sorting

  EventSpec next;

  public EventSpec (String name, Object[] args){
    this.name = name;
    this.args = args;
    this.priority = PRIO_NORM;
    this.next = null;
  }

  public EventSpec (String name, Object[] args, EventSpec next){
    this.name = name;
    this.args = args;
    this.priority = PRIO_NORM;
    this.next = next;
  }


  public EventSpec (String name, Object[] args, int priority, EventSpec next){
    this.name = name;
    this.args = args;
    this.priority = priority;
    this.next = next;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(name);
    sb.append('(');
    for (int i=0; i<args.length; i++){
      if (i>0){
        sb.append(',');
      }
      sb.append(args[i]);
    }
    sb.append(')');

    return sb.toString();
  }

  /**
   * only compare event name, args and priority
   */
  public boolean equals (String n, Object[] a, int prio){

    if (!name.equals(n)) return false;

    if (priority != prio) return false;

    if ((a != null) && (args != null)){
      if (a.length != args.length) return false;

      for (int i=0; i<args.length; i++){
        if (!a[i].equals(args[i])) return false;
      }
    } else {
      if (a != args) return false;
    }

    return true;
  }

  public String getName() {
    return name;
  }

  public Object[] getArgs() {
    return args;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority (int priority){
    this.priority = priority;
  }

  public void setNext (EventSpec next){
    this.next = next;
  }

  public EventSpec getNext() {
    return next;
  }

  public boolean matches (Method m) {
    String mname = m.getName();

    // <2do> no argtypes comparison yet
    return mname.equals(name);
  }

}
