package com.example.timebreaker.ui.home


import android.annotation.SuppressLint
import androidx.lifecycle.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class HomeViewModel : ViewModel() {

    private val _currentTime = MutableLiveData<String>()
    val currentTime: LiveData<String> = _currentTime

    private val _timeWorked = MutableLiveData("00:00:00")
    val timeWorked: LiveData<String> = _timeWorked

    private val _timeLeft = MutableLiveData("08:00:00")
    val timeLeft: LiveData<String> = _timeLeft

    private val _breakTime = MutableLiveData("00:00:00")
    val breakTime: LiveData<String> = _breakTime

    private val _overtime = MutableLiveData("00:00:00")
    val overtime: LiveData<String> = _overtime

    private val _isClockedIn = MutableLiveData(false)
    val isClockedIn: LiveData<Boolean> = _isClockedIn

    private val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())

    // Tracking variables
    private var workJob: Job? = null
    private var breakJob: Job? = null
    private var startWorkTime: Long = 0L
    private var totalWorkedMillis: Long = 0L
    private var totalBreakMillis: Long = 0L
    private var breakStartTime: Long = 0L

    private val totalShiftMillis = 8 * 60 * 60 * 1000L // 8 hours

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

    /** Start Work **/
    fun clockIn() {
        if (_isClockedIn.value == true) return
        _isClockedIn.value = true

        // Stop break timer but preserve accumulated break time
        breakJob?.cancel()
        if (breakStartTime != 0L) {
            totalBreakMillis += System.currentTimeMillis() - breakStartTime
            breakStartTime = 0L
        }

        startWorkTime = System.currentTimeMillis()
        workJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val currentWorked = System.currentTimeMillis() - startWorkTime
                val workedTotal = totalWorkedMillis + currentWorked

                _timeWorked.postValue(formatDuration(workedTotal))

                val remaining = totalShiftMillis - workedTotal
                if (remaining >= 0) {
                    _timeLeft.postValue(formatDuration(remaining))
                    _overtime.postValue("00:00:00")
                } else {
                    _timeLeft.postValue("00:00:00")
                    _overtime.postValue(formatDuration(-remaining))
                }

                delay(1000)
            }
        }
    }

    /** Stop Work (Start Break) **/
    fun clockOut() {
        if (_isClockedIn.value == false) return
        _isClockedIn.value = false

        // Stop work timer, save worked time
        workJob?.cancel()
        totalWorkedMillis += System.currentTimeMillis() - startWorkTime

        // Start break timer (accumulates)
        breakStartTime = System.currentTimeMillis()
        breakJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val currentBreak = System.currentTimeMillis() - breakStartTime
                val total = totalBreakMillis + currentBreak
                _breakTime.postValue(formatDuration(total))
                delay(1000)
            }
        }
    }

    /** Reset Everything **/
    fun endDay() {
        workJob?.cancel()
        breakJob?.cancel()

        totalWorkedMillis = 0L
        totalBreakMillis = 0L
        startWorkTime = 0L
        breakStartTime = 0L

        _isClockedIn.value = false
        _timeWorked.value = "00:00:00"
        _breakTime.value = "00:00:00"
        _timeLeft.value = "08:00:00"
        _overtime.value = "00:00:00"
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

