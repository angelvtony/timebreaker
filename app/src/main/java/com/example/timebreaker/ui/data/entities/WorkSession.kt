package com.example.timebreaker.ui.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_sessions")
data class WorkSession(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String,
    val clockInTime: String?,
    val clockOutTime: String?,
    val totalWorked: Long,
    val totalBreak: Long,
    val shiftDuration: Long,
    val leavingTime: String?
)

