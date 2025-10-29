package com.example.timebreaker.ui.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.timebreaker.ui.data.dao.WorkSessionDao
import com.example.timebreaker.ui.data.entities.WorkSession

@Database(entities = [WorkSession::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workSessionDao(): WorkSessionDao
}
