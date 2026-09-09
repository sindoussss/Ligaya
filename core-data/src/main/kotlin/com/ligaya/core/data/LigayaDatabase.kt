package com.ligaya.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ligaya.core.data.dao.EmergencyEventDao
import com.ligaya.core.data.dao.EmergencyStateSnapshotDao
import com.ligaya.core.data.dao.FamilyMemberDao
import com.ligaya.core.data.dao.HouseholdDao
import com.ligaya.core.data.dao.LocationEventDao
import com.ligaya.core.data.dao.NotificationEventDao
import com.ligaya.core.data.dao.SubscriptionDao
import com.ligaya.core.data.dao.UserDao
import com.ligaya.core.data.entity.EmergencyEventEntity
import com.ligaya.core.data.entity.EmergencyStateSnapshotEntity
import com.ligaya.core.data.entity.FamilyMemberEntity
import com.ligaya.core.data.entity.HouseholdEntity
import com.ligaya.core.data.entity.LocationEventEntity
import com.ligaya.core.data.entity.NotificationEventEntity
import com.ligaya.core.data.entity.SubscriptionEntity
import com.ligaya.core.data.entity.UserEntity

/**
 * The on-device database mirroring LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 24's ERD, plus
 * the local-only EmergencyStateSnapshot table used for section 25's crash-recovery requirement.
 */
@Database(
    entities = [
        UserEntity::class,
        HouseholdEntity::class,
        FamilyMemberEntity::class,
        EmergencyEventEntity::class,
        LocationEventEntity::class,
        NotificationEventEntity::class,
        SubscriptionEntity::class,
        EmergencyStateSnapshotEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LigayaDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun householdDao(): HouseholdDao
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun emergencyEventDao(): EmergencyEventDao
    abstract fun locationEventDao(): LocationEventDao
    abstract fun notificationEventDao(): NotificationEventDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun emergencyStateSnapshotDao(): EmergencyStateSnapshotDao

    companion object {
        const val DATABASE_NAME = "ligaya.db"

        // Step 11: EmergencyForegroundService (and, later, other production callers) need one
        // shared instance rather than each opening their own Room connection against the same
        // file. Most tests are unaffected — they build their own short-lived instances directly
        // via Room.databaseBuilder, as established since Step 3, precisely so they can control
        // the database file's lifecycle (delete/reopen) for crash-recovery testing. The one
        // exception is EmergencyForegroundServiceTest: an Android Service is instantiated by the
        // OS itself, not by test code, so it always calls this exact getInstance() function —
        // there is no way to construct it with a substitute repository the way a plain class
        // allows. setInstanceForTesting below is the seam that test needs, in the absence of a
        // DI framework in this codebase.
        @Volatile private var instance: LigayaDatabase? = null

        fun getInstance(context: Context): LigayaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, LigayaDatabase::class.java, DATABASE_NAME)
                    .build()
                    .also { instance = it }
            }

        /** Test-only: points getInstance() at a test-controlled database (or, passed null,
         *  clears the override) instead of the real production "ligaya.db" file. */
        fun setInstanceForTesting(database: LigayaDatabase?) {
            instance = database
        }
    }
}
