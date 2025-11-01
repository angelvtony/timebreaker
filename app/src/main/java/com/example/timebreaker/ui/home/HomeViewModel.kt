package com.example.timebreaker.ui.home

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import androidx.lifecycle.*
import com.example.timebreaker.ui.data.BreakTimerService
import com.example.timebreaker.ui.data.DatabaseProvider
import com.example.timebreaker.ui.data.PrefsHelper
import com.example.timebreaker.ui.data.WorkTimerService
import com.example.timebreaker.ui.data.entities.WorkSession
import com.example.timebreaker.ui.data.repositories.WorkRepository
import kotlinx.coroutines.*
import java.lang.Long.max
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.*


@RequiresApi(Build.VERSION_CODES.O) // Add this if not present
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentTime = MutableLiveData<String>()
    val currentTime: LiveData<String> = _currentTime

    private val _timeWorked = MutableLiveData("--:--:--")
    val timeWorked: LiveData<String> = _timeWorked

    private val _timeLeft = MutableLiveData("08:00:00")
    val timeLeft: LiveData<String> = _timeLeft

    private val _breakTime = MutableLiveData("--:--:--")
    val breakTime: LiveData<String> = _breakTime

    private val _leavingTime = MutableLiveData("--:--")
    val leavingTime: LiveData<String> = _leavingTime

    private val _isClockedIn = MutableLiveData(false)
    val isClockedIn: LiveData<Boolean> = _isClockedIn

    private val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
    private val shortTimeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // --- State variables ---
    private var clockInTime: Long = 0L
    private var startWorkTime: Long = 0L // The System.currentTimeMillis() when work *started*
    private var breakStartTime: Long = 0L // The System.currentTimeMillis() when break *started*
    private var totalWorkedMillis: Long = 0L // The total duration worked
    private var totalBreakMillis: Long = 0L // The total duration on break
    private var totalShiftMillis: Long = 8 * 3600 * 1000L // default 8 hours

    private val dao = DatabaseProvider.getDatabase(application).workSessionDao()
    private val repository = WorkRepository(dao)
    val allSessions: LiveData<List<WorkSession>> = repository.getAllSessions()

    // --- BroadcastReceivers ---

    private val workTimerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WorkTimerService.ACTION_WORK_TIMER_UPDATE) {
                val totalWorked = intent.getLongExtra("totalWorked", 0L)
                val timeLeft = intent.getLongExtra("timeLeft", 0L)
                val leavingTime = intent.getLongExtra("leavingTime", 0L)

                totalWorkedMillis = totalWorked // Update local value

                _timeWorked.postValue(formatDuration(totalWorked))
                _timeLeft.postValue(formatDuration(timeLeft))
                if (leavingTime > 0L) {
                    _leavingTime.postValue(shortTimeFormat.format(Date(leavingTime)))
                }
            }
        }
    }

    private val breakTimerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BreakTimerService.ACTION_BREAK_TIMER_UPDATE) {
                val totalBreak = intent.getLongExtra("totalBreak", 0L)
                val leavingTime = intent.getLongExtra("leavingTime", 0L)

                totalBreakMillis = totalBreak // Update local value

                _breakTime.postValue(formatDuration(totalBreak))
                if (leavingTime > 0L) {
                    _leavingTime.postValue(shortTimeFormat.format(Date(leavingTime)))
                }
            }
        }
    }

    // --- REMOVED stateChangeReceiver ---

    init {
        loadFromPrefs()
        startClock()

        val app = getApplication<Application>()
        app.registerReceiver(
            workTimerReceiver,
            IntentFilter(WorkTimerService.ACTION_WORK_TIMER_UPDATE),
            AppCompatActivity.RECEIVER_NOT_EXPORTED
        )
        app.registerReceiver(
            breakTimerReceiver,
            IntentFilter(BreakTimerService.ACTION_BREAK_TIMER_UPDATE),
            AppCompatActivity.RECEIVER_NOT_EXPORTED
        )
        // --- REMOVED stateChangeReceiver registration ---
    }

    private fun loadFromPrefs() {
        val context = getApplication<Application>()
        clockInTime = PrefsHelper.getClockIn(context)
        totalWorkedMillis = PrefsHelper.getTotalWorked(context)
        totalBreakMillis = PrefsHelper.getTotalBreak(context)
        totalShiftMillis = PrefsHelper.getShiftDuration(context)
        breakStartTime = PrefsHelper.getBreakStart(context)
        startWorkTime = PrefsHelper.getWorkStartTime(context)
        val isClockedIn = PrefsHelper.getIsClockedIn(context)

        _isClockedIn.value = isClockedIn
        updateUI()
    }

    private fun startClock() {
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                _currentTime.postValue(timeFormat.format(Date()))
                delay(1000)
            }
        }
    }

    // --- REVERTED to internal logic (NO ClockManager) ---
    fun clockIn() {
        if (_isClockedIn.value == true) return // Prevent double-tap
        _isClockedIn.value = true

        val context = getApplication<Application>()

        // This is the first clock-in of the day
        if (PrefsHelper.getClockIn(context) == 0L) {
            PrefsHelper.saveClockIn(context, System.currentTimeMillis())
            clockInTime = PrefsHelper.getClockIn(context) // update local var
        }

        // Stop break service
        BreakTimerService.stopService(context)
        PrefsHelper.saveBreakStart(context, 0L) // Clear break start time

        // Start work timer service
        val totalWorked = PrefsHelper.getTotalWorked(context) // Get latest total
        startWorkTime = System.currentTimeMillis()
        PrefsHelper.saveWorkStartTime(context, startWorkTime)
        PrefsHelper.saveIsClockedIn(context, true)

        WorkTimerService.startService(context, startWorkTime, totalWorked)
    }

    // --- REVERTED to internal logic (NO ClockManager) ---
    fun clockOut() {
        if (_isClockedIn.value == false) return
        _isClockedIn.value = false

        val context = getApplication<Application>()

        // Stop work timer service
        WorkTimerService.stopService(context)
        PrefsHelper.saveWorkStartTime(context, 0L) // Clear work start time
        PrefsHelper.saveIsClockedIn(context, false)

        // Start break timer service
        val totalBreak = PrefsHelper.getTotalBreak(context) // Get latest total
        breakStartTime = System.currentTimeMillis()
        PrefsHelper.saveBreakStart(context, breakStartTime)

        BreakTimerService.startService(context, breakStartTime, totalBreak)
    }

    fun endDay() {
        if (clockInTime != 0L) {
            val date = sdfDate.format(Date(clockInTime))
            val clockInStr = shortTimeFormat.format(Date(clockInTime))
            val clockOutStr = shortTimeFormat.format(Date(System.currentTimeMillis()))
            val leavingStr = clockOutStr

            val context = getApplication<Application>()

            // Load the FINAL totals from Prefs.
            // The services have already been saving the correct values.
            val finalTotalWorked = PrefsHelper.getTotalWorked(context)
            val finalTotalBreak = PrefsHelper.getTotalBreak(context)

            viewModelScope.launch(Dispatchers.IO) {
                repository.insert(
                    WorkSession(
                        date = date,
                        clockInTime = clockInStr,
                        clockOutTime = clockOutStr,
                        totalWorked = finalTotalWorked,
                        totalBreak = finalTotalBreak,
                        shiftDuration = totalShiftMillis,
                        leavingTime = leavingStr
                    )
                )
            }
        }

        val context = getApplication<Application>()
        WorkTimerService.stopService(context)
        BreakTimerService.stopService(context)
        PrefsHelper.clear(context)

        // Reset all local variables and LiveData
        clockInTime = 0L
        startWorkTime = 0L
        breakStartTime = 0L
        totalWorkedMillis = 0L
        totalBreakMillis = 0L
        _isClockedIn.value = false
        _timeWorked.value = "--:--:--"
        _breakTime.value = "--:--:--"
        _timeLeft.value = formatDuration(totalShiftMillis)
        _leavingTime.value = "--:--"
    }

    private fun updateUI() {
        _timeWorked.value = if (totalWorkedMillis > 0) formatDuration(totalWorkedMillis) else "--:--:--"
        _breakTime.value = if (totalBreakMillis > 0) formatDuration(totalBreakMillis) else "--:--:--"
        _timeLeft.value = formatDuration(max(totalShiftMillis - totalWorkedMillis, 0L))

        if (clockInTime > 0L) {
            val leavingMillis = clockInTime + totalShiftMillis + totalBreakMillis
            _leavingTime.value = shortTimeFormat.format(Date(leavingMillis))
        } else {
            _leavingTime.value = "--:--"
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun setManualShiftTime(hours: Int, minutes: Int) {
        totalShiftMillis = (hours * 3600 + minutes * 60) * 1000L
        PrefsHelper.saveShiftDuration(getApplication(), totalShiftMillis)

        // Reload current state from Prefs before restarting services
        val context = getApplication<Application>()
        val currentTotalWorked = PrefsHelper.getTotalWorked(context)
        val currentWorkStart = PrefsHelper.getWorkStartTime(context)
        val currentTotalBreak = PrefsHelper.getTotalBreak(context)
        val currentBreakStart = PrefsHelper.getBreakStart(context)

        if (_isClockedIn.value == true) {
            WorkTimerService.startService(getApplication(), currentWorkStart, currentTotalWorked)
        } else if (currentBreakStart != 0L) { // Check if on break
            BreakTimerService.startService(getApplication(), currentBreakStart, currentTotalBreak)
        }
        updateUI()
    }

    fun setManualClockTimes(clockInStr: String?, clockOutStr: String?) {
        if (clockInStr == null || clockOutStr == null) return

        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        try {
            val clockInDate = sdf.parse(clockInStr) ?: return
            val clockOutDate = sdf.parse(clockOutStr) ?: return

            var worked = clockOutDate.time - clockInDate.time
            if (worked < 0) {
                worked += 24 * 3600 * 1000 // Handle overnight
            }

            totalWorkedMillis = worked
            PrefsHelper.saveTotalWorked(getApplication(), totalWorkedMillis)
            totalBreakMillis = 0L
            PrefsHelper.saveTotalBreak(getApplication(), 0L)
            clockInTime = clockInDate.time
            PrefsHelper.saveClockIn(getApplication(), clockInTime)

            updateUI()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    override fun onCleared() {
        super.onCleared()
        val app = getApplication<Application>()
        try {
            app.unregisterReceiver(workTimerReceiver)
            app.unregisterReceiver(breakTimerReceiver)
            // --- REMOVED stateChangeReceiver unregistration ---
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}