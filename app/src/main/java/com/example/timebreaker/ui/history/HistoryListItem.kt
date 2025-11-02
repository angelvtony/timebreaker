package com.example.timebreaker.ui.history

import com.example.timebreaker.ui.data.entities.WorkSession

sealed class HistoryListItem {
    data class DateHeader(val date: String) : HistoryListItem()
    data class SessionItem(val session: WorkSession) : HistoryListItem()
}
