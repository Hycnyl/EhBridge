package com.hycnyl.ehbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.hycnyl.ehbridge.model.Progress
import com.hycnyl.ehbridge.ui.MainActivity

class BatchService : Service() {
    private val repository get() = (application as BridgeApp).repository
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastUpdate = 0L
    override fun onCreate() {
        super.onCreate()
        val localized = withBridgeLocale()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, localized.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(Progress()), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EhBridge:batch").apply { acquire(6 * 60 * 60 * 1000L) }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) { repository.cancel(); return START_NOT_STICKY }
        repository.execute(onProgress = { progress ->
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastUpdate > 1000 || progress.completed == progress.total) {
                lastUpdate = now
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(progress))
            }
        }, onFinished = {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        })
        return START_NOT_STICKY
    }
    private fun notification(progress: Progress): Notification {
        val localized = withBridgeLocale()
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(this, 1, Intent(this, BatchService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(localized.getString(R.string.notification_title))
            .setContentText(localized.getString(R.string.gallery_progress, progress.completed, progress.total))
            .setProgress(progress.total, progress.completed, progress.total == 0)
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
            .addAction(0, localized.getString(R.string.cancel), cancel).build()
    }
    override fun onTimeout(startId: Int, fgsType: Int) { repository.cancel(); stopSelf() }
    override fun onDestroy() {
        if (repository.state.value.phase == Phase.RUNNING) repository.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        private const val CHANNEL = "conversion"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_CANCEL = "com.hycnyl.ehbridge.CANCEL"
    }
}
