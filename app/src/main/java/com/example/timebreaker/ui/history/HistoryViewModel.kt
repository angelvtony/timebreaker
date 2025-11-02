package com.example.timebreaker.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.timebreaker.ui.data.DatabaseProvider
import com.example.timebreaker.ui.data.entities.WorkSession
import com.example.timebreaker.ui.data.repositories.WorkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = DatabaseProvider.getDatabase(application).workSessionDao()
    private val repository = WorkRepository(dao)

    val allSessions: LiveData<List<WorkSession>> = repository.getAllSessions()

    fun deleteSession(session: WorkSession) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSession(session)
        }
    }

}
