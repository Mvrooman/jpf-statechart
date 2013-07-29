import gov.nasa.jpf.sc.State;


public class KeepAlive extends State {

  class A extends State {
    
    public void kickOff () {
      sendEvent(getMasterState(), 9, "tick");
    }
    
    public void tick() {
      System.out.println("this is A.tick()");
      sendEvent(getMasterState(), 8, "tick");
      sendEvent(getMasterState(), 9, "tack");
    }
  } A a = makeInitial(new A());
  
  class B extends State {
    
    public void tack() {
      System.out.println("this is B.tack()");
    }
    
  } B b = makeInitial(new B());
}
