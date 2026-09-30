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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

class MoebooruLoginActivity : Activity() {
    private lateinit var webView: WebView
    private var hasCapturedSession = false
    private val scope = CoroutineScope(Dispatchers.Main)

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
        val cookieString = CookieManager.getInstance().getCookie("https://$host") ?: return

        val cookiePairs = cookieString.split(";").map { it.trim() }
        val userId = cookiePairs.find { it.startsWith("user_id=") }?.substringAfter("user_id=")

        val isNumericUserId = userId?.toLongOrNull()?.let { it > 0 } == true
        val isNotOnLoginPage = !currentUrl.contains("/user/login") && !currentUrl.contains("/user/authenticate")

        if (isNumericUserId && isNotOnLoginPage) {
            hasCapturedSession = true
            CookieManager.getInstance().flush()

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

            scope.launch {
                val resolvedUsername = withContext(Dispatchers.IO) {
                    fetchUsername(host, userId)
                }

                MoebooruAuthCallbackBus.publish(
                    MoebooruAuthResult(
                        externalId = externalId,
                        username = resolvedUsername,
                        passHash = "",
                        userId = userId,
                    )
                )

                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun fetchUsername(host: String, userId: String): String {
        return try {
            val req = Request.Builder()
                .url("https://$host/user.json?id=$userId")
                .build()
            OkHttpProvider.client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val nameMatch = Regex(""""name"\s*:\s*"([^"]+)"""").find(body)
                    nameMatch?.groupValues?.get(1) ?: "User_$userId"
                } else {
                    "User_$userId"
                }
            }
        } catch (_: Exception) {
            "User_$userId"
        }
    }

    companion object {
        const val EXTRA_SITE_NAME = "extra_site_name"
        const val EXTRA_LOGIN_URL = "extra_login_url"
        const val EXTRA_EXTERNAL_ID = "extra_external_id"
    }
}
