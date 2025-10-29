package com.example.timebreaker.ui.data

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.timebreaker.ui.MainActivity
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class WorkTimerService : Service() {

    private val CHANNEL_ID = "work_timer_channel"
    private var job: Job? = null

    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var startTime = 0L

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START -> startTimer()
            ACTION_STOP -> stopTimer()
        }

        return START_STICKY
    }

    private fun startTimer() {
        if (job != null) return
        startTime = System.currentTimeMillis()

        startForeground(NOTIFICATION_ID, createNotification("Working...", "00:00:00"))

        job = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val elapsedMillis = System.currentTimeMillis() - startTime
                val time = formatDuration(elapsedMillis)
                val notification = createNotification("Working...", time)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
                delay(1000)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun stopTimer() {
        job?.cancel()
        job = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(title: String, time: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Time Worked: $time")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Work Timer",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("DefaultLocale")
    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val NOTIFICATION_ID = 101

        @RequiresApi(Build.VERSION_CODES.O)
        fun startService(context: Context) {
            val intent = Intent(context, WorkTimerService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WorkTimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}