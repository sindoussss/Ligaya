package com.ligaya.feature.home

import java.time.LocalTime

/**
 * What Home says at the top, in Filipino, at this hour.
 *
 * Visual design screens 2 and 11 are the same Home at different times of day: "Magandang araw" by day and
 * "Magandang gabi" in the evening, with a line underneath that changes with it — by day she offers to help,
 * at night she tells you to rest. The greeting is the honest part: it follows the phone's own clock, so it
 * never wishes someone a good morning at midnight.
 */
data class HomeGreeting(val salutation: String, val intro: String)

private const val DAY_INTRO =
    "I'm Ligaya. I'm here to help, answer your questions, and make your day a little easier."
private const val EVENING_INTRO = "Rest well. I'm always here when you need me."

/**
 * Filipino splits the day the way this does: umaga until noon, hapon through the afternoon, gabi from six.
 * [now] is a parameter rather than a call inside so the choice can be tested at every hour instead of only
 * at whatever time the tests happen to run.
 */
fun homeGreetingFor(now: LocalTime = LocalTime.now()): HomeGreeting = when (now.hour) {
    in 0..4 -> HomeGreeting("Magandang gabi,", EVENING_INTRO)
    in 5..11 -> HomeGreeting("Magandang umaga,", DAY_INTRO)
    in 12..17 -> HomeGreeting("Magandang hapon,", DAY_INTRO)
    else -> HomeGreeting("Magandang gabi,", EVENING_INTRO)
}
