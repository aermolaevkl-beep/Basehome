package com.example.basehome

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import java.util.Calendar
import java.util.TimeZone

/** Records at most one app visit per local calendar day for the signed-in user. */
object LoginStreakTracker {
    private const val COUNT_KEY = "loginStreakCount"
    private const val LAST_DAY_KEY = "loginStreakLastDay"
    private const val MILLIS_PER_DAY = 86_400_000L

    fun recordDailyLogin(userRef: DatabaseReference) {
        val today = localDayNumber()
        userRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentCount = currentData.child(COUNT_KEY).getValue(Long::class.java) ?: 0L
                val previousDay = currentData.child(LAST_DAY_KEY).getValue(Long::class.java)

                val newCount = when {
                    previousDay == null -> 1L
                    today <= previousDay -> currentCount.coerceAtLeast(1L)
                    today - previousDay <= 3L -> currentCount.coerceAtLeast(1L) + 1L
                    else -> 1L
                }

                currentData.child(COUNT_KEY).value = newCount
                if (previousDay == null || today > previousDay) {
                    currentData.child(LAST_DAY_KEY).value = today
                }
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: com.google.firebase.database.DatabaseError?,
                committed: Boolean,
                currentData: com.google.firebase.database.DataSnapshot?
            ) = Unit
        }, false)
    }

    private fun localDayNumber(): Long {
        val localCalendar = Calendar.getInstance()
        val year = localCalendar.get(Calendar.YEAR)
        val month = localCalendar.get(Calendar.MONTH)
        val day = localCalendar.get(Calendar.DAY_OF_MONTH)
        val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day)
        }
        return utcCalendar.timeInMillis / MILLIS_PER_DAY
    }
}
