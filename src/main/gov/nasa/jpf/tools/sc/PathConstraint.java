package gov.nasa.jpf.tools.sc;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.jvm.ChoiceGenerator;
import gov.nasa.jpf.jvm.JVM;
import gov.nasa.jpf.jvm.choice.sc.SCEvent;
import gov.nasa.jpf.jvm.choice.sc.SCEventGenerator;
import gov.nasa.jpf.util.script.Alternative;
import gov.nasa.jpf.util.script.ESParser;
import gov.nasa.jpf.util.script.ElementProcessor;
import gov.nasa.jpf.util.script.Event;
import gov.nasa.jpf.util.script.Repetition;
import gov.nasa.jpf.util.script.Script;
import gov.nasa.jpf.util.script.ScriptElement;
import gov.nasa.jpf.util.script.Section;


public class PathConstraint extends ListenerAdapter implements ElementProcessor {
  
  static Logger log = JPF.getLogger("gov.nasa.jpf.sc.cons");
  
  String fileName; // where do we get our constraints from
  ArrayList<Constraint> constraints = new ArrayList<Constraint>();
  HashMap<SCEvent,Constraint> map = new HashMap<SCEvent,Constraint>();
    
  static class Constraint { // this is just our anchor
    List<String> ids;
    ConsElement last = new ConsEnd();
    ConsElement cur;
    
    Constraint (List<String> ids){
      this.ids = ids;
    }
    
    String getIdString() {
      StringBuilder sb = new StringBuilder();
      for (String s : ids) {
        if (sb.length() > 0) {
          sb.append(',');
        }
        sb.append(s);
      }
      return sb.toString();
    }
    
    ConsElement getLast() {
      return last;
    }
    
    void add (ConsElement e) {
      e.prev = last;
      last = e;
    }
    
    public boolean matches (SCEvent e) {
      if (cur == null){
        cur = last;
      }
      cur = cur.match(e);
      return (cur != null);
    }
    
    public boolean isMatched () {
      return (cur != null) ? cur.isEnd() : false;
    }

    void resetMatch() {
      cur = null;
    }
    
    SCEvent[] getAnchors() {
      return last.getAnchors();
    }
  }
  
  static abstract class ConsElement {
    ConsElement prev;
    SCEvent[] getAnchors() { return null; }
    boolean isEnd() { return false; }
    abstract ConsElement match (SCEvent e);
    abstract void printOn(PrintWriter pw);
  }
    
  static class ConsEnd extends ConsElement {
    ConsElement match (SCEvent e) { return null; }
    boolean isEnd() { return true; }
    void printOn(PrintWriter pw) { pw.print("END"); }
  }
  
  static class ConsEvent extends ConsElement {
    SCEvent event;
    
    ConsEvent (SCEvent e) {
      event = e;
    }
    
    SCEvent[] getAnchors() {
      SCEvent[] a = new SCEvent[1];
      a[0] = event;
      return a;
    }
    
    ConsElement match (SCEvent e) {
      if (event.equals(e)){
        return prev;
      } else {
        return null;
      }
    }
    
    void printOn(PrintWriter pw) {
      pw.print(event);
    }
  }
  
  static class ConsAlternative extends ConsElement {
    ArrayList<ConsElement> alternatives = new ArrayList<ConsElement>();
    
    void add (ConsElement a) {
      a.prev = a; // that's a hack!
      alternatives.add(a);
    }
    
    ConsElement match(SCEvent e) {
      for (ConsElement a : alternatives) {
        if (a.match(e) != null) {
          return prev;
        }
      }
      return null;
    }
    
    SCEvent[] getAnchors() {
      ArrayList<SCEvent> anchors = new ArrayList<SCEvent>();
      for (ConsElement ae : alternatives) {
        SCEvent[] anc = ae.getAnchors();
        if (anc != null){
          for (int i=0; i<anc.length; i++){
            anchors.add(anc[i]);
          }
        }
      }
      return anchors.toArray(new SCEvent[anchors.size()]);
    }
    
    void printOn (PrintWriter pw){
      pw.print("ALT {");
      for (int i=0; i<alternatives.size(); i++){
        if (i > 0){
          pw.print(',');
        }
        alternatives.get(i).printOn(pw);
      }
      pw.print("}");
    }
  }
  
  static class ConsRepetition extends ConsElement {
    ConsElement back;

    void setBackjump (ConsElement back) {
      this.back = back;
    }
    
    ConsElement match (SCEvent e) {
      ConsElement a = prev.match(e);
      if (a != null){
        return a;
      } else {
        return back.match(e);
      }
    }

    void printOn (PrintWriter pw) {
      int n=0;
      for (ConsElement e=back; e != this; e=e.prev){
        n++;
      }
      pw.print("REP -> -" + n);
    }    
  }
  
  static class ConsAny extends ConsElement {
    
    ConsElement match (SCEvent e){
      ConsElement a = prev.match(e);
      if (a != null){
        return a;
      } else {
        return this;
      }
    }
    
    void printOn(PrintWriter pw) {
      pw.print("*");
    }
  }
  
  // <2do> that's pretty improvised - should be a composite, but then
  // we have to update the element processors
  static class ConsAnyExcept extends ConsElement {
    SCEvent except;
    
    ConsAnyExcept (SCEvent e){
      except = e;
    }
    
    ConsElement match (SCEvent e) {
      if (except.equals(e)){
        if (prev.isEnd()){
          return null;
        } else {
          return prev;
        }
      } else {
        return this;
      }
    }
    
    void printOn (PrintWriter pw) {
      pw.print("~");
      pw.print(except);
    }
  }
  
  //------------------- those are just used during initialization
  Constraint constraint;

  PathConstraint(){} // just for stand alone debugging purposes
  
  public PathConstraint (Config config) {
    fileName = config.getString("sc.constraint"); // just temporary
    if (fileName != null){
      init(fileName);
    }
  }
  
  //------------------- internal stuff
  
  public void init (String fname) {
    try {
      log.info("loading constraints from: " + fname);
      ESParser parser= new ESParser(fname);
      Script script = parser.parse();
      script.process(this);
      registerAnchors();
    } catch (ESParser.Exception x) {
      log.severe("parse error in constraint file: " + fname + ":" + x);
    }
  }
  
  void dump( PrintWriter pw) {
    for (Constraint c : constraints){
      pw.println("CONSTRAINT: " + c.getIdString());
      for (ConsElement e = c.last; e != null; e = e.prev) {
        e.printOn(pw);
        pw.println();
      }
    }
  }
  
  Constraint getConstraint (SCEvent anchor) {
    return map.get(anchor);
  }
  
  void registerAnchors () {
    for (Constraint c : constraints){
      for (SCEvent anchor : c.getAnchors()){
        log.info("register rule: " + c.getIdString() + " for event: " + anchor);
        map.put(anchor, c);
      }
    }
  }
  
  //------------------- VMListener interface
  //@Override
  //A ChoiceGenerator is no longer passed in -- mvrooman
  public void choiceGeneratorAdvanced (JVM vm, ChoiceGenerator cg){
    
    if (cg instanceof SCEventGenerator){
      SCEvent e = ((SCEventGenerator)cg).getNextChoice();
      if (e != null){
        log.info("checking event for constraint: " + e);
        Constraint cons = getConstraint(e);
        if (cons != null){
          log.info("constraint found for event " + e + ":" + cons.getIdString());
          cons.resetMatch();
          
          while (cg != null) {
            if (e != null){
              if (!cons.matches(e)){
                break;
              }
              if (cons.isMatched()){
                log.info("constraint satisfied");
                return;
              }
            }
            cg = cg.getPreviousChoiceGeneratorOfType(SCEventGenerator.class);
            if (cg != null){
              e = ((SCEventGenerator)cg).getNextChoice();              
            }
          }
          log.info("constraint violated, ignore event");
          vm.getSystemState().setIgnored(true);          
        }
      }
    }
  }

  //------------------- ElementProcessor interface
  public void process (Section sec) {
    constraint = new Constraint(sec.getIds());
    constraints.add(constraint);
    sec.processChildren(this);
    
    for (String id : sec.getIds()) {
      
    }
  }

  public void process (Event e) {
    List<Event> list = e.expand();
    if (list.size() == 1) {
      SCEvent ev = new SCEvent(e);
      ConsElement ce;
      
      if (ev.isWildcard()) {
        ce = new ConsAny();
      } else if (ev.isComplement()) {
        ce = new ConsAnyExcept (ev.getComplementEvent());
      } else {
        ce = new ConsEvent(ev);
      }
      
      constraint.add( ce);
    } else {
      ConsAlternative alt = new ConsAlternative();
      for (Event ee : list) {
        alt.add(new ConsEvent(new SCEvent(ee)));
      }
      constraint.add( alt);
    }
  }

  public void process (Alternative a) {
    ConsAlternative alt = new ConsAlternative();
    
    for (ScriptElement e = a.getFirstChild(); e != null; e = e.getNextSibling()) {
      if (e instanceof Event) {
        for (Event ee : ((Event)e).expand()) {
          SCEvent sce = new SCEvent(ee);
          alt.add(new ConsEvent(sce));
        }
      }
    }

    constraint.add(alt);
  }

  public void process (Repetition r) {
    int n = r.getRepeatCount();
    
    if (n > 0) { // simply unroll it
      for (int i=0; i<n; i++) {
        r.processChildren(this);
      }
    } else {
      ConsRepetition rep = new ConsRepetition();
      constraint.add(rep);
      
      r.processChildren(this);
      
      rep.setBackjump( constraint.getLast());
    }
  }
  
  //------------------- test driver
  public static void main (String[] args){
    PathConstraint a = new PathConstraint();
    
    try {
      ESParser parser= new ESParser(args[0]);
      Script script = parser.parse();
      
      PrintWriter pw = new PrintWriter(System.out, true);
      pw.println("------------------ script AST:");
      script.dump(pw);

      script.process(a);
      
      pw.println("------------------ constraints:");
      a.registerAnchors();
      a.dump(pw);
/**/
      SCEvent anchor = new SCEvent("tliBurn", 100, "harr");
      ArrayList<SCEvent> trace = new ArrayList<SCEvent>();
      trace.add(anchor);
      trace.add(new SCEvent("oneIgnored"));
      trace.add(new SCEvent("twoIgnored", 42));
      trace.add(new SCEvent("bla"));
      trace.add(new SCEvent("whatever"));
      trace.add(new SCEvent("huch"));
      trace.add(new SCEvent("gna"));
      trace.add(new SCEvent("huch"));      
      trace.add(new SCEvent("lsamRendezvous"));
      
      Constraint pc = a.getConstraint(anchor);
      if (pc != null){
        for (SCEvent e : trace) {
          pw.println("@@ ?match: " + e);
          if (!pc.matches(e)){
            pw.println("@@ no match");
            break;
          }
          if (pc.isMatched()){
            pw.println("@@ constraint match");
            break;
          }
        }
      } else {
        pw.println("@@ no entry for anchor: " + anchor);
      }
/**/   
    } catch (ESParser.Exception x) {
      x.printStackTrace();
    }
  }
}
