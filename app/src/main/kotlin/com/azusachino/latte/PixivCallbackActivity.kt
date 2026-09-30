package com.azusachino.latte

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.azusachino.latte.data.network.PixivOAuthCallbackBus
import com.azusachino.latte.data.network.PixivOAuthClient

/**
 * Transparent trampoline activity for Pixiv OAuth callback redirects (`pixiv://account/login`).
 *
 * Exists solely to receive the callback URI, forward it to [PixivOAuthCallbackBus],
 * and return the user to the already-open [MainActivity] / [AccountManagerScreen].
 *
 * The Custom Tab that fired this redirect lives *inside Latte's own task* (Custom
 * Tabs launch into the calling app's task), so merely finishing here would leave
 * the browser tab sitting on top of MainActivity. Explicitly re-launching
 * MainActivity with CLEAR_TOP | SINGLE_TOP removes every activity above it in the
 * task — including the browser tab — and brings the app back to the foreground.
 */
class PixivCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data
        if (uri != null && PixivOAuthClient.isCallbackUri(uri)) {
            PixivOAuthCallbackBus.publish(uri.toString())
        }

        // Clear back to the existing MainActivity, removing the Custom Tab
        // (and anything else) stacked above it in this task.
        val home = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(home)

        finish()
    }
}
