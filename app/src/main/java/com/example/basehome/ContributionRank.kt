package com.example.basehome

import android.content.Context

object ContributionRank {
    private val thresholds = listOf(0, 5, 10, 20, 35, 50, 75, 100, 125, 150, 175, 200)
    private val rankStrings = listOf(
        R.string.rank_newbie,
        R.string.rank_helper,
        R.string.rank_explorer,
        R.string.rank_activist,
        R.string.rank_city_helper,
        R.string.rank_advanced,
        R.string.rank_expert,
        R.string.rank_master,
        R.string.rank_mentor,
        R.string.rank_city_legend,
        R.string.rank_city_hero,
        R.string.rank_legend
    )

    fun title(context: Context, points: Int): String {
        val index = thresholds.indexOfLast { points >= it }.coerceAtLeast(0)
        return context.getString(rankStrings[index])
    }

    fun progress(context: Context, points: Int): String {
        val current = points.coerceAtLeast(0)
        val currentIndex = thresholds.indexOfLast { current >= it }.coerceAtLeast(0)
        if (currentIndex == thresholds.lastIndex) {
            return context.getString(R.string.rank_max_progress, current, thresholds.last())
        }
        val next = thresholds[currentIndex + 1]
        return context.getString(R.string.rank_progress, current, next, next - current)
    }
}
