package com.example.timebreaker.ui.data.repositories

import com.example.timebreaker.ui.data.dao.WorkSessionDao
import com.example.timebreaker.ui.data.entities.WorkSession

class WorkRepository(private val dao: WorkSessionDao) {

    fun getAllSessions() = dao.getAllSessions()

    suspend fun insert(session: WorkSession) = dao.insertSession(session)

    suspend fun getSessionByDate(date: String) = dao.getSessionByDate(date)
}

