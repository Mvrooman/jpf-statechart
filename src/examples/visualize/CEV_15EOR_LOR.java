
//Copyright (C) 2008 United States Government as represented by the
//Administrator of the National Aeronautics and Space Administration
//(NASA).  All Rights Reserved.

//This software is distributed under the NASA Open Source Agreement
//(NOSA), version 1.3.  The NOSA has been approved by the Open Source
//Initiative.  See the file NOSA-1.3-JPF at the top of the distribution
//directory tree for the complete NOSA document.

//THE SUBJECT SOFTWARE IS PROVIDED "AS IS" WITHOUT ANY WARRANTY OF ANY
//KIND, EITHER EXPRESSED, IMPLIED, OR STATUTORY, INCLUDING, BUT NOT
//LIMITED TO, ANY WARRANTY THAT THE SUBJECT SOFTWARE WILL CONFORM TO
//SPECIFICATIONS, ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR
//A PARTICULAR PURPOSE, OR FREEDOM FROM INFRINGEMENT, ANY WARRANTY THAT
//THE SUBJECT SOFTWARE WILL BE ERROR FREE, OR ANY WARRANTY THAT
//DOCUMENTATION, IF PROVIDED, WILL CONFORM TO THE SUBJECT SOFTWARE.



//DISCLAIMER - this file is part of the 'ESAS' demonstration project. As
//such, it is only intended for demonstration purposes, does not contain
//or refer to actual NASA flight software, and is solely derived from
//publicly available information. For further details, please refer to the
//README-ESAS file that is included in this distribution.


package visualize;

import gov.nasa.jpf.sc.*;
import gov.nasa.jpf.tools.visualize.ExternalState;
import gov.nasa.jpf.tools.visualize.NextNeighbor;
import gov.nasa.jpf.tools.visualize.Transitions;

/**
 * An annotated version of examples/jpfESAS/CEV_15EOR_LOR
 * 
 * simplified state machine for CEV 1.5 EOR-LOR mission profile
 * 
 * we model the following aspects:
 *   (1) flight phases (Ascent, EarthOrbit, TransitEarthMoon etc.)
 *   (2) events (srbIgnition, stage1Separation, lsamRendezvous etc.)
 *   (3) vehicle configuration (via controlled object 'spacecraft')
 *   (4) failures (via controlled object 'failures')
 *   
 * this example requires extensions/statechart to be compiled
 */
public class CEV_15EOR_LOR extends State {

	//--------------------------------- our controlled objects

	ErrorLog errors = new ErrorLog();
	Failures failures = new Failures(errors);
	Spacecraft spacecraft = new Spacecraft(failures,errors);

	//--------------------------------- our state model

	@Transitions(
			neighbors = {
					@NextNeighbor(state="earthOrbit", label="stage2Separation()"),
					@NextNeighbor(state="earthOrbit", label="completion()"),
					@NextNeighbor(state="entry", label="completion()"),
					@NextNeighbor(state="END", label="")
			},
			externals = {}
	)
	class Ascent extends State {

		@Transitions(
				neighbors = {
						@NextNeighbor(state="ascent.firstStage", label="srbIgnition()"),
						@NextNeighbor(state="ascent.padAbort", label="failure()"),
						@NextNeighbor(state="ascent.holdLaunch", label="failure()") 
				},
				externals = {}
		)
		class PrelaunchCheck extends State {
			public void srbIgnition () {
				setNextState(firstStage);
			}

			@Params("10|1")
			public void failure (int tminus) {
				if (tminus <= 5) {
					setNextState(padAbort);
				} else {
					setNextState(holdLaunch);
				}
			}
		}  PrelaunchCheck prelaunchCheck = makeInitial(new PrelaunchCheck());


		@Transitions( 
				neighbors = {
						@NextNeighbor(state="ascent.secondStage", label="doStage1Separation()"),
						@NextNeighbor(state="ascent.abortLowActiveLAS", label="abort()"),
						@NextNeighbor(state="ascent.abortPassiveLAS", label="abort()"),
						@NextNeighbor(state="ascent.abortHighActiveLAS", label="abort()")
				},
				externals = {}
		)
		class FirstStage extends State {
			public void stage1Separation() {
				spacecraft.doStage1Separation();

				setNextState(secondStage);
			}

			@Params("5000|120000|200000, true|false")
			public void abort (int altitude, boolean controlMotorFired) {
				spacecraft.doStage1Abort(altitude,controlMotorFired);

				if (altitude <= 120000) {
					if (controlMotorFired) {
						setNextState(abortLowActiveLAS);
					} else {
						setNextState(abortPassiveLAS);
					}
				}

				if (altitude >= 120000) { // <ERR> interval overlap -> ambiguity
					setNextState(abortHighActiveLAS);
					// <ERR> forgotten controlMotor branch
				}

				assert hasNextState() : errors.log("abort command did not enter abort state");
			}
		}  FirstStage firstStage = new FirstStage();

		@Transitions(
				neighbors = {
						@NextNeighbor(state="ascent.secondStage", label="lastJettison()"),
						@NextNeighbor(state="ascent.abortHighActiveLAS", label="abort()"),
						@NextNeighbor(state="ascent.abortPassiveLAS", label="abort()")
				},
				externals = {
						@ExternalState(state="earthOrbit", label="stage2Separation", isNext=true)
				}
		)
		class SecondStage extends State {

			public void lasJettison() {
				spacecraft.doLASjettison();

				setNextState(this);
			}

			public void stage2Separation () {
				spacecraft.doStage2Separation();

				setNextState(earthOrbit);
			}

			public void abort (boolean controlMotorFired) {
				spacecraft.doStage2Abort(controlMotorFired);

				if (controlMotorFired) {
					setNextState(abortHighActiveLAS);
				} else {
					setNextState(abortPassiveLAS);          
				}

				assert hasNextState() : errors.log("abort command did not enter abort state");
			}
		}  SecondStage secondStage = new SecondStage();

		//------ the off-nominals
		@Transitions(
				neighbors = { @NextNeighbor(state="END", label="completion()") }, 
				externals = {}
		)
		class HoldLaunch extends State {
			public void completion() {
				setEndState();
			}
		}  HoldLaunch holdLaunch = new HoldLaunch();

		@Transitions(
				neighbors = { @NextNeighbor(state="END", label="completion()")},
				externals = {}
		)
		class PadAbort extends State {
			public void completion() {
				setEndState();
			}      
		}  PadAbort padAbort = new PadAbort();

		@Transitions(
				neighbors={},
				externals={ @ExternalState(state="entry.chuteSequence", label="completion()", isNext=true) }
		)
		class AbortPassiveLAS extends State {
			public void completion() {
				spacecraft.doLowPassiveAbort();

				setNextState(entry.chuteSequence);
			}
		}  AbortPassiveLAS abortPassiveLAS = new AbortPassiveLAS();


		@Transitions(
				neighbors={},
				externals={ @ExternalState(state="entry.chuteSequence", label="completion()", isNext=true) }
		)
		class AbortLowActiveLAS extends State {
			public void completion() {
				assert failures.noLAS_CNTRLfailure() : errors.log("active LAS with failed control motor");

				spacecraft.doLowActiveAbort();

				setNextState(entry.chuteSequence);
			}
		}  AbortLowActiveLAS abortLowActiveLAS = new AbortLowActiveLAS();


		@Transitions(
				neighbors={},
				externals={ @ExternalState(state="entry.chuteSequence", label="completion()", isNext=true) }
		)
		class AbortHighActiveLAS extends State {
			public void completion() {
				assert failures.noLAS_CNTRLfailure() : errors.log("active LAS with failed control motor");

				//lasJettison();
				setNextState(entry.chuteSequence);
			}      
		}  AbortHighActiveLAS abortHighActiveLAS = new AbortHighActiveLAS();

	}  Ascent ascent = makeInitial(new Ascent());

	@Transitions(
			neighbors = {
					@NextNeighbor(state="transitEarthMoon", label="tliBurn()"),
					@NextNeighbor(state="entry", label="deOrbit()") },
					externals = {}
	)
	class EarthOrbit extends State {

		@Transitions(
				neighbors = {
						@NextNeighbor(state="earthOrbit.orbitOps", label="completion()"),
						@NextNeighbor(state="earthOrbit.safeHold", label="completion()")
				},
				externals = {}
		)
		class Insertion extends State {
			public void completion() {
				if (failures.noEARTH_SENSORfailure()){
					setNextState(orbitOps);
				} else {
					setNextState(safeHold);    // <ERR> never reached
				}
			}
		}  Insertion insertion = makeInitial(new Insertion());

		@Transitions(
				neighbors = { 
						@NextNeighbor(state="earthOrbit.orbitOps", label="lsamRendezvous()") 
				},
				externals = {
						@ExternalState(state="transitEarthMoon", label="tliBurn()", isNext=true),
						@ExternalState(state="entry", label="tliBurn()", isNext=true)
				}
		)
		class OrbitOps extends State {

			public void lsamRendezvous() {
				assert spacecraft.readyForLSAMrendezvous() : errors.last();

				spacecraft.doLSAMrendezvous();

				setNextState(this);
			}

			public void tliBurn() {
				assert spacecraft.readyForTliBurn() : errors.last();

				setNextState(transitEarthMoon);
			}

			public void deorbit() {
				assert spacecraft.readyForDeorbit() : errors.last();

				setNextState(entry);
			}
		}  OrbitOps orbitOps = new OrbitOps();

		@Transitions(
				neighbors = {
						@NextNeighbor(state="earthOrbit.orbitOps", label="enterOrbitOps()")
				},
				externals = {
						@ExternalState(state="entry", label="deOrbit()", isNext=true)
				}
		)
		class SafeHold extends State {
			public void enterOrbitOps() {
				if (failures.noEARTH_SENSORfailure()){
					setNextState(orbitOps);
				}
			}

			public void deorbit() {
				assert spacecraft.readyForDeorbit() : errors.last();

				setNextState(entry);
			}      
		}  SafeHold safeHold = new SafeHold();

	}  EarthOrbit earthOrbit = new EarthOrbit();

	@Transitions(
			neighbors = {
					@NextNeighbor(state="transitEarthMoon", label="edsSeparation()"),
					@NextNeighbor(state="lunarOps", label="loiBurn()")
			},
			externals = {}
	)
	class TransitEarthMoon extends State {

		public void edsSeparation() {
			spacecraft.doEDSseparation();

			setNextState(this);
		}

		public void loiBurn () {
			setNextState(lunarOps);
		}
	}  TransitEarthMoon transitEarthMoon = new TransitEarthMoon();

	@Transitions(
			neighbors = {
					@NextNeighbor(state="transitMoonEarth", label="teiBurn()")
			},
			externals = {}
	)
	class LunarOps extends State {

		@Transitions(
				neighbors = {
						@NextNeighbor(state="lunarOps.lunarOrbit", label="completion()")
				},
				externals = {}
		)
		class Insertion extends State {
			public void completion() {
				setNextState(lunarOrbit);
			}
		}  Insertion insertion = makeInitial(new Insertion());

		@Transitions(
				neighbors = {
						@NextNeighbor(state="lunarOps.lunarLanding", label="lsamSeparation()")
				},
				externals = {
						@ExternalState(state="transitMoonEarth", label="teiBurn()", isNext=true)
				}
		)
		class LunarOrbit extends State {
			public void lsamSeparation() {
				setNextState(lunarLanding);
			}

			public void teiBurn() {
				assert spacecraft.readyForTeiBurn() : errors.last();

				setNextState(transitMoonEarth);
			}
		}  LunarOrbit lunarOrbit = new LunarOrbit();

		@Transitions(
				neighbors = {
						@NextNeighbor(state="lunarOps.lunarOrbit", label="completion()")
				},
				externals = {}
		)
		class LunarLanding extends State {

			public void completion() {
				setNextState(lunarOrbit);
			}

			//--- CEV region
			@Transitions(
					neighbors = {
							@NextNeighbor(state="END", label="lsamAscentRendezvous()")
					},
					externals = {}
			)
			class OrbitOpsLoiter extends State {
				public void lsamAscentRendezvous () {
					setEndState();
				}
			}  OrbitOpsLoiter orbitOpsLoiter = makeInitial(new OrbitOpsLoiter()); // CEV region

			//--- LSAM region
			@Transitions(
					neighbors = { @NextNeighbor(state="lunarOps.lunarLanding.surfaceOps", label="completion()") },
					externals = {}
			)
			class LunarDescent extends State {
				public void completion () {
					setNextState(surfaceOps);
				}
			}  LunarDescent lunarDescent = makeInitial(new LunarDescent()); // LSAM region

			@Transitions(
					neighbors = {@NextNeighbor(state="lunarOps.lunarLanding.lunarAscent", label="lsamAscentBurn()")},
					externals = {}
			)
			class SurfaceOps extends State {
				public void lsamAscentBurn () {
					spacecraft.doLSAMascentBurn();

					setNextState(lunarAscent);
				}
			}  SurfaceOps surfaceOps = new SurfaceOps();

			@Transitions(
					neighbors = {@NextNeighbor(state="END", label="lsamAscentRendezvous()")},
					externals = {}
			)
			class LunarAscent extends State {
				public void lsamAscentRendezvous () {
					spacecraft.doLSAMascentRendezvous();

					setEndState();
				}
			}  LunarAscent lunarAscent = new LunarAscent();
		}  LunarLanding lunarLanding = new LunarLanding();

	}  LunarOps lunarOps = new LunarOps();

	@Transitions(
			neighbors = {
					@NextNeighbor(state="entry", label="eiBurn()"),
					@NextNeighbor(state="transitMoonEarth", label="smSeparation()")
			},
			externals = {}
	)
	class TransitMoonEarth extends State {
		public void smSeparation() {
			spacecraft.doSMseparation();

			setNextState(this);
		}

		public void eiBurn (boolean cmImbalance, boolean rcsFailure) {
			// spacecraft should now only consist of CM
			assert spacecraft.readyForEiBurn() : errors.last();

			spacecraft.doEiBurn(cmImbalance, rcsFailure);

			if (cmImbalance) {
				setNextState(entry.abortEntryBallistic);
			} else if (rcsFailure) {
				setNextState(entry.abortEntryFixedBank);
			} else {
				setNextState(entry);
			}

			// ?? and what about cmImbalance AND rcsFailure ??
		}
	}  TransitMoonEarth transitMoonEarth = new TransitMoonEarth();

	@Transitions(
			neighbors = {@NextNeighbor(state="END", label="")},
			externals = {}
	)
	class Entry extends State {

		//--- nominal states
		@Transitions(
				neighbors = {@NextNeighbor(state="entry.nominalEntry", label="completion()")},
				externals = {}
		)
		class EntryInterface extends State {      
			public void completion() {        
				setNextState(nominalEntry);
			}
		}  EntryInterface entryInterface = makeInitial(new EntryInterface());

		@Transitions(
				neighbors = {@NextNeighbor(state="entry.chuteSequence", label="completion()")},
				externals = {}
		)
		class NominalEntry extends State {
			public void completion() {
				setNextState(chuteSequence);
			}
		}  NominalEntry nominalEntry = new NominalEntry();

		@Transitions(
				neighbors = {@NextNeighbor(state="entry.landing", label="completion()")},
				externals = {
						@ExternalState(state="ascent", label="completion()", isNext=false)
				}
		)
		class ChuteSequence extends State {   
			public void completion() {
				assert spacecraft.readyForChuteSequence() : errors.last();

				setNextState(landing);
			}
		}  ChuteSequence chuteSequence = new ChuteSequence();

		@Transitions(
				neighbors = {@NextNeighbor(state="END", label="completion()")},
				externals = {}
		)
		class Landing extends State {
			public void completion() {
				setEndState();
			}
		}  Landing landing = new Landing();

		//--- off nominal states  (?? from interface)
		@Transitions(
				neighbors = {@NextNeighbor(state="entry.chuteSequence", label="completion()")},
				externals = {@ExternalState(state="transitMoonEarth", label="eiBurn()", isNext=false)}
		)
		class AbortEntryBallistic extends State {
			// CEV mass distribution out of whack

			public void completion() {
				setNextState(chuteSequence);
			}
		}  AbortEntryBallistic abortEntryBallistic = new AbortEntryBallistic();

		@Transitions(
				neighbors = {@NextNeighbor(state="entry.chuteSequence", label="completion()")},
				externals = {@ExternalState(state="transitMoonEarth", label="eiBurn()", isNext=false)}
		)
		class AbortEntryFixedBank extends State {
			// RCS did not work

			public void completion() {
				setNextState(chuteSequence);
			}

		}  AbortEntryFixedBank abortEntryFixedBank = new AbortEntryFixedBank();


	}  Entry entry = new Entry();

}
