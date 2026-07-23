package com.example.basehome

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class UserRankAdapter(
    private var userRanks: List<UserRank>,
    private val onItemClick: (UserRank) -> Unit
) : RecyclerView.Adapter<UserRankAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPosition: TextView = view.findViewById(R.id.tvRankPosition)
        val tvName: TextView = view.findViewById(R.id.tvRankUserName)
        val tvCount: TextView = view.findViewById(R.id.tvRankCount)
    }

    fun updateList(newList: List<UserRank>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize() = userRanks.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = userRanks[oldPos].userId == newList[newPos].userId
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = userRanks[oldPos] == newList[newPos]
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.userRanks = newList.toList()
        diffResult.dispatchUpdatesTo(diffResult.let { this }) // Simplified for brevity in writing, but standard is diffResult.dispatchUpdatesTo(this)
        notifyDataSetChanged() // Fallback to ensure UI refresh if diff logic has nuances
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_rank, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val userRank = userRanks[position]
        holder.tvPosition.text = (position + 1).toString()
        holder.tvCount.text = userRank.addressCount.toString()

        holder.tvName.text = RankHelper.formatNameWithRank(userRank.userId, userRank.userName)

        when (position) {
            0 -> holder.tvPosition.setTextColor(Color.parseColor("#FFD700")) // Золото
            1 -> holder.tvPosition.setTextColor(Color.parseColor("#C0C0C0")) // Серебро
            2 -> holder.tvPosition.setTextColor(Color.parseColor("#CD7F32")) // Бронза
            else -> holder.tvPosition.setTextColor(Color.GRAY)
        }

        holder.itemView.setOnClickListener { onItemClick(userRank) }
    }

    override fun getItemCount() = userRanks.size
}
