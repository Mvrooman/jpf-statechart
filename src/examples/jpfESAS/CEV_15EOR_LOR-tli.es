// model checker guide script to verify 'EarthOrbit' state

SECTION ascent {
  srbIgnition
  stage1Separation
  lasJettison
  stage2Separation
}

SECTION earthOrbit {
  ANY {*}
}