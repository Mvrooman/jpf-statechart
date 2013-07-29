                    'JPF ESAS' demonstration project README
                    =======================================


DISCLAIMER - this file is part of the 'JPF UML' demonstration project. As
such, it is only intended for demonstration purposes, does not contain
or refer to actual NASA flight software, and is solely derived from
publicly available information.

Goal of the 'JPF ESAS' demonstration project is to show model driven
verification, and especially UML state chart analysis with Java
Pathfinder (JPF). For this purpose, we have created a simple UML model of the
so called "1.5 EOR LOR" mission profile, on the basis of the final
"Exploration Systems Architecture Study", available on:

  http://www.nasa.gov/mission_pages/exploration/news/ESAS_report.html
  
The model represents a complete mission profile for a lunar landing, involving
the Crew Launch Vehicle (CLV), Crew Exploration Vehicle (CEV), and the 
Lunar Surface Access Module (LSAM). Modeled aspects include

 - flight phases
 - major events
 - spacecraft configuration
 - failures
 - aborts

  
The model contains several seeded defects like:

 - violated safety properties specified as in-code assertions
 - ambiguous transitions
 - unreachable states
 
While these defects were designed to be as realistic as such a simple model
allows for, it should be noted that they are artificial, and were only created
to demonstrate JPF capabilities.

Please see contents of the 'doc' directory for further information.

This example depends on the JPF "State Chart" extension, which has to be compiled
in order to run it. You can find more information about the JPF statechart
verification framework, and esp. the required statechart encoding as a Java
program using this framework in

  extensions/statechart/doc
  
Some of the included Eclipse launch configurations also refer to the JPF
"Compositional Verification" (CV) extension, which can be found in

  extensions/cv
  
The related examples uses machine learning to generate assumptions about
the environment, filtering out "impossible" event sequences".