package com.ligaya.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.data.entity.EmergencyEventEntity
import com.ligaya.core.data.entity.EmergencyStateSnapshotEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 25's requirement that emergency state is
 * "never held only in memory": data written through one database instance must still be
 * readable from a brand-new instance opened against the same on-disk file, with nothing carried
 * over in memory — the same guarantee the app needs after Android kills and relaunches the
 * process mid-emergency.
 *
 * Requires a connected device or emulator (instrumented test); it cannot run as a plain JVM
 * unit test because Room needs a real Android SQLite implementation.
 */
@RunWith(AndroidJUnit4::class)
class LigayaDatabaseCrashRecoveryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "crash-recovery-test.db"

    private fun openDatabase(): LigayaDatabase =
        Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()

    @Test
    fun emergencyEventSurvivesDatabaseReopen() = runTest {
        context.deleteDatabase(dbName)

        val event = EmergencyEventEntity(
            id = "event-1",
            userId = "user-1",
            incidentType = "FIRE",
            status = "EMERGENCY_ACTIVE",
            createdAtEpochMillis = 1_000L,
            resolvedAtEpochMillis = null,
        )

        // Simulate the process before death: write, then close this instance completely.
        val firstInstance = openDatabase()
        firstInstance.emergencyEventDao().upsert(event)
        firstInstance.close()

        // Simulate the process after Android relaunches it: a brand-new database instance
        // pointed at the same on-disk file, with nothing carried over in memory.
        val secondInstance = openDatabase()
        val recovered = secondInstance.emergencyEventDao().getById("event-1")
        secondInstance.close()

        assertNotNull("Emergency event did not survive a simulated process restart", recovered)
        assertEquals(event, recovered)

        context.deleteDatabase(dbName)
    }

    @Test
    fun emergencyStateSnapshotSurvivesDatabaseReopen() = runTest {
        context.deleteDatabase(dbName)

        val snapshot = EmergencyStateSnapshotEntity(
            emergencyEventId = "event-1",
            mainState = "EMERGENCY_ACTIVE",
            locationFlowState = "PENDING",
            unified911FlowState = "PENDING",
            emergencyServiceFlowState = "PENDING",
            familyAlertFlowState = "PENDING",
            companionState = "PENDING",
            lastUpdatedAtEpochMillis = 2_000L,
        )

        val firstInstance = openDatabase()
        firstInstance.emergencyStateSnapshotDao().upsert(snapshot)
        firstInstance.close()

        val secondInstance = openDatabase()
        val recovered = secondInstance.emergencyStateSnapshotDao().getSnapshot()
        secondInstance.close()

        assertNotNull("Emergency state snapshot did not survive a simulated process restart", recovered)
        assertEquals(snapshot, recovered)

        context.deleteDatabase(dbName)
    }
}
