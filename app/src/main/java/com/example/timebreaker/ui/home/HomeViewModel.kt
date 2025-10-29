package com.example.timebreaker.ui.home

import android.annotation.SuppressLint
import android.app.Application
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

    private var workJob: Job? = null
    private var breakJob: Job? = null

    private var clockInTime: Long = 0L
    private var startWorkTime: Long = 0L
    private var breakStartTime: Long = 0L
    private var totalWorkedMillis: Long = 0L
    private var totalBreakMillis: Long = 0L
    private var totalShiftMillis: Long = 8 * 3600 * 1000L // default 8 hours

    private val dao = DatabaseProvider.getDatabase(application).workSessionDao()
    private val repository = WorkRepository(dao)
    val allSessions: LiveData<List<WorkSession>> = repository.getAllSessions()

    init {
        loadFromPrefs()
        startClock()
    }

    private fun loadFromPrefs() {
        val context = getApplication<Application>()
        clockInTime = PrefsHelper.getClockIn(context)
        totalWorkedMillis = PrefsHelper.getTotalWorked(context)
        totalBreakMillis = PrefsHelper.getTotalBreak(context)
        totalShiftMillis = PrefsHelper.getShiftDuration(context)
        breakStartTime = PrefsHelper.getBreakStart(context)
        val isClockedIn = PrefsHelper.getIsClockedIn(context)

        _isClockedIn.value = isClockedIn

        if (isClockedIn) {
            startWorkTime = System.currentTimeMillis() - totalWorkedMillis
            startWorkTimer()
        } else if (breakStartTime != 0L) {
            startBreakTimer()
        }

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

    fun clockIn() {
        if (_isClockedIn.value == true) return
        _isClockedIn.value = true

        val context = getApplication<Application>()

        // Resume from break
        if (breakStartTime != 0L) {
            totalBreakMillis += System.currentTimeMillis() - breakStartTime
            breakStartTime = 0L
            PrefsHelper.saveTotalBreak(context, totalBreakMillis)
            PrefsHelper.saveBreakStart(context, 0L)
        }

        clockInTime = if (clockInTime == 0L) System.currentTimeMillis() else clockInTime
        PrefsHelper.saveClockIn(context, clockInTime)
        PrefsHelper.saveIsClockedIn(context, true)

        // Stop break timer service, start work timer service
        BreakTimerService.stopService(context)
        WorkTimerService.startService(context)

        startWorkTime = System.currentTimeMillis()
        startWorkTimer()
    }

    fun clockOut() {
        if (_isClockedIn.value == false) return
        _isClockedIn.value = false

        val context = getApplication<Application>()

        workJob?.cancel()
        totalWorkedMillis += System.currentTimeMillis() - startWorkTime
        PrefsHelper.saveTotalWorked(context, totalWorkedMillis)
        PrefsHelper.saveIsClockedIn(context, false)

        breakStartTime = System.currentTimeMillis()
        PrefsHelper.saveBreakStart(context, breakStartTime)

        // Stop work timer service, start break timer service
        WorkTimerService.stopService(context)
        BreakTimerService.startService(context)

        startBreakTimer()
    }

    private fun startWorkTimer() {
        workJob?.cancel()
        workJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val workedNow = System.currentTimeMillis() - startWorkTime
                val total = totalWorkedMillis + workedNow
                _timeWorked.postValue(formatDuration(total))
                _timeLeft.postValue(formatDuration(max(totalShiftMillis - total, 0L)))
                updateLeavingTime()
                PrefsHelper.saveTotalWorked(getApplication(), total)
                delay(1000)
            }
        }
    }

    private fun startBreakTimer() {
        breakJob?.cancel()
        breakJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val currentBreak = System.currentTimeMillis() - breakStartTime
                val total = totalBreakMillis + currentBreak
                _breakTime.postValue(formatDuration(total))

                if (currentBreak % 5000L < 1000L) {
                    PrefsHelper.saveTotalBreak(getApplication(), total)
                }

                updateLeavingTime(total)
                delay(1000)
            }
        }
    }

    private fun updateLeavingTime(currentBreakMillis: Long = totalBreakMillis) {
        if (clockInTime == 0L) return
        val leavingMillis = clockInTime + totalShiftMillis + currentBreakMillis
        _leavingTime.postValue(shortTimeFormat.format(Date(leavingMillis)))
    }

    fun endDay() {
        if (clockInTime != 0L) {
            val date = sdfDate.format(Date(clockInTime))
            val clockInStr = shortTimeFormat.format(Date(clockInTime))
            val clockOutStr = shortTimeFormat.format(Date(System.currentTimeMillis()))
            val leavingStr = clockOutStr

            viewModelScope.launch(Dispatchers.IO) {
                repository.insert(
                    WorkSession(
                        date = date,
                        clockInTime = clockInStr,
                        clockOutTime = clockOutStr,
                        totalWorked = totalWorkedMillis,
                        totalBreak = totalBreakMillis,
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
        workJob?.cancel()
        breakJob?.cancel()
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
        updateLeavingTime()
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

        val remaining = max(totalShiftMillis - totalWorkedMillis, 0L)
        _timeLeft.value = formatDuration(remaining)
        updateLeavingTime()
    }

    fun setManualClockTimes(clockInStr: String?, clockOutStr: String?) {
        if (clockInStr == null || clockOutStr == null) return

        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        try {
            val clockInDate = sdf.parse(clockInStr) ?: return
            val clockOutDate = sdf.parse(clockOutStr) ?: return

            totalWorkedMillis = clockOutDate.time - clockInDate.time
            PrefsHelper.saveTotalWorked(getApplication(), totalWorkedMillis)

            _timeWorked.value = formatDuration(totalWorkedMillis)

            clockInTime = clockInDate.time
            PrefsHelper.saveClockIn(getApplication(), clockInTime)

            updateLeavingTime()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }




    override fun onCleared() {
        super.onCleared()
        workJob?.cancel()
        breakJob?.cancel()
    }
}
