package com.example.timebreaker.ui.home

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.example.timebreaker.ui.data.DatabaseProvider
import com.example.timebreaker.ui.data.entities.WorkSession
import com.example.timebreaker.ui.data.repositories.WorkRepository
import kotlinx.coroutines.*
import java.lang.Long.max
import java.text.SimpleDateFormat
import java.util.*

class HomeViewModel(application: Application) : AndroidViewModel(application) {

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

    private val dao = DatabaseProvider.getDatabase(application).workSessionDao()
    private val repository = WorkRepository(dao)
    private val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val allSessions: LiveData<List<WorkSession>> = repository.getAllSessions()


    private var totalShiftMillis = 8 * 60 * 60 * 1000L

    init {
        startClock()
        loadTodaySession()
    }

    private fun loadTodaySession() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = sdfDate.format(Date())
            val session = repository.getSessionByDate(today)
            session?.let {
                withContext(Dispatchers.Main) {
                    _isClockedIn.value = it.clockOutTime == null
                    _timeWorked.value = formatDuration(it.totalWorked)
                    _breakTime.value = formatDuration(it.totalBreak)
                    _timeLeft.value = formatDuration(
                        max(it.shiftDuration - it.totalWorked, 0L)
                    )
                    _leavingTime.value = it.leavingTime ?: "--:-- --"
                    clockInTime = timeToMillis(it.clockInTime)
                    totalWorkedMillis = it.totalWorked
                    totalBreakMillis = it.totalBreak
                }
            }
            if (_isClockedIn.value == true) {
                startWorkTime = System.currentTimeMillis() - totalWorkedMillis
                clockIn()
            }
        }
    }

    private fun timeToMillis(time: String?): Long {
        if (time.isNullOrEmpty()) return 0L
        return try {
            val parsed = shortTimeFormat.parse(time)
            val calendar = Calendar.getInstance()
            val now = calendar.time
            val diff = parsed!!.time - shortTimeFormat.parse(shortTimeFormat.format(now))!!.time
            now.time + diff
        } catch (e: Exception) {
            0L
        }
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

                    val date = sdfDate.format(Date())
                    viewModelScope.launch {
                        val session = WorkSession(
                            date = date,
                            clockInTime = clockIn,
                            clockOutTime = clockOut,
                            totalWorked = workedMillis,
                            totalBreak = totalBreakMillis,
                            shiftDuration = totalShiftMillis,
                            leavingTime = clockOut
                        )
                        repository.insert(session)
                    }
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

            val date = sdfDate.format(Date(clockInTime))
            viewModelScope.launch {
                val session = WorkSession(
                    date = date,
                    clockInTime = shortTimeFormat.format(Date(clockInTime)),
                    clockOutTime = null,
                    totalWorked = 0L,
                    totalBreak = 0L,
                    shiftDuration = totalShiftMillis,
                    leavingTime = _leavingTime.value
                )
                repository.insert(session)
            }
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
                if (remaining >= 0) _timeLeft.postValue(formatDuration(remaining))
                else _timeLeft.postValue("00:00:00")

                delay(1000)
            }
        }
    }

    fun clockOut() {
        if (_isClockedIn.value == false) return
        _isClockedIn.value = false

        workJob?.cancel()
        val endTime = System.currentTimeMillis()
        totalWorkedMillis += endTime - startWorkTime

        val date = sdfDate.format(Date(clockInTime))
        val clockInStr = shortTimeFormat.format(Date(clockInTime))
        val clockOutStr = shortTimeFormat.format(Date(endTime))
        val leavingStr = shortTimeFormat.format(Date(endTime))

        viewModelScope.launch {
            val session = WorkSession(
                date = date,
                clockInTime = clockInStr,
                clockOutTime = clockOutStr,
                totalWorked = totalWorkedMillis,
                totalBreak = totalBreakMillis,
                shiftDuration = totalShiftMillis,
                leavingTime = leavingStr
            )
            repository.insert(session)
        }

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

