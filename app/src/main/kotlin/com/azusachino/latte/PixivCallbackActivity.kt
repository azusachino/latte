package com.azusachino.latte

import android.app.Activity
import android.os.Bundle
import com.azusachino.latte.data.network.PixivOAuthCallbackBus
import com.azusachino.latte.data.network.PixivOAuthClient

/**
 * Transparent trampoline activity for Pixiv OAuth callback redirects (`pixiv://account/login`).
 *
 * Exists solely to receive the callback URI, forward it to [PixivOAuthCallbackBus],
 * and immediately finish so Android dismisses the Custom Tab and smoothly returns
 * to the already-open [MainActivity] and [AccountManagerScreen] without spawning
 * duplicate activity instances or clobbering the navigation stack.
 */
class PixivCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data
        if (uri != null && PixivOAuthClient.isCallbackUri(uri)) {
            PixivOAuthCallbackBus.publish(uri.toString())
        }

        // Finish immediately so the task stack returns to the originating activity.
        finish()
    }
}
