package com.example.basehome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

class CommentAdapter(
    private val comments: List<Comment>,
    private val isAdmin: Boolean,
    private val onReactionClick: (Comment, Boolean) -> Unit,
    private val onReplyClick: (Comment) -> Unit,
    private val onDeleteClick: (Comment) -> Unit,
    private val onEditClick: (Comment) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    private val nameCache = mutableMapOf<String, String>()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAuthor: TextView = view.findViewById(R.id.tvCommentAuthor)
        val tvDate: TextView = view.findViewById(R.id.tvCommentDate)
        val tvText: TextView = view.findViewById(R.id.tvCommentText)
        val btnPlus: TextView = view.findViewById(R.id.btnPlus)
        val btnMinus: TextView = view.findViewById(R.id.btnMinus)
        val btnReply: TextView = view.findViewById(R.id.btnReply)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditComment)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteComment)
        val rootLayout: LinearLayout = view as LinearLayout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        
        val uid = comment.authorId
        if (!uid.isNullOrEmpty()) {
            if (nameCache.containsKey(uid)) {
                holder.tvAuthor.text = nameCache[uid]
            } else {
                holder.tvAuthor.text = "" 
                FirebaseDatabase.getInstance().getReference("users").child(uid).child("name")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val name = snapshot.getValue(String::class.java)
                            val finalName = if (!name.isNullOrEmpty()) name else comment.authorEmail
                            nameCache[uid] = finalName
                            if (holder.adapterPosition == position) {
                                holder.tvAuthor.text = finalName
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            }
        } else {
            holder.tvAuthor.text = comment.authorEmail
        }

        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        holder.tvDate.text = sdf.format(Date(comment.timestamp))
        holder.tvText.text = comment.text

        holder.btnPlus.text = "+ ${comment.plusCount}"
        holder.btnMinus.text = "- ${comment.minusCount}"

        // Show edit/delete if user is author OR admin
        val isAuthor = currentUserId != null && currentUserId == comment.authorId
        holder.btnDelete.visibility = if (isAdmin || isAuthor) View.VISIBLE else View.GONE
        holder.btnEdit.visibility = if (isAuthor) View.VISIBLE else View.GONE

        holder.btnDelete.setOnClickListener { onDeleteClick(comment) }
        holder.btnEdit.setOnClickListener { onEditClick(comment) }

        val density = holder.itemView.context.resources.displayMetrics.density
        val marginStart = if (!comment.parentId.isNullOrEmpty()) (24 * density).toInt() else 0
        
        val params = holder.rootLayout.layoutParams as ViewGroup.MarginLayoutParams
        params.setMargins(marginStart, (4 * density).toInt(), 0, (4 * density).toInt())
        holder.rootLayout.layoutParams = params

        if (!comment.parentId.isNullOrEmpty()) {
            holder.rootLayout.setBackgroundResource(R.drawable.reply_background)
        } else {
            holder.rootLayout.setBackgroundResource(android.R.color.white)
        }

        holder.btnPlus.setOnClickListener { onReactionClick(comment, true) }
        holder.btnMinus.setOnClickListener { onReactionClick(comment, false) }
        holder.btnReply.setOnClickListener { onReplyClick(comment) }
    }

    override fun getItemCount() = comments.size
}
