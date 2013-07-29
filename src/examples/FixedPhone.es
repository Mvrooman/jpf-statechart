/* simple sequence event spec for FixedPhone */

SECTION default {
  liftReceiver;
  dialDigit;
  REPEAT 2 {
    //ANY { incompleteNumber, validNumber, invalidNumber };
    ANY {*}
    dialDigit
  }
}

SECTION active.connecting {
  busy
}

SECTION active.busy {
  hangupReceiver
}
