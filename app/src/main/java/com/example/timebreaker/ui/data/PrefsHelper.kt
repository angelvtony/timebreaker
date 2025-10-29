package com.example.timebreaker.ui.data

import android.content.Context
import android.content.SharedPreferences

object PrefsHelper {

    private const val PREF_NAME = "work_prefs"
    private const val KEY_CLOCK_IN = "clock_in"
    private const val KEY_TOTAL_WORKED = "total_worked"
    private const val KEY_TOTAL_BREAK = "total_break"
    private const val KEY_BREAK_START = "break_start"
    private const val KEY_IS_CLOCKED_IN = "is_clocked_in"
    private const val KEY_SHIFT_DURATION = "shift_duration"

    fun getClockIn(context: Context): Long =
        getPrefs(context).getLong(KEY_CLOCK_IN, 0L)

    fun saveClockIn(context: Context, value: Long) {
        getPrefs(context).edit().putLong(KEY_CLOCK_IN, value).apply()
    }

    fun getTotalWorked(context: Context): Long =
        getPrefs(context).getLong(KEY_TOTAL_WORKED, 0L)

    fun saveTotalWorked(context: Context, value: Long) {
        getPrefs(context).edit().putLong(KEY_TOTAL_WORKED, value).apply()
    }

    fun getTotalBreak(context: Context): Long =
        getPrefs(context).getLong(KEY_TOTAL_BREAK, 0L)

    fun saveTotalBreak(context: Context, value: Long) {
        getPrefs(context).edit().putLong(KEY_TOTAL_BREAK, value).apply()
    }

    fun getBreakStart(context: Context): Long =
        getPrefs(context).getLong(KEY_BREAK_START, 0L)

    fun saveBreakStart(context: Context, value: Long) {
        getPrefs(context).edit().putLong(KEY_BREAK_START, value).apply()
    }

    fun getIsClockedIn(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_IS_CLOCKED_IN, false)

    fun saveIsClockedIn(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_CLOCKED_IN, value).apply()
    }

    fun getShiftDuration(context: Context): Long =
        getPrefs(context).getLong(KEY_SHIFT_DURATION, 8 * 3600 * 1000L)

    fun saveShiftDuration(context: Context, value: Long) {
        getPrefs(context).edit().putLong(KEY_SHIFT_DURATION, value).apply()
    }

    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
