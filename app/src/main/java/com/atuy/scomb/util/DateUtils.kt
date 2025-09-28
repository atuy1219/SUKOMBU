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
            0L // パース失敗時は0を返す
        }
    }

    // Long型のミリ秒を人間が読める形式に変換する関数
    fun timeToString(time: Long): String {
        val date = Date(time)
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = time }

        // 年が違う場合はフルで表示
        if (now.get(Calendar.YEAR) != target.get(Calendar.YEAR)) {
            return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(date)
        }

        // 今日の場合
        if (now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
            return "今日 ${SimpleDateFormat("HH:mm", Locale.JAPAN).format(date)}"
        }

        // 明日の場合
        now.add(Calendar.DAY_OF_YEAR, 1)
        if (now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
            return "明日 ${SimpleDateFormat("HH:mm", Locale.JAPAN).format(date)}"
        }

        // それ以外
        return SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN).format(date)
    }
}