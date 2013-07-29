import gov.nasa.jpf.sc.State;

/**
 * example to show how to explicitly send and check for events,
 * and do other nasty stuff from actions (like setting yourself
 * an end state upon entry. Don't ask me what this is good for -
 * I don't know everything, I just work here)
 */
public class SendEvents1 extends State {

  //------------------------------------------------- SendEvent.A
  class A extends State {
    public void e1 () {
      setNextState(b);
      sendEvent("e2", 42);
    }
  } final A a = makeInitial( new A());
  
  //------------------------------------------------- SendEvent.B
  class B extends State {
        
    public void entryAction() {
      String eid = getEventId();
      log("# entered B via: ", eid);
    }
    
    @Params("42")
    public void e2 (int arg) {
      if (arg == 42) {
        setNextState(c);
      }
    }
  } final B b = new B();

  //------------------------------------------------- SendEvent.C
  class C extends State {
    public void entryAction(){
      String eid = getEventId();
      Object[] eargs = getEventArguments();
      log("# entered C via: ", eid);
      if ((eargs.length == 1) && (eargs[0] instanceof Integer)){
        if (((Integer)eargs[0]) == 42){
          log("#   with arg==42 => C is done");
          setEndState();
        }
      }
    }
  } final C c = new C();
}
