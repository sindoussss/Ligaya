package com.ligaya.core.data.profile

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.data.LigayaDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Same acceptance criteria as EmergencyProfileOnboardingTest, but through the real Room/SQLite
 *  database on a device, not an in-memory fake — proving the JSON round-trip survives a real
 *  persistence layer, the same rigor applied to LigayaDatabaseCrashRecoveryTest in Step 3. */
@RunWith(AndroidJUnit4::class)
class EmergencyProfileRepositoryRoomTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "emergency-profile-test.db"

    private fun openDatabase(): LigayaDatabase =
        Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()

    @Test
    fun skippedThenLaterFilledProfileSurvivesARealDatabase() = runTest {
        context.deleteDatabase(dbName)
        val db = openDatabase()
        val repo: EmergencyProfileRepository = RoomEmergencyProfileRepository(db.userDao())

        // Skip during onboarding.
        repo.saveProfile("user-real-1", EmergencyProfile())
        assertEquals(EmergencyProfile(), repo.getProfile("user-real-1"))

        // Filled in later.
        val filled = EmergencyProfile(
            name = "Elena",
            medicalInfo = MedicalInfo(bloodType = "O-", allergies = listOf("penicillin")),
            contacts = listOf(EmergencyContact(name = "Marco", relationship = "spouse", phoneNumber = "+63 900 000 0000")),
            preferences = mapOf("preferredLanguage" to "tl"),
        )
        repo.saveProfile("user-real-1", filled)
        assertEquals(filled, repo.getProfile("user-real-1"))

        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun noUserRowMeansNoProfileOnARealDatabase() = runTest {
        context.deleteDatabase(dbName)
        val db = openDatabase()
        val repo: EmergencyProfileRepository = RoomEmergencyProfileRepository(db.userDao())

        assertNull(repo.getProfile("never-created-real"))

        db.close()
        context.deleteDatabase(dbName)
    }
}
