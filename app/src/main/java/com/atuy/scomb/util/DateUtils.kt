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

        val formatYear = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN)
        val formatMonthDay = SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN)
        val formatTime = SimpleDateFormat("HH:mm", Locale.JAPAN)

        if (target.before(now)) {
            return if (now.get(Calendar.YEAR) != target.get(Calendar.YEAR)) {
                formatYear.format(date)
            } else {
                formatMonthDay.format(date)
            }
        }

        if (now.get(Calendar.YEAR) != target.get(Calendar.YEAR)) {
            return formatYear.format(date)
        }

        if (now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
            return "今日 ${formatTime.format(date)}"
        }

        now.add(Calendar.DAY_OF_YEAR, 1)
        if (now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
            return "明日 ${formatTime.format(date)}"
        }

        return formatMonthDay.format(date)
    }

    fun formatRemainingTime(deadline: Long): String {
        val now = System.currentTimeMillis()
        val diff = deadline - now

        if (diff <= 0) {
            return "期限切れ"
        }

        val days = diff / (1000 * 60 * 60 * 24)
        val hours = (diff / (1000 * 60 * 60)) % 24
        val minutes = (diff / (1000 * 60)) % 60

        return when {
            days > 0 -> "あと${days}日"
            hours > 0 -> "あと${hours}時間"
            minutes > 0 -> "あと${minutes}分"
            else -> "まもなく"
        }
    }

    data class ScombTerm(val year: Int, val term: String) {
        val apiTerm: String
            get() = if (term == "1") "01" else "02"
        val yearApiTerm: String
            get() = "$year$apiTerm"
    }

    fun getCurrentScombTerm(): ScombTerm {
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH) // 0-11
        val year = if (month < 3) { // 1月, 2月, 3月は前年度扱い
            calendar.get(Calendar.YEAR) - 1
        } else {
            calendar.get(Calendar.YEAR)
        }
        // 4月～8月 (index 3-7) が前期 "1"
        // 9月～3月 (index 8-11, 0-2) が後期 "2"
        val term = if (month in 3..7) "1" else "2"

        return ScombTerm(year, term)
    }
}
