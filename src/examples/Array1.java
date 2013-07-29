import gov.nasa.jpf.sc.State;

public class Array1 extends State {

  class A extends State {
    public void e () {
      setNextState(a[1]);
    }
    
    public void f () {
      setNextState(b);
    }
  } final A[] a = { makeInitial(new A()), new A() };
  
  class B extends State {
    public void completion() {
      log("done");
      setEndState(this);
    }
  } final B b = new B();
  
}
