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

/**
 * simple example that shows how a hierarchical statechart gets translated
 * into a hierarchy of inner classes:
 * 
 *  - there is one master-class representing the whole system ('FixedPhone')
 * 
 *  - no "main" method required
 *  
 *  - every sub-state gets translated into a nested class of it's superstate
 *  
 *  - state classes extend gov.nasa.jpf.sc.State
 *  
 *  - sub-states instances are defined in State fields of their
 *    super-state class:
 *      class SuperState extends State {..
 *        class SubState extends State {..}
 *        SubState sub = new SubState();
 *        ..
 *  
 *  - the master state does not need to be instantiated
 *  
 *  - initial states are instantiated using "makeInitial":
 *    "..class Idle extends State {..} final Idle idle = makeInitial(new Idle());.."
 *  
 *  - orthogonal regions of a super-state are defined by having several
 *    "makeInitial(..)" sub-states inside a super-state
 *  
 *  - triggers become 'public void' methods (that can take 'boolean', 'int',
 *    'double' and string literal arguments
 *  
 *  - completion triggers are implemented as "public void completion()" methods
 *  
 *  - guards are normal Java expressions inside of trigger methods
 *  
 *  - transitions are defined by calling "setNextState(<tgtState>)" from within
 *    trigger methods. The <tgtState> is the corresponding field (object reference),
 *    not a String
 *  
 *  - end state transitions are defined by calling "setEndState()" from within
 *    trigger methods
 *  
 *  - entry/exit/do actions are implemented as
 *    "public void entryAction()/exitAction()/doAction()" methods
 * 
 * while trigger methods and actions can refer to any number of controlled objects,
 * it is stringly recommended to not include any execution policy dependency in
 * those. Albeit possible, access to SCEvent and StateMachine instances should
 * therefore be used very carefully. The purpose of the State hierarchy is to
 * provide an execution policy- free, invariant representation of the state machine
 * structure that can be used with various StateMachine implementations (e.g.
 * also for simulation)
 * 
 * ultimately, this is supposed to be translated automatically from a statechart XMI
 */

import gov.nasa.jpf.sc.*;

@Source("FixedPhone.pdf") // the source for this state and all it's children
public class FixedPhone extends State {
  
  public void entryAction () {
    turnOnDisplay();
  }
  void turnOnDisplay() {  // private action
    System.out.println("display on");
  }

  public void exitAction () {
    turnOffDisplay();
  }
  void turnOffDisplay() {  // private action
    System.out.println("display off");
  }
  
  public class Idle extends State {

    public void entryAction () {
      idling();
    }
    void idling() { // private action
      System.out.println("I'm idling around..");
    }

    // signal triggers
    public void liftReceiver() {
      System.out.println("lifted receiver");
      setNextState(active);
    }
  }  Idle idle = makeInitial(new Idle());
  
  
  public class Active extends State {

    //--- our own triggers
    public void hangupReceiver() {
      setNextState(idle);
    }
     
    public void disconnect() {
      setNextState(active);
    }
    
    //--- out sub states
    public class DialTone extends State {    
      public void dialDigit() {
        setNextState(dialing.send);
      }
      
      public void after (int time) {
        setNextState(timeout);
      }
    }  DialTone dialTone = makeInitial(new DialTone());
    
    
    public class Timeout extends State {
      public void doAction () {
        playMessage();
      }
      void playMessage() {  // private action
        System.out.println("timeout, please hang up and try again");
      }
    }  Timeout timeout = new Timeout(); 

    
    public class Invalid extends State {
      public void doAction () {
        playMessage();
      }
      void playMessage() {  // private action
        System.out.println("invalid number");
      }
      
      public void dialDigit() {
        throw new AssertionError("can't dial in invalid state");
      }
    }  Invalid invalid = new Invalid();
    
    
    public class Dialing extends State {

      public void after(int time) {
        setNextState(timeout);
      }

      public class Send extends State {
        public void completion(){
          setNextState(receive);
        }
      }  Send send = new Send();
      
      
      public class Receive extends State {
        public void validNumber() {
          setNextState(Active.this.connecting);
        }
        
        public void invalidNumber() {
          setNextState(Active.this.invalid);
        }
        
        public void incompleteNumber() {
          setNextState(wait);
        }
      }  Receive receive = new Receive();
      
      
      public class Wait extends State {
        public void dialDigit() {
          setNextState(send);
        }
      }  Wait wait = new Wait();
      
    }  Dialing dialing = new Dialing();
    
    
    public class Connecting extends State {
      public void entryAction () {
        System.out.println("connecting..");
      }
      
      public void busy() {
        setNextState(busy);
      }
      
      public void connected() {
        setNextState(ringing);
      }
    }  Connecting connecting = new Connecting();

    
    public class Ringing extends State {
      
      public void doAction() {
        playRingTone();
      }
      void playRingTone() {  // private action
        System.out.println("rriing, rriing ..");
      }
      
      public void calleeAnswers() {
        setNextState(talking);
        enableSpeech();
      }
      void enableSpeech () {  // private transition action
        // whatever side effect you want
        System.out.println("enable speech");
      }
    }  Ringing ringing = new Ringing();

    
    public class Busy extends State {
      public void doAction() {
        playBusyTone();
      }
      void playBusyTone() {  // private action
        System.out.println("beep beep beep..");
      }
    }  Busy busy = new Busy();

    
    public class Talking extends State {
      public void calleeHangsUp() {
        setNextState(pinned);
      }
    }  Talking talking = new Talking();

    
    public class Pinned extends State {
      public void calleeAnswers() {
        setNextState(talking);
      }
    }  Pinned pinned = new Pinned();
    
  }  Active active = new Active();
  
}
