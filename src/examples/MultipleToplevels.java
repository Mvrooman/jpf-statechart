import gov.nasa.jpf.sc.State;

// this would be a second compile unit
class SecondToplevel extends State {
  
  class A extends State {
    public void completion() {
      System.out.println("state machine structure from " + getName() + ".completion()");
      getMachine().showMachine(); 
    }
  } A a = makeInitial(new A());
  
  class B extends State {
  } B b = new B();
  
}

// this is our master state
public class MultipleToplevels extends State {

  // look Ma, no nested class def 
  SecondToplevel secondToplevel = makeInitial(new SecondToplevel());
  
  // illegal - secondToplevel.b 's super class is secondToplevel !!
  //SecondToplevel.B bb = secondToplevel.b;
}
