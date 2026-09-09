package com.ligaya.core.telephony

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The real dial action: hands off to the system dialer via ACTION_DIAL, pre-filled with the
 * Philippines Unified 911 number. ACTION_DIAL (not ACTION_CALL) is the confirmed approach —
 * Android does not allow a third-party app to silently place a call to an emergency number
 * regardless of granted permissions, so the user must still tap Call themselves in the dialer
 * that opens. No CALL_PHONE or any other dangerous permission is needed for this reason.
 *
 * Checks resolveActivity first rather than letting ActivityNotFoundException surface — the
 * architecture diagram's "911 action available?" fork is exactly this case (see
 * Unified911FlowCoordinator's doc comment for why it still reports as CallFailed rather than a
 * separate state).
 */
class IntentUnified911DialAction(
    private val context: Context,
) : Unified911DialAction {

    override fun dial(): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$EMERGENCY_NUMBER")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        check(intent.resolveActivity(context.packageManager) != null) {
            "No activity can handle ACTION_DIAL"
        }
        context.startActivity(intent)
    }

    companion object {
        /** The Philippines Unified 911 number (section 15). */
        const val EMERGENCY_NUMBER = "911"
    }
}
