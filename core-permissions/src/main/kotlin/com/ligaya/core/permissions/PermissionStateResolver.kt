package com.ligaya.core.permissions

/** Abstracts the two Android-framework signals the resolver's decision depends on
 *  (ContextCompat.checkSelfPermission and ActivityCompat.shouldShowRequestPermissionRationale),
 *  so the branching logic below is unit-testable on a plain JVM with fakes for both — no
 *  device, Activity, or Robolectric needed. */
fun interface IsPermissionGranted {
    operator fun invoke(permission: String): Boolean
}

fun interface ShouldShowRationale {
    operator fun invoke(permission: String): Boolean
}

/**
 * The actual four-state decision (LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 19), kept separate
 * from any Android API call so it's fully deterministic and testable:
 *
 * - granted                                          -> Granted
 * - not granted, never requested before               -> NotRequested
 * - not granted, requested before, rationale showable  -> Denied
 * - not granted, requested before, rationale NOT showable -> PermanentlyDenied
 */
class PermissionStateResolver(
    private val history: PermissionRequestHistory,
    private val isGranted: IsPermissionGranted,
    private val shouldShowRationale: ShouldShowRationale,
) {
    fun resolve(permission: String): PermissionState {
        if (isGranted(permission)) return PermissionState.Granted
        if (!history.hasRequestedBefore(permission)) return PermissionState.NotRequested
        return if (shouldShowRationale(permission)) {
            PermissionState.Denied
        } else {
            PermissionState.PermanentlyDenied
        }
    }
}
