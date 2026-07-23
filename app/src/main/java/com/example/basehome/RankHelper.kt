package com.example.basehome

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan

object RankHelper {
    fun formatNameWithRank(userId: String?, userName: String?): CharSequence {
        val name = userName ?: "Аноним"
        val top3 = UserRank.top3UserIds
        
        if (userId == null || top3.isEmpty()) return name

        return when (userId) {
            top3.getOrNull(0) -> "$name 👑"
            top3.getOrNull(1) -> "$name 🥈"
            top3.getOrNull(2) -> "$name 🥉"
            else -> name
        }
    }
}
