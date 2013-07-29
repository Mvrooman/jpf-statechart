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

import gov.nasa.jpf.jvm.ChoiceGenerator;
import gov.nasa.jpf.util.StringSetMatcher;

import java.util.ArrayList;
import java.util.Collection;

public class SCEventFromSet extends SCEventGenerator {

  ArrayList<SCEvent> values;
  int count;

  public SCEventFromSet (String id) {
    super(id);

    count = -1;
    values = new ArrayList<SCEvent>();
  }

  public SCEventFromSet (String id, Collection<SCEvent> events){
    super(id);

    count = -1;
    values = new ArrayList<SCEvent>(events.size());
    for (SCEvent e : events){
      values.add(e);
    }
  }

  public SCEventFromSet (String id, SCEvent... events){
    super(id);

    count = -1;
    values= new ArrayList<SCEvent>(events.length);
    for (SCEvent e : events){
      values.add(e);
    }
  }

  /**
   * not sure if that really makes sense - if one event isn't consumed, that
   * doesn't mean all the choices will go unhandled
   */
  public SCEventGenerator copy () {
    SCEventFromSet cg = new SCEventFromSet(id);
    cg.values = values;
    cg.count = count;
    cg.setSequenceNumber(getSequenceNumber());
    cg.setId(getId());
    return cg;
  }

  public SCEventGenerator add (SCEvent e) {
    values.add(e);
    return this;
  }

  public void addAll (Collection<? extends SCEvent> list) {
    values.addAll(list);
  }

  public void addEventsFrom (SCEventGenerator other) {
    for (SCEvent e : other.getSCEvents()) {
      values.add(e);
    }
  }

  public void advance () {
    count++;
  }

  public SCEvent[] getSCEvents() {
    SCEvent[] a = new SCEvent[values.size()];
    return values.toArray(a);
  }

  public int getProcessedNumberOfChoices () {
    return count+1;
  }

  public int getTotalNumberOfChoices () {
    return values.size();
  }

  public boolean hasMoreChoices () {
    return !isDone && (count < values.size()-1);
  }

  public ChoiceGenerator randomize () {
    for (int i = values.size() - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      SCEvent tmp = values.get(i);
      values.set(i, values.get(j));
      values.set(j,tmp);
    }

    return this;
  }

  public void reset () {
    count = -1;
  }

  public void clear () {
    values.clear();
    reset();
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getClass().getName());

    sb.append("[id=\"");
    sb.append(id);
    sb.append("\",");

    for (int i=0; i<values.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      if (i == count) {
        sb.append(MARKER);
      }
      sb.append(values.get(i).toString());
    }
    sb.append(']');
    return sb.toString();
  }

  public String toShortString() {
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    for (int i=0; i<values.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(values.get(i).toString());
    }
    sb.append('}');
    return sb.toString();

  }

  public SCEvent getNextChoice() {
    if ((count >= 0) && (count < values.size())) {
      return values.get(count);
    } else {
      return null;
    }
  }

  public boolean hasWildcard() {
    int n = values.size();
    for (int i=0; i<n; i++) {
      if (values.get(i).isWildcard()) {
        return true;
      }
    }
    return false;
  }

  /**
   * only used for sets that have wildcard elements
   */
  public StringSetMatcher getIdMatcher () {
    int n = values.size();
    ArrayList<String> patterns = new ArrayList<String>(n);

    for (int i=0; i<n; i++) {
      String[] rc = values.get(i).getReceiverConstraints();
      if (rc != null) {
        for (int j=0; j<rc.length; j++) {
          patterns.add(rc[j]);
        }
      }
    }

    return new StringSetMatcher(patterns.toArray(new String[patterns.size()]));
  }

  public StringSetMatcher getReceiverMatcher () {
    String[] patterns = new String[values.size()];
    for (int i=0; i<patterns.length; i++) {

      patterns[i] = values.get(i).getEventName();
    }

    return new StringSetMatcher(patterns);
  }

}
