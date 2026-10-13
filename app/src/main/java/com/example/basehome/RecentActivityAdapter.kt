package com.example.basehome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.DateFormat
import java.util.Date

data class ActivityItem(
    val type: ActivityType,
    val text: String,
    val timestamp: Long
)

enum class ActivityType { ADDRESS, ENTRANCE, COMMENT }

class RecentActivityAdapter(private var items: List<ActivityItem>) :
    RecyclerView.Adapter<RecentActivityAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView = view.findViewById(R.id.tvActivityIcon)
        val tvText: TextView = view.findViewById(R.id.tvActivityText)
        val tvTime: TextView = view.findViewById(R.id.tvActivityTime)
    }

    fun updateList(newItems: List<ActivityItem>) {
        items = newItems.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_activity, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvIcon.text = when (item.type) {
            ActivityType.ADDRESS -> "🏠"
            ActivityType.ENTRANCE -> "🚪"
            ActivityType.COMMENT -> "💬"
        }
        holder.tvText.text = item.text
        if (item.timestamp > 0L) {
            val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            holder.tvTime.text = dateFormat.format(Date(item.timestamp))
            holder.tvTime.visibility = View.VISIBLE
        } else {
            holder.tvTime.visibility = View.GONE
        }
    }

    override fun getItemCount() = items.size
}
