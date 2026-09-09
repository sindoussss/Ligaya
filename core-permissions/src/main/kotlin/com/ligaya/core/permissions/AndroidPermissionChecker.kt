package com.ligaya.core.permissions

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat

interface PermissionChecker {
    fun currentState(permission: String): PermissionState
}

/**
 * Wires PermissionStateResolver to the real Android APIs. `activity` is only needed to
 * distinguish Denied from PermanentlyDenied (shouldShowRequestPermissionRationale requires an
 * Activity); pass null when checking outside any Activity context and an unresolved
 * not-granted permission conservatively reports as Denied rather than falsely PermanentlyDenied.
 */
class AndroidPermissionChecker(
    context: Context,
    activity: Activity?,
    history: PermissionRequestHistory,
) : PermissionChecker {

    private val resolver = PermissionStateResolver(
        history = history,
        isGranted = { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        },
        shouldShowRationale = { permission ->
            activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, permission) } ?: true
        },
    )

    override fun currentState(permission: String): PermissionState = resolver.resolve(permission)
}
