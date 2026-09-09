package com.ligaya.core.voice

import com.ligaya.core.ai.ValidatedResponse
import com.ligaya.core.ai.ValidatedSpeech
import com.ligaya.core.ai.toValidatedSpeechOrNull
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers this step's own acceptance criteria directly: proves — not just documents — that only
 * an allow-listed EmergencyStatusMessage, or text that has already gone through core-ai's
 * ResponseValidator (ValidatedSpeech, Step 26), can ever reach a SpeechOutput's speak call. If a
 * future change added an overload accepting a raw String, this test would fail.
 */
class SpeechOutputContractTest {

    private val allowedParameterTypes = setOf(EmergencyStatusMessage::class.java, ValidatedSpeech::class.java)

    private class RecordingSpeechOutput : SpeechOutput {
        val statusMessages = mutableListOf<EmergencyStatusMessage>()
        val validatedSpeech = mutableListOf<ValidatedSpeech>()

        override suspend fun speak(message: EmergencyStatusMessage) {
            statusMessages += message
        }

        override suspend fun speak(speech: ValidatedSpeech) {
            validatedSpeech += speech
        }
    }

    @Test
    fun `every speak method's parameter is an allow-listed type, never a raw String`() {
        // Filtered to declared, non-synthetic methods rather than asserting an exact count: the
        // Kotlin/JVM suspend-function compilation can add synthetic/bridge methods here that
        // aren't part of this interface's real public contract, and asserting past those would
        // make this test fragile to compiler-version details unrelated to what it actually
        // checks — that nothing in this interface accepts a raw String.
        val speakMethods = SpeechOutput::class.java.declaredMethods
            .filter { it.name == "speak" && !it.isSynthetic && !it.isBridge }

        assertTrue("expected at least one speak method", speakMethods.isNotEmpty())
        for (method in speakMethods) {
            val parameterType = method.parameterTypes.first()
            assertTrue(
                "speak(${method.parameterTypes.joinToString()}) must be one of $allowedParameterTypes, " +
                    "never a raw String",
                parameterType in allowedParameterTypes,
            )
        }
    }

    @Test
    fun `a fake SpeechOutput receives exactly the status message it was called with`() = runTest {
        val speechOutput = RecordingSpeechOutput()

        speechOutput.speak(EmergencyStatusMessage.EMERGENCY_ACTIVATED)
        speechOutput.speak(EmergencyStatusMessage.EMERGENCY_RESOLVED)

        assertEquals(
            listOf(EmergencyStatusMessage.EMERGENCY_ACTIVATED, EmergencyStatusMessage.EMERGENCY_RESOLVED),
            speechOutput.statusMessages,
        )
    }

    @Test
    fun `a fake SpeechOutput receives exactly the validated speech it was called with`() = runTest {
        val speechOutput = RecordingSpeechOutput()
        val validated = ValidatedResponse.Passed("I'm here with you.").toValidatedSpeechOrNull()!!

        speechOutput.speak(validated)

        assertEquals(listOf(validated), speechOutput.validatedSpeech)
    }
}
