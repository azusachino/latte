package com.azusachino.latte.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azusachino.latte.BuildConfig
import com.azusachino.latte.data.cache.CacheManager
import com.azusachino.latte.data.settings.LatteBackupCodec
import com.azusachino.latte.data.settings.LattePreferences
import com.azusachino.latte.data.settings.ThemeMode
import com.azusachino.latte.data.update.ApkInstaller
import com.azusachino.latte.data.update.DownloadProgress
import com.azusachino.latte.data.update.UpdateCheckResult
import com.azusachino.latte.data.update.UpdateDownloader
import com.azusachino.latte.data.update.UpdateInfo
import com.azusachino.latte.data.update.UpdateService
import com.azusachino.latte.ui.common.GlassPillChip
import com.azusachino.latte.ui.common.LatteIcons
import com.azusachino.latte.ui.common.ToastManager
import com.azusachino.latte.ui.theme.GlassDefaults
import com.azusachino.latte.ui.theme.glassPillSelection
import com.azusachino.latte.ui.theme.glassSurface
import com.azusachino.latte.ui.theme.pressScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import com.azusachino.latte.ui.common.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: LattePreferences,
    onBack: () -> Unit,
    onOpenAccountManager: () -> Unit = {},
    onOpenViewHistory: () -> Unit = {},
    onClearSearchCaches: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val columnCount by preferences.columnCount.collectAsStateWithLifecycle()
    val themeMode by preferences.themeMode.collectAsStateWithLifecycle()
    val safeMode by preferences.safeMode.collectAsStateWithLifecycle()

    val cacheManager = remember {
        CacheManager(
            context = context,
            preferences = preferences,
            onClearSearchCaches = onClearSearchCaches,
        )
    }
    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cacheSizeBytes = withContext(Dispatchers.IO) {
            cacheManager.getTotalCacheSizeBytes()
        }
    }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }

    val proxyHost by preferences.proxyHost.collectAsStateWithLifecycle()
    val proxyPort by preferences.proxyPort.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val updateService = remember { UpdateService() }
    val updateDownloader = remember(context) { UpdateDownloader(context) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfoToPrompt by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var downloadErrorMessage by remember { mutableStateOf<String?>(null) }

    var pendingRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(preferences.exportBackup()) }
                            ?: error("Could not open the backup file")
                    }.isSuccess
                }
                if (saved) {
                    ToastManager.showSuccess("Backup saved")
                } else {
                    ToastManager.showWarning("Could not save the backup")
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes == null) {
                    ToastManager.showWarning("Could not read the backup file")
                } else {
                    pendingRestoreBytes = bytes
                }
            }
        }
    }

    pendingRestoreBytes?.let { bytes ->
        AlertDialog(
            onDismissRequest = { pendingRestoreBytes = null },
            title = { Text("Restore backup?") },
            text = {
                Text(
                    "This replaces your settings, favorite tags, recent searches and viewing history. " +
                        "Accounts are not included, so sign in again if needed.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestoreBytes = null
                        coroutineScope.launch {
                            if (preferences.importBackup(bytes).isSuccess) {
                                ToastManager.showSuccess("Backup restored")
                            } else {
                                ToastManager.showWarning("That file is not a valid Latte backup")
                            }
                        }
                    },
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreBytes = null }) {
                    Text("Cancel")
                }
            },
        )
    }

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
                            text = "Settings",
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
            // Accounts Section
            SettingsSectionHeader("Accounts")
            SettingsGroupCard {
                SettingsRow(
                    icon = LatteIcons.AccountCircle,
                    title = "Platforms & accounts",
                    subtitle = "Connect, switch, and manage yande.re or Pixiv sessions",
                    showChevron = true,
                    onClick = onOpenAccountManager,
                )
            }

            // Content Section
            SettingsSectionHeader("Content")
            SettingsGroupCard {
                SettingsSwitchRow(
                    icon = LatteIcons.Security,
                    title = "Safe Mode",
                    subtitle = "Show safe content only on yande.re and konachan",
                    checked = safeMode,
                    onCheckedChange = { preferences.setSafeMode(it) },
                )
            }

            // Appearance Section
            SettingsSectionHeader("Appearance")
            SettingsGroupCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GlassPillChip(
                            selected = themeMode == ThemeMode.SYSTEM,
                            onClick = { preferences.setThemeMode(ThemeMode.SYSTEM) },
                            label = "System",
                        )
                        GlassPillChip(
                            selected = themeMode == ThemeMode.LIGHT,
                            onClick = { preferences.setThemeMode(ThemeMode.LIGHT) },
                            label = "Light",
                        )
                        GlassPillChip(
                            selected = themeMode == ThemeMode.DARK,
                            onClick = { preferences.setThemeMode(ThemeMode.DARK) },
                            label = "Dark",
                        )
                    }
                }

                SettingsDivider()

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = "Grid Columns",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        (1..3).forEach { count ->
                            GlassPillChip(
                                selected = columnCount == count,
                                onClick = { preferences.setColumnCount(count) },
                                label = "$count column${if (count > 1) "s" else ""}",
                            )
                        }
                    }
                }
            }

            // Storage & Network Section
            SettingsSectionHeader("Data")
            SettingsGroupCard {
                SettingsRow(
                    icon = Icons.Default.DateRange,
                    title = "Viewing History",
                    subtitle = "Posts you have opened",
                    showChevron = true,
                    onClick = onOpenViewHistory,
                )

                SettingsDivider()

                SettingsRow(
                    icon = Icons.Default.KeyboardArrowUp,
                    title = "Export Backup",
                    subtitle = "Settings, favorite tags, searches and history (no accounts)",
                    onClick = { exportLauncher.launch(LatteBackupCodec.fileName(LocalDateTime.now())) },
                )

                SettingsDivider()

                SettingsRow(
                    icon = Icons.Default.Refresh,
                    title = "Restore Backup",
                    subtitle = "Replace local data with a backup file",
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                )
            }

            SettingsSectionHeader("Storage & Network")
            SettingsGroupCard {
                SettingsRow(
                    icon = Icons.Default.Delete,
                    title = "Clear Cache",
                    subtitle = "${formatFileSize(cacheSizeBytes)} total (images, HTTP, updates)",
                    onClick = {
                        showClearCacheDialog = true
                    },
                )

                SettingsDivider()

                SettingsRow(
                    icon = LatteIcons.Security,
                    title = "HTTP Proxy",
                    subtitle = if (!proxyHost.isNullOrBlank() && proxyPort != null) "$proxyHost:$proxyPort" else "Disabled (Direct / DoH Fallback)",
                    onClick = { showProxyDialog = true },
                )
            }

            // Updates & About Section
            SettingsSectionHeader("Updates & About")
            SettingsGroupCard {
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

                SettingsDivider()

                SettingsRow(
                    icon = LatteIcons.Code,
                    title = "GitHub",
                    subtitle = "https://github.com/azusachino/latte",
                    showChevron = true,
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/azusachino/latte"),
                        )
                        context.startActivity(intent)
                    },
                )

                SettingsDivider()

                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "About Latte",
                    subtitle = "Version ${BuildConfig.VERSION_NAME} · Fast native client",
                    onClick = {
                        showAboutDialog = true
                    },
                )

                SettingsDivider()

                SettingsRow(
                    icon = LatteIcons.Security,
                    title = "Open Source License",
                    subtitle = "MIT License · azusachino",
                    onClick = {
                        showLicenseDialog = true
                    },
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
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

    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text("Open Source License") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "MIT License",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Copyright (c) 2026 azusachino\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
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

    if (showClearCacheDialog) {
        ClearCacheDialog(
            cacheManager = cacheManager,
            onDismissRequest = { showClearCacheDialog = false },
            onClearSuccess = { clearedTypes ->
                coroutineScope.launch {
                    cacheSizeBytes = withContext(Dispatchers.IO) {
                        cacheManager.getTotalCacheSizeBytes()
                    }
                }
                showClearCacheDialog = false
                ToastManager.showSuccess("Cleared ${clearedTypes.size} cache categories")
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

