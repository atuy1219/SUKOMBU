package com.atuy.scomb.util

import android.util.Log
import com.atuy.scomb.BuildConfig

object AppLogger {
    @Volatile
    private var isEnabled: Boolean = BuildConfig.DEBUG

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun d(tag: String, msg: String) {
        if (isEnabled) Log.d(tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (isEnabled) Log.e(tag, msg, tr)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (isEnabled) Log.w(tag, msg, tr)
    }

    fun i(tag: String, msg: String) {
        if (isEnabled) Log.i(tag, msg)
    }
}