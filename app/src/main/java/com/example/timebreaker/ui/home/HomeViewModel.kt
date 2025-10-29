package com.example.timebreaker.ui.home


import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.*
import java.lang.Long.max
import java.text.SimpleDateFormat
import java.util.*

class HomeViewModel : ViewModel() {

    private val _currentTime = MutableLiveData<String>()
    val currentTime: LiveData<String> = _currentTime

    private val _timeWorked = MutableLiveData("--:-- --")
    val timeWorked: LiveData<String> = _timeWorked

    private val _timeLeft = MutableLiveData("08:00:00")
    val timeLeft: LiveData<String> = _timeLeft

    private val _breakTime = MutableLiveData("--:-- --")
    val breakTime: LiveData<String> = _breakTime

    private val _leavingTime = MutableLiveData("--:-- --")
    val leavingTime: LiveData<String> = _leavingTime

    private val _isClockedIn = MutableLiveData(false)
    val isClockedIn: LiveData<Boolean> = _isClockedIn

    private val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
    private val shortTimeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    private var workJob: Job? = null
    private var breakJob: Job? = null

    private var startWorkTime: Long = 0L
    private var totalWorkedMillis: Long = 0L
    private var totalBreakMillis: Long = 0L
    private var breakStartTime: Long = 0L
    private var clockInTime: Long = 0L

    private var totalShiftMillis = 8 * 60 * 60 * 1000L

    init {
        startClock()
    }

    private fun startClock() {
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                _currentTime.postValue(timeFormat.format(Date()))
                delay(1000)
            }
        }
    }

    fun setManualShiftTime(hours: Int, minutes: Int) {
        totalShiftMillis = (hours * 60 * 60 + minutes * 60) * 1000L
        _timeLeft.value = formatDuration(totalShiftMillis)
        updateLeavingTime()
    }

    @SuppressLint("SimpleDateFormat")
    fun setManualClockTimes(clockIn: String, clockOut: String) {
        try {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val inTime = sdf.parse(clockIn)
            val outTime = sdf.parse(clockOut)

            if (inTime != null && outTime != null) {
                val workedMillis = outTime.time - inTime.time
                if (workedMillis > 0) {
                    _timeWorked.value = formatDuration(workedMillis)
                    _timeLeft.value = formatDuration(max(totalShiftMillis - workedMillis, 0))
                    _isClockedIn.value = false
                    _leavingTime.value = clockOut
                } else {
                    _timeWorked.value = "--:-- --"
                }
            }
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Invalid manual clock input: ${e.message}")
        }
    }


    fun clockIn() {
        if (_isClockedIn.value == true) return
        _isClockedIn.value = true

        if (clockInTime == 0L) {
            clockInTime = System.currentTimeMillis()
            updateLeavingTime()
        }

        breakJob?.cancel()
        if (breakStartTime != 0L) {
            totalBreakMillis += System.currentTimeMillis() - breakStartTime
            breakStartTime = 0L
            updateLeavingTime()
        }

        startWorkTime = System.currentTimeMillis()

        workJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val workedNow = System.currentTimeMillis() - startWorkTime
                val workedTotal = totalWorkedMillis + workedNow
                _timeWorked.postValue(formatDuration(workedTotal))

                val remaining = totalShiftMillis - workedTotal
                if (remaining >= 0) {
                    _timeLeft.postValue(formatDuration(remaining))
                } else {
                    _timeLeft.postValue("00:00:00")
                }

                delay(1000)
            }
        }
    }

    fun clockOut() {
        if (_isClockedIn.value == false) return
        _isClockedIn.value = false

        workJob?.cancel()
        totalWorkedMillis += System.currentTimeMillis() - startWorkTime

        breakStartTime = System.currentTimeMillis()
        breakJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val currentBreak = System.currentTimeMillis() - breakStartTime
                val total = totalBreakMillis + currentBreak
                _breakTime.postValue(formatDuration(total))
                updateLeavingTime(total)
                delay(1000)
            }
        }
    }

    fun endDay() {
        workJob?.cancel()
        breakJob?.cancel()

        totalWorkedMillis = 0L
        totalBreakMillis = 0L
        startWorkTime = 0L
        breakStartTime = 0L
        clockInTime = 0L

        _isClockedIn.value = false
        _timeWorked.value = "--:-- --"
        _breakTime.value = "--:-- --"
        _timeLeft.value = "08:00:00"
        _leavingTime.value = "--:-- --"
    }

    private fun updateLeavingTime(currentBreakMillis: Long = totalBreakMillis) {
        if (clockInTime == 0L) return
        val leavingMillis = clockInTime + totalShiftMillis + currentBreakMillis
        val leavingDate = Date(leavingMillis)
        _leavingTime.postValue(shortTimeFormat.format(leavingDate))
    }

    @SuppressLint("DefaultLocale")
    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        workJob?.cancel()
        breakJob?.cancel()
    }
}

