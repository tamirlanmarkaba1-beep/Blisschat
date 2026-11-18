package com.bliss.blisschat.ui.components

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayMonth = SimpleDateFormat("d MMM", Locale.getDefault())
private val fullDay = SimpleDateFormat("d MMMM yyyy 'г.'", Locale("ru"))

fun formatClock(ts: Long): String = if (ts <= 0) "" else clock.format(Date(ts))

fun isSameDay(a: Long, b: Long): Boolean {
    val x = Calendar.getInstance().apply { timeInMillis = a }
    val y = Calendar.getInstance().apply { timeInMillis = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}

/** "14:05" for today, "3 нояб." otherwise. */
fun formatListTime(ts: Long): String = when {
    ts <= 0 -> ""
    isSameDay(ts, System.currentTimeMillis()) -> clock.format(Date(ts))
    else -> dayMonth.format(Date(ts))
}

/** "Сегодня", "Вчера" or "24 октября 2026 г.". */
fun formatDayHeader(ts: Long): String {
    val now = System.currentTimeMillis()
    return when {
        isSameDay(ts, now) -> "Сегодня"
        isSameDay(ts, now - 24L * 60 * 60 * 1000) -> "Вчера"
        else -> fullDay.format(Date(ts))
    }
}

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}
