package com.ligaya.core.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyStatusMessageTest {

    @Test
    fun `every template has non-blank spoken text`() {
        for (message in EmergencyStatusMessage.values()) {
            assertTrue("${message.name} has blank spokenText", message.spokenText.isNotBlank())
        }
    }

    @Test
    fun `every template's spoken text is distinct`() {
        val allSpokenText = EmergencyStatusMessage.values().map { it.spokenText }
        assertFalse("duplicate spoken text found: $allSpokenText", allSpokenText.size != allSpokenText.toSet().size)
    }

    @Test
    fun `the doc's own example message is present verbatim`() {
        assertTrue(EmergencyStatusMessage.values().any { it.spokenText == "Emergency activated." })
    }
}
