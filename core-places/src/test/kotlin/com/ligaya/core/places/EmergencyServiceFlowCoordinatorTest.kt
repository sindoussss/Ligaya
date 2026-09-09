package com.ligaya.core.places

import com.ligaya.core.emergencyengine.EmergencyCompanionState
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.EmergencyStateMachine
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.IncidentType
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers this step's acceptance criteria against the real EmergencyStateMachine (Step 9) — not a
 * fake — so a passing test actually proves the engine reaches Succeeded/LookupFailed, not just
 * that this class calls a mock correctly.
 */
class EmergencyServiceFlowCoordinatorTest {

    private val near = GeoCoordinates(14.5995, 120.9842) // Manila

    private class RecordingNearbySearchSource(private val results: List<PlaceCandidate>) : NearbySearchSource {
        var lastCategory: PlacesCategory? = null
            private set

        override suspend fun search(near: GeoCoordinates, category: PlacesCategory): List<PlaceCandidate> {
            lastCategory = category
            return results
        }
    }

    private class FailingNearbySearchSource : NearbySearchSource {
        override suspend fun search(near: GeoCoordinates, category: PlacesCategory): List<PlaceCandidate> =
            error("must not be called: this incident type has no Places category (Issue I)")
    }

    private class FixedPlaceDetailsSource(private val details: PlaceDetails?) : PlaceDetailsSource {
        override suspend fun getDetails(placeId: String): PlaceDetails? = details
    }

    private fun engineAt(state: EmergencyState) = EmergencyStateMachine(initial = EmergencySnapshot(state = state))

    private fun reporterFor(engine: EmergencyStateMachine) =
        EmergencyServiceFlowReporter { engine.updateEmergencyServiceFlow(it) }

    private fun assertOtherSubsystemsUntouched(engine: EmergencyStateMachine) {
        assertEquals(LocationFlowState.Pending, engine.snapshot.subsystems.location)
        assertEquals(Unified911FlowState.Pending, engine.snapshot.subsystems.unified911)
        assertEquals(FamilyAlertFlowState.Pending, engine.snapshot.subsystems.familyAlert)
        assertEquals(EmergencyCompanionState.Pending, engine.snapshot.subsystems.companion)
    }

    @Test
    fun `FIRE queries the fire station category and succeeds`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val search = RecordingNearbySearchSource(listOf(PlaceCandidate("p1", near)))
        val coordinator = EmergencyServiceFlowCoordinator(search, FixedPlaceDetailsSource(PlaceDetails("123")), reporterFor(engine))

        coordinator.run(IncidentType.FIRE, near)

        assertEquals(PlacesCategory.FIRE_STATION, search.lastCategory)
        assertEquals(EmergencyServiceFlowState.Succeeded, engine.snapshot.subsystems.emergencyService)
        assertOtherSubsystemsUntouched(engine)
    }

    @Test
    fun `POLICE queries the police category and succeeds`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val search = RecordingNearbySearchSource(listOf(PlaceCandidate("p1", near)))
        val coordinator = EmergencyServiceFlowCoordinator(search, FixedPlaceDetailsSource(PlaceDetails("123")), reporterFor(engine))

        coordinator.run(IncidentType.POLICE, near)

        assertEquals(PlacesCategory.POLICE, search.lastCategory)
        assertEquals(EmergencyServiceFlowState.Succeeded, engine.snapshot.subsystems.emergencyService)
    }

    @Test
    fun `MEDICAL queries the hospital category and succeeds`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val search = RecordingNearbySearchSource(listOf(PlaceCandidate("p1", near)))
        val coordinator = EmergencyServiceFlowCoordinator(search, FixedPlaceDetailsSource(PlaceDetails("123")), reporterFor(engine))

        coordinator.run(IncidentType.MEDICAL, near)

        assertEquals(PlacesCategory.HOSPITAL, search.lastCategory)
        assertEquals(EmergencyServiceFlowState.Succeeded, engine.snapshot.subsystems.emergencyService)
    }

    @Test
    fun `RESCUE triggers the Issue I fallback without ever calling the Places search`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val coordinator = EmergencyServiceFlowCoordinator(
            FailingNearbySearchSource(),
            FixedPlaceDetailsSource(PlaceDetails("123")),
            reporterFor(engine),
        )

        val result = coordinator.run(IncidentType.RESCUE, near)

        assertTrue(result.isSuccess)
        assertEquals(EmergencyServiceFlowState.LookupFailed, engine.snapshot.subsystems.emergencyService)
        assertOtherSubsystemsUntouched(engine)
    }

    @Test
    fun `OTHER triggers the Issue I fallback without ever calling the Places search`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val coordinator = EmergencyServiceFlowCoordinator(
            FailingNearbySearchSource(),
            FixedPlaceDetailsSource(PlaceDetails("123")),
            reporterFor(engine),
        )

        val result = coordinator.run(IncidentType.OTHER, near)

        assertTrue(result.isSuccess)
        assertEquals(EmergencyServiceFlowState.LookupFailed, engine.snapshot.subsystems.emergencyService)
    }

    @Test
    fun `picks the actually nearest candidate, not the first result returned`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val far = PlaceCandidate("far", GeoCoordinates(15.5, 121.9)) // listed first, but farther
        val nearer = PlaceCandidate("nearer", GeoCoordinates(14.6, 120.98)) // listed second, but closer
        val search = RecordingNearbySearchSource(listOf(far, nearer))
        var requestedPlaceId: String? = null
        val details = object : PlaceDetailsSource {
            override suspend fun getDetails(placeId: String): PlaceDetails {
                requestedPlaceId = placeId
                return PlaceDetails("123")
            }
        }
        val coordinator = EmergencyServiceFlowCoordinator(search, details, reporterFor(engine))

        coordinator.run(IncidentType.FIRE, near)

        assertEquals("nearer", requestedPlaceId)
        assertEquals(EmergencyServiceFlowState.Succeeded, engine.snapshot.subsystems.emergencyService)
    }

    @Test
    fun `no candidates found reaches LookupFailed`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val search = RecordingNearbySearchSource(emptyList())
        val coordinator = EmergencyServiceFlowCoordinator(search, FixedPlaceDetailsSource(PlaceDetails("123")), reporterFor(engine))

        coordinator.run(IncidentType.FIRE, near)

        assertEquals(EmergencyServiceFlowState.LookupFailed, engine.snapshot.subsystems.emergencyService)
    }

    @Test
    fun `place details lookup failing reaches LookupFailed`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val search = RecordingNearbySearchSource(listOf(PlaceCandidate("p1", near)))
        val coordinator = EmergencyServiceFlowCoordinator(search, FixedPlaceDetailsSource(null), reporterFor(engine))

        coordinator.run(IncidentType.FIRE, near)

        assertEquals(EmergencyServiceFlowState.LookupFailed, engine.snapshot.subsystems.emergencyService)
    }

    @Test
    fun `a place with no public phone number still succeeds, never inventing one`() = runTest {
        val engine = engineAt(EmergencyState.EMERGENCY_ACTIVE)
        val search = RecordingNearbySearchSource(listOf(PlaceCandidate("p1", near)))
        // Simulates a mocked Places response with no phone number field present at all.
        val noNumberDetails = PlaceDetails(phoneNumber = null)
        val coordinator = EmergencyServiceFlowCoordinator(search, FixedPlaceDetailsSource(noNumberDetails), reporterFor(engine))

        val result = coordinator.run(IncidentType.FIRE, near)

        assertTrue(result.isSuccess)
        // Succeeded, not LookupFailed: a missing phone number is section 16's valid "do not
        // invent a number" outcome, not a failure. EmergencyServiceFlowState itself carries no
        // phone-number field at all, so there is no code path here that could fabricate one.
        assertEquals(EmergencyServiceFlowState.Succeeded, engine.snapshot.subsystems.emergencyService)
        assertNull(noNumberDetails.phoneNumber)
    }
}
