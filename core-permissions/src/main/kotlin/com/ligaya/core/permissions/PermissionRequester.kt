package com.ligaya.core.permissions

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Triggers the real system permission dialog. Must be constructed while `activity` is in the
 * CREATED state or earlier (registerForActivityResult's own requirement) — e.g. in onCreate,
 * not deferred to a later callback.
 */
class PermissionRequester(
    activity: ComponentActivity,
    private val history: PermissionRequestHistory,
    private val checker: PermissionChecker,
    private val onResult: (permission: String, state: PermissionState) -> Unit,
) {
    private var pendingPermission: String? = null

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val permission = pendingPermission ?: return@registerForActivityResult
        history.markRequested(permission)
        onResult(permission, if (granted) PermissionState.Granted else checker.currentState(permission))
        pendingPermission = null
    }

    fun request(permission: String) {
        pendingPermission = permission
        launcher.launch(permission)
    }
}
