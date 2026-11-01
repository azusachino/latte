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

    private val mediaReadPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Read access result handled: download dedup falls back to
            // best-effort until granted.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        publishPixivCallback(intent)

        requestNotificationPermission()
        requestMediaReadPermission()

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

    /**
     * Media read access is what makes download dedup see previously saved
     * files after a reinstall: without it, MediaStore queries only return
     * rows this install owns, and orphaned pre-reinstall files are invisible.
     */
    private fun requestMediaReadPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            mediaReadPermissionLauncher.launch(permission)
        }
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
