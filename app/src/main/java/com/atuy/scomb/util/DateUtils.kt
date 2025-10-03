package com.atuy.scomb.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    fun stringToTime(time: String, format: String = "yyyy/MM/dd HH:mm"): Long {
        return try {
            val dateFormat = SimpleDateFormat(format, Locale.JAPAN)
            dateFormat.parse(time)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun timeToString(time: Long): String {
        val date = Date(time)
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = time }

        if (now.get(Calendar.YEAR) != target.get(Calendar.YEAR)) {
            return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(date)
        }

        if (now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
            return "今日 ${SimpleDateFormat("HH:mm", Locale.JAPAN).format(date)}"
        }

        now.add(Calendar.DAY_OF_YEAR, 1)
        if (now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
            return "明日 ${SimpleDateFormat("HH:mm", Locale.JAPAN).format(date)}"
        }

        return SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN).format(date)
    }
}