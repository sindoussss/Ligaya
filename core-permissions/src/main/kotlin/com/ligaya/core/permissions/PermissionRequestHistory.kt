package com.ligaya.core.permissions

/** Tracks, per permission, whether this app has ever asked for it before — the piece of state
 *  Android itself doesn't expose, needed to tell NotRequested apart from PermanentlyDenied. */
interface PermissionRequestHistory {
    fun hasRequestedBefore(permission: String): Boolean
    fun markRequested(permission: String)
}
