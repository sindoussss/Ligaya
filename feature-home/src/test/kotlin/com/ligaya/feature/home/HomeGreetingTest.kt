package com.ligaya.feature.home

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Screens 2 and 11 are the same Home at different hours, so the hour is what this pins down: every one of
 * the 24 lands on a greeting, and the evening ones say something different from the daytime ones.
 */
class HomeGreetingTest {

    @Test
    fun sheIntroducesHerselfTheSameWayAtEveryHour() {
        for (hour in 0..23) {
            assertEquals("hour $hour", "Hello,", greetingAt(hour).salutation)
        }
    }

    @Test
    fun thePartOfTheNightBeforeDawnIsStillEvening() {
        // Midnight to before five: asking what she can do would be wrong, and so would offering the day.
        for (hour in 0..4) {
            assertEquals("I'm Ligaya. Rest well, I'm always here when you need me.", greetingAt(hour).intro)
        }
    }

    @Test
    fun theLineUnderTheNameIsTheOneThatChanges() {
        assertEquals("I'm Ligaya. What can I do for you?", greetingAt(5).intro)
        assertEquals("I'm Ligaya. What can I do for you?", greetingAt(9).intro)
        assertEquals("I'm Ligaya. What can I do for you?", greetingAt(17).intro)
        assertEquals("I'm Ligaya. Rest well, I'm always here when you need me.", greetingAt(18).intro)
        assertEquals("I'm Ligaya. Rest well, I'm always here when you need me.", greetingAt(21).intro)
        assertEquals("I'm Ligaya. Rest well, I'm always here when you need me.", greetingAt(23).intro)
    }

    @Test
    fun everyHourOfTheDayIsCovered() {
        for (hour in 0..23) {
            val greeting = greetingAt(hour)
            assert(greeting.salutation.isNotBlank()) { "hour $hour has no greeting" }
            assert(greeting.intro.isNotBlank()) { "hour $hour has no line under the greeting" }
        }
    }

    private fun greetingAt(hour: Int) = homeGreetingFor(LocalTime.of(hour, 30))
}
