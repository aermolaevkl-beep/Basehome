package com.example.basehome

import android.graphics.drawable.GradientDrawable
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class UsersAdapter(
    private var users: List<UserDetail>,
    private val onAdminToggle: (UserDetail, Boolean) -> Unit
) : RecyclerView.Adapter<UsersAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvUserDetailName)
        val tvEmail: TextView = view.findViewById(R.id.tvUserDetailEmail)
        val tvAddresses: TextView = view.findViewById(R.id.tvUserDetailAddressCount)
        val tvComments: TextView = view.findViewById(R.id.tvUserDetailCommentCount)
        val vStatus: View = view.findViewById(R.id.vOnlineStatus)
        val tvLastSeen: TextView = view.findViewById(R.id.tvLastSeen)
        val btnToggleAdminStatus: MaterialButton = view.findViewById(R.id.btnToggleAdminStatus)
    }

    fun updateList(newList: List<UserDetail>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize() = users.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = users[oldPos].uid == newList[newPos].uid
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = users[oldPos] == newList[newPos]
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.users = newList.toList()
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_details, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.tvName.text = RankHelper.formatNameWithRank(user.uid, user.name)
        holder.tvEmail.text = user.email
        holder.tvAddresses.text = "Адресов: ${user.addressCount}"
        holder.tvComments.text = "Комментариев: ${user.commentCount}"

        val statusColor = if (user.isOnline) android.R.color.holo_green_light else android.R.color.holo_red_light
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(holder.itemView.context, statusColor))
        }
        holder.vStatus.background = shape

        if (user.isOnline) {
            holder.tvLastSeen.text = "В сети"
        } else {
            if (user.lastSeen > 0) {
                val timeAgo = DateUtils.getRelativeTimeSpanString(
                    user.lastSeen,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
                holder.tvLastSeen.text = "был(а) $timeAgo"
            } else {
                holder.tvLastSeen.text = "не заходил(а)"
            }
        }

        if (user.isAdmin) {
            holder.btnToggleAdminStatus.text = "👑 АДМИНИСТРАТОР"
            holder.btnToggleAdminStatus.setStrokeColorResource(android.R.color.holo_purple)
            holder.btnToggleAdminStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_purple))
        } else {
            holder.btnToggleAdminStatus.text = "Права: Пользователь"
            holder.btnToggleAdminStatus.setStrokeColorResource(android.R.color.darker_gray)
            holder.btnToggleAdminStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray))
        }

        holder.btnToggleAdminStatus.setOnClickListener {
            onAdminToggle(user, !user.isAdmin)
        }
    }

    override fun getItemCount() = users.size
}
