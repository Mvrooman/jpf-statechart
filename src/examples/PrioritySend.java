import gov.nasa.jpf.sc.State;

/**
 * test for prioritized events in hierarchies
 */
public class PrioritySend extends State {
  
  class A extends State {
        
    class B extends State {
      
      public void foo() {
        System.out.println("this is foo()");
      }
    
      public void bar() { 
        System.out.println("this is bar()");
      }

      public void fazz() { 
        System.out.println("this is fazz()");
      }

      public void gna() { 
        System.out.println("this is gna()");
      }

      public void har() { 
        System.out.println("this is har()");
      }

      
      public void kickOff() {
        sendEvent(a, 6, "foo");
        sendEvent(getMasterState(), 9, "bar");        
        sendEvent(this, 9, "fazz");
        sendEvent(getMasterState(), 8, "gna");
        sendEvent(this, 1, "har");
      }
      
      
    } B b = makeInitial(new B());
    
  } A a = makeInitial(new A());
}
