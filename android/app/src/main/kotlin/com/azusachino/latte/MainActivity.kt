package com.azusachino.latte

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.TimeUnit

class MainActivity : FlutterActivity() {
    private val channelName = "com.azusachino.latte/download"
    private val notificationPermissionRequestCode = 4001

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                if (call.method == "openDownloadNotifications") {
                    startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        },
                    )
                    result.success(null)
                    return@setMethodCallHandler
                }
                if (call.method != "saveImage") {
                    result.notImplemented()
                    return@setMethodCallHandler
                }

                val sourceUrl = call.argument<String>("sourceUrl")
                val displayName = call.argument<String>("displayName")
                val mimeType = call.argument<String>("mimeType")
                if (sourceUrl == null || displayName == null || mimeType == null) {
                    result.error("invalid_request", "Download arguments are incomplete", null)
                    return@setMethodCallHandler
                }

                requestNotificationPermission()
                val workManager = WorkManager.getInstance(applicationContext)
                val uniqueName = DownloadWorker.uniqueWorkName(displayName)
                val workInfos = workManager.getWorkInfosForUniqueWork(uniqueName)
                workInfos.addListener(
                    {
                        val active = runCatching {
                            workInfos.get().any { !it.state.isFinished }
                        }.getOrDefault(false)
                        if (active) {
                            result.success(
                                mapOf(
                                    "status" to "already_running",
                                    "album" to DownloadWorker.album,
                                    "displayName" to displayName,
                                ),
                            )
                            return@addListener
                        }

                        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                            .setInputData(
                                DownloadWorker.inputData(
                                    sourceUrl = sourceUrl,
                                    displayName = displayName,
                                    mimeType = mimeType,
                                ),
                            )
                            .setBackoffCriteria(
                                BackoffPolicy.EXPONENTIAL,
                                10,
                                TimeUnit.SECONDS,
                            )
                            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                            .build()
                        workManager.enqueueUniqueWork(
                            uniqueName,
                            ExistingWorkPolicy.KEEP,
                            request,
                        )
                        result.success(
                            mapOf(
                                "status" to "started",
                                "album" to DownloadWorker.album,
                                "displayName" to displayName,
                            ),
                        )
                    },
                    ContextCompat.getMainExecutor(applicationContext),
                )
            }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                notificationPermissionRequestCode,
            )
        }
    }
}
