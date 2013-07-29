SECTION ascent {
  srbIgnition();
  stage1Separation();
  stage2Separation();
}

SECTION earthOrbit {
  lsamRendezvous();
  ANY {*}
}
