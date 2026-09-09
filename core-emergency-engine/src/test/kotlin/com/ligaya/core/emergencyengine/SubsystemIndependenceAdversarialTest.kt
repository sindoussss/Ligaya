package com.ligaya.core.emergencyengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 29: proves section 13's "none blocks another" claim exhaustively, not just anecdotally —
 * the roadmap's own distinction from Step 14's narrower "all four failed at once" coverage.
 *
 * Scope note: this is deliberately an engine-only harness, not one wiring together the real
 * LocationFlowCoordinator/Unified911FlowCoordinator/EmergencyServiceFlowCoordinator/family-alert
 * backend with all of their own external fakes (GPS, dialer, Places, SMS gateway). Each of those
 * already has its own dedicated test suite (Steps 15–19) proving it reports the right state into
 * whatever engine it's given; what was still only anecdotally covered is what the engine itself
 * guarantees once a state has been reported — that is exactly what ConcurrentSubsystemStates and
 * EmergencyStateMachine are, so testing them directly, fast and without any Android/network
 * dependency, is what actually makes this suitable as "an automated CI gating check before any
 * release" (this step's own words) — a slow, flaky integration harness would undermine the very
 * property it's meant to guarantee.
 *
 * EmergencyCompanionState is deliberately excluded from the failure-injection cases below: it has
 * no failure sub-state at all (see its own doc comment, Step 9) — a Gemini/AI failure is a
 * degraded-capability condition handled entirely outside this engine (Step 27's
 * VoiceInterpretationOutcome), never a state-machine failure state. It is still covered by every
 * assertion below that checks the *other* subsystems are untouched, since its default Pending
 * value must never change either.
 */
class SubsystemIndependenceAdversarialTest {

    private fun engineAt(state: EmergencyState) = EmergencyStateMachine(initial = EmergencySnapshot(state = state))

    // --- (a) Forcing exactly one subsystem to fail never touches the other four ---

    @Test
    fun `forcing exactly one subsystem to fail never changes any of the other four, and resolution still succeeds`() {
        val cases: List<Pair<String, (EmergencyStateMachine) -> ConcurrentSubsystemStates>> = listOf(
            "location" to { engine ->
                engine.updateLocationFlow(LocationFlowState.Unavailable)
                ConcurrentSubsystemStates(location = LocationFlowState.Unavailable)
            },
            "unified911" to { engine ->
                engine.updateUnified911Flow(Unified911FlowState.CallFailed)
                ConcurrentSubsystemStates(unified911 = Unified911FlowState.CallFailed)
            },
            "emergencyService" to { engine ->
                engine.updateEmergencyServiceFlow(EmergencyServiceFlowState.LookupFailed)
                ConcurrentSubsystemStates(emergencyService = EmergencyServiceFlowState.LookupFailed)
            },
            "familyAlert" to { engine ->
                engine.updateFamilyAlertFlow(FamilyAlertFlowState.DeliveryFailed)
                ConcurrentSubsystemStates(familyAlert = FamilyAlertFlowState.DeliveryFailed)
            },
        )

        for ((label, applyFailureAndBuildExpectation) in cases) {
            val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)

            val expectedSubsystems = applyFailureAndBuildExpectation(engine)

            // Comparing the WHOLE ConcurrentSubsystemStates, not just the failed field: since
            // `expectedSubsystems` only sets the one targeted field and leaves the other four at
            // their default Pending, this single equality check proves both "the failure was
            // recorded" and "nothing else moved" at once.
            assertEquals(
                "forcing $label to fail must never change an unrelated subsystem",
                expectedSubsystems,
                engine.snapshot.subsystems,
            )

            assertTrue("$label failure must not block markUserSafe", engine.markUserSafe().isSuccess)
            assertTrue("$label failure must not block resolveEmergency", engine.resolveEmergency().isSuccess)
            assertTrue("$label failure must not block closeEmergency", engine.closeEmergency().isSuccess)
            assertEquals(EmergencyState.CLOSED, engine.snapshot.state)
        }
    }

    // --- (b) Every combination of simultaneous failures still resolves — genuinely exhaustive,
    //     not just the single "all four failed" case Step 14 already covered ---

    @Test
    fun `resolution succeeds for every combination of simultaneous subsystem failures`() {
        val locationOutcomes = listOf(LocationFlowState.Pending, LocationFlowState.Unavailable)
        val unified911Outcomes = listOf(Unified911FlowState.Pending, Unified911FlowState.CallFailed)
        val emergencyServiceOutcomes = listOf(EmergencyServiceFlowState.Pending, EmergencyServiceFlowState.LookupFailed)
        val familyAlertOutcomes = listOf(FamilyAlertFlowState.Pending, FamilyAlertFlowState.DeliveryFailed)

        var scenariosRun = 0
        for (location in locationOutcomes) {
            for (unified911 in unified911Outcomes) {
                for (emergencyService in emergencyServiceOutcomes) {
                    for (familyAlert in familyAlertOutcomes) {
                        scenariosRun++
                        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
                        engine.updateLocationFlow(location)
                        engine.updateUnified911Flow(unified911)
                        engine.updateEmergencyServiceFlow(emergencyService)
                        engine.updateFamilyAlertFlow(familyAlert)

                        val scenario = "location=$location, unified911=$unified911, " +
                            "emergencyService=$emergencyService, familyAlert=$familyAlert"
                        assertTrue("markUserSafe failed for [$scenario]", engine.markUserSafe().isSuccess)
                        assertTrue("resolveEmergency failed for [$scenario]", engine.resolveEmergency().isSuccess)
                        assertTrue("closeEmergency failed for [$scenario]", engine.closeEmergency().isSuccess)
                        assertEquals("[$scenario]", EmergencyState.CLOSED, engine.snapshot.state)
                    }
                }
            }
        }

        // 2 outcomes ^ 4 independently-varied subsystems — every combination, not a sample.
        assertEquals(16, scenariosRun)
    }
}
