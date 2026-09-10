package com.ligaya.feature.onboarding

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.core.data.profile.EmergencyProfile
import com.ligaya.core.data.profile.EmergencyProfileRepository
import com.ligaya.core.data.profile.MedicalInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Visual design screen 5. The contract worth pinning is §3's: nothing here is required, and
 * whatever *is* filled must actually survive the save — a profile step that silently dropped a
 * medical note would be worse than one that never asked.
 */
@RunWith(AndroidJUnit4::class)
class EmergencyProfileScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class RecordingProfileRepository(
        private val existing: EmergencyProfile? = null,
    ) : EmergencyProfileRepository {
        var saved: EmergencyProfile? = null
            private set
        var saveCount = 0
            private set

        override suspend fun getProfile(userId: String): EmergencyProfile? = existing

        override suspend fun saveProfile(userId: String, profile: EmergencyProfile) {
            saved = profile
            saveCount++
        }
    }

    private fun setScreen(repository: EmergencyProfileRepository, onSaved: () -> Unit = {}) {
        composeTestRule.setContent {
            EmergencyProfileScreen(
                profileRepository = repository,
                userId = "user-1",
                onSaved = onSaved,
                onBack = {},
            )
        }
    }

    // Fields are addressed by their text-input action for the same reason as the account screen's
    // — see CreateAccountScreenTest for the full explanation. Order is layout order.
    private fun typeInto(index: Int, text: String) {
        val field = composeTestRule.onAllNodes(hasSetTextAction())[index]
        field.requestFocus()
        field.performTextInput(text)
    }

    private fun tapNext() {
        composeTestRule.onNodeWithText("Next").performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun everyFieldIsOptionalSoNextAlwaysSaves() {
        val repository = RecordingProfileRepository()
        var savedCalled = false
        setScreen(repository, onSaved = { savedCalled = true })

        tapNext()

        assertEquals("an empty profile is a valid stored state, not a blocked one", 1, repository.saveCount)
        assertTrue(savedCalled)
        assertNull(repository.saved?.name)
        assertNull(repository.saved?.medicalInfo)
    }

    @Test
    fun everythingTypedIsActuallyPersisted() {
        val repository = RecordingProfileRepository()
        setScreen(repository)

        typeInto(0, "John Daniel Casili")
        typeInto(1, "+63 912 345 6789")
        typeInto(2, "Penicillin allergy")
        tapNext()

        val saved = requireNotNull(repository.saved)
        assertEquals("John Daniel Casili", saved.name)
        assertEquals("+63 912 345 6789", saved.phoneNumber)
        assertEquals("Penicillin allergy", saved.medicalInfo?.notes)
    }

    @Test
    fun blankFieldsAreStoredAsAbsentRatherThanEmptyStrings() {
        val repository = RecordingProfileRepository()
        setScreen(repository)

        typeInto(0, "   ")
        tapNext()

        // "" and "not provided" must not become two different downstream states.
        assertNull(repository.saved?.name)
    }

    @Test
    fun anExistingProfileIsLoadedInsteadOfBlankingWhatWasAlreadySaved() {
        val repository = RecordingProfileRepository(
            existing = EmergencyProfile(
                name = "Maria Santos",
                phoneNumber = "+63 917 000 1111",
                medicalInfo = MedicalInfo(notes = "Asthma"),
            ),
        )
        setScreen(repository)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Maria Santos").assertExists()
        composeTestRule.onNodeWithText("+63 917 000 1111").assertExists()
        composeTestRule.onNodeWithText("Asthma").assertExists()
    }

    @Test
    fun reSavingAnExistingProfileKeepsItRatherThanWipingIt() {
        val repository = RecordingProfileRepository(
            existing = EmergencyProfile(name = "Maria Santos", phoneNumber = "+63 917 000 1111"),
        )
        setScreen(repository)
        composeTestRule.waitForIdle()

        tapNext()

        assertEquals("Maria Santos", repository.saved?.name)
        assertEquals("+63 917 000 1111", repository.saved?.phoneNumber)
    }

    @Test
    fun theFormLabelsMatchWhatTheDesignAsksFor() {
        setScreen(RecordingProfileRepository())

        composeTestRule.onNodeWithText("Emergency Profile").assertExists()
        composeTestRule.onNodeWithText("Help us get you the right kind of help.").assertExists()
        composeTestRule.onNodeWithText("Full Name").assertExists()
        composeTestRule.onNodeWithText("Phone Number").assertExists()
        composeTestRule.onNodeWithText("Medical Info (optional)").assertExists()
    }
}
