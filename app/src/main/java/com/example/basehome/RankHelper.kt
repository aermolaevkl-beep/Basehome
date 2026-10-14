package com.example.basehome

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.widget.TextView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object RankHelper {
    @JvmOverloads
    fun bindName(
        textView: TextView,
        userId: String?,
        userName: String?,
        prefix: CharSequence = "",
        suffix: CharSequence = "",
        streakCount: Int? = null,
    ) {
        val name = userName ?: "Аноним"
        val requestTag = "${userId.orEmpty()}|$name|$prefix|$suffix"
        textView.tag = requestTag
        if (streakCount != null) {
            textView.text = formatNameWithRank(userId, name, streakCount, prefix, suffix)
            return
        }
        textView.text = formatNameWithRank(userId, name, null, prefix, suffix)
        if (userId.isNullOrBlank()) return

        FirebaseDatabase.getInstance().getReference("users").child(userId)
            .child("loginStreakCount").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val streak = snapshot.getValue(Long::class.java)?.toInt()?.coerceAtLeast(0) ?: 0
                    if (textView.tag == requestTag) {
                        textView.text = formatNameWithRank(userId, name, streak, prefix, suffix)
                    }
                }

                override fun onCancelled(error: DatabaseError) = Unit
            })
    }

    @JvmOverloads
    fun formatNameWithRank(
        userId: String?,
        userName: String?,
        streakCount: Int? = null,
        prefix: CharSequence = "",
        suffix: CharSequence = "",
    ): CharSequence {
        val name = userName ?: "Аноним"
        val top3 = UserRank.top3UserIds
        val rankName = if (userId == null || top3.isEmpty()) name else when (userId) {
            top3.getOrNull(0) -> "$name 👑"
            top3.getOrNull(1) -> "$name 🥈"
            top3.getOrNull(2) -> "$name 🥉"
            else -> name
        }
        val badgeText = if (streakCount != null && streakCount > 0) "$streakCount" else ""
        val badgeGap = if (badgeText.isNotEmpty()) "  " else ""
        val content = SpannableString("$prefix$rankName$badgeGap$badgeText$suffix")
        if (badgeText.isNotEmpty()) {
            val start = prefix.length + rankName.length + badgeGap.length
            content.setSpan(
                CircleBadgeSpan(streakCount!!),
                start,
                start + badgeText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return content
    }

    private class CircleBadgeSpan(private val count: Int) : ReplacementSpan() {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5B6EE1.toInt() }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        }
        private val bounds = Rect()

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?,
        ): Int {
            textPaint.textSize = paint.textSize * 0.72f
            textPaint.getTextBounds(count.toString(), 0, count.toString().length, bounds)
            return (maxOf(bounds.width(), textPaint.textSize.toInt()) + paint.textSize * 0.65f).toInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint,
        ) {
            textPaint.textSize = paint.textSize * 0.72f
            textPaint.getTextBounds(count.toString(), 0, count.toString().length, bounds)
            val diameter = maxOf(bounds.width().toFloat(), textPaint.textSize) + paint.textSize * 0.65f
            val radius = diameter / 2f
            val centerX = x + radius
            val centerY = (top + bottom) / 2f
            canvas.drawCircle(centerX, centerY, radius, backgroundPaint)
            canvas.drawText(count.toString(), centerX, centerY - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
        }
    }
}
