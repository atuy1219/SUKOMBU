package com.atuy.scomb.util

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    fun stringToTime(time: String, format: String = "yyyy/MM/dd HH:mm"): Long {
        return try {
            val dateFormat = SimpleDateFormat(format, Locale.JAPAN)
            dateFormat.parse(time)?.time ?: 0L
        } catch (e: Exception) {
            0L // パース失敗時は0を返す
        }
    }
}