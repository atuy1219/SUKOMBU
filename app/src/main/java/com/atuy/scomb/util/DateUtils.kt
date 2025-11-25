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

    // 現在の時限を取得 (0-based index: 0=1限, 1=2限...)
    // 該当なしの場合は -1 を返す
    fun getCurrentPeriod(): Int {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val currentTime = hour * 60 + minute

        // 開始・終了時刻を分単位で定義
        // 1限: 9:00 - 10:40
        // 2限: 10:50 - 12:30
        // 3限: 13:20 - 15:00
        // 4限: 15:10 - 16:50
        // 5限: 17:00 - 18:40
        // 6限: 18:50 - 20:30

        val periods = listOf(
            9 * 60 + 0 to 10 * 60 + 40,
            10 * 60 + 40 to 12 * 60 + 30,
            12 * 60 + 30 to 15 * 60 + 0,
            15 * 60 + 0 to 16 * 60 + 50,
            16 * 60 + 50 to 18 * 60 + 40,
            18 * 60 + 40 to 20 * 60 + 30
        )

        for (i in periods.indices) {
            val (start, end) = periods[i]
            if (currentTime in (start - 10)..end) {
                return i
            }
        }
        return -1
    }
}