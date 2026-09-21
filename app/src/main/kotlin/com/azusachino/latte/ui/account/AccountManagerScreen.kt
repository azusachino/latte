package com.azusachino.latte.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.azusachino.latte.plugin.SitePlugin
import com.azusachino.latte.plugin.SitePluginManager
import com.azusachino.latte.ui.common.ToastManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagerScreen(
    pluginManager: SitePluginManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeLoginPlugin by remember { mutableStateOf<SitePlugin?>(null) }
    var activeProfilePlugin by remember { mutableStateOf<SitePlugin?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Platforms & accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Platform connections",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            pluginManager.plugins.forEach { plugin ->
                val isLoggedIn by plugin.isLoggedInFlow.collectAsState(initial = plugin.isLoggedIn)

                AccountPreferenceWidget(
                    plugin = plugin,
                    isLoggedIn = isLoggedIn,
                    onClick = {
                        if (isLoggedIn) {
                            activeProfilePlugin = plugin
                        } else {
                            activeLoginPlugin = plugin
                        }
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Security Note
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Hardware-Backed Security",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Passwords and session tokens are encrypted using the Android Keystore (AES-256-GCM) and never saved in plaintext. Signing out completely purges all stored credentials.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // Login Dialog
    activeLoginPlugin?.let { plugin ->
        PluginLoginDialog(
            plugin = plugin,
            onDismissRequest = { activeLoginPlugin = null },
            onLoginSuccess = {
                activeLoginPlugin = null
                ToastManager.showSuccess("Signed in to ${plugin.name}")
            },
        )
    }

    // Profile / Sign Out Dialog
    activeProfilePlugin?.let { plugin ->
        PluginProfileDialog(
            plugin = plugin,
            onDismissRequest = { activeProfilePlugin = null },
            onSignOut = {
                activeProfilePlugin = null
                ToastManager.showInfo("Signed out from ${plugin.name}")
            },
        )
    }
}
