package com.ligaya.core.backend.audit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ligaya.core.emergencyengine.AuditEvent
import com.ligaya.core.emergencyengine.AuditedSubsystem
import com.ligaya.core.emergencyengine.EmergencyCompanionState
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.EmergencyStateMachine
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 31's own acceptance criteria against the real (emulated) Firestore, not a fake: a full
 * emergency lifecycle — activation, subsystem events including one failure, resolution — produces
 * a complete, ordered audit trail readable back from the backend exactly as the engine produced
 * it.
 */
@RunWith(AndroidJUnit4::class)
class AuditLogIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private data class SignedIn(val uid: String, val firestore: FirebaseFirestore)

    private suspend fun signIn(appName: String): SignedIn {
        val options = FirebaseOptions.Builder()
            .setProjectId("demo-ligaya-test")
            .setApplicationId("1:000000000000:android:0000000000000000000000")
            .setApiKey("fake-api-key-for-emulator-only")
            .build()
        val app = FirebaseApp.initializeApp(context, options, appName)

        val auth = FirebaseAuth.getInstance(app).apply { useEmulator("10.0.2.2", 9099) }
        val email = "user-${System.currentTimeMillis()}-${(0..999999).random()}@test.ligaya.app"
        val uid = auth.createUserWithEmailAndPassword(email, "correcthorsebatterystaple").await().user!!.uid

        val firestore = FirebaseFirestore.getInstance(app).apply { useEmulator("10.0.2.2", 8090) }
        return SignedIn(uid, firestore)
    }

    @Test
    fun aFullScriptedEmergencyLifecycleProducesACompleteOrderedAuditTrail() = runTest {
        val user = signIn("auditLogLifecycle")

        val eventId = "event-${System.currentTimeMillis()}"
        user.firestore.collection("emergencyEvents").document(eventId)
            .set(mapOf("user_id" to user.uid, "incident_type" to "FIRE", "status" to "ACTIVE"))
            .await()

        val repository: AuditLogRepository = FirestoreAuditLogRepository(user.firestore)
        val machine = AuditingEmergencyStateMachine(EmergencyStateMachine(), repository, eventId)

        machine.detectEmergency()
        machine.confirmEmergency()
        machine.activateEmergency()
        machine.updateLocationFlow(LocationFlowState.Succeeded)
        machine.updateUnified911Flow(Unified911FlowState.CallFailed) // the scripted failure event
        machine.updateEmergencyServiceFlow(EmergencyServiceFlowState.Succeeded)
        machine.updateFamilyAlertFlow(FamilyAlertFlowState.Succeeded)
        machine.updateEmergencyCompanion(EmergencyCompanionState.Active)
        machine.markUserSafe()
        machine.resolveEmergency()
        machine.closeEmergency()

        val expected = listOf(
            AuditEvent.MainStateTransition(EmergencyState.IDLE, EmergencyState.EMERGENCY_DETECTED),
            AuditEvent.MainStateTransition(EmergencyState.EMERGENCY_DETECTED, EmergencyState.EMERGENCY_CONFIRMED),
            AuditEvent.MainStateTransition(EmergencyState.EMERGENCY_CONFIRMED, EmergencyState.EMERGENCY_ACTIVE),
            AuditEvent.SubsystemStateChanged(AuditedSubsystem.LOCATION, "Pending", "Succeeded"),
            AuditEvent.SubsystemStateChanged(AuditedSubsystem.UNIFIED_911, "Pending", "CallFailed"),
            AuditEvent.SubsystemStateChanged(AuditedSubsystem.EMERGENCY_SERVICE, "Pending", "Succeeded"),
            AuditEvent.SubsystemStateChanged(AuditedSubsystem.FAMILY_ALERT, "Pending", "Succeeded"),
            AuditEvent.SubsystemStateChanged(AuditedSubsystem.COMPANION, "Pending", "Active"),
            AuditEvent.MainStateTransition(EmergencyState.EMERGENCY_ACTIVE, EmergencyState.USER_MARKED_SAFE),
            AuditEvent.MainStateTransition(EmergencyState.USER_MARKED_SAFE, EmergencyState.EMERGENCY_RESOLVED),
            AuditEvent.MainStateTransition(EmergencyState.EMERGENCY_RESOLVED, EmergencyState.CLOSED),
        )

        assertEquals(EmergencyState.CLOSED, machine.snapshot.state)

        val readBack = repository.getOrderedAuditTrail(eventId)
        assertTrue(readBack.isSuccess)
        assertEquals(expected, readBack.getOrNull())
    }

    @Test
    fun aRejectedTransitionNeverProducesAnAuditEntry() = runTest {
        val user = signIn("auditLogRejected")

        val eventId = "event-rejected-${System.currentTimeMillis()}"
        user.firestore.collection("emergencyEvents").document(eventId)
            .set(mapOf("user_id" to user.uid, "incident_type" to "FIRE", "status" to "ACTIVE"))
            .await()

        val repository: AuditLogRepository = FirestoreAuditLogRepository(user.firestore)
        val machine = AuditingEmergencyStateMachine(EmergencyStateMachine(), repository, eventId)

        // Illegal from IDLE — must not appear in the backend audit log at all.
        val result = machine.activateEmergency()
        assertTrue(result.isFailure)

        val readBack = repository.getOrderedAuditTrail(eventId)
        assertTrue(readBack.isSuccess)
        assertTrue(readBack.getOrNull().orEmpty().isEmpty())
    }

    @Test
    fun anotherUsersEmergencyAuditLogCannotBeReadOrWrittenByAnOutsider() = runTest {
        val owner = signIn("auditLogOwner")
        val outsider = signIn("auditLogOutsider")

        val eventId = "event-outsider-${System.currentTimeMillis()}"
        owner.firestore.collection("emergencyEvents").document(eventId)
            .set(mapOf("user_id" to owner.uid, "incident_type" to "FIRE", "status" to "ACTIVE"))
            .await()

        val outsiderRepository: AuditLogRepository = FirestoreAuditLogRepository(outsider.firestore)

        val appendResult = outsiderRepository.append(
            eventId,
            sequence = 0,
            event = AuditEvent.MainStateTransition(EmergencyState.IDLE, EmergencyState.EMERGENCY_DETECTED),
        )
        assertTrue("an outsider's write should be rejected by firestore.rules", appendResult.isFailure)

        val readResult = outsiderRepository.getOrderedAuditTrail(eventId)
        assertTrue("an outsider's read should be rejected by firestore.rules", readResult.isFailure)
    }
}
