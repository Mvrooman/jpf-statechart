import gov.nasa.jpf.sc.State;


public class ReceiverConstraints extends State {

  class A extends State {
    public void e () {
      assert false : "a should not receive e()";
    }
    
    public void f() {
      setEndState(this);
    }
    
  } A a = makeInitial( new A());
  
  
  public class B extends State {
    public void e () {
      sendEvent("g");  // sent to all
      setNextState(this);
    }
    
    public void g() {
      sendEvent(a, "f"); // implicit receiver constraint
      setEndState(this);
    }
    
    public void f () {
      assert false : "b should not receive f()";
    }
  } B b = makeInitial( new B());
}
