package com.ligaya.feature.companion

import com.ligaya.core.ai.CompanionResponseProvider
import com.ligaya.core.ai.CompanionTurn
import com.ligaya.core.ai.ResponseValidator
import com.ligaya.core.ai.toValidatedSpeechOrNull
import com.ligaya.core.voice.SpeechOutput
import com.ligaya.core.voice.SpeechResult
import com.ligaya.core.voice.TranscriptionEvent
import com.ligaya.core.voice.VoiceCaptureCoordinator
import com.ligaya.core.voice.VoicePipelinePhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Section 19's full companion loop: capture (Step 21) -> Gemini (Step 22's pipeline, but the
 * free-form half, Step 26) -> Response Validator (Step 25) -> speak (Step 24/26) -> repeat.
 *
 * Deliberately one turn per call, not a self-looping Flow: a caller drives the continuous
 * "TALKOUT -> TALK" cycle the architecture diagram draws by calling [runOneTurn] repeatedly
 * (e.g. from a coroutine loop once a real UI exists to host it), which lets the loop be stopped
 * cleanly between turns — once the emergency resolves, say — without needing to cancel a
 * coroutine mid-utterance.
 *
 * Runs independently of the 911, location, Places, and family-alert subsystems (section 13):
 * this class never reads or branches on any of their success/failure itself — the only place
 * subsystem state is even consulted is inside ResponseValidator's own claim-verification checks,
 * which only ever change whether a specific sentence survives, never whether this loop continues
 * to run. A subsystem failure changes what Ligaya is allowed to say, never whether she keeps
 * talking.
 */
class EmergencyCompanionCoordinator(
    private val captureCoordinator: VoiceCaptureCoordinator,
    private val responseProvider: CompanionResponseProvider,
    private val speechOutput: SpeechOutput,
    private val snapshotProvider: EmergencySnapshotProvider,
    private val clock: () -> Long = System::currentTimeMillis,
    /**
     * Called with each final spoken utterance [runOneTurn] captures, before Ligaya replies. The composition root
     * uses it to pass what was said into the emergency intake too (section 9: voice reaches the deterministic
     * engine), so an emergency said to Ligaya's mic isn't only answered in conversation. Must not block.
     */
    private val onHeard: (String) -> Unit = {},
) {
    private val _transcript = MutableStateFlow<List<CompanionTurn>>(emptyList())

    /** Step 39's live transcript signal — the same list [runOneTurn]/[runOneTurnWithText] already
     *  maintained internally since Step 26, just observable now instead of private. */
    val transcript: StateFlow<List<CompanionTurn>> = _transcript.asStateFlow()

    private val _turnTimes = MutableStateFlow<List<Long>>(emptyList())

    /** When each [transcript] turn was added (epoch millis, from [clock]), index-aligned with it — the chat
     *  screen's message times. Kept here rather than in the screen so they survive leaving and reopening it. */
    val turnTimes: StateFlow<List<Long>> = _turnTimes.asStateFlow()

    private val _lastResult = MutableStateFlow<CompanionTurnResult?>(null)

    /** How the most recent turn ended; null before the first turn and while one is running. Lets the chat say
     *  something when a turn ends without a reply, instead of the conversation silently stopping. */
    val lastResult: StateFlow<CompanionTurnResult?> = _lastResult.asStateFlow()

    private val _phase = MutableStateFlow(VoicePipelinePhase.IDLE)

    /** Step 38's live phase signal, updated at the start of each stage below — not after it
     *  completes, so a bound UI reflects "listening now"/"processing now"/"speaking now" as each
     *  one actually begins, with no artificial delay of its own (perceived-latency acceptance
     *  criterion is a consequence of setting this synchronously right before the corresponding
     *  suspend call, not a separate thing to optimize). Always ends back at IDLE — the `finally`
     *  below covers every exit path, including the NoSpeechCaptured/ResponseBlocked early returns
     *  and a genuinely unexpected exception, not just the successful Spoken path. */
    val phase: StateFlow<VoicePipelinePhase> = _phase.asStateFlow()

    /**
     * Step 51's own finding, from field-testing this pipeline's real responsiveness: this wait
     * used to have no timeout at all. A real on-device SpeechRecognizer session normally ends
     * itself — a final result, an error, or its own internal silence timeout — but a genuinely
     * broken/absent recognizer (confirmed directly: zero callbacks of any kind, ever, on one real
     * emulator system image) delivers none of those, and `firstOrNull` on a Flow that never
     * completes and never emits a match suspends forever. Unlike a permission denial (an
     * immediate, synchronous PERMISSION_DENIED from VoiceCaptureCoordinator itself), this is the
     * one STT failure mode nothing downstream was already guarding against — during a real active
     * emergency, that would silently stall the whole companion turn indefinitely, exactly the
     * kind of failure this app's own established pattern (never let a subsystem outage become a
     * silent hang, e.g. AndroidSpeechOutput's own matching Step 51 fix) already exists to prevent
     * elsewhere. [LISTENING_TIMEOUT_MILLIS] is generous enough to never cut off a real listening
     * session early — it only ever matters when nothing would otherwise end the wait at all.
     */
    suspend fun runOneTurn(): CompanionTurnResult {
        _lastResult.value = null
        try {
            _phase.value = VoicePipelinePhase.LISTENING
            val transcriptEvent = withTimeoutOrNull(LISTENING_TIMEOUT_MILLIS) {
                captureCoordinator.startListening().firstOrNull { it is TranscriptionEvent.Success && it.isFinal }
            } as? TranscriptionEvent.Success

            if (transcriptEvent != null) onHeard(transcriptEvent.text)
            val result = if (transcriptEvent == null) CompanionTurnResult.NoSpeechCaptured else respondTo(transcriptEvent.text)
            _lastResult.value = result
            return result
        } finally {
            _phase.value = VoicePipelinePhase.IDLE
        }
    }

    /**
     * Step 39's text fallback: "a user who never speaks can still hold a full companion
     * conversation via the text fallback" — this never touches [captureCoordinator] at all, so
     * there's no RECORD_AUDIO permission, no microphone, and no LISTENING phase for a typed turn;
     * phase goes straight from IDLE to PROCESSING, same as [runOneTurn] does once it already has
     * a transcript in hand.
     */
    suspend fun runOneTurnWithText(text: String): CompanionTurnResult {
        _lastResult.value = null
        try {
            val result = respondTo(text)
            _lastResult.value = result
            return result
        } finally {
            _phase.value = VoicePipelinePhase.IDLE
        }
    }

    /** The shared second half of a turn, once a user utterance exists by whatever means — voice
     *  or typed. Ligaya's own reply is always spoken (TTS), regardless of which one the user
     *  used: section 19's "voice-first" framing is about how *she* communicates, not a
     *  requirement that the user's own input method be voice too. */
    private suspend fun respondTo(userUtterance: String): CompanionTurnResult {
        // Snapshot taken BEFORE appending this turn's own utterance: respond()'s `history`
        // parameter means "everything prior," matching its own doc comment — the utterance
        // being responded to is passed separately as `latestUserUtterance` precisely so it
        // isn't redundantly present in both places.
        val priorHistory = _transcript.value
        _transcript.value = priorHistory + CompanionTurn(CompanionTurn.Speaker.USER, userUtterance)
        _turnTimes.value = _turnTimes.value + clock()

        _phase.value = VoicePipelinePhase.PROCESSING
        val rawResponse = responseProvider.respond(priorHistory, userUtterance)
        val snapshot = snapshotProvider.currentSnapshot()
        val validated = ResponseValidator.validate(rawResponse, snapshot)
        val speech = validated.toValidatedSpeechOrNull() ?: return CompanionTurnResult.ResponseBlocked

        _transcript.value = _transcript.value + CompanionTurn(CompanionTurn.Speaker.LIGAYA, speech.text)
        _turnTimes.value = _turnTimes.value + clock()
        _phase.value = VoicePipelinePhase.SPEAKING
        val spoken = speechOutput.speak(speech)

        return CompanionTurnResult.Spoken(speech.text, aloud = spoken == SpeechResult.SPOKEN)
    }

    private companion object {
        const val LISTENING_TIMEOUT_MILLIS = 20_000L
    }
}
