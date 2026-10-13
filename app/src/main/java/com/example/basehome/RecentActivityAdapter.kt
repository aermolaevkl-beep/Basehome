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
    val timestamp: Long,
    val targetId: String? = null,
    val isBusiness: Boolean = false,
    val address: String? = null
)

enum class ActivityType { ADDRESS, BUSINESS_ADDRESS, ENTRANCE, COMMENT }

class RecentActivityAdapter(
    private var items: List<ActivityItem>,
    private val onItemClick: (ActivityItem) -> Unit
) :
    RecyclerView.Adapter<RecentActivityAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView = view.findViewById(R.id.tvActivityIcon)
        val tvAddress: TextView = view.findViewById(R.id.tvActivityAddress)
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
            ActivityType.BUSINESS_ADDRESS -> "🏢"
            ActivityType.ENTRANCE -> "🚪"
            ActivityType.COMMENT -> "💬"
        }
        holder.tvAddress.text = item.address.orEmpty()
        holder.tvAddress.visibility = if (item.address.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.tvText.text = item.text
        holder.itemView.isClickable = item.targetId != null
        holder.itemView.setOnClickListener {
            if (item.targetId != null) onItemClick(item)
        }
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
