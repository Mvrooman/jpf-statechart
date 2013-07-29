State Machine Annotations
******************************

For a sample implementation see extensions/statechart/examples/CEV_15EOR_LOR
(visualize.CEV_15EOR_LOR)

These annotations were designed as an extendable means of quickly getting information 
about possible transitions that can be made from State to State.  Also, annotations may 
indicate points where states can transition to endStates in the diagram.  The 
annotations were created for the purpose of (re)creating a UML diagram from a state 
machine.


The form of these annotations is as follows:

@Transitions(
  neighbors = {
    @NextNeighbor(state="statename", label="triggerMethod()"),
    ...
  },
  externals = {
    @ExternalState(state="statename", label="triggerMethod()", isNext=true/false),
    ...
  }
)

Naming Conventions
*******************

For the 'state' fields of these annotations, we use a textual representation of the state, 
which does not include the master state.  Camel case should be used for all state names.  
For example, in the visualize.CEV_15EOR_LOR example, CEV_15EOR_LOR is the master state and 
its substates would be represented as "ascent", "earthOrbit", etc.  While the substates of 
Ascent would be written as "ascent.prelaunchCheck", "ascent.firstStage", etc.


@Transitions
****************

Only needs to be present if edges exist to/from the annotated state.
This annotation holds two arrays of annotations:

@NextNeighbor[] neighbors
@ExternalState[] externals


@NextNeighbor
*****************

String state
String label

This annotation should be used to indicate that an edge exists FROM the annotated state 
or any of its substates, TO another state (indicated in the 'state' field).  In other 
words, the annotated state is the tail of the edge, while the state represented by the 
'state' field of the annotation is the head.  The label can be the trigger method which 
caused this transition.

IMPORTANT: This annotation should only be used to describe a transition to a state which 
is on the same level of hierarchy as the annotated state.  This includes transitions 
from the annotated state to a substate of some state on the same level of hierarchy.
While transitions to substates are represented by this annotation, only state names
on the same level of hierarchy as the annotated state are to be used in the "state" field. 
For example, take the states A and B to be on the same level of hierarchy, with state C 
being a substate of B.  There is a transition from A -> C, therefore an annotation on A 
would look like this: @NextNeighbor(state="B", label="method()").

Duplicate states may be included in order to show that multiple methods may cause this transition.  
These annotations may include transitions to the state itself, but should not indicate any internal 
transitions within the annotated state.

The End Case
*****************

Transitions to the end pseudo state should be denoted by a @NextNeighbor annotation with 
the state="END".  To find these transitions in the code, look for setEndState() and other 
places where execution could end.

@ExternalState
********************

String state
String label
boolean isNext

This annotation is similar to @NextNeighbor, but behaves a little differently.  It is intended 
only to describe transitions that occur to / from states that are not on the same level of 
hierarchy as the annotated state.  These annotations can indicate either a transition to OR 
from the annotated state.  Set the isNext() flag to 'true' to indicate that the state 
represented by the 'state' field is the target of the transition.  Setting it false will 
indicate that the annotated state is the target of the transition.  
