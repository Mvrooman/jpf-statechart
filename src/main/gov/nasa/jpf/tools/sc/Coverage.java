//
// Copyright (C) 2006 United States Government as represented by the
// Administrator of the National Aeronautics and Space Administration
// (NASA).  All Rights Reserved.
// 
// This software is distributed under the NASA Open Source Agreement
// (NOSA), version 1.3.  The NOSA has been approved by the Open Source
// Initiative.  See the file NOSA-1.3-JPF at the top of the distribution
// directory tree for the complete NOSA document.
// 
// THE SUBJECT SOFTWARE IS PROVIDED "AS IS" WITHOUT ANY WARRANTY OF ANY
// KIND, EITHER EXPRESSED, IMPLIED, OR STATUTORY, INCLUDING, BUT NOT
// LIMITED TO, ANY WARRANTY THAT THE SUBJECT SOFTWARE WILL CONFORM TO
// SPECIFICATIONS, ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR
// A PARTICULAR PURPOSE, OR FREEDOM FROM INFRINGEMENT, ANY WARRANTY THAT
// THE SUBJECT SOFTWARE WILL BE ERROR FREE, OR ANY WARRANTY THAT
// DOCUMENTATION, IF PROVIDED, WILL CONFORM TO THE SUBJECT SOFTWARE.
//
package gov.nasa.jpf.tools.sc;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.ClassInfo;
import gov.nasa.jpf.jvm.JVM;
import gov.nasa.jpf.jvm.MJIEnv;
import gov.nasa.jpf.jvm.MethodInfo;
import gov.nasa.jpf.jvm.ThreadInfo;
import gov.nasa.jpf.jvm.bytecode.Instruction;
import gov.nasa.jpf.jvm.bytecode.RETURN;
import gov.nasa.jpf.jvm.bytecode.INVOKEVIRTUAL;
import gov.nasa.jpf.report.ConsolePublisher;
import gov.nasa.jpf.report.Publisher;
import gov.nasa.jpf.report.PublisherExtension;
import gov.nasa.jpf.search.Search;


/**
 * collect and report coverage metrics on state machine execution
 */
public class Coverage extends PropertyListenerAdapter implements PublisherExtension {

	/*
	 * Helper Class to record all the coverage information for one StateMachine
	 */
	class StateMachineCoverage {	
		// maps State Id's to how many times they were covered
		LinkedHashMap<Integer,Integer> coverage = new LinkedHashMap<Integer,Integer>();
		// maps State Id's to the name of the State for reporting
	  LinkedHashMap<Integer,String>  idMap    = new LinkedHashMap<Integer,String>();

	  
	  public void addMapping(int id, String name) {
	  	idMap.put(id, name);
	  }
	  
	  public void addCoverage(int id, int visits) {
	  	coverage.put(id, visits);
	  }
	  
	  public String getStateName(int id) {
	  	return idMap.get(id);
	  }
	  
	  public int getCoverage(int id) {
	  	return coverage.get(id);
	  }

      String getConstraintViolation() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        
        for (Map.Entry<Integer,String> e : idMap.entrySet()) {
          String stateName = e.getValue();
          
          Integer cov = coverage.get(e.getKey());
          int nVisited = (cov != null) ? cov.intValue() : 0;

          if (nVisited == 0) {
            if (contains(required, stateName)) {
              pw.println("required  " + stateName + " NOT COVERED");
            }
          } else {
            if (contains(forbidden, stateName)){
              pw.println("forbidden " + stateName + " VISITED: " + nVisited);              
            }
          }
        }
        
        String s = sw.toString();
        if (s.length() > 0) {
          return s;
        } else {
          return null;
        }
      }
      
	  // Used for printing the results
	  public void printCoverageOn(PrintWriter pw) {
        
    	for (Map.Entry<Integer,String> e : idMap.entrySet()) {
    	  String stateName = e.getValue();
          
    	  Integer cov = coverage.get(e.getKey());
    	  int nVisited = (cov != null) ? cov.intValue() : 0;

          pw.print("  ");
          
          if (nVisited == 0) {
            pw.print( stateName + " : not covered");
            if (contains(required, stateName)) {
              pw.print(", REQUIRED");
            }
            pw.println();
            
          } else {
            pw.print( stateName + " : " + nVisited);
            if (contains(forbidden, stateName)){
              pw.print(", FORBIDDEN");              
            }
            pw.println();
          }
    	}
	  }
      	  
      boolean contains (String[] list, String stateName) {
        if (list == null) {
          return false;
        } else {
          for (int i=0; i<list.length; i++){
            if (stateName.startsWith(list[i])) {
              return true;
            }
          }
          return false;
        }
      }
	}
	
	// only allows 256 StateMachines
	public static final int MAXMACHINES = 256;
	// records the information per state machine
  StateMachineCoverage[] allCoverage = new StateMachineCoverage[MAXMACHINES];
  int numMachines=0;
  
  MethodInfo runMth;
  MethodInfo visitedMth;
  
  String[] required;
  String[] forbidden;
  
  String errorMsg;
  
  public Coverage (Config conf, JPF jpf){    
    if (jpf.addPublisherExtension(ConsolePublisher.class, this)) {      
      required = conf.getStringArray("sc.required");
      forbidden = conf.getStringArray("sc.forbidden");
    }    
  }
   
  public String getErrorMessage() {
    return errorMsg;
  }
  
  //@Override
  //No longer used? -- mvrooman
  public void vmInitialized (JVM vm){
    // we can't load classes until the VM is initialized
    //ClassInfo ciStateMachine = ClassLoaderInfo.getSystemResolvedClassInfo("gov.nasa.jpf.sc.StateMachine");
    ClassInfo ciStateMachine = ClassInfo.getResolvedClassInfo("gov.nasa.jpf.sc.StateMachine"); //mvrooman
    runMth = ciStateMachine.getMethod("run()V", true);
    
    //ClassInfo ciState = ClassLoaderInfo.getSystemResolvedClassInfo("gov.nasa.jpf.sc.State");//mvrooman
    ClassInfo ciState = ClassInfo.getResolvedClassInfo("gov.nasa.jpf.sc.State");
    visitedMth = ciState.getMethod("setVisited()V", true);
  }
  
  //@Override
  //No longer used? -- mvrooman
  public void executeInstruction (JVM jvm, ThreadInfo ti, Instruction insn) {
    if (insn instanceof INVOKEVIRTUAL) {
    	// see if the run method was called since this is the point where
    	// we know the StateMachine structure is already set
      if (insn.getMethodInfo() == runMth) {
        int machineRef = ti.getThis();
        MJIEnv env = ti.getEnv();

        int machineId = env.getIntField(machineRef, "id");
        // machineIds are allocated from 0 sequentially hence it is fine to use it as an array index here
        if (machineId < MAXMACHINES && allCoverage[machineId] == null) {
          allCoverage[machineId] = new StateMachineCoverage();
          numMachines++;
        }
        else {
          // only measure coverage upto MAXMACHINES 
          // if this machine's states have already been initialzed for coverage skip the rest
          return;
        }
        
        int statesRef = env.getReferenceField(machineRef, "states");
        int nStates = env.getIntField(machineRef, "nStates");
        for (int i=0; i<nStates; i++) {
          int sRef = env.getReferenceArrayElement(statesRef, i);
          int nameRef = env.getReferenceField(sRef,"fieldName");
          int id = env.getIntField(sRef,"id");
          String sName = env.getStringObject(nameRef);
          
          if (sName != null){
            // maps the state id to the string name for this state machine
            allCoverage[machineId].addMapping(id, sName);
          }
        }
      }
    }
    if (insn instanceof RETURN) {
      // once we return from the visitedState method we know the state was visited
      // and we can record the information for coverage
      if (insn.getMethodInfo() == visitedMth) {
        int stateRef = ti.getThis();
        MJIEnv env = ti.getEnv();
        int id = env.getIntField(stateRef, "id");
        // the visited field record how many times it was visited
        int nVisits = env.getIntField(stateRef, "visited");
        
        int machineRef = env.getReferenceField(stateRef, "machine");
        int machineId = env.getIntField(machineRef, "id");
        // we don't record if we have too many machines
        if (machineId < numMachines){
          allCoverage[machineId].addCoverage(id, nVisits);
        }
      }
    } 
  }

  @Override
  public void searchFinished (Search search) {
    // here we turn into a property, by explicitly setting a
    // search error in case
    if (required != null || forbidden != null) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < numMachines; i++) {
        String msg = allCoverage[i].getConstraintViolation();
        if (msg != null){
          sb.append(msg);
        }
      }
      if (sb.length() > 0){
        errorMsg = sb.toString();
        search.error(this);
      }
    }
  }
  
  @Override
  public void publishStart(Publisher publisher) {
    // nothing to do here
  }

  @Override
  public void publishTransition(Publisher publisher) {
    // nothing to do here
  }
  
  @Override
  public void publishPropertyViolation (Publisher publisher) {
    // nothing to do here
  }
  
  @Override
  public void publishFinished(Publisher publisher) {
    PrintWriter out = publisher.getOut();
    publisher.publishTopicStart("state coverage");
    for (int i = 0; i < numMachines; i++) {
      out.println("StateMachine #" + i);
      allCoverage[i].printCoverageOn(out);
  	}
  }

}

