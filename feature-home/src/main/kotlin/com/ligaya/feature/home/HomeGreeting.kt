package com.ligaya.feature.home

import java.time.LocalTime

/**
 * What Home says at the top.
 *
 * She introduces herself and asks what you need, rather than announcing the hour: the clock is on the
 * phone already, and someone opening this app wants to know she is listening. The name line between the
 * two carries whoever is using it.
 *
 * Visual design screens 2 and 11 are the same Home at different times of day, so the line under the name
 * is still the one that changes: by day she asks what you need, at night she tells you to rest. It follows
 * the phone's own clock, so it never tells someone to rest well over breakfast.
 */
data class HomeGreeting(val salutation: String, val intro: String)

private const val GREETING = "Hello,"
private const val DAY_INTRO = "I'm Ligaya. What can I do for you?"
private const val EVENING_INTRO = "I'm Ligaya. Rest well, I'm always here when you need me."

/**
 * [now] is a parameter rather than a call inside so the choice can be tested at every hour instead of only
 * at whatever time the tests happen to run.
 */
fun homeGreetingFor(now: LocalTime = LocalTime.now()): HomeGreeting = when (now.hour) {
    in 5..17 -> HomeGreeting(GREETING, DAY_INTRO)
    else -> HomeGreeting(GREETING, EVENING_INTRO)
}
