import gov.nasa.jpf.sc.State;


public class CompletionSend extends State {

  class A extends State {

    void entryAction () {
      sendEvent ("e1");
      sendEvent ("e2");
    }

    public void completion () {
      System.out.println("  @@@ this is the A.completion() action");
    }

    public void e1 () {
      System.out.println("  @@@ this is the A.e1() action");
      setNextState(b);
    }
  } A a = makeInitial(new A());

  class B extends State {
    public void e2 () {
      System.out.println("  @@@ this is the B.e2() action");
      //setEndState();
      setNextState(c);
    }
  } B b = new B();

  class C extends State {

    public void e3() {
      System.out.println("  @@@ this is the C.e3() action");
      setEndState();
    }

  } C c = new C();
}
