//
//Copyright  (C) 2006 United States Government as represented by the
//Administrator of the National Aeronautics and Space Administration
//(NASA).  All Rights Reserved.
//
//This software is distributed under the NASA Open Source Agreement
//(NOSA), version 1.3.  The NOSA has been approved by the Open Source
//Initiative.  See the file NOSA-1.3-JPF at the top of the distribution
//directory tree for the complete NOSA document.
//
//THE SUBJECT SOFTWARE IS PROVIDED "AS IS" WITHOUT ANY WARRANTY OF ANY
//KIND, EITHER EXPRESSED, IMPLIED, OR STATUTORY, INCLUDING, BUT NOT
//LIMITED TO, ANY WARRANTY THAT THE SUBJECT SOFTWARE WILL CONFORM TO
//SPECIFICATIONS, ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR
//A PARTICULAR PURPOSE, OR FREEDOM FROM INFRINGEMENT, ANY WARRANTY THAT
//THE SUBJECT SOFTWARE WILL BE ERROR FREE, OR ANY WARRANTY THAT
//DOCUMENTATION, IF PROVIDED, WILL CONFORM TO THE SUBJECT SOFTWARE.
//
package gov.nasa.jpf.jvm.choice.sc;

import gov.nasa.jpf.util.StringSetMatcher;
import gov.nasa.jpf.util.script.EventGenerator;

public abstract class SCEventGenerator extends EventGenerator<SCEvent> implements Cloneable {

  protected SCEventGenerator (String id){
    super(id);
  }

  // number of CG in sequence (unroll repetitions to avoid premature
  // state matching)
  int sequenceNumber;

  public void setSequenceNumber(int n) {
    sequenceNumber = n;
  }
  public int getSequenceNumber() {
    return sequenceNumber;
  }

  public abstract SCEvent getNextChoice();

  public Class<SCEvent> getChoiceType() {
    return SCEvent.class;
  }

  public abstract SCEventGenerator add (SCEvent e);

  public abstract SCEvent[] getSCEvents();

  /**
   * it's not quite a clone() since we have to reset the enumeration state.
   * this is the generic version
   */
  public SCEventGenerator copy()  {
    try {
      SCEventGenerator cgNew =  (SCEventGenerator) super.clone();
      cgNew.reset();
      return cgNew;
    } catch (CloneNotSupportedException x) {
      return null;
    }
  }

  public String toShortString() {
    return toString();
  }

  public boolean hasWildcard() {
    return false;
  }

  /**
   * matcher for potential receiver constraints
   */
  public StringSetMatcher getReceiverMatcher () {
    return null;
  }

  /**
   * matcher for potential event id constraints
   */
  public StringSetMatcher getIdMatcher () {
    return null;
  }

}
