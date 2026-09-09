package com.ligaya.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ligaya.app.screens.PlaceholderScreen
import com.ligaya.app.screens.SosScreen
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.feature.companion.EmergencyCompanionCoordinator
import com.ligaya.feature.companion.EmergencyCompanionScreen
import com.ligaya.feature.emergencyactive.EmergencyActiveScreen
import com.ligaya.feature.home.HomeScreen
import com.ligaya.feature.home.NavigableDestination

@Composable
fun LigayaNavHost(
    emergencyController: EmergencyController,
    companionCoordinator: EmergencyCompanionCoordinator,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = LigayaDestination.Home.route) {
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
            EmergencyActiveScreen(
                emergencyController = emergencyController,
                safetyCircleDeliveryStatus = emptyList(),
                voicePipelinePhase = companionCoordinator.phase,
                onMarkedSafe = { navController.popBackStack(LigayaDestination.Home.route, inclusive = false) },
            )
        }
        composable(LigayaDestination.EmergencyCompanion.route) {
            EmergencyCompanionScreen(coordinator = companionCoordinator)
        }
        LigayaDestination.all
            .filter {
                it != LigayaDestination.Home &&
                    it != LigayaDestination.Sos &&
                    it != LigayaDestination.EmergencyActive &&
                    it != LigayaDestination.EmergencyCompanion
            }
            .forEach { destination ->
                composable(destination.route) {
                    PlaceholderScreen(destination = destination, onBack = { navController.popBackStack() })
                }
            }
    }
}
