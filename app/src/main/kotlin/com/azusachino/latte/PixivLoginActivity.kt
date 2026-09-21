package com.azusachino.latte

import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.azusachino.latte.data.network.PixivOAuthCallbackBus
import com.azusachino.latte.data.network.PixivOAuthClient

class PixivLoginActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val loginUrl = intent.getStringExtra(EXTRA_LOGIN_URL)
            ?: run {
                finish()
                return
            }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = consumeCallback(request.url.toString())

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    consumeCallback(url)
            }
        }
        setContentView(webView)
        webView.loadUrl(loginUrl)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun consumeCallback(value: String): Boolean {
        val uri = runCatching { android.net.Uri.parse(value) }.getOrNull() ?: return false
        if (!PixivOAuthClient.isCallbackUri(uri)) return false
        PixivOAuthCallbackBus.publish(uri.toString())
        finish()
        return true
    }

    companion object {
        const val EXTRA_LOGIN_URL = "pixiv_login_url"
    }
}
