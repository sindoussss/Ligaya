package com.ligaya.feature.emergencyactive

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.data.LigayaDatabase
import com.ligaya.core.data.entity.FamilyMemberEntity
import com.ligaya.core.data.entity.FamilyMemberStatus
import com.ligaya.core.data.entity.HouseholdEntity
import com.ligaya.core.data.entity.EmergencyEventEntity
import com.ligaya.core.data.entity.NotificationEventEntity
import com.ligaya.core.data.repository.RoomFamilyMemberRepository
import com.ligaya.core.data.repository.RoomNotificationEventRepository
import com.ligaya.designsystem.LigayaDeliveryState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves RoomSafetyCircleDeliveryStatusSource's join is genuinely correct against a real
 * database — this class has no caller supplying real household/event ids yet (see its own doc
 * comment), so this is the test that actually exercises it end to end rather than leaving it
 * unverified.
 */
@RunWith(AndroidJUnit4::class)
class SafetyCircleDeliveryStatusSourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "safety-circle-delivery-status-test.db"
    private lateinit var db: LigayaDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        db = Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun joinsHouseholdRosterWithThisEmergencysNotificationEventsPerMemberPerChannel() = runTest {
        val familyMemberRepository = RoomFamilyMemberRepository(db.familyMemberDao())
        val notificationEventRepository = RoomNotificationEventRepository(db.notificationEventDao())

        db.householdDao().upsert(HouseholdEntity(id = "house-1", ownerId = "owner-1", subscriptionStateJson = "{}"))
        familyMemberRepository.save(
            FamilyMemberEntity(
                householdId = "house-1",
                userId = "member-1",
                relationship = "sibling",
                permissionsJson = "{}",
                notificationChannel = "push",
                status = FamilyMemberStatus.ACTIVE,
            ),
        )
        db.emergencyEventDao().upsert(
            EmergencyEventEntity(
                id = "event-1",
                userId = "owner-1",
                incidentType = "FIRE",
                status = "ACTIVE",
                createdAtEpochMillis = 0L,
                resolvedAtEpochMillis = null,
            ),
        )
        notificationEventRepository.save(
            NotificationEventEntity(
                id = "notif-push",
                emergencyEventId = "event-1",
                recipient = "member-1",
                channel = "push",
                state = "CONFIRMED",
                timestampEpochMillis = 0L,
            ),
        )
        notificationEventRepository.save(
            NotificationEventEntity(
                id = "notif-sms",
                emergencyEventId = "event-1",
                recipient = "member-1",
                channel = "sms",
                state = "FAILED",
                timestampEpochMillis = 1L,
            ),
        )

        val source = RoomSafetyCircleDeliveryStatusSource(
            familyMemberRepository = familyMemberRepository,
            notificationEventRepository = notificationEventRepository,
            householdId = "house-1",
            emergencyEventId = "event-1",
        )

        val result = source.observe().first()

        assertEquals(1, result.size)
        val member = result.single()
        assertEquals("Sibling", member.memberName)
        assertEquals(2, member.channelStatuses.size)
        assertEquals(LigayaDeliveryState.CONFIRMED, member.channelStatuses.first { it.channelLabel == "Push" }.state)
        assertEquals(LigayaDeliveryState.FAILED, member.channelStatuses.first { it.channelLabel == "Sms" }.state)
    }

    @Test
    fun aMemberWithNoNotificationEventsYetHasAnEmptyChannelList() = runTest {
        val familyMemberRepository = RoomFamilyMemberRepository(db.familyMemberDao())
        val notificationEventRepository = RoomNotificationEventRepository(db.notificationEventDao())

        db.householdDao().upsert(HouseholdEntity(id = "house-2", ownerId = "owner-2", subscriptionStateJson = "{}"))
        familyMemberRepository.save(
            FamilyMemberEntity(
                householdId = "house-2",
                userId = "member-2",
                relationship = "parent",
                permissionsJson = "{}",
                notificationChannel = "push",
                status = FamilyMemberStatus.ACTIVE,
            ),
        )

        val source = RoomSafetyCircleDeliveryStatusSource(
            familyMemberRepository = familyMemberRepository,
            notificationEventRepository = notificationEventRepository,
            householdId = "house-2",
            emergencyEventId = "event-never-happened",
        )

        val result = source.observe().first()

        assertEquals(1, result.size)
        assertEquals("Parent", result.single().memberName)
        assertEquals(emptyList<ChannelDeliveryStatus>(), result.single().channelStatuses)
    }
}
