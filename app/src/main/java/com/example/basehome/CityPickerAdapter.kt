package com.example.basehome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CityPickerAdapter(
    private val cities: List<String>,
    private val selectedCity: String,
    private val listener: OnCitySelectedListener
) : RecyclerView.Adapter<CityPickerAdapter.ViewHolder>() {

    fun interface OnCitySelectedListener {
        fun onCitySelected(city: String)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCityName: TextView = view.findViewById(R.id.tvCityItemName)
        val ivCheck: ImageView = view.findViewById(R.id.ivCitySelectedCheck)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_city_picker, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val city = cities[position]
        val isAll = CityManager.ALL_CITIES.equals(city, ignoreCase = true)
        val displayText = if (isAll) "🌐 $city" else "📍 $city"
        holder.tvCityName.text = displayText

        val isSelected = city.equals(selectedCity, ignoreCase = true)
        holder.ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE

        if (isSelected) {
            holder.tvCityName.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            holder.tvCityName.setTypeface(null, android.graphics.Typeface.NORMAL)
        }

        holder.itemView.setOnClickListener {
            listener.onCitySelected(city)
        }
    }

    override fun getItemCount(): Int = cities.size
}
