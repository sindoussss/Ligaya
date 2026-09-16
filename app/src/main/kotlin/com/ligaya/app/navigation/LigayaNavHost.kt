package com.ligaya.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.saveable.rememberSaveable
import com.ligaya.app.screens.PlaceholderScreen
import com.ligaya.app.screens.settings.AboutSettingsScreen
import com.ligaya.app.screens.settings.AppearanceSettingsScreen
import com.ligaya.app.screens.settings.CharacterSettingsScreen
import com.ligaya.app.screens.settings.GeneralSettingsScreen
import com.ligaya.app.screens.settings.LigayaSettings
import com.ligaya.app.screens.settings.PrivacySettingsScreen
import com.ligaya.app.screens.settings.SettingsScreen
import com.ligaya.app.screens.settings.SettingsSection
import com.ligaya.app.screens.settings.VoiceSettingsScreen
import com.ligaya.app.screens.SosScreen
import com.ligaya.app.screens.WelcomeScreen
import com.ligaya.designsystem.LigayaMotion
import com.ligaya.designsystem.rememberIsReduceMotionEnabled
import com.ligaya.designsystem.LigayaThemeMode
import com.ligaya.designsystem.components.LigayaTab
import com.ligaya.core.backend.auth.AuthRepository
import com.ligaya.core.data.profile.EmergencyProfileRepository
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.permissions.PermissionState
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.uistate.SosResult
import com.ligaya.core.voice.VoicePipelinePhase
import com.ligaya.feature.companion.CompanionTurnResult
import com.ligaya.feature.companion.EmergencyCompanionCoordinator
import com.ligaya.feature.companion.EmergencyCompanionScreen
import com.ligaya.feature.companion.ListeningState
import com.ligaya.core.ai.CompanionTurn
import com.ligaya.core.ai.NetworkStatus
import com.ligaya.feature.companion.VoiceListeningScreen
import com.ligaya.feature.companion.TroubleReason
import com.ligaya.feature.companion.VoiceSpeakingScreen
import com.ligaya.feature.companion.VoiceThinkingScreen
import com.ligaya.feature.companion.VoiceTroubleScreen
import com.ligaya.feature.emergencyactive.EmergencyActiveScreen
import com.ligaya.feature.emergencyactive.EmergencyResolvedScreen
import com.ligaya.feature.home.HomeScreen
import com.ligaya.feature.home.NavigableDestination
import com.ligaya.feature.onboarding.CreateAccountScreen
import com.ligaya.feature.onboarding.EmergencyProfileScreen
import com.ligaya.feature.onboarding.LocationPermissionScreen
import com.ligaya.feature.onboarding.OnboardingIntroScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The signed-in user's name from their emergency profile, or null with no session or no saved name. */
@Composable
private fun rememberProfileName(authRepository: AuthRepository, profileRepository: EmergencyProfileRepository): String? {
    val name by produceState<String?>(initialValue = null) {
        value = authRepository.currentUserId()?.let { userId ->
            runCatching { profileRepository.getProfile(userId) }.getOrNull()?.name
        }
    }
    return name
}

/** States in which an emergency is underway, so the chat greets the user as a companion, not an assistant. */
private val EMERGENCY_IN_PROGRESS = setOf(
    EmergencyState.EMERGENCY_DETECTED,
    EmergencyState.EMERGENCY_CONFIRMED,
    EmergencyState.EMERGENCY_ACTIVE,
)

@Composable
fun LigayaNavHost(
    emergencyController: EmergencyController,
    companionCoordinator: EmergencyCompanionCoordinator,
    voicePhase: StateFlow<VoicePipelinePhase>,
    voiceAiUnavailable: StateFlow<Boolean>,
    authRepository: AuthRepository,
    profileRepository: EmergencyProfileRepository,
    locationPermissionState: StateFlow<PermissionState>,
    onRequestLocationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    showWelcome: Boolean,
    onWelcomeCompleted: () -> Unit,
    onStartVoiceTurn: () -> Unit,
    /** Whether Gemini is configured for chat replies; without it the chat says replies are limited. */
    companionAiAvailable: Boolean,
    /** Live microphone level (0..1) from the speech recognizer, for the Listening screen's bars. */
    inputLevel: StateFlow<Float>,
    /** True while a voice turn started with [onStartVoiceTurn] is running. */
    voiceTurnActive: StateFlow<Boolean>,
    onCancelVoiceTurn: () -> Unit,
    isMicPermitted: () -> Boolean,
    /** Read when a turn fails, so screen 8 can name being offline as the cause only when that is actually true
     *  (section 21 lists it as its own failure row; section 23 forbids stating a cause nothing established). */
    networkStatus: NetworkStatus,
    /** The saved Appearance choice, and the action that cycles it. Both the Home header's button and Settings
     *  change the same one setting. */
    themeMode: LigayaThemeMode,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSetThemeMode: (LigayaThemeMode) -> Unit,
    /** Every saved setting Settings can change: appearance, her animation, and the wake phrase. */
    settings: LigayaSettings,
    /** Shown on About. The real one, read from the build, not a number typed into the screen. */
    appVersionName: String,
    navController: NavHostController = rememberNavController(),
) {
    val scope = rememberCoroutineScope()
    val emergencySnapshots = remember { emergencyController.observeSnapshot() }

    // Sections 5 and 9: however an emergency becomes active — SOS, the wake phrase, or something said to Ligaya's
    // mic — the Emergency screen comes up. Only on the transition, so leaving it after resolving isn't undone.
    LaunchedEffect(emergencySnapshots) {
        emergencySnapshots.map { it?.state }.distinctUntilChanged().collect { state ->
            if (state == EmergencyState.EMERGENCY_ACTIVE &&
                navController.currentDestination?.route != LigayaDestination.EmergencyActive.route
            ) {
                navController.navigate(LigayaDestination.EmergencyActive.route) { launchSingleTop = true }
            }
        }
    }
    val openListening = {
        navController.navigate(LigayaDestination.Listening.route) { launchSingleTop = true }
    }
    val openChat = {
        navController.navigate(LigayaDestination.EmergencyCompanion.route) { launchSingleTop = true }
    }
    val openProfile = {
        val signedIn = authRepository.currentUserId() != null
        navController.navigate(if (signedIn) LigayaDestination.EmergencyProfile.route else LigayaDestination.CreateAccount.route)
    }
    val openSettings = {
        navController.navigate(LigayaDestination.Settings.route) { launchSingleTop = true }
    }
    NavHost(
        navController = navController,
        startDestination = if (showWelcome) LigayaDestination.Welcome.route else LigayaDestination.Home.route,
        // One gentle crossfade for every route change, set once here rather than per-destination:
        // navigation-compose's default is a slide, which fights the calm of the visual design and
        // (more practically) would slide the splash off-screen right after it has just faded its
        // own content out — two competing exits for one handoff. A fade lets the splash's own
        // fade-out and the route change read as a single continuous transition.
        enterTransition = { fadeIn(animationSpec = tween(LigayaMotion.durationStateTransition, easing = LigayaMotion.easingGentle)) },
        exitTransition = { fadeOut(animationSpec = tween(LigayaMotion.durationStateTransition, easing = LigayaMotion.easingGentle)) },
    ) {
        composable(LigayaDestination.Welcome.route) {
            WelcomeScreen(
                onGetStarted = {
                    onWelcomeCompleted()
                    navController.navigate(LigayaDestination.Home.route) {
                        // Not returnable-to: the first Back press from Home exits the app.
                        popUpTo(LigayaDestination.Welcome.route) { inclusive = true }
                    }
                },
            )
        }
        composable(LigayaDestination.Home.route) {
            // Step 36: everything except Home/SafetyCircle/EmergencyCompanion gets its own
            // dedicated entry point on the real Home screen now; the rest stay reachable through
            // this generic list exactly as Step 7's own test already expects — feature-home
            // knows nothing about LigayaDestination (that dependency would run backwards), so
            // this module builds the (route, title) pairs itself.
            //
            // Sos is also excluded here, not just given its own entry point: Home's SosControl
            // already IS a real, directly-functional SOS action (Step 13's flow, reached with
            // one tap, no separate confirmation screen) — a second "SOS"-labeled generic button
            // leading to the older standalone Sos screen would be genuinely ambiguous on the same
            // screen (proven directly: it broke Compose UI Test's onNodeWithText("SOS") — two
            // matching nodes — not just a hypothetical UX concern). The Sos route and SosScreen
            // composable are untouched below and still reachable by direct navigation; they're
            // just no longer offered as a redundant generic path from Home.
            //
            // EmergencyActive (Step 37) is excluded for the same reason: it's reached for real
            // by activating SOS (via onSosActivated below), not by tapping a generic placeholder
            // button — and the real screen has no "Back" button or literal "Emergency Active"
            // title text for a generic button-and-placeholder loop to find anyway (see
            // NavigationRouteReachabilityTest's matching exclusion for the same reasoning).
            val otherDestinations = LigayaDestination.all
                .filter {
                    it != LigayaDestination.Home &&
                        it != LigayaDestination.SafetyCircle &&
                        it != LigayaDestination.EmergencyCompanion &&
                        it != LigayaDestination.Sos &&
                        it != LigayaDestination.EmergencyActive
                }
                .map { NavigableDestination(it.route, it.title) }
            // The greeting's name comes from the signed-in user's emergency profile; with no session or
            // no saved name, Home greets them as "kaibigan".
            val userName = rememberProfileName(authRepository, profileRepository)
            HomeScreen(
                emergencyController = emergencyController,
                otherDestinations = otherDestinations,
                onSosActivated = { navController.navigate(LigayaDestination.EmergencyActive.route) { launchSingleTop = true } },
                onNavigateToSafetyCircle = { navController.navigate(LigayaDestination.SafetyCircle.route) },
                onNavigateToCompanion = openChat,
                onNavigateToRoute = { route -> navController.navigate(route) },
                voicePhase = voicePhase,
                voiceAiUnavailable = voiceAiUnavailable,
                userName = userName,
                // A typed question opens the conversation and is answered there, through the same
                // validated companion turn the chat screen's own input uses.
                onAskText = { question ->
                    openChat()
                    scope.launch { companionCoordinator.runOneTurnWithText(question) }
                },
                // The Listening screen starts (and owns) the voice turn.
                onStartVoice = openListening,
                onNavigateToTools = { navController.navigate(LigayaDestination.Tools.route) },
                // The Profile tab opens Settings (screen 9); the profile card there leads on to the profile
                // itself, or to signing in when there is no account yet.
                onNavigateToProfile = openSettings,
                onToggleTheme = onToggleTheme,
                isDarkTheme = isDarkTheme,
            )
        }
        composable(LigayaDestination.Tools.route) {
            PlaceholderScreen(destination = LigayaDestination.Tools, onBack = { navController.popBackStack() })
        }
        composable(LigayaDestination.Sos.route) {
            SosScreen(
                controller = emergencyController,
                onActivated = { navController.navigate(LigayaDestination.EmergencyActive.route) { launchSingleTop = true } },
                onBack = { navController.popBackStack() },
            )
        }
        composable(LigayaDestination.EmergencyActive.route) {
            // safetyCircleDeliveryStatus is empty here, not wired to real household/notification
            // data: nothing in this app's composition graph has a "current user's household"
            // concept yet (RoomSafetyCircleDeliveryStatusSource is real and tested — see
            // feature-emergency-active's own SafetyCircleDeliveryStatusSourceTest — it just has
            // no household/event id to construct with here). Genuinely out of Step 37's own file
            // scope (feature-emergency-active only) to invent that missing infrastructure.
            //
            // onRetryCall (Step 41's fix) is real: emergencyController.retryCall() reaches all
            // the way to a real Unified911FlowCoordinator now (see DefaultEmergencyController).
            EmergencyActiveScreen(
                emergencyController = emergencyController,
                safetyCircleDeliveryStatus = emptyList(),
                voicePipelinePhase = companionCoordinator.phase,
                // Only called once the engine has accepted "I'm safe" (see EmergencyActiveScreen), so screen 6 can
                // state it as fact. The emergency screen itself is left behind, not returnable-to.
                onMarkedSafe = {
                    navController.navigate(LigayaDestination.Resolved.route) {
                        popUpTo(LigayaDestination.EmergencyActive.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onRetryCall = { scope.launch { emergencyController.retryCall() } },
            )
        }
        composable(LigayaDestination.EmergencyCompanion.route) {
            // Visual design screen 3: Chat. SOS from its menu goes straight to the deterministic engine, the
            // same one-tap flow as Home's pill, never through Gemini.
            val userName = rememberProfileName(authRepository, profileRepository)
            val snapshot by emergencySnapshots.collectAsState(initial = null)
            EmergencyCompanionScreen(
                coordinator = companionCoordinator,
                userName = userName,
                aiAvailable = companionAiAvailable,
                inEmergency = snapshot?.state in EMERGENCY_IN_PROGRESS,
                onBack = { navController.popBackStack() },
                onSelectTab = { tab ->
                    when (tab) {
                        LigayaTab.Home -> if (!navController.popBackStack(LigayaDestination.Home.route, inclusive = false)) {
                            navController.navigate(LigayaDestination.Home.route)
                        }
                        LigayaTab.Chat -> Unit
                        LigayaTab.Tools -> navController.navigate(LigayaDestination.Tools.route)
                        LigayaTab.Profile -> openSettings()
                    }
                },
                onStartVoice = openListening,
                onSos = {
                    scope.launch {
                        when (emergencyController.triggerSos()) {
                            is SosResult.Activated, is SosResult.AlreadyInProgress ->
                                navController.navigate(LigayaDestination.EmergencyActive.route) { launchSingleTop = true }
                        }
                    }
                },
            )
        }
        composable(LigayaDestination.Listening.route) {
            // Visual design screen 4. This screen starts the voice turn when it opens and owns it: closing or
            // going back cancels it. Once Ligaya has heard something, the turn moves on to Thinking.
            val phase by companionCoordinator.phase.collectAsState()
            val lastResult by companionCoordinator.lastResult.collectAsState()
            val active by voiceTurnActive.collectAsState()
            val level by inputLevel.collectAsState()
            var micPermitted by remember { mutableStateOf(isMicPermitted()) }
            // `requested` covers the moment between asking for a turn and it actually starting; `finishedOne` makes
            // sure "didn't catch that" only ever describes a turn started here, not an older one.
            var requested by remember { mutableStateOf(false) }
            var sawActive by remember { mutableStateOf(false) }
            var finishedOne by remember { mutableStateOf(false) }

            fun start() {
                micPermitted = isMicPermitted()
                if (!micPermitted) return
                requested = true
                onStartVoiceTurn()
            }
            fun close() {
                onCancelVoiceTurn()
                navController.popBackStack()
            }

            LaunchedEffect(Unit) { start() }
            LaunchedEffect(active) {
                if (active) {
                    sawActive = true
                } else if (sawActive) {
                    sawActive = false
                    requested = false
                    finishedOne = true
                }
            }
            LaunchedEffect(phase) {
                if (phase == VoicePipelinePhase.PROCESSING) {
                    navController.navigate(LigayaDestination.Thinking.route) {
                        popUpTo(LigayaDestination.Listening.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            BackHandler { close() }

            val state = when {
                !micPermitted -> ListeningState.MicBlocked
                active || requested -> ListeningState.Listening
                finishedOne && lastResult is CompanionTurnResult.NoSpeechCaptured -> ListeningState.NotHeard
                else -> ListeningState.Paused
            }
            VoiceListeningScreen(
                state = state,
                inputLevel = level,
                onMicTap = {
                    when (state) {
                        ListeningState.Listening -> {
                            requested = false
                            onCancelVoiceTurn()
                        }
                        ListeningState.MicBlocked -> {
                            micPermitted = isMicPermitted()
                            if (micPermitted) start() else onOpenAppSettings()
                        }
                        else -> start()
                    }
                },
                onClose = ::close,
            )
        }
        composable(LigayaDestination.Resolved.route) {
            // Visual design screen 6, after the engine has confirmed "I'm safe". Back or the Home tab returns to Home;
            // the emergency screen is already off the stack.
            val goHome = {
                if (!navController.popBackStack(LigayaDestination.Home.route, inclusive = false)) {
                    navController.navigate(LigayaDestination.Home.route) {
                        popUpTo(LigayaDestination.Resolved.route) { inclusive = true }
                    }
                }
            }
            BackHandler { goHome() }
            EmergencyResolvedScreen(
                emergencyController = emergencyController,
                onSelectTab = { tab ->
                    when (tab) {
                        LigayaTab.Home -> goHome()
                        LigayaTab.Chat -> openChat()
                        LigayaTab.Tools -> navController.navigate(LigayaDestination.Tools.route)
                        LigayaTab.Profile -> openSettings()
                    }
                },
            )
        }
        composable(LigayaDestination.Trouble.route) {
            // Visual design screen 8. The reason is established, never guessed: offline only when the device says
            // so, a blocked reply when that is what came back, otherwise her assistant could not be reached.
            val lastResult by companionCoordinator.lastResult.collectAsState()
            val transcript by companionCoordinator.transcript.collectAsState()
            // A reply that is only the safe fallback means Gemini never really answered, which is the same story
            // for the user as not reaching it at all — so both land on AssistantUnavailable.
            val reason = remember(lastResult, networkStatus) {
                when {
                    !networkStatus.isOnline() -> TroubleReason.Offline
                    lastResult is CompanionTurnResult.ResponseBlocked -> TroubleReason.ReplyBlocked
                    else -> TroubleReason.AssistantUnavailable
                }
            }
            val leaveForChat = {
                navController.navigate(LigayaDestination.EmergencyCompanion.route) {
                    popUpTo(LigayaDestination.Trouble.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
            BackHandler { leaveForChat() }
            VoiceTroubleScreen(
                reason = reason,
                onTryAgain = {
                    navController.navigate(LigayaDestination.Listening.route) {
                        popUpTo(LigayaDestination.Trouble.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(LigayaDestination.Speaking.route) {
            // Visual design screen 7. The reply itself comes from the transcript — it is appended just before she
            // starts speaking — but whether the phone actually said it out loud is only known once the turn ends.
            // So this shows "Speaking..." while playback runs, and if the engine reports nothing was audible it
            // stays put showing the words instead of quietly moving on (section 23).
            val phase by companionCoordinator.phase.collectAsState()
            val transcript by companionCoordinator.transcript.collectAsState()
            val lastResult by companionCoordinator.lastResult.collectAsState()
            val reply = transcript.lastOrNull { it.speaker == CompanionTurn.Speaker.LIGAYA }?.text.orEmpty()
            val finishedSilent = phase != VoicePipelinePhase.SPEAKING &&
                (lastResult as? CompanionTurnResult.Spoken)?.aloud == false
            var leaving by remember { mutableStateOf(false) }
            val openChatOnce = {
                if (!leaving) {
                    leaving = true
                    navController.navigate(LigayaDestination.EmergencyCompanion.route) {
                        popUpTo(LigayaDestination.Speaking.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            LaunchedEffect(phase, lastResult) {
                if (phase == VoicePipelinePhase.SPEAKING) return@LaunchedEffect
                val result = lastResult ?: return@LaunchedEffect
                // Heard: the conversation carries on in Chat. Not heard: stay, so the reply can be read.
                if (result !is CompanionTurnResult.Spoken || result.aloud) openChatOnce()
            }
            BackHandler { openChatOnce() }
            VoiceSpeakingScreen(reply = reply, aloud = !finishedSilent)
        }
        composable(LigayaDestination.Thinking.route) {
            // Visual design screen 5, shown while her reply is being worked out. As soon as the turn moves on (she
            // starts speaking, or it ended without a reply) the conversation continues in Chat, where the reply or
            // the reason there isn't one appears. Back cancels the turn. What was said has already gone to the
            // emergency engine as well, and cancelling doesn't undo that.
            val phase by companionCoordinator.phase.collectAsState()
            val lastThinkingResult by companionCoordinator.lastResult.collectAsState()
            var leaving by remember { mutableStateOf(false) }
            LaunchedEffect(phase) {
                if (phase != VoicePipelinePhase.PROCESSING && !leaving) {
                    leaving = true
                    // She has an answer: screen 7 reads it out. A reply that was blocked has a reason worth
                    // showing, so that goes to screen 8; anything else continues in Chat.
                    val next = when {
                        phase == VoicePipelinePhase.SPEAKING -> LigayaDestination.Speaking.route
                        lastThinkingResult is CompanionTurnResult.ResponseBlocked -> LigayaDestination.Trouble.route
                        else -> LigayaDestination.EmergencyCompanion.route
                    }
                    navController.navigate(next) {
                        popUpTo(LigayaDestination.Thinking.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            BackHandler {
                if (!leaving) {
                    leaving = true
                    onCancelVoiceTurn()
                    navController.popBackStack()
                }
            }
            VoiceThinkingScreen(aiAvailable = companionAiAvailable)
        }
        // Visual design, screen 9 and its six destinations. Each row leads to a real screen; nothing here is
        // a placeholder, and every choice on those screens is saved and applied at once.
        composable(LigayaDestination.Settings.route) {
            val settingsName = rememberProfileName(authRepository, profileRepository)
            val appearanceValue = when (themeMode) {
                LigayaThemeMode.Light -> "Light"
                LigayaThemeMode.Dark -> "Dark"
                LigayaThemeMode.System -> "System"
            }
            SettingsScreen(
                userName = settingsName,
                userEmail = authRepository.currentUserEmail(),
                appearanceValue = appearanceValue,
                onBack = { navController.popBackStack() },
                onOpenSection = { section ->
                    navController.navigate(
                        when (section) {
                            SettingsSection.General -> LigayaDestination.SettingsGeneral.route
                            SettingsSection.Appearance -> LigayaDestination.SettingsAppearance.route
                            SettingsSection.Voice -> LigayaDestination.SettingsVoice.route
                            SettingsSection.Character -> LigayaDestination.SettingsCharacter.route
                            SettingsSection.Privacy -> LigayaDestination.SettingsPrivacy.route
                            SettingsSection.About -> LigayaDestination.SettingsAbout.route
                        },
                    )
                },
                onOpenProfile = openProfile,
                onSelectTab = { tab ->
                    when (tab) {
                        LigayaTab.Home -> if (!navController.popBackStack(LigayaDestination.Home.route, inclusive = false)) {
                            navController.navigate(LigayaDestination.Home.route)
                        }
                        LigayaTab.Chat -> openChat()
                        LigayaTab.Tools -> navController.navigate(LigayaDestination.Tools.route)
                        LigayaTab.Profile -> Unit
                    }
                },
            )
        }
        composable(LigayaDestination.SettingsGeneral.route) {
            // Read once per visit: the welcome flag is only acted on at launch, so this is what the next
            // launch will do, not something that changes while the screen is open.
            val willShowWelcomeAgain = remember { !settings.welcomeCompleted }
            var askedForWelcome by rememberSaveable { mutableStateOf(false) }
            GeneralSettingsScreen(
                onOpenProfile = openProfile,
                onShowWelcomeAgain = {
                    settings.setWelcomeCompleted(false)
                    askedForWelcome = true
                },
                welcomeWillShowAgain = willShowWelcomeAgain || askedForWelcome,
                onBack = { navController.popBackStack() },
            )
        }
        composable(LigayaDestination.SettingsAppearance.route) {
            AppearanceSettingsScreen(
                mode = themeMode,
                onSelectMode = onSetThemeMode,
                onBack = { navController.popBackStack() },
            )
        }
        composable(LigayaDestination.SettingsVoice.route) {
            val wakePhraseEnabled by settings.wakePhraseEnabled.collectAsState()
            VoiceSettingsScreen(
                wakePhraseEnabled = wakePhraseEnabled,
                onWakePhraseChange = settings::setWakePhraseEnabled,
                micPermitted = isMicPermitted(),
                smartRepliesConfigured = companionAiAvailable,
                onOpenSystemSettings = onOpenAppSettings,
                onBack = { navController.popBackStack() },
            )
        }
        composable(LigayaDestination.SettingsCharacter.route) {
            val mascotPreferences by settings.mascot.collectAsState()
            CharacterSettingsScreen(
                preferences = mascotPreferences,
                reduceMotionOnPhone = rememberIsReduceMotionEnabled(),
                onAnimationSpeedChange = settings::setAnimationSpeed,
                onDepthStrengthChange = settings::setDepthStrength,
                onMotionChange = settings::setMotion,
                onBack = { navController.popBackStack() },
            )
        }
        composable(LigayaDestination.SettingsPrivacy.route) {
            PrivacySettingsScreen(
                signedInEmail = authRepository.currentUserEmail(),
                smartRepliesConfigured = companionAiAvailable,
                onSignOut = {
                    authRepository.logOut()
                    // Back to Home rather than staying on a screen describing an account nobody is in.
                    navController.navigate(LigayaDestination.Home.route) {
                        popUpTo(LigayaDestination.Home.route) { inclusive = true }
                    }
                },
                onOpenSystemSettings = onOpenAppSettings,
                onBack = { navController.popBackStack() },
            )
        }
        composable(LigayaDestination.SettingsAbout.route) {
            AboutSettingsScreen(
                versionName = appVersionName,
                smartRepliesConfigured = companionAiAvailable,
                onBack = { navController.popBackStack() },
            )
        }
        composable(LigayaDestination.Onboarding.route) {
            // Visual design screen 2. Both exits pop back for now: the real hand-off into the
            // account/profile setup flow (feature-onboarding's own OnboardingScreen — reference
            // screens 3/5/6) gets wired when those screens are designed, rather than pointing
            // "Get Started" at a half-styled screen in the meantime.
            OnboardingIntroScreen(
                onGetStarted = { navController.navigate(LigayaDestination.CreateAccount.route) },
                onSkip = { navController.popBackStack() },
            )
        }
        composable(LigayaDestination.CreateAccount.route) {
            // Visual design screen 3. On success this pops back for now — the emergency-profile and
            // Safety Circle steps that should follow are reference screens 5/6/7, still unstyled,
            // and routing into them half-designed would be worse than stopping here.
            CreateAccountScreen(
                authRepository = authRepository,
                // Screen 4 is the next step of §3's onboarding order, so an authenticated user
                // continues into the location primer rather than being dropped back at Home.
                onAuthenticated = { navController.navigate(LigayaDestination.LocationPermission.route) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(LigayaDestination.LocationPermission.route) {
            val locationState by locationPermissionState.collectAsState()
            LocationPermissionScreen(
                permissionState = locationState,
                onRequestPermission = onRequestLocationPermission,
                onOpenSettings = onOpenAppSettings,
                // Both "Continue" and "Not Now" lead on: denial must never block onboarding (§3),
                // and location is one of §13's independent subsystems either way.
                onContinue = { navController.navigate(LigayaDestination.EmergencyProfile.route) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(LigayaDestination.EmergencyProfile.route) {
            // The session is the source of truth for whose profile this is — LocalAuthRepository
            // (or Firebase, once configured) already persists it, so nothing has to be threaded
            // through the back stack as a navigation argument.
            val userId = authRepository.currentUserId()
            if (userId == null) {
                // Only reachable by deep link, since the flow above always authenticates first.
                LaunchedEffect(Unit) { navController.popBackStack(LigayaDestination.Home.route, inclusive = false) }
            } else {
                EmergencyProfileScreen(
                    profileRepository = profileRepository,
                    userId = userId,
                    onSaved = { navController.popBackStack(LigayaDestination.Home.route, inclusive = false) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        LigayaDestination.all
            .filter {
                it != LigayaDestination.Home &&
                    it != LigayaDestination.Sos &&
                    it != LigayaDestination.EmergencyActive &&
                    it != LigayaDestination.EmergencyCompanion &&
                    // Visual design screen 2: has a real screen now, so it must drop out of the
                    // placeholder fallback or the route would be registered twice.
                    it != LigayaDestination.Onboarding
            }
            .forEach { destination ->
                composable(destination.route) {
                    PlaceholderScreen(destination = destination, onBack = { navController.popBackStack() })
                }
            }
    }
}
