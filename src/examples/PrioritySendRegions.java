import gov.nasa.jpf.sc.State;


public class PrioritySendRegions extends State {
  
  class R1 extends State {

    public void faz () {
      System.out.println("this is R1.faz()");
    }
    
    public void zing() {
      System.out.println("this is R1.zing()");      
    }
    
    public void kickOff() {
      sendEvent( r2, 3, "boing");
      sendEvent( getMasterState(), 9, "faz");
      sendEvent( getMasterState(), 1, "zing");
      sendEvent( r2, 9, "zack");
      sendEvent( this, 2, "zing");
    }
    
  } R1 r1 = makeInitial(new R1());
  

  class R2 extends State {

    public void zack () {
      System.out.println("this is R2.zack()");      
    }
    
    public void faz () {
      System.out.println("this is R2.faz()");
    }
    
    public void boing () {
      System.out.println("this is R2.boing()");
    }
    
  } R2 r2 = makeInitial(new R2());

}
