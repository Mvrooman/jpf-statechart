//
// Copyright  (C) 2006 United States Government as represented by the
// Administrator of the National Aeronautics and Space Administration
//  (NASA).  All Rights Reserved.
// 
// This software is distributed under the NASA Open Source Agreement
//  (NOSA), version 1.3.  The NOSA has been approved by the Open Source
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
package gov.nasa.jpf.vm;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.jvm.ClassInfo;
import gov.nasa.jpf.jvm.FieldInfo;
import gov.nasa.jpf.jvm.NativePeer;

import java.util.ArrayList;
import java.util.HashMap;


public class JPF_gov_nasa_jpf_sc_State extends NativePeer {
  
  // map state names to (invariant) ElementInfo ids
  public static HashMap<String,Integer> map;
      
  public JPF_gov_nasa_jpf_sc_State (Config conf){
    map  = new HashMap<String,Integer>();
  }
  
  @MJI
  public void setStateFields____V (MJIEnv env, int objRef) {
    ClassInfo ci = env.getClassInfo(objRef);
    ClassInfo sci = ClassInfo.getResolvedClassInfo("gov.nasa.jpf.sc.State");

    String typeName = ci.getName().replace('$', '.');
    int tnRef = env.newString(typeName);
    env.setReferenceField(objRef, "typeName", tnRef);
    
    int fnRef = env.getReferenceField(objRef, "fieldName");
    String fieldName = env.getStringObject(fnRef);
    
    // update our symbolic state map
    map.put(fieldName, new Integer(objRef));
        
    ArrayList<Integer> a = new ArrayList<Integer>();

    for (; ci != sci; ci = ci.getSuperClass()) {
      // get super and sub states
      // NOTE: we need to do this for *all* (i.e. also inherited and possibly
      // masked) fields, since our terminal State classes might use a user defined base
      int n = ci.getNumberOfDeclaredInstanceFields();

      for (int i=0; i<n; i++) {
        FieldInfo fi = ci.getDeclaredInstanceField(i);

        // somebody might store state references in fields that do not
        // denote sub-states, skip those
        if (fi.isReference() && (fi.getAnnotation("gov.nasa.jpf.sc.State$NoSubState") == null)) {

          int r = env.getReferenceField(objRef, fi);
          
          if (r != MJIEnv.NULL) {
            ClassInfo fci = env.getClassInfo(r);

            if (fci.isInstanceOf(sci)){       // a 'State' instance
              String fName = fi.getName();

              if (fName.startsWith("this$")) { // our enclosing state
                env.setReferenceField(objRef, "superState", r);              
              } else {                                // a sub state
                String fn = fieldName != null ? fieldName + '.' + fName : fName;
                int fnr = env.newString(fn);
                env.setReferenceField(r, "fieldName", fnr);              
                a.add(r);
              }
            }

            if (fci.isArray()) {              // a 'State' array            
              ClassInfo cci = fci.getComponentClassInfo();
              if (cci.isInstanceOf(sci)) {
                String fName = fi.getName();
                String fn = fieldName != null ? fieldName + '.' + fName : fName;
                int len = env.getArrayLength(r);
                for (int j=0; j<len; j++) {
                  int e = env.getReferenceArrayElement(r, j);
                  int fnr = env.newString(fn + '[' + j + ']');
                  env.setReferenceField(e, "fieldName", fnr);
                  a.add(e);
                }
              }
            }
          }
        }
      }
    }

    int nSub = a.size();
    if (nSub > 0){
      int aRef = env.newObjectArray("Lgov/nasa/jpf/sc/State;", nSub);
      for (int i=0; i<nSub; i++){
        env.setReferenceArrayElement(aRef, i, a.get(i));
      }
      env.setReferenceField(objRef, "subStates", aRef);
    }
  }
  
  public static void setSpecialMethods____V (MJIEnv env, int objRef){
    // we don't need them, since we use the StateMachine native peer
    // to execute the actions
  }
}
