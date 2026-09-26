package com.azusachino.latte.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azusachino.latte.BuildConfig
import com.azusachino.latte.data.settings.LattePreferences
import com.azusachino.latte.data.settings.ThemeMode
import com.azusachino.latte.ui.common.ToastManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: LattePreferences,
    onBack: () -> Unit,
    onOpenAccountManager: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val columnCount by preferences.columnCount.collectAsState()
    val themeMode by preferences.themeMode.collectAsState()
    val safeMode by preferences.safeMode.collectAsState()

    var cacheSizeBytes by remember { mutableLongStateOf(preferences.getCacheSizeBytes()) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            // Accounts Section
            SettingsSectionHeader("Accounts")

            SettingsRow(
                icon = Icons.Default.AccountCircle,
                title = "Platforms & accounts",
                subtitle = "Connect, switch, and manage yande.re or Pixiv sessions",
                onClick = onOpenAccountManager,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Content Section
            SettingsSectionHeader("Content")

            SettingsSwitchRow(
                icon = Icons.Default.Security,
                title = "Safe Mode",
                subtitle = "Show safe contents only",
                checked = safeMode,
                onCheckedChange = { preferences.setSafeMode(it) },
            )

            BlacklistTagsEditor(preferences)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Appearance Section
            SettingsSectionHeader("Appearance")

            // Theme Mode
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Theme", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { preferences.setThemeMode(ThemeMode.SYSTEM) },
                        label = { Text("System") },
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { preferences.setThemeMode(ThemeMode.LIGHT) },
                        label = { Text("Light") },
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { preferences.setThemeMode(ThemeMode.DARK) },
                        label = { Text("Dark") },
                    )
                }
            }

            // Default Columns
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Grid Columns", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { count ->
                        FilterChip(
                            selected = columnCount == count,
                            onClick = { preferences.setColumnCount(count) },
                            label = { Text("$count column${if (count > 1) "s" else ""}") },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Storage Section
            SettingsSectionHeader("Storage")

            SettingsRow(
                icon = Icons.Default.Delete,
                title = "Clear Image Cache",
                subtitle = "${formatFileSize(cacheSizeBytes)} cached",
                onClick = {
                    preferences.clearCache()
                    cacheSizeBytes = preferences.getCacheSizeBytes()
                    ToastManager.showSuccess("Cache cleared")
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // About Section (GitHub, Check update, About)
            SettingsSectionHeader("About")

            SettingsRow(
                icon = Icons.Default.Code,
                title = "GitHub",
                subtitle = "https://github.com/azusachino/latte",
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/azusachino/latte"),
                    )
                    context.startActivity(intent)
                },
            )

            SettingsRow(
                icon = Icons.Default.SystemUpdate,
                title = "Check for Updates",
                subtitle = "Version ${BuildConfig.VERSION_NAME}",
                onClick = {
                    ToastManager.showInfo("You're using the latest version (${BuildConfig.VERSION_NAME})")
                },
            )

            SettingsRow(
                icon = Icons.Default.Info,
                title = "About Latte",
                subtitle = "Version ${BuildConfig.VERSION_NAME} · Fast native client",
                onClick = {
                    showAboutDialog = true
                },
            )
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Latte") },
            text = {
                Column {
                    Text("Latte ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("A fast native Moebooru client for Android.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Built with Jetpack Compose, Material 3, OkHttp HTTP/2 multiplexing, and Coil 3.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups >= units.size) digitGroups = units.size - 1
    val df = java.text.DecimalFormat("#,##0.#")
    return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}

@Composable
private fun BlacklistTagsEditor(preferences: LattePreferences, modifier: Modifier = Modifier) {
    val blacklist by preferences.blacklistTags.collectAsState()
    var newTag by remember { mutableStateOf("") }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Blacklisted Tags", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Hide posts carrying these tags on every feed",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newTag,
                onValueChange = { newTag = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("e.g. comic") },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            )
            TextButton(
                onClick = {
                    preferences.setTagBlacklisted(newTag, true)
                    newTag = ""
                },
                enabled = newTag.isNotBlank(),
            ) {
                Text("Add")
            }
        }
        if (blacklist.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                blacklist.sorted().forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = { preferences.setTagBlacklisted(tag, false) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove $tag",
                                modifier = Modifier.height(16.dp).width(16.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}
