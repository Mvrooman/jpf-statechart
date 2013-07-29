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

package gov.nasa.jpf.jvm.choice.sc;

import java.util.Collection;
import gov.nasa.jpf.jvm.MJIEnv;
import gov.nasa.jpf.jvm.MethodInfo;
import gov.nasa.jpf.jvm.MethodLocator;
import gov.nasa.jpf.util.script.Event;

/**
 * class that represents enabling events in UML statechart processing
 */
public class SCEvent implements MethodLocator {

  static final char RC_CHAR = ':';

  public static final String WILDCARD = "*";
  public static final String COMPLETION = "completion";
  public static final String TIMEOUT = "timeout";

  public static SCEvent TIMEOUT_EVENT = new SCEvent(TIMEOUT);
  public static SCEvent COMPLETION_EVENT = new SCEvent(COMPLETION);

  String id;
  Object[] arguments;
  int line;

  boolean isConsumed;

  String mthName; // on demand

  public static String createId (String receiver, String eventName) {
    if (receiver != null) {
      // <2do> this is not yet correct - a*:foo() should only match a.foo() or
      // a.b.foo(), but not aaa.foo()
      return receiver + WILDCARD + RC_CHAR + eventName;
    } else {
      return WILDCARD + RC_CHAR + eventName;
    }
  }

  public SCEvent (Event e) {
    id = e.getId();
    if (!e.isNone()) {
      arguments = e.getConcreteArguments();
    }
    line = e.getLine();
  }

  public SCEvent (String receiver, String eventName, Object... args) {
    this(createId(receiver, eventName), args);
  }

  public SCEvent (String id, Object... arguments){
    this.id = id;

    if ((arguments != null) && (arguments.length == 0)){
      this.arguments = null;
    } else {
      this.arguments = arguments;
    }
  }

  public String[] getReceiverConstraints() {
    int idx = id.indexOf(RC_CHAR);
    if (idx < 0) {
      return null;

    } else {
      String rc = id.substring(0, idx);
      String[] rec = rc.split("[|]");
      return rec;
    }
  }

  public String getEventName() {
    int idx = id.indexOf(RC_CHAR);
    if (idx < 0) {
      return id;
    } else {
      return id.substring(idx+1);
    }
  }


  public static SCEvent getTimeoutEvent() {
    return TIMEOUT_EVENT;
  }

  public static SCEvent getCompletionEvent() {
    return COMPLETION_EVENT;
  }

  public void setLine (int line) {
    this.line = line;
  }

  public int getLine() {
    return line;
  }

  public boolean isConsumed() {
    return isConsumed;
  }

  public void setConsumed(boolean b) {
    isConsumed = b;
  }

  public int hashCode() {
    // a little bit improvised..
    int hc = id.hashCode() << 16;

    if (arguments != null){
      for (int i=0; i<arguments.length; i++){
        hc += (arguments[i].hashCode() >>16);
      }
    }

    return hc;
  }

  /**
   * we consider type, id and arguments as equality, line is a hint
   * (wouldn't map to a different method)
   */
  public boolean equals (Object other) {
    if (getClass() != other.getClass()) {
      return false;
    }
    SCEvent e = (SCEvent)other;

    if (!id.equals(e.id)) {
      return false;
    }

    if (arguments == e.arguments) {
      return true;
    }
    if ((arguments == null) != (e.arguments == null)) {
      return false;
    }
    if (arguments.length != e.arguments.length) {
      return false;
    }
    for (int i=0; i<arguments.length; i++) {
      if (!arguments[i].equals(e.arguments[i])) {
        return false;
      }
    }

    return true;
  }

  public String getId() {
    return id;
  }

  // <2do> that's not quite it - should be a composite
  public SCEvent getComplementEvent() {
    if (id.charAt(0) == '~'){
      return new SCEvent( id.substring(1), arguments);
    } else {
      return new SCEvent( "~" + id, arguments);
    }
  }
  public boolean isComplement() {
    return (id.charAt(0) == '~');
  }

  public boolean isWildcard() {
    return (id.indexOf('*') >= 0);
    //return WILDCARD.equals(id);
  }

  public boolean isCompletion() {
    return ((this == COMPLETION_EVENT) || COMPLETION.equals(id));
  }

  public boolean isTimeout() {
    return TIMEOUT.equals(id);
  }

  public boolean match (MethodInfo mi) {
    // TODO Auto-generated method stub
    return false;
  }

  public Object[] getArguments() {
    return arguments;
  }

  public Class[] getArgumentTypes() {
    if (arguments == null) {
      return new Class[0];
    } else {
      Class[] list = new Class[arguments.length];
      for (int i=0; i<arguments.length; i++) {
        Object a = arguments[i];

        if (a instanceof String) {
          list[i] = String.class;
        } else if (a instanceof Boolean) {
          list[i] = boolean.class;
        } else if (a instanceof Double) {
          list[i] = double.class;
        } else if (a instanceof Integer) {
          list[i] = int.class;
        } else {
          assert false : "unsupported argument type in event: " + a;
        }
      }
      return list;
    }
  }

  public String getMethodName () {
    return getEventName();
  }

  public String getUniqueMethodName() {
    if (mthName == null) {
      StringBuilder sb = new StringBuilder();

      sb.append(getEventName());
      sb.append('(');
      if (arguments != null) {
        for (int i=0; i<arguments.length; i++) {
          Object a = arguments[i];

          if (a instanceof String) {
            sb.append("Ljava/lang/String;");
          } else if (a instanceof Boolean) {
            sb.append('Z');
          } else if (a instanceof Double) {
            sb.append('J');
          } else if (a instanceof Integer) {
            sb.append('I');
          } else {
            assert false : "unsupported argument type in event: " + a;
          }
        }
      }
      sb.append(")V");
      mthName = sb.toString();
    }

    return mthName;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(id);

    if (!Event.isNone(id)) {
      sb.append('(');
      if (arguments != null) {
        for (int i=0; i<arguments.length; i++){
          if (i > 0) {
            sb.append(',');
          }

          Object a = arguments[i];
          if (a instanceof String) {
            sb.append('"');
            sb.append(a);
            sb.append('"');
          } else if (a instanceof Boolean) {
            sb.append(((Boolean)a).booleanValue());
          } else if (a instanceof Integer) {
            sb.append(((Integer)a).intValue());
          } else if (a instanceof Double) {
            sb.append(((Double)a).doubleValue());
          }
        }
      }
      sb.append(')');
    }
    return sb.toString();
  }

  public void addUniqueTo (Collection<SCEvent> set) {
    if (!set.contains(this)){
      set.add(this);
    }
  }

  public void addUniqueTo (Collection<SCEvent> set, String receiver) {
    if (receiver == null) {
      addUniqueTo(set);

    } else {
      for (SCEvent e : set) {
        if (e.id.equals(id)) {
          return; // already in there

        } else if (e.getEventName().equals(id)) { // same event but different receiver
          if (e.id.indexOf(RC_CHAR) >= 0) {
            e.id = receiver + '|' + e.id; // extend receiver constraint pattern
          } else {
            // it's already in there w/o receiver constraint -> no point to add one
          }
          return;
        }
      }

      id = receiver + RC_CHAR + id;
      set.add(this);
    }
  }

  public void setProcessed () {
    //nothing, overridden by subclasses
  }
}
