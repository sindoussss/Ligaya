package com.ligaya.core.data.engine

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ligaya.core.data.LigayaDatabase
import com.ligaya.core.data.repository.RoomEmergencyStateSnapshotRepository
import com.ligaya.core.emergencyengine.EmergencyState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The real Android lifecycle vehicle for EMERGENCY_ACTIVE (LIGAYA_ARCHITECTURE_FINAL_VOICE.md
 * section 25; the "safety-critical, persisted state" engine of section 12 needed a concrete
 * host — flagged as an open platform question in the original architecture review).
 *
 * Lives in core-data, not app: it needs direct access to EmergencyState/the persisted snapshot,
 * and :app is architecturally forbidden from depending on :core-emergency-engine directly
 * (enforced by :checkModuleBoundaries) — it may only reach engine state through
 * :core-ui-state's presentation-mapped models. core-data is already Android-aware and already
 * depends on core-emergency-engine (Step 10), so it — not :app — is where this belongs. :app
 * only ever starts/stops this service by class reference (a plain Intent), never touching
 * engine types itself. This resolves the roadmap's own flagged ambiguity ("app or a dedicated
 * module") in favor of the option that doesn't require weakening that boundary rule.
 *
 * Self-managing, not externally told: rather than relying on whoever triggers an emergency to
 * remember to also start/stop this service in lockstep, the service observes the persisted
 * snapshot itself (EmergencyStateSnapshotRepository.observeCurrent(), already built in Step 3)
 * and starts/stops itself based on that ground truth. This means a service killed and restarted
 * by the OS (START_STICKY) correctly resumes or exits on its own just by re-reading persisted
 * state — no in-memory hand-off required, consistent with section 25's "never held only in
 * memory."
 *
 * Foreground service type: only LOCATION is used now. PHONE_CALL was in the roadmap's own
 * tentative list ("microphone/location/phoneCall as applicable") but is not actually applicable
 * — that type is for apps managing a live call via a telecom ConnectionService, which this app
 * does not do (the confirmed 911 approach is ACTION_DIAL, a one-tap handoff to the system
 * dialer, per the architecture review's Issue B). MICROPHONE is deferred, not omitted by
 * oversight: Android 14+ requires RECORD_AUDIO to already be granted at the exact moment
 * startForeground() is called with that type, and no step before the voice pipeline (21+)
 * requests that permission — declaring/using it now would either crash or force a premature,
 * disconnected permission prompt. SPECIAL_USE is the fallback when location permission isn't
 * granted, so this service can always start regardless of that permission's state, matching the
 * app's established graceful-degradation posture.
 */
class EmergencyForegroundService : Service() {

    private val database by lazy { LigayaDatabase.getInstance(applicationContext) }
    private val repository by lazy { RoomEmergencyStateSnapshotRepository(database.emergencyStateSnapshotDao()) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()

        // Android requires startForeground() within a few seconds of the startForegroundService()
        // call that created this instance (RemoteServiceException$ForegroundServiceDidNotStartInTimeException
        // otherwise) — it does not wait for us to first go read persisted state. So this must
        // happen synchronously here, unconditionally, before the async observation below ever
        // gets a chance to run. The caller (DefaultEmergencyController) only ever starts this
        // service right after persisting an open state, so it's always correct to foreground
        // immediately; the observation's job is purely to notice when the session later closes.
        startForegroundWithNotification()

        observationJob = serviceScope.launch {
            repository.observeCurrent().collect { entity ->
                val state = entity?.mainState?.let { raw -> runCatching { EmergencyState.valueOf(raw) }.getOrNull() }
                if (state == null || state !in SESSION_OPEN_STATES) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observationJob?.cancel()
        isRunning = false
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val type = if (hasLocationPermission) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun buildNotification(): android.app.Notification {
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Ligaya")
            .setContentText("An emergency is active. Tap to open Ligaya.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .apply { contentIntent?.let(::setContentIntent) }
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Active emergency",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Shown while Ligaya has an active emergency session."
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "ligaya_emergency_active"
        const val NOTIFICATION_ID = 1001

        /** Set true in onCreate, false in onDestroy. Exists so a caller (test or otherwise)
         *  can reliably confirm the service instance has actually been torn down, rather than
         *  guessing with a fixed delay after stopService() — Android does not make stopService()
         *  synchronous, and a test that proceeds (e.g. closing the database this instance still
         *  holds a lazy reference to) before onDestroy() truly completes races a real failure. */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** A session is "open" — and this service should be running — from EMERGENCY_ACTIVE
         *  through EMERGENCY_RESOLVED, matching PersistedEmergencyStateMachine's own subsystem-
         *  update window (Step 10): subsystems, and therefore the service hosting them, stay
         *  alive until the session is fully CLOSED, not just until the user marks safe. */
        private val SESSION_OPEN_STATES = setOf(
            EmergencyState.EMERGENCY_ACTIVE,
            EmergencyState.USER_MARKED_SAFE,
            EmergencyState.EMERGENCY_RESOLVED,
        )
    }
}
