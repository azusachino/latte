package com.azusachino.latte.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import com.azusachino.latte.ui.common.LatteIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.azusachino.latte.data.update.ApkInstaller
import com.azusachino.latte.data.update.DownloadProgress
import com.azusachino.latte.data.update.UpdateCheckResult
import com.azusachino.latte.data.update.UpdateDownloader
import com.azusachino.latte.data.update.UpdateInfo
import com.azusachino.latte.data.update.UpdateService
import com.azusachino.latte.ui.common.ToastManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

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
    var showProxyDialog by remember { mutableStateOf(false) }

    val proxyHost by preferences.proxyHost.collectAsState()
    val proxyPort by preferences.proxyPort.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val updateService = remember { UpdateService() }
    val updateDownloader = remember(context) { UpdateDownloader(context) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfoToPrompt by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var downloadErrorMessage by remember { mutableStateOf<String?>(null) }

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
                icon = LatteIcons.AccountCircle,
                title = "Platforms & accounts",
                subtitle = "Connect, switch, and manage yande.re or Pixiv sessions",
                onClick = onOpenAccountManager,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Content Section
            SettingsSectionHeader("Content")

            SettingsSwitchRow(
                icon = LatteIcons.Security,
                title = "Safe Mode",
                subtitle = "Show safe contents only",
                checked = safeMode,
                onCheckedChange = { preferences.setSafeMode(it) },
            )

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
                title = "Clear Cache",
                subtitle = "${formatFileSize(cacheSizeBytes)} total (images, HTTP, updates)",
                onClick = {
                    preferences.clearCache()
                    cacheSizeBytes = preferences.getCacheSizeBytes()
                    ToastManager.showSuccess("Cache cleared")
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Network Section
            SettingsSectionHeader("Network")

            SettingsRow(
                icon = LatteIcons.Security,
                title = "HTTP Proxy",
                subtitle = if (!proxyHost.isNullOrBlank() && proxyPort != null) "$proxyHost:$proxyPort" else "Disabled (Direct / DoH Fallback)",
                onClick = { showProxyDialog = true },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // About Section (GitHub, Check update, About)
            SettingsSectionHeader("About")

            SettingsRow(
                icon = LatteIcons.Code,
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
                icon = LatteIcons.SystemUpdate,
                title = "Check for Updates",
                subtitle = if (isCheckingUpdate) "Checking for updates..." else "Version ${BuildConfig.VERSION_NAME}",
                onClick = {
                    if (isCheckingUpdate) return@SettingsRow
                    isCheckingUpdate = true
                    coroutineScope.launch {
                        when (val result = updateService.checkForUpdate()) {
                            is UpdateCheckResult.Available -> {
                                updateInfoToPrompt = result.updateInfo
                                downloadProgress = null
                                downloadedApkFile = null
                                downloadErrorMessage = null
                            }
                            is UpdateCheckResult.UpToDate -> {
                                ToastManager.showInfo("You're using the latest version (${BuildConfig.VERSION_NAME})")
                            }
                            is UpdateCheckResult.Error -> {
                                ToastManager.showError(result.message)
                            }
                        }
                        isCheckingUpdate = false
                    }
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

    if (showProxyDialog) {
        var tempHost by remember { mutableStateOf(proxyHost.orEmpty()) }
        var tempPort by remember { mutableStateOf(proxyPort?.toString().orEmpty()) }

        AlertDialog(
            onDismissRequest = { showProxyDialog = false },
            title = { Text("HTTP Proxy") },
            text = {
                Column {
                    Text(
                        "Configure an optional HTTP proxy for restricted networks (e.g. Clash/V2Ray on localhost).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempHost,
                        onValueChange = { tempHost = it },
                        label = { Text("Host (e.g. 127.0.0.1)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempPort,
                        onValueChange = { tempPort = it },
                        label = { Text("Port (e.g. 7890)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val portInt = tempPort.trim().toIntOrNull()
                        if (tempHost.isNotBlank() && portInt != null && portInt in 1..65535) {
                            preferences.setHttpProxy(tempHost.trim(), portInt)
                            ToastManager.showSuccess("Proxy set to ${tempHost.trim()}:$portInt")
                        } else if (tempHost.isBlank() && tempPort.isBlank()) {
                            preferences.setHttpProxy(null, null)
                            ToastManager.showInfo("Proxy disabled")
                        } else {
                            ToastManager.showWarning("Invalid proxy host or port")
                        }
                        showProxyDialog = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    if (!proxyHost.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                preferences.setHttpProxy(null, null)
                                ToastManager.showInfo("Proxy disabled")
                                showProxyDialog = false
                            },
                        ) {
                            Text("Disable")
                        }
                    }
                    TextButton(onClick = { showProxyDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
        )
    }

    val updateInfo = updateInfoToPrompt
    if (updateInfo != null) {
        if (!updateInfo.isDeviceSupported) {
            AlertDialog(
                onDismissRequest = { updateInfoToPrompt = null },
                title = { Text("Unsupported Android Version") },
                text = {
                    Column {
                        Text(
                            text = "Latte v${updateInfo.versionName} requires Android API ${updateInfo.minSdk} or newer.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your device is running Android API ${Build.VERSION.SDK_INT}. This update cannot be installed on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { updateInfoToPrompt = null }) {
                        Text("Dismiss")
                    }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = {
                    if (!updateInfo.isMandatory && downloadProgress !is DownloadProgress.Downloading) {
                        updateInfoToPrompt = null
                    }
                },
                title = {
                    Text(if (updateInfo.isMandatory) "Required Update Available" else "Update Available")
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = "Version ${updateInfo.versionName} is available (current: ${BuildConfig.VERSION_NAME})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (updateInfo.isMandatory) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "This update is required to continue using Latte.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (updateInfo.fileSize > 0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Download size: ${formatFileSize(updateInfo.fileSize)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (updateInfo.releaseNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Release Notes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = updateInfo.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        when (val prog = downloadProgress) {
                            is DownloadProgress.Downloading -> {
                                Spacer(modifier = Modifier.height(16.dp))
                                val fraction = prog.fraction
                                if (fraction != null) {
                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${formatFileSize(prog.bytesDownloaded)} / ${formatFileSize(prog.totalBytes)} (${(fraction * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${formatFileSize(prog.bytesDownloaded)} downloaded",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            is DownloadProgress.Failed -> {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = downloadErrorMessage ?: "Download failed: ${prog.error.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            is DownloadProgress.Completed -> {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Download complete. Ready to install.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            null -> Unit
                        }
                    }
                },
                confirmButton = {
                    when (downloadProgress) {
                        is DownloadProgress.Completed -> {
                            TextButton(
                                onClick = {
                                    val file = downloadedApkFile
                                    if (file != null && file.exists()) {
                                        if (!ApkInstaller.canRequestPackageInstalls(context)) {
                                            ToastManager.showWarning("Please allow installation of unknown apps for Latte")
                                            ApkInstaller.openInstallPermissionSettings(context)
                                        } else {
                                            val installResult = ApkInstaller.installApk(context, file)
                                            if (installResult.isFailure) {
                                                ToastManager.showError("Failed to launch installer: ${installResult.exceptionOrNull()?.message}")
                                            }
                                        }
                                    } else {
                                        ToastManager.showError("Installer file not found")
                                    }
                                },
                            ) {
                                Text("Install")
                            }
                        }
                        is DownloadProgress.Downloading -> {
                            TextButton(
                                onClick = {
                                    downloadJob?.cancel()
                                    downloadJob = null
                                    downloadProgress = null
                                },
                            ) {
                                Text("Cancel")
                            }
                        }
                        else -> {
                            TextButton(
                                onClick = {
                                    downloadProgress = DownloadProgress.Downloading(0L, updateInfo.fileSize)
                                    downloadJob = coroutineScope.launch {
                                        updateDownloader.download(updateInfo).collect { event ->
                                            downloadProgress = event
                                            when (event) {
                                                is DownloadProgress.Completed -> {
                                                    downloadedApkFile = event.file
                                                    if (ApkInstaller.canRequestPackageInstalls(context)) {
                                                        val installResult = ApkInstaller.installApk(context, event.file)
                                                        if (installResult.isFailure) {
                                                            ToastManager.showError("Failed to launch installer: ${installResult.exceptionOrNull()?.message}")
                                                        }
                                                    } else {
                                                        ToastManager.showWarning("Please allow installation of unknown apps for Latte")
                                                        ApkInstaller.openInstallPermissionSettings(context)
                                                    }
                                                }
                                                is DownloadProgress.Failed -> {
                                                    downloadErrorMessage = event.error.message ?: "Download failed"
                                                }
                                                is DownloadProgress.Downloading -> Unit
                                            }
                                        }
                                    }
                                },
                            ) {
                                Text(if (downloadProgress is DownloadProgress.Failed) "Retry" else "Download & Install")
                            }
                        }
                    }
                },
                dismissButton = {
                    if (!updateInfo.isMandatory && downloadProgress !is DownloadProgress.Downloading) {
                        TextButton(
                            onClick = { updateInfoToPrompt = null },
                        ) {
                            Text("Later")
                        }
                    }
                },
            )
        }
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
