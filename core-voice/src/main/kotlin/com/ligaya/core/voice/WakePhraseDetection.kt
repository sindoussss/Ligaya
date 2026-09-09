package com.ligaya.core.voice

/**
 * Issue A's confirmed MVP scope: no OS-level custom wake word exists, so "wake detection" here
 * means checking a transcript already captured by VoiceCaptureCoordinator (while the app is
 * foregrounded) for the wake phrase, rather than a dedicated always-on listener. The whole
 * transcript — wake phrase included — is passed on to Gemini unchanged (Step 22's own prompt
 * already handles the doc's example, "Ligaya, tulong. May sunog.", exactly as spoken).
 */
internal fun containsWakePhrase(transcript: String): Boolean =
    transcript.contains(WAKE_WORD, ignoreCase = true)

private const val WAKE_WORD = "Ligaya"
