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
    fun eachPartOfTheDayHasItsOwnGreeting() {
        assertEquals("Magandang umaga,", greetingAt(5).salutation)
        assertEquals("Magandang umaga,", greetingAt(11).salutation)
        assertEquals("Magandang hapon,", greetingAt(12).salutation)
        assertEquals("Magandang hapon,", greetingAt(17).salutation)
        assertEquals("Magandang gabi,", greetingAt(18).salutation)
        assertEquals("Magandang gabi,", greetingAt(23).salutation)
    }

    @Test
    fun thePartOfTheNightBeforeDawnIsStillEvening() {
        // Midnight to before five: "good morning" would be wrong, and so would offering to help with the day.
        for (hour in 0..4) {
            assertEquals("Magandang gabi,", greetingAt(hour).salutation)
            assertEquals("Rest well. I'm always here when you need me.", greetingAt(hour).intro)
        }
    }

    @Test
    fun theEveningLineIsTheOneThatChanges() {
        assertEquals(
            "I'm Ligaya. I'm here to help, answer your questions, and make your day a little easier.",
            greetingAt(9).intro,
        )
        assertEquals("Rest well. I'm always here when you need me.", greetingAt(21).intro)
    }

    @Test
    fun everyHourOfTheDayIsCovered() {
        for (hour in 0..23) {
            val greeting = greetingAt(hour)
            assert(greeting.salutation.startsWith("Magandang")) { "hour $hour: ${greeting.salutation}" }
            assert(greeting.intro.isNotBlank()) { "hour $hour has no line under the greeting" }
        }
    }

    private fun greetingAt(hour: Int) = homeGreetingFor(LocalTime.of(hour, 30))
}
