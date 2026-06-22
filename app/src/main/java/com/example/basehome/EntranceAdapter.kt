package com.example.basehome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class EntranceAdapter(
    private val entrances: List<Entrance>,
    private val onEditClick: (Entrance) -> Unit
) : RecyclerView.Adapter<EntranceAdapter.EntranceViewHolder>() {

    class EntranceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumber: TextView = view.findViewById(R.id.tvEntranceNumber)
        val tvIntercom: TextView = view.findViewById(R.id.tvIntercomCode)
        val tvCross: TextView = view.findViewById(R.id.tvCrossFloor)
        val tvBasement: TextView = view.findViewById(R.id.tvBasementKey)
        val tvLastEdit: TextView = view.findViewById(R.id.tvLastEdit)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditEntrance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntranceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_entrance, parent, false)
        return EntranceViewHolder(view)
    }

    override fun onBindViewHolder(holder: EntranceViewHolder, position: Int) {
        val entrance = entrances[position]
        holder.tvNumber.text = "Подъезд №${entrance.number}"
        
        // Отображаем только заполненные поля
        if (entrance.intercomCode.isNullOrBlank()) {
            holder.tvIntercom.visibility = View.GONE
        } else {
            holder.tvIntercom.visibility = View.VISIBLE
            holder.tvIntercom.text = "Код домофона: ${entrance.intercomCode}"
        }

        if (entrance.crossFloor.isNullOrBlank()) {
            holder.tvCross.visibility = View.GONE
        } else {
            holder.tvCross.visibility = View.VISIBLE
            holder.tvCross.text = "Этаж кросса: ${entrance.crossFloor}"
        }

        if (entrance.basementKey.isNullOrBlank()) {
            holder.tvBasement.visibility = View.GONE
        } else {
            holder.tvBasement.visibility = View.VISIBLE
            holder.tvBasement.text = "Ключ от подвала: ${entrance.basementKey}"
        }
        
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val date = sdf.format(Date(entrance.lastEditTimestamp))
        holder.tvLastEdit.text = "Изм: ${entrance.lastEditorName} ($date)"

        holder.btnEdit.setOnClickListener { onEditClick(entrance) }
    }

    override fun getItemCount() = entrances.size
}
