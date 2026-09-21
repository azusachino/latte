package com.azusachino.latte

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.azusachino.latte.data.network.PixivOAuthClient
import com.azusachino.latte.data.network.PixivOAuthCallbackBus
import com.azusachino.latte.ui.LatteApp

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Notification permission result handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        publishPixivCallback(intent)

        requestNotificationPermission()

        setContent {
            LatteApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishPixivCallback(intent)
    }

    private fun publishPixivCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (PixivOAuthClient.isCallbackUri(uri)) {
            PixivOAuthCallbackBus.publish(uri.toString())
            // The authorization code is single-use. Do not replay it when the
            // activity is recreated or the same intent is delivered again.
            intent.data = null
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
