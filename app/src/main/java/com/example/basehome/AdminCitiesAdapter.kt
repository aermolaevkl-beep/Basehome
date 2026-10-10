package com.example.basehome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AdminCitiesAdapter(
    private var cities: List<String>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<AdminCitiesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvAdminCityName)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteCity)
    }

    fun updateList(newList: List<String>) {
        this.cities = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_city_admin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val city = cities[position]
        holder.tvName.text = city
        holder.btnDelete.setOnClickListener { onDeleteClick(city) }
    }

    override fun getItemCount(): Int = cities.size
}
