package com.example.motiondetector

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * @param showDate when true, list rows show a full date+time (used by the
 * searchable history screen, which can span many days). When false, only
 * the time is shown (used by the live detection list on the main screen).
 */
class DetectionLogAdapter(private val showDate: Boolean = false) :
    RecyclerView.Adapter<DetectionLogAdapter.ViewHolder>() {

    // Newest first
    private val items = mutableListOf<DetectionRecord>()
    private val timeFormat = SimpleDateFormat("a h:mm:ss", Locale.KOREA)
    private val dateTimeFormat = SimpleDateFormat("M월 d일 a h:mm:ss", Locale.KOREA)

    /** Adds a single new record to the top of the list (live detection screen). */
    fun addRecord(record: DetectionRecord) {
        items.add(0, record)
        if (items.size > 100) {
            items.removeAt(items.size - 1)
        }
        notifyItemInserted(0)
    }

    /** Replaces the entire list (history/search screen). */
    fun submitList(newItems: List<DetectionRecord>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun clear() {
        val size = items.size
        items.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun size(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], if (showDate) dateTimeFormat else timeFormat)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val iconText: TextView = view.findViewById(R.id.iconText)
        private val labelText: TextView = view.findViewById(R.id.labelText)
        private val timeText: TextView = view.findViewById(R.id.timeText)
        private val scoreText: TextView = view.findViewById(R.id.scoreText)

        fun bind(record: DetectionRecord, timeFormat: SimpleDateFormat) {
            val isPerson = record.type == DetectionType.PERSON
            iconText.text = if (isPerson) "👤" else "🚗"
            iconText.setBackgroundResource(
                if (isPerson) R.drawable.bg_icon_person else R.drawable.bg_icon_vehicle
            )
            labelText.text = "${record.label} 감지됨"
            timeText.text = timeFormat.format(Date(record.timestampMillis))
            scoreText.text = "${(record.score * 100).toInt()}%"
        }
    }
}
