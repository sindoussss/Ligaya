package com.ligaya.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ligaya.app.screens.PlaceholderScreen
import com.ligaya.app.screens.SosScreen
import com.ligaya.app.screens.SplashScreen
import com.ligaya.designsystem.LigayaMotion
import com.ligaya.core.backend.auth.AuthRepository
import com.ligaya.core.data.profile.EmergencyProfileRepository
import com.ligaya.core.permissions.PermissionState
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.voice.VoicePipelinePhase
import com.ligaya.feature.companion.EmergencyCompanionCoordinator
import com.ligaya.feature.companion.EmergencyCompanionScreen
import com.ligaya.feature.emergencyactive.EmergencyActiveScreen
import com.ligaya.feature.home.HomeScreen
import com.ligaya.feature.home.NavigableDestination
import com.ligaya.feature.onboarding.CreateAccountScreen
import com.ligaya.feature.onboarding.EmergencyProfileScreen
import com.ligaya.feature.onboarding.LocationPermissionScreen
import com.ligaya.feature.onboarding.OnboardingIntroScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    navController: NavHostController = rememberNavController(),
) {
    val scope = rememberCoroutineScope()
    NavHost(
        navController = navController,
        startDestination = LigayaDestination.Splash.route,
        // One gentle crossfade for every route change, set once here rather than per-destination:
        // navigation-compose's default is a slide, which fights the calm of the visual design and
        // (more practically) would slide the splash off-screen right after it has just faded its
        // own content out — two competing exits for one handoff. A fade lets the splash's own
        // fade-out and the route change read as a single continuous transition.
        enterTransition = { fadeIn(animationSpec = tween(LigayaMotion.durationStateTransition, easing = LigayaMotion.easingGentle)) },
        exitTransition = { fadeOut(animationSpec = tween(LigayaMotion.durationStateTransition, easing = LigayaMotion.easingGentle)) },
    ) {
        composable(LigayaDestination.Splash.route) {
            SplashScreen(
                onFinished = {
                    navController.navigate(LigayaDestination.Home.route) {
                        // Splash is never returnable-to: popping it (inclusive) means the very
                        // first Back press from Home exits the app, rather than replaying the
                        // brand animation the user has already sat through.
                        popUpTo(LigayaDestination.Splash.route) { inclusive = true }
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
            HomeScreen(
                emergencyController = emergencyController,
                otherDestinations = otherDestinations,
                onSosActivated = { navController.navigate(LigayaDestination.EmergencyActive.route) },
                onNavigateToSafetyCircle = { navController.navigate(LigayaDestination.SafetyCircle.route) },
                onNavigateToCompanion = { navController.navigate(LigayaDestination.EmergencyCompanion.route) },
                onNavigateToRoute = { route -> navController.navigate(route) },
                voicePhase = voicePhase,
                voiceAiUnavailable = voiceAiUnavailable,
            )
        }
        composable(LigayaDestination.Sos.route) {
            SosScreen(
                controller = emergencyController,
                onActivated = { navController.navigate(LigayaDestination.EmergencyActive.route) },
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
                onMarkedSafe = { navController.popBackStack(LigayaDestination.Home.route, inclusive = false) },
                onRetryCall = { scope.launch { emergencyController.retryCall() } },
            )
        }
        composable(LigayaDestination.EmergencyCompanion.route) {
            EmergencyCompanionScreen(coordinator = companionCoordinator)
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
