package com.azusachino.latte.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azusachino.latte.plugin.SitePlugin
import com.azusachino.latte.plugin.SitePluginManager
import com.azusachino.latte.ui.common.ToastManager
import com.azusachino.latte.ui.theme.glassSurface
import com.azusachino.latte.ui.theme.pressScale

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                        isElevated = true,
                    ),
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Platforms & accounts",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.pressScale(),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "PLATFORM CONNECTIONS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
            )

            // Apple-style inset grouped glass card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .glassSurface(shape = RoundedCornerShape(16.dp), isElevated = true),
            ) {
                pluginManager.plugins.forEachIndexed { index, plugin ->
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
                    if (index < pluginManager.plugins.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Security Note: Render using glassSurface(shape = RoundedCornerShape(16.dp)) with subtle rim border
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .glassSurface(
                        shape = RoundedCornerShape(16.dp),
                        isElevated = false,
                    )
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Hardware-Backed Security",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Passwords and session tokens are encrypted using the Android Keystore (AES-256-GCM) and never saved in plaintext. Signing out completely purges all stored credentials.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
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
