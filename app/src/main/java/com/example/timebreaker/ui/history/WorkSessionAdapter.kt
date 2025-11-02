package com.example.timebreaker.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.timebreaker.databinding.ItemWorkSessionBinding
import com.example.timebreaker.ui.data.entities.WorkSession

class WorkSessionAdapter :
    ListAdapter<WorkSession, WorkSessionAdapter.WorkSessionViewHolder>(DiffCallback()) {

    inner class WorkSessionViewHolder(val binding: ItemWorkSessionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(session: WorkSession) {
            binding.tvDate.text = session.date
            binding.tvClockIn.text = "In: ${session.clockInTime ?: "--:--"}"
            binding.tvClockOut.text = "Out: ${session.clockOutTime ?: "--:--"}"
            binding.tvWorked.text = "Worked: ${formatDuration(session.totalWorked)}"
            binding.tvBreak.text = "Break: ${formatDuration(session.totalBreak)}"
            binding.tvLeaving.text = "Leaving: ${session.leavingTime ?: "--:--"}"
        }

        private fun formatDuration(millis: Long): String {
            val totalSeconds = millis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkSessionViewHolder {
        val binding = ItemWorkSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WorkSessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WorkSessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<WorkSession>() {
        override fun areItemsTheSame(oldItem: WorkSession, newItem: WorkSession): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: WorkSession, newItem: WorkSession): Boolean =
            oldItem == newItem
    }
}
