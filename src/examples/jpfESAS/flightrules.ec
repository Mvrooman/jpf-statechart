/**********************************************************************
 * example of flight rule encoding
 *
 * this encoding uses accepting automata that are constraints over the
 * environment, i.e. valid event sequences. The syntax is essentially the
 * same as for guidance scripts (which imperatively define valid sequences),
 * except of that sequences have to end in (sets of) explicit events.
 * This restriction is due to the fact that we match backwards, i.e. when
 * processing a certain event, we check if it's a terminating event in one
 * of the configured assumptions, and if it is, we backwards match the
 * corresponding sequence, discarding in case the automaton does not
 * accept the current trace. This mechanism saves us from having to
 * synchronize the statemachine with the assumption automatons, and is
 * arguably more intuitive for assumptions of the form "if event 'b' occurs,
 * it has to be preceeded by event 'a'" ([] !b U a), or "if event 'b' occurs,
 * event 'a' should not have happened before" ([] a -> !<>b).
 * This is of course only a small subset of possible assumptions.
 */

SECTION :no_deorbit_with_stack {
  ~lsamRendezvous    // we need a real 'ALL_EXCEPT {..}'
  deorbit
}

SECTION :no_lsamRendezvous_without_lasJettison {
  lasJettison
  *
  lsamRendezvous
}

SECTION :no_tliBurn_without_lsamRendezvous {
  lsamRendezvous
  *
  tliBurn
}


SECTION :no_loiBurn_without_edsSeparation {
  edsSeparation
  *
  loiBurn
}

SECTION :no_lsamAscentRendezvous_without_lsamAscentBurn {
  lsamAscentBurn
  *
  lsamAscentRendezvous
}

SECTION :no_teiBurn_without_lsamSeparation {
  lsamSeparation
  *
  teiBurn
}

SECTION :no_eiBurn_without_smSeparation {
  smSeparation
  *
  eiBurn(true|false,true|false)
}