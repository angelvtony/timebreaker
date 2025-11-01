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
import androidx.core.content.ContextCompat
import com.example.timebreaker.R
import kotlinx.coroutines.*
import java.lang.Long.max
import java.text.SimpleDateFormat
import java.util.*
import android.app.*
import kotlinx.coroutines.*
import java.util.*

class WorkTimerService : Service() {

    companion object {
        const val ACTION_WORK_TIMER_UPDATE = "WORK_TIMER_UPDATE"

        fun startService(context: Context, workStartTime: Long, totalWorkedAtStart: Long) {
            val intent = Intent(context, WorkTimerService::class.java).apply {
                putExtra("WORK_START_TIME", workStartTime)
                putExtra("TOTAL_WORKED_AT_START", totalWorkedAtStart)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WorkTimerService::class.java)
            context.stopService(intent)
        }
    }

    private var timerJob: Job? = null
    private val channelId = "work_timer_channel"
    private val notificationId = 1 // Must be different from BreakTimerService

    // We need these to calculate leaving time
    private var clockInTime: Long = 0L
    private var totalBreak: Long = 0L
    private var shiftDuration: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Start with a default notification
        startForeground(notificationId, buildNotification("00:00:00"))

        // Load values that don't change during the session
        clockInTime = PrefsHelper.getClockIn(this)
        totalBreak = PrefsHelper.getTotalBreak(this)
        shiftDuration = PrefsHelper.getShiftDuration(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val workStartTime = intent?.getLongExtra("WORK_START_TIME", 0L) ?: 0L
        val totalWorkedAtStart = intent?.getLongExtra("TOTAL_WORKED_AT_START", 0L) ?: 0L

        // (Re)load these in case they changed (e.g., user set manual shift)
        clockInTime = PrefsHelper.getClockIn(this)
        totalBreak = PrefsHelper.getTotalBreak(this)
        shiftDuration = PrefsHelper.getShiftDuration(this)

        startTimer(workStartTime, totalWorkedAtStart)

        // Ensures the service restarts if killed
        return START_STICKY
    }

    private fun startTimer(workStartTime: Long, totalWorkedAtStart: Long) {
        if (workStartTime == 0L) {
            stopSelf() // Invalid start, stop
            return
        }

        timerJob?.cancel()
        timerJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                // 1. Calculate new values
                val workedNow = System.currentTimeMillis() - workStartTime
                val totalWorked = totalWorkedAtStart + workedNow
                val timeLeft = max(shiftDuration - totalWorked, 0L)
                val leavingTime = clockInTime + shiftDuration + totalBreak

                // 2. Save the new total
                PrefsHelper.saveTotalWorked(applicationContext, totalWorked)

                // 3. Broadcast all data to the UI
                Intent(ACTION_WORK_TIMER_UPDATE).apply {
                    putExtra("totalWorked", totalWorked)
                    putExtra("timeLeft", timeLeft)
                    putExtra("leavingTime", leavingTime)
                    // Add this line to make the broadcast explicit
                    setPackage(applicationContext.packageName)
                }.also { sendBroadcast(it) }

                // 4. Update the foreground notification
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(notificationId, buildNotification(formatDuration(totalWorked)))

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
            .setContentTitle("Clocked In")
            .setContentText("Time Worked: $time")
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
            val channel = NotificationChannel(channelId, "Work Timer", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Notification for active work timer"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}