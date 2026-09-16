package com.ligaya.core.ai

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The companion reply provider: a Gemini failure degrades to the safe reply, but a cancelled turn stays cancelled. */
class GeminiCompanionResponseProviderTest {

    private fun generator(block: suspend (String) -> String) = object : GeminiTextGenerator {
        override suspend fun generateText(prompt: String): String = block(prompt)
    }

    @Test
    fun `a Gemini failure degrades to the safe fallback reply`() = runTest {
        val provider = GeminiCompanionResponseProvider(generator { error("network unreachable") })

        assertEquals(GeminiCompanionResponseProvider.SAFE_FALLBACK_RESPONSE, provider.respond(emptyList(), "Hi Ligaya"))
    }

    @Test
    fun `a real reply is passed through`() = runTest {
        val provider = GeminiCompanionResponseProvider(generator { "Of course! What subject is it for?" })

        assertEquals("Of course! What subject is it for?", provider.respond(emptyList(), "Can you help me?"))
    }

    @Test
    fun `cancelling a reply in progress does not produce the fallback reply`() = runTest {
        val provider = GeminiCompanionResponseProvider(generator { awaitCancellation() })
        var reply: String? = null

        val turn = launch { reply = provider.respond(emptyList(), "Can you help me?") }
        runCurrent()
        turn.cancelAndJoin()

        assertNull(reply)
    }
}
