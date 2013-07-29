
//--- check all of earth orbit (except of in-flight aborts)

SECTION ascent.prelaunchCheck {
  srbIgnition
}

SECTION ascent.firstStage {
  stage1Separation
}

SECTION ascent.secondStage {
  lasJettison
  stage2Separation
}


//--- check all of EarthOrbit
SECTION earthOrbit { // covers Insertion and SafeHold
  ANY {*}
}

SECTION earthOrbit.orbitOps {
  // here it would be nice to have nested ANYs, so that we also catch deOrbit
  // (ANY{*} would run into the tli-w/o-EDS error)
  lsamRendezvous
  tliBurn
}
