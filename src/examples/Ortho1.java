import gov.nasa.jpf.sc.*;

public class Ortho1 extends State {

  final S1 s1 = makeInitial(new S1());
  final S2 s2 = new S2();
  final S3 s3 = new S3();

  public class S1 extends State {
    public void e1() {
      setNextState(s2);
    }
  }

  public class S2 extends State {

    final A1 a1 = makeInitial(new A1());
    final A2 a2 = new A2();
    final B1 b1 = makeInitial(new B1());
    final B2 b2 = new B2();

    public class A1 extends State {
      public void e2() {
        setNextState(a2);
      }
    }

    public class A2 extends State {

      public void e3() {
        setEndState();
      }
    }

    public class B1 extends State {
      public void e2() {
        setNextState(b2);
      }
    }

    public class B2 extends State {
      public void e4() {
        setEndState();
      }

      public void e5() {
        //setEndState(Ortho1.this);
        terminate(); // that's synonym, but more readable
      }
    }

    public void completion () {
      setNextState(s3);
    }
  }

  public class S3 extends State {
    public void completion () {
      setEndState();
    }
  }
}
