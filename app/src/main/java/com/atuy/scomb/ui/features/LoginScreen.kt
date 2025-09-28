package com.atuy.scomb.ui.features

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.atuy.scomb.R

// Scomb のログインページ（redirect_uri は myapp://auth/callback を登録しておく）
const val SCOMB_LOGIN_PAGE_URL =
    "https://scombz.shibaura-it.ac.jp/saml/login?idp=http://adfs.sic.shibaura-it.ac.jp/adfs/services/trust&redirect_uri=myapp://auth/callback"

/**
 * Chrome Custom Tabs を開くヘルパー
 */
private fun openLoginWithCustomTab(
    context: Context,
    loginUrl: String,
    toolbarColorResId: Int? = null
) {
    val builder = CustomTabsIntent.Builder()
        .setShowTitle(true)

    toolbarColorResId?.let {
        val color = ContextCompat.getColor(context, it)
        builder.setToolbarColor(color)
    }

    val customTabsIntent = builder.build()
    customTabsIntent.launchUrl(context, Uri.parse(loginUrl))
}

/**
 * ログイン画面 (Custom Tabs 版)
 */
@Composable
fun LoginScreen(
    onLoginStarted: () -> Unit = {}
) {
    val context = LocalContext.current

    Button(onClick = {
        openLoginWithCustomTab(
            context = context,
            loginUrl = SCOMB_LOGIN_PAGE_URL,
            toolbarColorResId = R.color.primary
        )
        onLoginStarted()
    }) {
        Text("Login (Open in browser)")
    }
}
