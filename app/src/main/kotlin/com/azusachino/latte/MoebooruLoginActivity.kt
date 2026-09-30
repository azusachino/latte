package com.azusachino.latte

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.azusachino.latte.data.network.OkHttpProvider
import com.azusachino.latte.plugin.moebooru.MoebooruAuthCallbackBus
import com.azusachino.latte.plugin.moebooru.MoebooruAuthResult
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class MoebooruLoginActivity : Activity() {
    private lateinit var webView: WebView
    private var hasCapturedSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val loginUrl = intent.getStringExtra(EXTRA_LOGIN_URL) ?: run {
            finish()
            return
        }
        val externalId = intent.getStringExtra(EXTRA_EXTERNAL_ID) ?: run {
            finish()
            return
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    checkLoginSuccess(url, externalId)
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    checkLoginSuccess(request.url.toString(), externalId)
                    return false
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    checkLoginSuccess(url, externalId)
                    return false
                }
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

    private fun checkLoginSuccess(currentUrl: String, externalId: String) {
        if (hasCapturedSession) return
        val httpUrl = currentUrl.toHttpUrlOrNull() ?: return
        val host = httpUrl.host
        val cookieString = CookieManager.getInstance().getCookie(currentUrl) ?: return

        val cookiePairs = cookieString.split(";").map { it.trim() }
        val userId = cookiePairs.find { it.startsWith("user_id=") }?.substringAfter("user_id=")
        val passHash = cookiePairs.find { it.startsWith("pass_hash=") }?.substringAfter("pass_hash=")
        val username = cookiePairs.find { it.startsWith("login=") }?.substringAfter("login=")
            ?: cookiePairs.find { it.startsWith("user_name=") }?.substringAfter("user_name=")

        if (!userId.isNullOrBlank() && !passHash.isNullOrBlank()) {
            hasCapturedSession = true

            val jar = OkHttpProvider.cookieJar
            if (jar != null) {
                val okCookies = cookiePairs.mapNotNull { pair ->
                    val name = pair.substringBefore("=").trim()
                    val value = pair.substringAfter("=", "").trim()
                    if (name.isNotEmpty()) {
                        Cookie.Builder()
                            .domain(host)
                            .path("/")
                            .name(name)
                            .value(value)
                            .build()
                    } else {
                        null
                    }
                }
                jar.saveFromResponse(httpUrl, okCookies)
            }

            val finalUsername = username ?: "User_$userId"
            MoebooruAuthCallbackBus.publish(
                MoebooruAuthResult(
                    externalId = externalId,
                    username = finalUsername,
                    passHash = passHash,
                    userId = userId,
                )
            )

            setResult(RESULT_OK)
            finish()
        }
    }

    companion object {
        const val EXTRA_SITE_NAME = "extra_site_name"
        const val EXTRA_LOGIN_URL = "extra_login_url"
        const val EXTRA_EXTERNAL_ID = "extra_external_id"
    }
}
