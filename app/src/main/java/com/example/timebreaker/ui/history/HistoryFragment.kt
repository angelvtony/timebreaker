package com.example.timebreaker.ui.history

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.timebreaker.R
import com.example.timebreaker.databinding.FragmentHistoryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

        adapter = WorkSessionAdapter().apply {
            onDateIconClick = { dateStr ->
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val date = sdf.parse(dateStr)
                    val cal = Calendar.getInstance()
                    cal.time = date!!

                    val year = cal.get(Calendar.YEAR)
                    val month = cal.get(Calendar.MONTH)

                    exportMonthPdfProfessional(year, month)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Unable to export PDF for this date", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.recyclerViewSessions.adapter = adapter
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

    @SuppressLint("MissingPermission")
    private fun exportMonthPdfProfessional(year: Int, month: Int) {
        val historyItems = getMonthHistory(year, month)
        if (historyItems.isEmpty()) {
            Toast.makeText(requireContext(), "No sessions for this month", Toast.LENGTH_SHORT).show()
            return
        }

        val pdfDocument = PdfDocument()
        val titlePaint = Paint().apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        val headerPaint = Paint().apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        val contentPaint = Paint().apply {
            textSize = 12f
        }

        val pageWidth = 595
        val pageHeight = 842
        var yPosition = 50
        var pageNumber = 1

        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val leftMargin = 40
        val columnGap = 10
        val colClockIn = leftMargin
        val colClockOut = colClockIn + 80 + columnGap
        val colWorked = colClockOut + 80 + columnGap
        val colBreak = colWorked + 80 + columnGap
        val colLeaving = colBreak + 80 + columnGap

        historyItems.forEach { item ->
            when (item) {
                is HistoryListItem.DateHeader -> {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val outputFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                    val date = inputFormat.parse(item.date)
                    val dateText = if (date != null) outputFormat.format(date) else item.date
                    canvas.drawText(dateText, leftMargin.toFloat(), yPosition.toFloat(), titlePaint)
                    yPosition += 25
                    canvas.drawText("Clock In", colClockIn.toFloat(), yPosition.toFloat(), headerPaint)
                    canvas.drawText("Clock Out", colClockOut.toFloat(), yPosition.toFloat(), headerPaint)
                    canvas.drawText("Worked", colWorked.toFloat(), yPosition.toFloat(), headerPaint)
                    canvas.drawText("Break", colBreak.toFloat(), yPosition.toFloat(), headerPaint)
                    canvas.drawText("Leaving", colLeaving.toFloat(), yPosition.toFloat(), headerPaint)
                    yPosition += 20
                }
                is HistoryListItem.SessionItem -> {
                    val session = item.session
                    canvas.drawText(session.clockInTime ?: "--:--", colClockIn.toFloat(), yPosition.toFloat(), contentPaint)
                    canvas.drawText(session.clockOutTime ?: "--:--", colClockOut.toFloat(), yPosition.toFloat(), contentPaint)
                    canvas.drawText(formatDuration(session.totalWorked), colWorked.toFloat(), yPosition.toFloat(), contentPaint)
                    canvas.drawText(formatDuration(session.totalBreak), colBreak.toFloat(), yPosition.toFloat(), contentPaint)
                    canvas.drawText(session.leavingTime ?: "--:--", colLeaving.toFloat(), yPosition.toFloat(), contentPaint)
                    yPosition += 20
                }
            }

            if (yPosition > pageHeight - 50) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPosition = 50
            }
        }

        pdfDocument.finishPage(page)
        val fileName = "WorkHistory_${month + 1}_$year.pdf"
        val file = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        showPdfNotification(file)

        Toast.makeText(requireContext(), "PDF saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showPdfNotification(file: File) {
        val context = requireContext()
        val channelId = "pdf_download_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "PDF Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for exported PDF reports"
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val fileUri = FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            file
        )

        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/pdf")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentTitle("PDF Export Complete")
            .setContentText("Tap to open your monthly report")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(1001, notification)
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun getMonthHistory(year: Int, month: Int): List<HistoryListItem> {
        val allSessions = viewModel.allSessions.value ?: return emptyList()

        val filteredSessions = allSessions.filter { session ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(session.date)
            val cal = Calendar.getInstance()
            if (date != null) {
                cal.time = date
                cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
            } else false
        }

        val groupedList = mutableListOf<HistoryListItem>()
        val groupedByDate = filteredSessions.groupBy { it.date }

        groupedByDate.forEach { (date, sessionList) ->
            groupedList.add(HistoryListItem.DateHeader(date))
            sessionList.forEach { session ->
                groupedList.add(HistoryListItem.SessionItem(session))
            }
        }

        return groupedList
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
