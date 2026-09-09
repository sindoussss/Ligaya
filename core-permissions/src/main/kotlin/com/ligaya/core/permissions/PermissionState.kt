package com.ligaya.core.permissions

/**
 * LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 19: nearly every subsystem needs to reason about
 * runtime permission state. Four states, not three — Android's own APIs cannot distinguish
 * "never asked" from "permanently denied" (both make shouldShowRequestPermissionRationale
 * return false); PermissionStateResolver derives the difference from request history we track
 * ourselves. See its doc comment for why.
 */
sealed interface PermissionState {
    data object Granted : PermissionState
    data object Denied : PermissionState
    data object PermanentlyDenied : PermissionState
    data object NotRequested : PermissionState
}
