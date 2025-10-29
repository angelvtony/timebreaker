package com.example.timebreaker.ui.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import com.example.timebreaker.R
import com.example.timebreaker.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BreakTimerService : Service() {

    companion object {
        private const val CHANNEL_ID = "break_timer_channel"
        private const val NOTIFICATION_ID = 2

        fun startService(context: Context) {
            val intent = Intent(context, BreakTimerService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BreakTimerService::class.java)
            context.stopService(intent)
        }
    }

    private var breakJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification("On Break", "00:00:00")
        )
        startBreakTimer()
    }

    private fun startBreakTimer() {
        breakJob?.cancel()

        val totalBreak = PrefsHelper.getTotalBreak(this)
        val breakStart = PrefsHelper.getBreakStart(this)
        if (breakStart == 0L) return

        val startTime = System.currentTimeMillis() - totalBreak

        breakJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val total = System.currentTimeMillis() - breakStart + totalBreak
                PrefsHelper.saveTotalBreak(applicationContext, total)

                val formatted = formatDuration(total)

                // Update notification every few seconds
                if ((total / 1000) % 5 == 0L) {
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(
                        NOTIFICATION_ID,
                        buildNotification("On Break", formatted)
                    )
                }

                delay(1000)
            }
        }
    }

    private fun buildNotification(title: String, time: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Break Time: $time")
            .setSmallIcon(R.drawable.ic_settings)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Break Timer",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onDestroy() {
        breakJob?.cancel()
        super.onDestroy()
    }
}