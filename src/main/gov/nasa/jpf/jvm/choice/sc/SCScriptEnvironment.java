package gov.nasa.jpf.jvm.choice.sc;

import gov.nasa.jpf.util.script.Event;
import gov.nasa.jpf.util.script.ScriptEnvironment;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.List;

public class SCScriptEnvironment extends ScriptEnvironment<SCEventGenerator> {

  public SCScriptEnvironment (String fname) throws FileNotFoundException {
    super( fname, new FileReader(fname));
  }

  public SCScriptEnvironment (String name, Reader r) {
    super(name,r);
  }

  protected SCEventGenerator createCGFromEvents(String id, List<Event> events) {
    if (events.isEmpty()) {
      return null;
    } else if (events.size() == 1) {
      return new SCEventSingleChoice( id, new SCEvent(events.get(0)));
    } else {
      SCEventFromSet cg = new SCEventFromSet(id);
      for (Event e : events) {
        cg.add( new SCEvent(e));
      }
      return cg;
    }
  }

}
