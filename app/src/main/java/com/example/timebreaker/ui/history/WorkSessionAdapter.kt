package com.example.timebreaker.ui.history

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.timebreaker.R
import com.example.timebreaker.databinding.ItemWorkSessionBinding
import com.example.timebreaker.ui.data.entities.WorkSession

class WorkSessionAdapter :
    ListAdapter<HistoryListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SESSION = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HistoryListItem.DateHeader -> TYPE_HEADER
            is HistoryListItem.SessionItem -> TYPE_SESSION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            else -> {
                val binding = ItemWorkSessionBinding.inflate(inflater, parent, false)
                WorkSessionViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HistoryListItem.DateHeader -> (holder as DateHeaderViewHolder).bind(item.date)
            is HistoryListItem.SessionItem -> (holder as WorkSessionViewHolder).bind(item.session)
        }
    }

    inner class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvDateHeader)
        fun bind(date: String) {
            tvDate.text = date
        }
    }

    inner class WorkSessionViewHolder(val binding: ItemWorkSessionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(session: WorkSession) {
            binding.tvClockIn.text = "In: ${session.clockInTime ?: "--:--"}"
            binding.tvClockOut.text = "Out: ${session.clockOutTime ?: "--:--"}"
            binding.tvWorked.text = "Worked: ${formatDuration(session.totalWorked)}"
            binding.tvBreak.text = "Break: ${formatDuration(session.totalBreak)}"
            binding.tvLeaving.text = "Leaving: ${session.leavingTime ?: "--:--"}"
        }

        @SuppressLint("DefaultLocale")
        private fun formatDuration(millis: Long): String {
            val totalSeconds = millis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<HistoryListItem>() {
        override fun areItemsTheSame(oldItem: HistoryListItem, newItem: HistoryListItem): Boolean {
            return when {
                oldItem is HistoryListItem.DateHeader && newItem is HistoryListItem.DateHeader ->
                    oldItem.date == newItem.date
                oldItem is HistoryListItem.SessionItem && newItem is HistoryListItem.SessionItem ->
                    oldItem.session.id == newItem.session.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: HistoryListItem, newItem: HistoryListItem): Boolean =
            oldItem == newItem
    }
}
