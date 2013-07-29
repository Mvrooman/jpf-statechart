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

import gov.nasa.jpf.annotation.FilterField;

/**
 * implements a simple priority based FIFO event queue, with the twist that
 * processed events get stored so that we can recycle them for state matching
 * purposes (i.e. don't create new event objects all the time)
 *
 * we use a per-queue repository to avoid races for the same event from
 * different senders, assuming that sends mostly happen from a static context
 * that doesn't change the target (or at least keep the number of alternating
 * targets small)
 *
 * <2do> ditch this - move add/get into State / StateMachine and make it
 * consistent with waitEvents
 */
public class PendingEventQueue {

  EventSpec head; // head of queue

  // set with previously created events (to prevent creating new ones that mess up
  // program state matching). We cache them once processed to make sure it was
  // tried at least once before we ever match
  @FilterField EventSpec processedEvent;

  public EventSpec getEvent (String eventId, Object[] args, int priority){
    // check our store first
    for (EventSpec e = processedEvent, eLast = null; e != null; eLast = e, e = e.next){
      if (e.equals(eventId,args, priority)){ // this is why it has to be on the model side - equals() can be user code
        if (eLast == null){
          processedEvent = e.next;
        } else {
          eLast.next = e.next;
        }

        e.next = null;
        return e;
      }
    }

    // nothing stored, create a new one
    return new EventSpec(eventId, args, priority, null);
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("PendingEventQueue[");

    boolean first = true;
    for (EventSpec e = head; e!= null; e = e.next){
      if (!first){
        sb.append(',');
      } else {
        first = false;
      }
      sb.append(e);
    }
    sb.append(']');
    return sb.toString();
  }

  //--- this is policy, so don't use it directly from State!
  public void add (String eventId, Object[] args, int priority){

    EventSpec eNew = getEvent(eventId, args, priority);

    // now sort it in according to priority & FIFO
    if (head == null){
      head = eNew;
    } else {
      for (EventSpec e=head, eLast = null; e != null; eLast = e, e = e.next){

        if (eNew.priority > e.priority){ // insert
          if (eLast == null){
            eNew.next = head;
            head = eNew;
          } else {
            eNew.next = e;
            eLast.next = eNew;
          }
          break;

        } else if (e.next == null){ // append
          eNew.next = null;
          e.next = eNew;
          break;
        }
      }

    }
  }

  public void remove (EventSpec eventSpec) {
    EventSpec e = head;
    
    if (e != null) {
      if (e == eventSpec) {
        head = head.next;
        
        eventSpec.next = processedEvent;
        processedEvent = eventSpec;
        
      } else {
        for (EventSpec eNext = e.next; eNext != null; e=eNext, eNext = e.next) {
          if (eNext == eventSpec) {
            e.next = eNext.next;
            
            eventSpec.next = processedEvent;
            processedEvent = eventSpec;
            break;
          }
        }
      }
    }
  }
  
  public EventSpec peekFirst() {
    return head;
  }

  public EventSpec getPendingEvent() {
    if (head != null){
      EventSpec e = head;
      head = e.next;

      e.next = processedEvent;
      processedEvent = e;

      return e;

    } else {
      return null;
    }
  }
}
