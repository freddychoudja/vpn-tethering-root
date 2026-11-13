package com.freddychoudja.vpntethering.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.freddychoudja.vpntethering.MainActivity
import com.freddychoudja.vpntethering.R
import com.freddychoudja.vpntethering.VpnTetheringApp
import com.freddychoudja.vpntethering.root.RootNetworkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TetheringForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val networkManager = RootNetworkManager()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startAsForeground(
                    intent.getStringExtra(EXTRA_STATUS)
                        ?: getString(R.string.notification_text)
                )
                scope.launch {
                    val result = networkManager.enableTethering()
                    broadcastStatus(result.success, result.message)
                    if (result.success) {
                        updateNotification(result.message)
                    } else {
                        stopSelfSafely()
                    }
                }
            }

            ACTION_STOP -> {
                scope.launch {
                    val result = networkManager.disableTethering()
                    broadcastStatus(false, result.message)
                    stopSelfSafely()
                }
            }

            else -> stopSelfSafely()
        }
        return START_STICKY
    }

    private fun startAsForeground(content: String) {
        val notification = buildNotification(content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun buildNotification(content: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TetheringForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VpnTetheringApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Désactiver", stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun broadcastStatus(active: Boolean, message: String) {
        val intent = Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_ACTIVE, active)
            putExtra(EXTRA_STATUS, message)
        }
        sendBroadcast(intent)
    }

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.freddychoudja.vpntethering.action.START"
        const val ACTION_STOP = "com.freddychoudja.vpntethering.action.STOP"
        const val ACTION_STATUS = "com.freddychoudja.vpntethering.action.STATUS"
        const val EXTRA_ACTIVE = "extra_active"
        const val EXTRA_STATUS = "extra_status"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context, statusHint: String? = null) {
            val intent = Intent(context, TetheringForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STATUS, statusHint)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TetheringForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
