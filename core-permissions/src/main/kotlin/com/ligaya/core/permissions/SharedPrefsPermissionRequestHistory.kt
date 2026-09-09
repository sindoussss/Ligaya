package com.ligaya.core.permissions

import android.content.Context
import androidx.core.content.edit

class SharedPrefsPermissionRequestHistory(context: Context) : PermissionRequestHistory {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun hasRequestedBefore(permission: String): Boolean = prefs.getBoolean(permission, false)

    override fun markRequested(permission: String) {
        prefs.edit { putBoolean(permission, true) }
    }

    private companion object {
        const val PREFS_NAME = "ligaya_permission_request_history"
    }
}
