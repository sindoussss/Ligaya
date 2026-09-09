package com.ligaya.core.permissions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPrefsPermissionRequestHistoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val permission = "android.permission.POST_NOTIFICATIONS"

    @Test
    fun requestHistoryIsFalseUntilMarkedThenTrue() {
        context.getSharedPreferences("ligaya_permission_request_history", Context.MODE_PRIVATE)
            .edit().clear().commit()

        val history = SharedPrefsPermissionRequestHistory(context)
        assertFalse(history.hasRequestedBefore(permission))

        history.markRequested(permission)
        assertTrue(history.hasRequestedBefore(permission))
    }

    @Test
    fun requestHistorySurvivesAFreshInstanceReadingTheSameSharedPreferences() {
        context.getSharedPreferences("ligaya_permission_request_history", Context.MODE_PRIVATE)
            .edit().clear().commit()

        SharedPrefsPermissionRequestHistory(context).markRequested(permission)

        // A brand-new instance — simulates a fresh process reading persisted state, same
        // pattern used for Room in Step 3 and Firebase Auth session persistence in Step 4.
        val freshInstance = SharedPrefsPermissionRequestHistory(context)
        assertTrue(freshInstance.hasRequestedBefore(permission))
    }
}
