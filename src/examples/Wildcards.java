import gov.nasa.jpf.sc.State;


public class Wildcards extends State {

  public class A extends State {

    public void someEvent() {
      setEndState();
    }

    public void e1() {
      setEndState();
    }

    public void e2() {
      setEndState();
    }

    public void dontGetHere() {
      setNextState(a);
    }

  } A a = makeInitial( new A());


  public class B extends State {

    public void e1() {
      setEndState();
    }

    public void e2() {
      setEndState();
    }

    public void dontGetThere() {
      setNextState(b);
    }

  } B b = makeInitial( new B());
}
