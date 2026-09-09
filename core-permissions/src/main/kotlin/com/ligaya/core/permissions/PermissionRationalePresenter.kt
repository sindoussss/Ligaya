package com.ligaya.core.permissions

/**
 * The "rationale-dialog contract" this step's roadmap entry calls for: a hook a UI layer
 * implements later (no UI/Compose dependency here) so PermissionRequester's caller can explain
 * why a permission matters before re-prompting after a Denied result. Returns true if the user
 * chose to proceed (re-attempt the system request), false if they declined.
 */
fun interface PermissionRationalePresenter {
    suspend fun presentRationale(permission: String): Boolean
}
