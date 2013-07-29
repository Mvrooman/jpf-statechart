import gov.nasa.jpf.sc.State;


public class SendEvents2 extends State {

  class A extends State {
    
    class A1 extends State {
      
      public void go () {
        sendEvent(b, "d");
        sendEvent(b, "h");
        setNextState(a2);
      }
      
    } A1 a1 = makeInitial(new A1());
    
    class A2 extends State {
      
      public void e () {
        sendEvent(b, "f");
        sendEvent(b.b1, "g");
        setEndState(a);
      }
      
    } A2 a2 = new A2();
    
  } A a = makeInitial( new A());
  
  
  class B extends State {
    
    class B1 extends State {
      
      public void d () {
        sendEvent(a.a2, "e");
        setNextState(this);
      }
      
      public void f() {
        sendEvent(this, "h");
        setNextState(this);
      }
      
      public void g() {
        setNextState(b2);
      }
      
      public void h() {
        setEndState();
      }
            
    } B1 b1 = makeInitial(new B1());
    
    class B2 extends State {
      public void completion() {
        setEndState();
      }
      
    } B2 b2 = new B2();
    
  } B b = makeInitial( new B());
}
