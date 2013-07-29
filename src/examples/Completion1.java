import gov.nasa.jpf.sc.State;


public class Completion1 extends State {
  
  final A a = makeInitial(new A());
  final B b = new B();
  
  class A extends State {
    boolean cond;
    
    public void completion() {
      if (cond){
        setNextState(b);
      }
    }
    
    public void setCond(boolean c){
      cond = c;
      setNextState(this);
    }
  }
  
  class B extends State {
    public void completion() {
      setEndState();
    }
  }
}
