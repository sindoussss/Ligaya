package com.ligaya.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The real FCM entry point (section 17's push channel, Step 18). Deliberately thin: both
 * callbacks immediately delegate to LigayaMessagingHandler, which is what's actually
 * unit-tested — Android itself constructs this class outside of test control, so keeping real
 * logic out of it is what makes any of this testable at all.
 */
class LigayaMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val handler by lazy {
        LigayaMessagingHandler(
            currentUserId = { FirebaseAuth.getInstance().currentUser?.uid },
            pushTokenRepository = FirestorePushTokenRepository(FirebaseFirestore.getInstance()),
            notificationEventRepository = FirestoreNotificationEventRepository(
                FirebaseFirestore.getInstance(),
                FirebaseFunctions.getInstance(),
            ),
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch { handler.handleNewToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        showNotification(message)
        serviceScope.launch { handler.handleMessageReceived(message.data) }
    }

    private fun showNotification(message: RemoteMessage) {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(NOTIFICATION_CHANNEL_ID, "Family alerts", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(message.notification?.title ?: "Ligaya emergency alert")
            .setContentText(message.notification?.body ?: "A member of your Safety Circle needs you.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "ligaya_family_alert"
        private const val NOTIFICATION_ID = 2001
    }
}
