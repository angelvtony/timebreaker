package com.example.timebreaker.ui.home

import android.annotation.SuppressLint
import android.os.Build
import androidx.fragment.app.viewModels
import com.example.timebreaker.databinding.FragmentHomeBinding
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.timebreaker.R
import com.example.timebreaker.ui.data.notification.WorkTimerService
import com.example.timebreaker.ui.history.HistoryFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_set_manual_time -> {
                    showSetTimeDialog()
                    true
                }
                R.id.action_manual_clock_entry -> {
                    showManualClockDialog()
                    true
                }
                R.id.action_dashboard_history -> {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, HistoryFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                else -> false
            }
        }

        viewModel.currentTime.observe(viewLifecycleOwner) {
            binding.tvCurrentTime.text = it
        }
        viewModel.timeWorked.observe(viewLifecycleOwner) {
            binding.tvTimeWorked.text = it
        }
        viewModel.timeLeft.observe(viewLifecycleOwner) {
            binding.tvTimeLeft.text = it
        }
        viewModel.breakTime.observe(viewLifecycleOwner) {
            binding.tvBreak.text = it
        }
        viewModel.leavingTime.observe(viewLifecycleOwner) {
            binding.tvOvertime.text = it
        }

        viewModel.allSessions.observe(viewLifecycleOwner) { sessions ->
            if (sessions.isNotEmpty()) {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val todaySessions = sessions.filter { it.date?.startsWith(today) == true }
                if (todaySessions.isNotEmpty()) {
                    val firstClockIn = todaySessions.minByOrNull { it.clockInTime ?: "23:59:59" }
                    binding.tvClockedIn.text = "Clocked in at ${formatTo12Hour(firstClockIn?.clockInTime)}"
                } else {
                    val lastSession = sessions.lastOrNull()
                    binding.tvClockedIn.text = "Last clock-in: ${lastSession?.clockInTime ?: "--:--"}"
                }
            } else {
                binding.tvClockedIn.text = "Not clocked in"
            }
        }

        viewModel.isClockedIn.observe(viewLifecycleOwner) { isClockedIn ->
            binding.btnClockIn.visibility = if (isClockedIn) View.GONE else View.VISIBLE
            binding.btnClockOut.visibility = if (isClockedIn) View.VISIBLE else View.GONE
        }


        binding.btnClockIn.setOnClickListener {
            viewModel.clockIn()
        }

        binding.btnClockOut.setOnClickListener {
            viewModel.clockOut()
        }

        binding.btnEndDay.setOnClickListener {
            viewModel.endDay()
            WorkTimerService.stopService(requireContext())
        }
    }

    private fun formatTo12Hour(time: String?): String {
        if (time.isNullOrEmpty()) return "--:--"
        return try {
            val cleaned = time.trim().uppercase(Locale.getDefault())
            val parsed = when {
                cleaned.contains("AM") || cleaned.contains("PM") -> SimpleDateFormat("hh:mm a", Locale.getDefault()).parse(cleaned)
                else -> SimpleDateFormat("HH:mm", Locale.getDefault()).parse(cleaned)
            }
            val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            outputFormat.format(parsed!!)
        } catch (e: Exception) {
            time
        }
    }

    @SuppressLint("MissingInflatedId")
    private fun showSetTimeDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_set_time, null)

        val etHours = dialogView.findViewById<EditText>(R.id.etHours)
        val etMinutes = dialogView.findViewById<EditText>(R.id.etMinutes)

        AlertDialog.Builder(requireContext())
            .setTitle("Set Work Duration")
            .setView(dialogView)
            .setPositiveButton("Set") { _, _ ->
                val hours = etHours.text.toString().toIntOrNull() ?: 8
                val minutes = etMinutes.text.toString().toIntOrNull() ?: 0
                viewModel.setManualShiftTime(hours, minutes)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("SetTextI18n")
    private fun showManualClockDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_manual_clock_entry, null)

        val tvClockIn = dialogView.findViewById<TextView>(R.id.tvClockIn)
        val tvClockOut = dialogView.findViewById<TextView>(R.id.tvClockOut)

        var clockInTime: String? = null
        var clockOutTime: String? = null

        @SuppressLint("DefaultLocale")
        fun showTimePicker(targetView: TextView, onTimeSelected: (String) -> Unit) {
            val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
                .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
                .setHour(9)
                .setMinute(0)
                .setTitleText("Select Time")
                .build()

            picker.addOnPositiveButtonClickListener {
                val hour = picker.hour
                val minute = picker.minute
                val time24 = String.format("%02d:%02d", hour, minute)
                val amPm = if (hour >= 12) "PM" else "AM"
                val hour12 = if (hour % 12 == 0) 12 else hour % 12
                val displayTime = String.format("%02d:%02d %s", hour12, minute, amPm)
                targetView.text = displayTime
                onTimeSelected(time24)
            }
            picker.show(parentFragmentManager, "timePicker")
        }

        tvClockIn.setOnClickListener {
            showTimePicker(tvClockIn) { selected ->
                clockInTime = selected
            }
        }

        tvClockOut.setOnClickListener {
            showTimePicker(tvClockOut) { selected ->
                clockOutTime = selected
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Manual Clock Entry")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                if (clockInTime != null && clockOutTime != null) viewModel.setManualClockTimes(clockInTime, clockOutTime)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
