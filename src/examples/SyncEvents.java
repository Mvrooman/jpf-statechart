import gov.nasa.jpf.sc.State;


public class SyncEvents extends State {

  //--- ortho region 1
  class A1 extends State {
    boolean e1Seen;

    public void e1 () {
      e1Seen = true;
      receiveEvent("eSync");
      // whatever comes here would be still executed in this step
      // but along this path a1 will not process any trigger until it gets an 'eSync'
    }

    public void eSync () {
      if (e1Seen) {
        setNextState(a2);
      }
    }

    public void e2() {
      // shouldn't be triggered while we wait on eSync
      setNextState(this);
    }
  }
  A1 a1 = makeInitial(new A1());

  class A2 extends State {
    public void completion () {
      setEndState(this);
    }
  }
  A2 a2 = new A2();


  //--- ortho region 2
  class B1 extends State {
    public void e4 () {
      setNextState(b1);
    }

    public void eSync() {
      setEndState(this);
    }
  }
  B1 b1 = makeInitial(new B1());

}
