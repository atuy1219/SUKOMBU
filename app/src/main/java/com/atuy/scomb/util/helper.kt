package com.atuy.scomb.util

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat

fun openLoginWithCustomTab(context: Context, loginUrl: String, toolbarColorResId: Int? = null) {
    val builder = CustomTabsIntent.Builder()
        .setShowTitle(true)

    toolbarColorResId?.let {
        val color = ContextCompat.getColor(context, it)
        builder.setToolbarColor(color)
    }

    // アニメーション（任意）
    builder.setStartAnimations(context, android.R.anim.slide_in_left, android.R.anim.fade_out)
    builder.setExitAnimations(context, android.R.anim.fade_in, android.R.anim.slide_out_right)

    val customTabsIntent = builder.build()
    customTabsIntent.launchUrl(context, Uri.parse(loginUrl))
}
