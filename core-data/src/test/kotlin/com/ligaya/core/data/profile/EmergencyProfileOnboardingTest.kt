package com.ligaya.core.data.profile

import com.ligaya.core.data.dao.UserDao
import com.ligaya.core.data.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** In-memory fake — pure Kotlin, no Room/Android dependency, so this whole test runs as a plain
 *  JVM unit test (no emulator needed) even though UserDao is a Room @Dao interface elsewhere. */
private class FakeUserDao : UserDao {
    private val store = mutableMapOf<String, UserEntity>()
    override suspend fun upsert(user: UserEntity) { store[user.id] = user }
    override suspend fun getById(id: String): UserEntity? = store[id]
    override fun observeById(id: String): Flow<UserEntity?> = flowOf(store[id])
    override suspend fun delete(user: UserEntity) { store.remove(user.id) }
}

/** Covers this step's acceptance criteria: profile can be created, partially filled, skipped,
 *  and edited later without breaking onboarding completion. */
class EmergencyProfileOnboardingTest {

    @Test
    fun `no USER row yet means no profile, not an error`() = runTest {
        val repo: EmergencyProfileRepository = RoomEmergencyProfileRepository(FakeUserDao())
        assertNull(repo.getProfile("never-created"))
    }

    @Test
    fun `onboarding completes with an entirely empty (skipped) profile`() = runTest {
        val repo: EmergencyProfileRepository = RoomEmergencyProfileRepository(FakeUserDao())

        // This is what "skip for now" during onboarding does: save the default, empty profile.
        repo.saveProfile("user-1", EmergencyProfile())
        val loaded = repo.getProfile("user-1")

        assertNotNull(loaded)
        assertEquals(EmergencyProfile(), loaded)
    }

    @Test
    fun `a partially filled profile round-trips exactly`() = runTest {
        val repo: EmergencyProfileRepository = RoomEmergencyProfileRepository(FakeUserDao())

        val partial = EmergencyProfile(
            name = "Alice",
            contacts = listOf(EmergencyContact(name = "Bob", relationship = "sibling")),
        )
        repo.saveProfile("user-2", partial)

        assertEquals(partial, repo.getProfile("user-2"))
    }

    @Test
    fun `a skipped profile can be filled in later without losing the user`() = runTest {
        val repo: EmergencyProfileRepository = RoomEmergencyProfileRepository(FakeUserDao())

        repo.saveProfile("user-3", EmergencyProfile())
        repo.saveProfile("user-3", EmergencyProfile(name = "Carol"))

        assertEquals("Carol", repo.getProfile("user-3")?.name)
    }

    @Test
    fun `medical info stays optional even when other fields are filled`() = runTest {
        val repo: EmergencyProfileRepository = RoomEmergencyProfileRepository(FakeUserDao())

        val profile = EmergencyProfile(name = "Dana", medicalInfo = null)
        repo.saveProfile("user-4", profile)

        assertNull(repo.getProfile("user-4")?.medicalInfo)
    }
}
