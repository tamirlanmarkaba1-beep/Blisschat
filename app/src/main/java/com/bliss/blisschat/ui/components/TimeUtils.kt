package com.bliss.blisschat.ui.components

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayMonth = SimpleDateFormat("d MMM", Locale.getDefault())
private val fullDay = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())

fun formatClock(ts: Long): String = if (ts <= 0) "" else clock.format(Date(ts))

private fun sameDay(a: Long, b: Long): Boolean {
    val x = Calendar.getInstance().apply { timeInMillis = a }
    val y = Calendar.getInstance().apply { timeInMillis = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}

fun isSameDay(a: Long, b: Long) = sameDay(a, b)

/** "14:05" for today, "3 Nov" otherwise. */
fun formatListTime(ts: Long): String = when {
    ts <= 0 -> ""
    sameDay(ts, System.currentTimeMillis()) -> clock.format(Date(ts))
    else -> dayMonth.format(Date(ts))
}

/** "Today", "Yesterday" or "Monday, 9 November". */
fun formatDayHeader(ts: Long): String {
    val now = System.currentTimeMillis()
    return when {
        sameDay(ts, now) -> "Today"
        sameDay(ts, now - 24L * 60 * 60 * 1000) -> "Yesterday"
        else -> fullDay.format(Date(ts))
    }
}
