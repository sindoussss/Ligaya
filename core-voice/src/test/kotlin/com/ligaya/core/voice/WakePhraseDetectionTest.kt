package com.ligaya.core.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseDetectionTest {

    @Test
    fun `the doc's own example phrase contains the wake phrase`() {
        assertTrue(containsWakePhrase("Ligaya, tulong. May sunog."))
    }

    @Test
    fun `matching is case-insensitive`() {
        assertTrue(containsWakePhrase("LIGAYA, tulong!"))
        assertTrue(containsWakePhrase("ligaya tulong"))
    }

    @Test
    fun `the wake phrase can appear anywhere in the transcript, not only at the start`() {
        assertTrue(containsWakePhrase("Tulong, Ligaya, may sunog!"))
    }

    @Test
    fun `ordinary speech without the wake phrase does not match`() {
        assertFalse(containsWakePhrase("Kumusta ka? Ano balita?"))
        assertFalse(containsWakePhrase(""))
    }
}
