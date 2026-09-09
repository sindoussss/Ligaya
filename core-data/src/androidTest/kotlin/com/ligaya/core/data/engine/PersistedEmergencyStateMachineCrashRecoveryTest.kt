package com.ligaya.core.data.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.data.LigayaDatabase
import com.ligaya.core.data.repository.RoomEmergencyStateSnapshotRepository
import com.ligaya.core.emergencyengine.EmergencyCompanionState
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers this step's literal acceptance criteria: killing the process mid-EMERGENCY_ACTIVE and
 * relaunching restores the exact same state and subsystem statuses. Uses the same
 * close-and-reopen-against-the-same-file technique as Step 3's LigayaDatabaseCrashRecoveryTest
 * to simulate the process being killed and Android relaunching it, with nothing carried over in
 * memory between the two halves of each test.
 */
@RunWith(AndroidJUnit4::class)
class PersistedEmergencyStateMachineCrashRecoveryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "persisted-engine-crash-recovery-test.db"

    private fun openDatabase(): LigayaDatabase =
        Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()

    @Test
    fun killedMidEmergencyActiveWithMixedSubsystemStatesRestoresExactly() = runTest {
        context.deleteDatabase(dbName)

        // --- Before "process death" ---
        val firstDb = openDatabase()
        val firstRepository = RoomEmergencyStateSnapshotRepository(firstDb.emergencyStateSnapshotDao())

        val beforeDeath = PersistedEmergencyStateMachine.start(firstRepository, emergencyEventId = "event-1")
        beforeDeath.detectEmergency()
        beforeDeath.confirmEmergency()
        beforeDeath.activateEmergency()

        // Mixed, realistic mid-emergency subsystem states — deliberately not all the same value,
        // so a restore that silently defaulted anything would be caught.
        beforeDeath.updateLocationFlow(LocationFlowState.Succeeded)
        beforeDeath.updateUnified911Flow(Unified911FlowState.CallFailed)
        beforeDeath.updateEmergencyServiceFlow(EmergencyServiceFlowState.InProgress)
        beforeDeath.updateFamilyAlertFlow(FamilyAlertFlowState.DeliveryFailed)
        beforeDeath.updateEmergencyCompanion(EmergencyCompanionState.Active)

        val snapshotBeforeDeath = beforeDeath.snapshot
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, snapshotBeforeDeath.state)

        // Simulate the process being killed: close the database instance completely.
        firstDb.close()

        // --- After Android relaunches the process ---
        val secondDb = openDatabase()
        val secondRepository = RoomEmergencyStateSnapshotRepository(secondDb.emergencyStateSnapshotDao())

        val restored = PersistedEmergencyStateMachine.restore(secondRepository)
        assertTrue("expected a snapshot to recover", restored != null)
        restored!!

        assertEquals(snapshotBeforeDeath, restored.snapshot)
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, restored.snapshot.state)
        assertEquals(LocationFlowState.Succeeded, restored.snapshot.subsystems.location)
        assertEquals(Unified911FlowState.CallFailed, restored.snapshot.subsystems.unified911)
        assertEquals(EmergencyServiceFlowState.InProgress, restored.snapshot.subsystems.emergencyService)
        assertEquals(FamilyAlertFlowState.DeliveryFailed, restored.snapshot.subsystems.familyAlert)
        assertEquals(EmergencyCompanionState.Active, restored.snapshot.subsystems.companion)

        // The restored engine isn't just a frozen read — it's a live engine that can keep going,
        // including the resolution rule: safe-marking works regardless of subsystem completion.
        assertTrue(restored.markUserSafe().isSuccess)
        assertEquals(EmergencyState.USER_MARKED_SAFE, restored.snapshot.state)

        secondDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun restoringWithNothingEverPersistedReturnsNull() = runTest {
        context.deleteDatabase(dbName)
        val db = openDatabase()
        val repository = RoomEmergencyStateSnapshotRepository(db.emergencyStateSnapshotDao())

        assertNull(PersistedEmergencyStateMachine.restore(repository))

        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun startingAFreshEpisodePersistsImmediatelyEvenBeforeAnyTransition() = runTest {
        context.deleteDatabase(dbName)
        val firstDb = openDatabase()
        val repository = RoomEmergencyStateSnapshotRepository(firstDb.emergencyStateSnapshotDao())

        PersistedEmergencyStateMachine.start(repository, emergencyEventId = "event-2")
        firstDb.close()

        val secondDb = openDatabase()
        val restored = PersistedEmergencyStateMachine.restore(RoomEmergencyStateSnapshotRepository(secondDb.emergencyStateSnapshotDao()))

        assertTrue("expected the IDLE starting snapshot to have been persisted", restored != null)
        assertEquals(EmergencyState.IDLE, restored!!.snapshot.state)

        secondDb.close()
        context.deleteDatabase(dbName)
    }
}
