package com.example.timebreaker.ui.home

import android.os.Build
import androidx.fragment.app.viewModels
import com.example.timebreaker.databinding.FragmentHomeBinding
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.example.timebreaker.ui.data.WorkTimerService

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

        viewModel.isClockedIn.observe(viewLifecycleOwner) { isClockedIn ->
            binding.btnClockIn.visibility = if (isClockedIn) View.GONE else View.VISIBLE
            binding.btnClockOut.visibility = if (isClockedIn) View.VISIBLE else View.GONE
        }

        binding.btnClockIn.setOnClickListener {
            viewModel.clockIn()
            WorkTimerService.startService(requireContext())
        }

        binding.btnClockOut.setOnClickListener {
            viewModel.clockOut()
            WorkTimerService.startService(requireContext())
        }

        binding.btnEndDay.setOnClickListener {
            viewModel.endDay()
            WorkTimerService.stopService(requireContext())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
