package com.example.timebreaker.ui.home

import androidx.fragment.app.viewModels
import com.example.timebreaker.databinding.FragmentHomeBinding
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment

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
        viewModel.overtime.observe(viewLifecycleOwner) {
            binding.tvOvertime.text = it
        }

        viewModel.isClockedIn.observe(viewLifecycleOwner) { isClockedIn ->
            if (isClockedIn) {
                binding.btnClockIn.visibility = View.GONE
                binding.btnClockOut.visibility = View.VISIBLE
            } else {
                binding.btnClockIn.visibility = View.VISIBLE
                binding.btnClockOut.visibility = View.GONE
            }
        }

        binding.btnClockIn.setOnClickListener {
            viewModel.clockIn()
            Toast.makeText(requireContext(), "Clocked in!", Toast.LENGTH_SHORT).show()
        }

        binding.btnClockOut.setOnClickListener {
            viewModel.clockOut()
            Toast.makeText(requireContext(), "Clocked out!", Toast.LENGTH_SHORT).show()
        }

        binding.btnEndDay.setOnClickListener {
            viewModel.endDay()
            Toast.makeText(requireContext(), "Day ended and reset!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
