package gov.nasa.jpf.jvm.choice.sc;

import gov.nasa.jpf.jvm.ChoiceGenerator;
import gov.nasa.jpf.util.StringSetMatcher;

public class SCEventSingleChoice extends SCEventGenerator {

  SCEvent event;
  int state;

  public SCEventSingleChoice(String id, SCEvent e) {
    super(id);

    event = e;
    state = -1;
  }

  public SCEventGenerator copy () {
    SCEventGenerator cg = new SCEventSingleChoice(id, event);
    cg.setSequenceNumber(getSequenceNumber());
    cg.setId(getId());
    return cg;
  }

  public SCEvent getNextChoice () {
    if (state == 0) {
      return event;
    } else {
      return null;
    }
  }

  public SCEventGenerator add (SCEvent e) {
    return new SCEventFromSet(id, event,e);
  }

  public SCEvent[] getSCEvents () {
    SCEvent[] list = { event };
    return list;
  }

  public void advance () {
    state++;
  }

  public int getProcessedNumberOfChoices () {
    return (state == -1) ? 0 : 1;
  }

  public int getTotalNumberOfChoices () {
    return 1;
  }

  public boolean hasMoreChoices () {
    return !isDone && (state < 0);
  }

  public ChoiceGenerator randomize () {
    // it's hard to randomize this
    return this;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getClass().getName());

    sb.append("[id=\"");
    sb.append(id);
    sb.append("\",");

    if (state >= 0) {
      sb.append(MARKER);
    }
    sb.append(event.toString());

    sb.append(']');
    return sb.toString();
  }

  public String toShortString() {
    return "{" + event.toString() + '}';
  }

  public void reset () {
    state = -1;
  }

  public boolean hasWildcard() {
    return event.isWildcard();
  }

  /**
   * only used for sets that have wildcard elements
   */
  public StringSetMatcher getReceiverMatcher () {
    String[] rc = event.getReceiverConstraints();
    if (rc != null) {
      return new StringSetMatcher(rc);
    } else {
      return null;
    }
  }

  public StringSetMatcher getIdMatcher () {
    return new StringSetMatcher(event.getEventName());
  }

}
