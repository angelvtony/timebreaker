package com.example.timebreaker.ui.history

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
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
import com.example.timebreaker.ui.data.entities.WorkSession
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
        val pageWidth = 595
        val pageHeight = 842

        val marginLeft = 30
        val marginRight = 30
        val tableTop = 80
        val cellHeight = 25
        val headerHeight = 30

        val titlePaint = Paint().apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val headerPaint = Paint().apply {
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val cellPaint = Paint().apply {
            textSize = 12f
            typeface = Typeface.DEFAULT
        }
        val linePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.BLACK
        }
        val totalTableWidth = pageWidth - marginLeft - marginRight
        val columnWidths = listOf(100, 100, 100, 100, 100)
        val columnTitles = listOf("Clock In", "Clock Out", "Worked", "Break", "Leaving")

        var yPosition = tableTop
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(
            Calendar.getInstance().apply { set(Calendar.YEAR, year); set(Calendar.MONTH, month) }.time
        )
        canvas.drawText("Work Sessions - $monthName", marginLeft.toFloat(), 50f, titlePaint)

        fun drawTableHeader() {
            var xPosition = marginLeft
            columnTitles.forEachIndexed { index, title ->
                val cellRight = xPosition + columnWidths[index]
                canvas.drawRect(xPosition.toFloat(), yPosition.toFloat(),
                    cellRight.toFloat(), (yPosition + headerHeight).toFloat(), linePaint)
                canvas.drawText(title, xPosition + 10f, yPosition + 20f, headerPaint)
                xPosition += columnWidths[index]
            }
            yPosition += headerHeight
        }
        fun drawTableRow(session: WorkSession) {
            var xPosition = marginLeft
            val rowData = listOf(
                session.clockInTime ?: "--:--",
                session.clockOutTime ?: "--:--",
                formatDuration(session.totalWorked),
                formatDuration(session.totalBreak),
                session.leavingTime ?: "--:--"
            )
            rowData.forEachIndexed { index, text ->
                val cellRight = xPosition + columnWidths[index]
                canvas.drawRect(xPosition.toFloat(), yPosition.toFloat(),
                    cellRight.toFloat(), (yPosition + cellHeight).toFloat(), linePaint)
                canvas.drawText(text, xPosition + 10f, yPosition + 17f, cellPaint)
                xPosition += columnWidths[index]
            }

            yPosition += cellHeight
        }
        historyItems.forEach { item ->
            when (item) {
                is HistoryListItem.DateHeader -> {
                    // Draw date header
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val outputFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                    val formattedDate = outputFormat.format(inputFormat.parse(item.date)!!)
                    canvas.drawText(formattedDate, marginLeft.toFloat(), yPosition + 20f, titlePaint)
                    yPosition += 35
                    drawTableHeader()
                }

                is HistoryListItem.SessionItem -> {
                    drawTableRow(item.session)
                }
            }

            if (yPosition > pageHeight - 60) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPosition = tableTop
                drawTableHeader()
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
