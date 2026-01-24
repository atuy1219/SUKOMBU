package com.atuy.scomb.util

import android.util.Log
import com.atuy.scomb.BuildConfig

object AppLogger {
    private const val DEFAULT_TAG = "ScombApp"

    @Volatile
    private var isEnabled: Boolean = BuildConfig.DEBUG

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun d(tag: String, msg: String) {
        if (isEnabled) Log.d(tag, msg)
    }

    fun d(msg: String) {
        d(DEFAULT_TAG, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (isEnabled) Log.e(tag, msg, tr)
    }

    fun e(msg: String, tr: Throwable? = null) {
        e(DEFAULT_TAG, msg, tr)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (isEnabled) Log.w(tag, msg, tr)
    }

    fun w(msg: String, tr: Throwable? = null) {
        w(DEFAULT_TAG, msg, tr)
    }

    fun i(tag: String, msg: String) {
        if (isEnabled) Log.i(tag, msg)
    }

    fun i(msg: String) {
        i(DEFAULT_TAG, msg)
    }
}