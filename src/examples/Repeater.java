import gov.nasa.jpf.sc.State;


public class Repeater extends State {

  class A extends State {
    int n;

    public void foo () {
      System.out.print("## A.foo triggered with n = ");
      System.out.println(n);

      if (n < 4) {
        n++;
      } else {
        setEndState(); // <<<<< need this, or it's going to loop forever
      }
    }

    public void bar (int a, boolean b) {
      System.out.println("## a.bar called with: " + a + ',' + b);
      n++;
      setNextState(this);
    }

  } A a = makeInitial(new A());
}
