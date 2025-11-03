package com.example.timebreaker.ui.history

import android.app.AlertDialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.timebreaker.R
import com.example.timebreaker.databinding.FragmentHistoryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoryViewModel by viewModels()

    private lateinit var adapter: WorkSessionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = WorkSessionAdapter()
        binding.recyclerViewSessions.adapter = adapter
        binding.recyclerViewSessions.layoutManager = LinearLayoutManager(requireContext())

        viewModel.allSessions.observe(viewLifecycleOwner) { sessions ->
            val groupedList = mutableListOf<HistoryListItem>()
            val groupedByDate = sessions.groupBy { it.date }

            groupedByDate.forEach { (date, sessionList) ->
                groupedList.add(HistoryListItem.DateHeader(date))
                sessionList.forEach { session ->
                    groupedList.add(HistoryListItem.SessionItem(session))
                }
            }

            adapter.submitList(groupedList)
        }

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val item = adapter.currentList[viewHolder.adapterPosition]
                return if (item is HistoryListItem.DateHeader) 0 else super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = adapter.currentList[position]

                if (item is HistoryListItem.SessionItem) {
                    val session = item.session
                    MaterialAlertDialogBuilder(requireContext(), R.style.IOSDialogStyle)
                        .setTitle("Delete Session?")
                        .setMessage("Are you sure you want to delete this work session?")
                        .setCancelable(true)
                        .setPositiveButton("Delete") { _, _ ->
                            viewModel.deleteSession(session)
                        }
                        .setNegativeButton("Cancel") { _, _ ->
                            adapter.notifyItemChanged(position)
                        }
                        .show()
                } else {
                    adapter.notifyItemChanged(position)
                }
            }
        })

        itemTouchHelper.attachToRecyclerView(binding.recyclerViewSessions)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
