package com.example.timebreaker.ui.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.timebreaker.ui.data.entities.WorkSession

@Dao
interface WorkSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkSession)

    @Update
    suspend fun updateSession(session: WorkSession)

    @Delete
    suspend fun deleteSession(session: WorkSession)

    @Query("SELECT * FROM work_sessions ORDER BY id DESC")
    fun getAllSessions(): LiveData<List<WorkSession>>

    @Query("SELECT * FROM work_sessions WHERE date = :date LIMIT 1")
    suspend fun getSessionByDate(date: String): WorkSession?
}
