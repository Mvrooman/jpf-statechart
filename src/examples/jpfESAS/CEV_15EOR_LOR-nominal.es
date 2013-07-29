// simple nominal environment for CEVModes state machine

//--- earth ascent
srbIgnition
stage1Separation
lasJettison
stage2Separation
lsamRendezvous
tliBurn

//--- earth->moon transit
edsSeparation
loiBurn

//--- lunar ops
lsamSeparation
  // take a walk on the moon...
lsamAscentBurn
lsamAscentRendezvous
teiBurn

//--- moon->earth transit
smSeparation
eiBurn(false,false)

//--- earth entry (all automatic)