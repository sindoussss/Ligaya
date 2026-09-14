package com.ligaya.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.LocationServices
import com.ligaya.app.navigation.LigayaNavHost
import com.ligaya.core.ai.CompanionResponseProvider
import com.ligaya.core.ai.GeminiCompanionResponseProvider
import com.ligaya.core.ai.GeminiIntentProvider
import com.ligaya.core.ai.HttpGeminiContentGenerator
import com.ligaya.core.ai.HttpGeminiTextGenerator
import com.ligaya.core.ai.IntentProvider
import com.ligaya.core.ai.NullIntentProvider
import com.ligaya.core.ai.TimingCompanionResponseProvider
import com.ligaya.core.ai.TimingIntentProvider
import com.ligaya.core.backend.auth.AuthRepository
import com.ligaya.core.backend.auth.LocalAuthRepository
import com.ligaya.core.data.LigayaDatabase
import com.ligaya.core.data.engine.DefaultEmergencyController
import com.ligaya.core.data.engine.VoiceIntentResult
import com.ligaya.core.data.profile.RoomEmergencyProfileRepository
import com.ligaya.core.data.repository.RoomEmergencyStateSnapshotRepository
import com.ligaya.core.emergencyengine.EmergencyIntentDecision
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.IncidentType
import com.ligaya.core.permissions.AndroidPermissionChecker
import com.ligaya.core.permissions.PermissionRequester
import com.ligaya.core.permissions.PermissionState
import com.ligaya.core.permissions.SharedPrefsPermissionRequestHistory
import com.ligaya.core.places.EmergencyServiceFlowCoordinator
import com.ligaya.core.places.EmergencyServiceFlowReporter
import com.ligaya.core.places.GooglePlacesDetailsSource
import com.ligaya.core.places.GooglePlacesNearbySearchSource
import com.ligaya.core.location.FusedLocationSource
import com.ligaya.core.location.LocationFlowCoordinator
import com.ligaya.core.location.LocationFlowReporter
import com.ligaya.core.voice.AndroidSpeechOutput
import com.ligaya.core.voice.AndroidSpeechTranscriber
import com.ligaya.core.voice.BatteryLevelLogger
import com.ligaya.core.voice.EmergencyStatusMessage
import com.ligaya.core.voice.TimingSpeechOutput
import com.ligaya.core.voice.TimingSpeechTranscriber
import com.ligaya.core.voice.VoiceActivationCoordinator
import com.ligaya.core.voice.VoiceActivationResult
import com.ligaya.core.voice.VoiceCaptureCoordinator
import com.ligaya.core.voice.VoiceEmergencyIntentReporter
import com.ligaya.feature.companion.EmergencyCompanionCoordinator
import com.ligaya.feature.companion.EmergencySnapshotProvider
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Step 13: the one place in :app that touches :core-data — constructing the real
 * EmergencyController. Everything downstream of this point (LigayaNavHost, every screen) only
 * ever sees it as core-ui-state's EmergencyController interface.
 *
 * Also the one place that assembles a real [EmergencyCompanionCoordinator] (closing the gap
 * Step 38/39 each documented and deferred): real on-device STT ([AndroidSpeechTranscriber]) and
 * TTS ([AndroidSpeechOutput]) — neither needs any API key — gated by a real
 * [AndroidPermissionChecker], and a real Gemini-backed [CompanionResponseProvider] when
 * `BuildConfig.GEMINI_API_KEY` is configured (see app/build.gradle.kts), otherwise a safe canned
 * reply so the companion loop still runs end-to-end (just without live Gemini output) rather than
 * being unusable without a key.
 *
 * Step 48's fix — the gap its own audit found: section 25's "EMERGENCY_ACTIVE fans out to five
 * independent parallel flows" was never actually wired together anywhere real, only built and
 * tested per-subsystem in isolation. This class now also:
 *  - requests RECORD_AUDIO for real (deferred since Step 41 for lack of a real "start listening"
 *    moment — the continuous wake-word loop below is now exactly that moment);
 *  - runs [VoiceActivationCoordinator] continuously while the app is foregrounded
 *    ([Lifecycle.State.STARTED], via [repeatOnLifecycle] — the architecture's own accepted
 *    "foreground-app-only" wake-word scope, not true background listening, which no OS API
 *    supports), feeding confirmed intents into the same [DefaultEmergencyController.submitVoiceIntent]
 *    entry point [com.ligaya.app.screens.SosScreen]'s manual SOS already uses;
 *  - observes [DefaultEmergencyController.observeSnapshot] for the edge into EMERGENCY_ACTIVE and,
 *    on it, automatically runs the real [LocationFlowCoordinator] and (only when
 *    `BuildConfig.PLACES_API_KEY` is configured) [EmergencyServiceFlowCoordinator] — both report
 *    through [DefaultEmergencyController]'s own thin passthroughs
 *    (reportLocationFlow/reportEmergencyServiceFlow) rather than this class reaching into the
 *    persisted machine directly.
 *  - the Unified 911 dial itself needs no wiring here — [DefaultEmergencyController.activate]
 *    already fires it automatically now (see that class's own doc comment).
 *
 * Family alerts (the fifth parallel flow) are still not wired: they need a real Firebase project
 * and a real household/Safety Circle to alert, neither of which exists yet — see
 * ACCOUNT_ACTIONS_NEEDED.md. Everything above needs no account at all.
 *
 * Step 49's own fix — found while verifying its "AI-dependent parts show explicit unavailable
 * states, no silent hang" acceptance criterion: the wake-word loop below used to discard every
 * [VoiceActivationResult] unconditionally, but [VoiceActivationResult.AiUnavailable] never goes
 * through `voiceReporter` (see VoiceActivationCoordinator's own doc comment) — so with Gemini
 * unreachable, speaking the wake phrase produced no feedback of any kind. It now speaks
 * [EmergencyStatusMessage.VOICE_AI_UNAVAILABLE] for that case specifically.
 *
 * Step 51's own gap, found the same way as 48/49's: "no new code" per the roadmap, but its own
 * acceptance criteria ("documented battery drain rate and latency figures") can't be satisfied
 * without something to actually capture them — nothing anywhere in the project measured timing or
 * battery before this. Every real STT/Gemini/TTS call constructed below is now wrapped with a
 * timing decorator (see PipelineLatencyLog's own doc comment), and a periodic
 * [com.ligaya.core.voice.BatteryLevelLogger] runs alongside the other two lifecycle-scoped loops
 * — both log through logcat only, no new UI or persistence, since a field tester reading figures
 * off a real device session is literally this step's own acceptance bar.
 */
class MainActivity : ComponentActivity() {
    private lateinit var speechOutput: AndroidSpeechOutput

    /** Visual design screen 4 — see the wiring in onCreate for why this outlives it. */
    private val locationPermissionState = MutableStateFlow<PermissionState>(PermissionState.NotRequested)
    private var refreshLocationPermission: (() -> Unit)? = null

    override fun onResume() {
        super.onResume()
        // Catches a grant (or revoke) made outside the app entirely — the Settings round trip the
        // location primer offers when the permission is permanently denied.
        refreshLocationPermission?.invoke()
    }

    /** The only remaining action once Android stops showing the permission dialog. */
    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Visual design, screen 1. Must run before super.onCreate: it swaps the launcher's
        // Theme.Ligaya.Splash for Theme.Ligaya (the manifest's postSplashScreenTheme) and holds
        // the system's brand-themed launch window until this Activity's first frame is ready —
        // which is what makes the OS splash and SplashScreen's own blush entrance read as one
        // continuous screen instead of two.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Visual design screen 2. targetSdk 35 makes edge-to-edge mandatory on Android 15, so the
        // window already draws behind the system bars whether or not this app asked to — which
        // left the status-bar clock and icons rendering in their default light colour on top of
        // this design's near-white canvas, effectively invisible (caught on a real screenshot, not
        // predicted). Declaring the bars "light" flips their content to dark. Screens are
        // responsible for their own inset padding; the bars themselves stay transparent so a
        // screen like the splash can run its gradient under them.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val database = LigayaDatabase.getInstance(applicationContext)
        val emergencyController = DefaultEmergencyController(
            context = applicationContext,
            repository = RoomEmergencyStateSnapshotRepository(database.emergencyStateSnapshotDao()),
        )

        // Visual design screen 3: which auth backend the account screen gets is decided here and
        // nowhere else — CreateAccountScreen only ever sees the AuthRepository interface.
        //
        // FirebaseAuthRepository is the intended production implementation, but constructing it
        // requires a google-services.json this repo does not have (ACCOUNT_ACTIONS_NEEDED.md item
        // 1); doing so anyway crashes at launch. LocalAuthRepository is a genuine working
        // implementation rather than a stub — real PBKDF2-hashed credentials, real persistence,
        // real rejection of duplicates and wrong passwords — so onboarding can be built and used
        // end to end now, and swaps to Firebase by changing this one expression later.
        val authRepository: AuthRepository = LocalAuthRepository(applicationContext)

        // Visual design screen 5: the same Room-backed repository the emergency profile has been
        // stored in since Step 5 — this screen is the first thing to actually write to it.
        val profileRepository = RoomEmergencyProfileRepository(database.userDao())

        val permissionHistory = SharedPrefsPermissionRequestHistory(applicationContext)
        val permissionChecker = AndroidPermissionChecker(applicationContext, this, permissionHistory)

        // Step 48: requested for real now — the continuous wake-word loop below is the real
        // "start listening" moment Step 41 deferred this to. VoiceCaptureCoordinator still
        // degrades to a PERMISSION_DENIED failure on its own if this is ever denied, so nothing
        // here needs to special-case that outcome.
        //
        // Step 50 also added a second, POST_NOTIFICATIONS request here (its own reasoning is on
        // requestPostNotificationsIfNeeded below) — chained to fire only from RECORD_AUDIO's own
        // onResult, strictly after that dialog resolves, never both requested back-to-back.
        // Confirmed necessary directly, the hard way: Android only supports one
        // ActivityResultLauncher.launch() pending at a time *per Activity*, not per launcher
        // instance — two separate PermissionRequester instances (each with their own independent
        // registerForActivityResult registration) still silently dropped the second dialog when
        // both request() calls fired synchronously back-to-back in the same onCreate(), verified
        // via a real device screenshot showing only the first dialog ever appeared and the second
        // permission's grant state never changed. Chaining through onResult is what actually
        // serializes them.
        val postNotificationsRequester = PermissionRequester(
            activity = this,
            history = permissionHistory,
            checker = permissionChecker,
            onResult = { _, _ -> },
        )

        // Step 50's own finding: asking upfront is the one available mitigation for a real,
        // reproduced crash when POST_NOTIFICATIONS is denied and SOS later tries to promote
        // EmergencyForegroundService to the foreground (see PostNotificationsDeniedInstrumentedTest's
        // own doc comment for the full diagnosis) — this doesn't eliminate the risk for a user who
        // explicitly denies it, but it does mean most users are asked before an emergency, not
        // during one. Safe to call unconditionally on any API level, including below 33 where this
        // permission doesn't exist as a runtime concept: RequestPermission's own launcher already
        // handles that as an automatic grant, no version check needed here.
        fun requestPostNotificationsIfNeeded() {
            if (permissionChecker.currentState(Manifest.permission.POST_NOTIFICATIONS) != PermissionState.Granted) {
                postNotificationsRequester.request(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Visual design screen 4. Its own requester rather than reusing either above, because this
        // one is user-initiated from the location primer — it fires when the user taps "Allow",
        // long after onCreate, so it can never collide with the startup pair that has to be
        // chained. Registered here regardless, since registerForActivityResult must happen before
        // the Activity is STARTED.
        //
        // refreshLocationPermission is what makes the "Open Settings" path actually close: a user
        // who grants the permission in Settings comes back through onResume, not through this
        // requester's callback, so without re-reading it there the primer would still be sitting on
        // "Location is currently blocked" over a permission that is now granted.
        refreshLocationPermission = {
            locationPermissionState.value =
                permissionChecker.currentState(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        refreshLocationPermission?.invoke()

        val locationRequester = PermissionRequester(
            activity = this,
            history = permissionHistory,
            checker = permissionChecker,
            onResult = { _, state -> locationPermissionState.value = state },
        )

        val recordAudioRequester = PermissionRequester(
            activity = this,
            history = permissionHistory,
            checker = permissionChecker,
            onResult = { _, _ -> requestPostNotificationsIfNeeded() },
        )
        fun requestStartupPermissions() {
            if (permissionChecker.currentState(Manifest.permission.RECORD_AUDIO) != PermissionState.Granted) {
                recordAudioRequester.request(Manifest.permission.RECORD_AUDIO)
            } else {
                requestPostNotificationsIfNeeded()
            }
        }

        // Visual design, screen 1: the welcome screen shows until Get Started is tapped once; after
        // that every launch opens straight to Home, so SOS is never behind an extra tap. On that first
        // launch the permission dialogs wait until Get Started, so they don't cover the welcome screen;
        // the wake-word loop below already waits for RECORD_AUDIO on its own.
        val appPreferences = getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE)
        val showWelcome = !appPreferences.getBoolean(KEY_WELCOME_COMPLETED, false)
        if (!showWelcome) requestStartupPermissions()

        // Step 51: every real STT/Gemini/TTS call below is wrapped with a timing decorator
        // (TimingSpeechTranscriber/TimingIntentProvider/TimingCompanionResponseProvider/
        // TimingSpeechOutput) rather than the raw implementation — see PipelineLatencyLog's own
        // doc comment for why instrumentation lives here, at the composition root, instead of
        // inside VoiceCaptureCoordinator/EmergencyCompanionCoordinator/VoiceActivationCoordinator
        // themselves. speechOutput itself stays the raw AndroidSpeechOutput (onDestroy() below
        // needs its own shutdown() method, which the SpeechOutput interface doesn't expose) —
        // timedSpeechOutput is the wrapped one every coordinator actually gets.
        speechOutput = AndroidSpeechOutput(applicationContext)
        val timedSpeechOutput = TimingSpeechOutput(speechOutput)
        val captureCoordinator = VoiceCaptureCoordinator(
            TimingSpeechTranscriber(AndroidSpeechTranscriber(applicationContext)),
            permissionChecker,
        )
        val responseProvider: CompanionResponseProvider = TimingCompanionResponseProvider(
            BuildConfig.GEMINI_API_KEY
                .takeIf { it.isNotBlank() }
                ?.let { key -> GeminiCompanionResponseProvider(HttpGeminiTextGenerator(key)) }
                ?: CompanionResponseProvider { _, _ -> GeminiCompanionResponseProvider.SAFE_FALLBACK_RESPONSE },
        )
        // Step 51's own finding, from the same field-testing pass as its two sibling fixes
        // (AndroidSpeechOutput/EmergencyCompanionCoordinator): this used to be an unbounded
        // .filterNotNull().first() — fine whenever it's actually called during a real emergency
        // (there is always a persisted snapshot by then), but "Emergency Companion" is also a
        // plain, unconditional Home destination (see LigayaNavHost) reachable at any time, with
        // no active emergency and therefore no snapshot ever emitted at all — confirmed directly,
        // navigating there without pressing SOS first hung this companion turn forever, the same
        // silent-hang failure mode as the other two Step 51 fixes, just one layer earlier in the
        // same call chain. A real EmergencySnapshot() with its own default values (IDLE, no
        // subsystem successes) is the semantically correct fallback here, not just a value to
        // avoid hanging: ResponseValidator uses this to decide what Ligaya's allowed to claim, and
        // "nothing is confirmed yet" is the true state of the world when there's genuinely no
        // active emergency.
        val snapshotProvider = EmergencySnapshotProvider {
            withTimeoutOrNull(SNAPSHOT_WAIT_TIMEOUT_MILLIS) {
                emergencyController.observeSnapshot().filterNotNull().first()
            } ?: EmergencySnapshot()
        }
        val companionCoordinator = EmergencyCompanionCoordinator(
            captureCoordinator,
            responseProvider,
            timedSpeechOutput,
            snapshotProvider,
        )

        // Step 48: the voice-activation (wake phrase) pipeline. Same BuildConfig.GEMINI_API_KEY
        // gating as the companion loop above — unconfigured degrades to NullIntentProvider
        // (always Unavailable), never a crash.
        val intentProvider: IntentProvider = TimingIntentProvider(
            BuildConfig.GEMINI_API_KEY
                .takeIf { it.isNotBlank() }
                ?.let { key -> GeminiIntentProvider(HttpGeminiContentGenerator(key)) }
                ?: NullIntentProvider(),
        )
        var lastKnownIncidentType: IncidentType? = null
        val voiceReporter = VoiceEmergencyIntentReporter { intent ->
            intent.incidentType?.let { lastKnownIncidentType = it }
            when (val result = emergencyController.submitVoiceIntent(intent)) {
                is VoiceIntentResult.Activated, is VoiceIntentResult.AlreadyInProgress ->
                    EmergencyIntentDecision.Confirmed(intent)
                is VoiceIntentResult.NeedsClarification -> EmergencyIntentDecision.NeedsClarification(result.intent)
            }
        }
        val voiceActivationCoordinator = VoiceActivationCoordinator(captureCoordinator, intentProvider, voiceReporter)

        // Step 53 audit follow-up: the one moment the wake-word loop below actually speaks
        // (VOICE_AI_UNAVAILABLE) has no visual counterpart from voiceActivationCoordinator.phase
        // alone — that flow settles back to IDLE once its capture session ends, which happens
        // around the same time this fires, not for the duration of it. A separate, explicit
        // signal owned here (the composition root, where the decision to speak already lives)
        // is more precise than trying to stretch the coordinator's own phase to cover it.
        val aiUnavailableNotice = MutableStateFlow(false)

        // Visual design screen 2: Home's mic button and Voice chip start one companion voice turn.
        // Android runs one speech recognizer at a time, so while that turn listens the wake-word loop
        // below stands down (cancelling its session releases the recognizer) and resumes after.
        val manualVoiceTurn = MutableStateFlow(false)
        val startVoiceTurn: () -> Unit = {
            if (!manualVoiceTurn.value) {
                manualVoiceTurn.value = true
                lifecycleScope.launch {
                    try {
                        delay(RECOGNIZER_HANDOFF_DELAY_MILLIS)
                        companionCoordinator.runOneTurn()
                    } finally {
                        manualVoiceTurn.value = false
                    }
                }
            }
        }

        // Step 48: the real Location flow.
        val fusedLocationSource = FusedLocationSource(LocationServices.getFusedLocationProviderClient(applicationContext))
        val locationCoordinator = LocationFlowCoordinator(
            fusedLocationSource,
            permissionChecker,
            LocationFlowReporter(emergencyController::reportLocationFlow),
        )

        // Step 48: the real Emergency-Service (Places) flow — only constructed when a key is
        // configured, so an empty key never fires a request guaranteed to fail (see
        // app/build.gradle.kts' own comment on placesApiKey).
        val emergencyServiceCoordinator = BuildConfig.PLACES_API_KEY.takeIf { it.isNotBlank() }?.let { key ->
            EmergencyServiceFlowCoordinator(
                GooglePlacesNearbySearchSource(key),
                GooglePlacesDetailsSource(key),
                EmergencyServiceFlowReporter(emergencyController::reportEmergencyServiceFlow),
            )
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LigayaNavHost(
                        showWelcome = showWelcome,
                        onWelcomeCompleted = {
                            appPreferences.edit().putBoolean(KEY_WELCOME_COMPLETED, true).apply()
                            requestStartupPermissions()
                        },
                        onStartVoiceTurn = startVoiceTurn,
                        emergencyController = emergencyController,
                        companionCoordinator = companionCoordinator,
                        voicePhase = voiceActivationCoordinator.phase,
                        voiceAiUnavailable = aiUnavailableNotice.asStateFlow(),
                        authRepository = authRepository,
                        profileRepository = profileRepository,
                        locationPermissionState = locationPermissionState.asStateFlow(),
                        onRequestLocationPermission = {
                            locationRequester.request(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        onOpenAppSettings = ::openAppSettings,
                    )
                }
            }
        }

        // Step 48: automatically run Location, then (if configured) Places, once per real
        // episode — the moment the snapshot's own state first becomes EMERGENCY_ACTIVE.
        // distinctUntilChanged on .state plus filtering for EMERGENCY_ACTIVE fires exactly once
        // per episode: the FSM only ever moves forward through EMERGENCY_ACTIVE once (Step 46's
        // own exhaustively-tested transition graph), it never revisits it.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                emergencyController.observeSnapshot()
                    .distinctUntilChanged { old, new -> old?.state == new?.state }
                    .filter { it?.state == EmergencyState.EMERGENCY_ACTIVE }
                    .collect {
                        launch {
                            val locationResult = locationCoordinator.run()
                            val locationSucceeded = locationResult.getOrNull()
                                ?.subsystems?.location == com.ligaya.core.emergencyengine.LocationFlowState.Succeeded
                            if (locationSucceeded && emergencyServiceCoordinator != null) {
                                // A fresh fix was just obtained above — getLastKnownLocation() is
                                // a fast, cached lookup, not a second full GPS request.
                                val fix = fusedLocationSource.getLastKnownLocation()
                                if (fix != null) {
                                    emergencyServiceCoordinator.run(
                                        lastKnownIncidentType ?: IncidentType.OTHER,
                                        com.ligaya.core.places.GeoCoordinates(fix.latitude, fix.longitude),
                                    )
                                }
                            }
                        }
                    }
            }
        }

        // Step 48: the continuous, foreground-only wake-word listening loop. Each
        // listenForWakePhrase() call is one SpeechRecognizer session (ends on a final result or
        // error); this restarts it for as long as the Activity stays STARTED. A short delay
        // between iterations avoids a tight spin loop, longer when RECORD_AUDIO isn't granted so
        // this doesn't busy-poll a permission the user hasn't granted (or has denied).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    if (permissionChecker.currentState(Manifest.permission.RECORD_AUDIO) != PermissionState.Granted) {
                        delay(RECORD_AUDIO_NOT_GRANTED_RETRY_DELAY_MILLIS)
                        continue
                    }
                    if (manualVoiceTurn.value) {
                        manualVoiceTurn.first { !it }
                        continue
                    }
                    coroutineScope {
                        val session = launch {
                            voiceActivationCoordinator.listenForWakePhrase().collect { result ->
                                // A Decision's own side effects already happened via voiceReporter
                                // above — nothing more to do here for that case. AiUnavailable is
                                // different: VoiceActivationCoordinator never calls voiceReporter for
                                // it (see its own doc comment), so without this branch a spoken wake
                                // phrase with the AI stack unreachable produced no feedback of any
                                // kind — a silent hang from the user's own perspective, which Step
                                // 49's acceptance criteria (no silent hang; AI-dependent parts show
                                // explicit unavailable states) specifically rules out.
                                if (result is VoiceActivationResult.AiUnavailable) {
                                    aiUnavailableNotice.value = true
                                    timedSpeechOutput.speak(EmergencyStatusMessage.VOICE_AI_UNAVAILABLE)
                                    aiUnavailableNotice.value = false
                                }
                            }
                        }
                        // A voice turn started from Home takes the recognizer; stand this session down.
                        val handOff = launch {
                            manualVoiceTurn.first { it }
                            session.cancel()
                        }
                        session.join()
                        handOff.cancel()
                    }
                    delay(BETWEEN_LISTENING_SESSIONS_DELAY_MILLIS)
                }
            }
        }

        // Step 51: periodic battery-level logging for as long as the app is foregrounded — same
        // repeatOnLifecycle(STARTED) shape as the two loops above, so a field tester's fixed
        // continuous-listening session captures real battery samples the whole time the wake-word
        // loop is also genuinely running, not before or after it. See BatteryLevelLogger's own
        // doc comment for how a field tester reads a drain rate off the resulting log lines.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                BatteryLevelLogger(applicationContext).samples().collect {}
            }
        }
    }

    override fun onDestroy() {
        speechOutput.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val APP_PREFERENCES = "ligaya_app"
        const val KEY_WELCOME_COMPLETED = "welcome_completed"
        const val RECORD_AUDIO_NOT_GRANTED_RETRY_DELAY_MILLIS = 3_000L
        const val BETWEEN_LISTENING_SESSIONS_DELAY_MILLIS = 300L
        const val RECOGNIZER_HANDOFF_DELAY_MILLIS = 250L

        // Short, not generous like the other two Step 51 timeouts: an already-active emergency's
        // snapshot is a local Room query away, not an external service call — this bound only
        // ever matters for the "no emergency exists at all" case, where it's the only thing that
        // stops the wait, not a margin for something slow but legitimate.
        const val SNAPSHOT_WAIT_TIMEOUT_MILLIS = 2_000L
    }
}
