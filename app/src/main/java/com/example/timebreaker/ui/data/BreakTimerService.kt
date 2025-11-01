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
        const val ACTION_BREAK_TIMER_UPDATE = "BREAK_TIMER_UPDATE"

        fun startService(context: Context, breakStartTime: Long, totalBreakAtStart: Long) {
            val intent = Intent(context, BreakTimerService::class.java).apply {
                putExtra("BREAK_START_TIME", breakStartTime)
                putExtra("TOTAL_BREAK_AT_START", totalBreakAtStart)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BreakTimerService::class.java)
            context.stopService(intent)
        }
    }

    private var timerJob: Job? = null
    private val channelId = "break_timer_channel"
    private val notificationId = 2 // Must be different from WorkTimerService

    // We need these to calculate leaving time
    private var clockInTime: Long = 0L
    private var shiftDuration: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Start with a default notification
        startForeground(notificationId, buildNotification("00:00:00"))

        // Load values that don't change during the session
        clockInTime = PrefsHelper.getClockIn(this)
        shiftDuration = PrefsHelper.getShiftDuration(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val breakStartTime = intent?.getLongExtra("BREAK_START_TIME", 0L) ?: 0L
        val totalBreakAtStart = intent?.getLongExtra("TOTAL_BREAK_AT_START", 0L) ?: 0L

        // Re-load in case they changed
        clockInTime = PrefsHelper.getClockIn(this)
        shiftDuration = PrefsHelper.getShiftDuration(this)

        startTimer(breakStartTime, totalBreakAtStart)

        // Ensures the service restarts if killed
        return START_STICKY
    }

    private fun startTimer(breakStartTime: Long, totalBreakAtStart: Long) {
        if (breakStartTime == 0L) {
            stopSelf() // Invalid start, stop
            return
        }

        timerJob?.cancel()
        timerJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                // 1. Calculate new values
                val breakNow = System.currentTimeMillis() - breakStartTime
                val totalBreak = totalBreakAtStart + breakNow
                val leavingTime = clockInTime + shiftDuration + totalBreak

                // 2. Save the new total
                PrefsHelper.saveTotalBreak(applicationContext, totalBreak)

                // 3. Broadcast all data to the UI
                Intent(ACTION_BREAK_TIMER_UPDATE).apply {
                    putExtra("totalBreak", totalBreak)
                    putExtra("leavingTime", leavingTime)
                    // --- THIS IS THE FIX ---
                    setPackage(applicationContext.packageName)
                    // -----------------------
                }.also { sendBroadcast(it) }

                // 4. Update the foreground notification
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(notificationId, buildNotification(formatDuration(totalBreak)))

                delay(1000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
    }

    private fun buildNotification(time: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java) // Assumes MainActivity
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("On Break")
            .setContentText("Break Time: $time")
            .setSmallIcon(R.drawable.ic_settings) // Replace with your app icon
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true) // Prevents sound/vibration every second
            .build()
    }

    // Utility function
    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Break Timer", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Notification for active break timer"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}