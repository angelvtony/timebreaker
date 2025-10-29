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

class WorkTimerService : Service() {

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, WorkTimerService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WorkTimerService::class.java)
            context.stopService(intent)
        }
    }

    private var timerJob: Job? = null
    private val channelId = "work_timer_channel"
    private val notificationId = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notificationId, buildNotification("Timer running...","00:00:00"))

        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        val clockIn = PrefsHelper.getClockIn(this)
        val totalWorked = PrefsHelper.getTotalWorked(this)
        val totalBreak = PrefsHelper.getTotalBreak(this)
        val isClockedIn = PrefsHelper.getIsClockedIn(this)
        val shiftDuration = PrefsHelper.getShiftDuration(this)

        if (!isClockedIn) return

        val startTime = System.currentTimeMillis() - totalWorked

        timerJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val workedNow = System.currentTimeMillis() - startTime
                val total = totalWorked + workedNow
                PrefsHelper.saveTotalWorked(applicationContext, total)
                val remaining = max(shiftDuration - total, 0L)
                val intent = Intent("WORK_TIMER_UPDATE").apply {
                    putExtra("totalWorked", total)
                    putExtra("timeLeft", remaining)
                }
                sendBroadcast(intent)
                delay(1000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
    }

    private fun buildNotification(title: String, time: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText("Time Worked: $time")
            .setSmallIcon(R.drawable.ic_settings)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Work Timer", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}