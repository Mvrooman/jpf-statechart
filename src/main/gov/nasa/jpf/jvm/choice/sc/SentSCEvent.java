package gov.nasa.jpf.jvm.choice.sc;

import gov.nasa.jpf.sc.EventSpec;
import gov.nasa.jpf.sc.State;

public class SentSCEvent extends SCEvent {

  // the target state and the event spec this was created from
  State state;
  EventSpec eventSpec;
  
  public SentSCEvent (State state, EventSpec eventSpec,
                      String receiver, String eventName, Object... args) {
    super(receiver, eventName, args);

    this.state = state;
    this.eventSpec = eventSpec;
  }

  public void setProcessed () {
    state.getPendingEventQueue().remove(eventSpec);
  }

}
