package com.azusachino.latte.ui.detail

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.azusachino.latte.ui.common.ToastManager
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WallpaperTemplate(val title: String, val subtitle: String) {
    FILL_SCREEN("Fill Screen", "Center-crop to exact screen ratio"),
    FIT_SCREEN("Fit Screen", "Full image with ambient backdrop"),
    DIRECT("Direct", "Raw bitmap without adjustment"),
}

data class WallpaperCropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    fun toAndroidRect(): Rect = Rect(left, top, right, bottom)
}

/**
 * Calculates a crop rectangle centered in the bitmap that matches the aspect ratio
 * of the target display screen.
 */
internal fun calculateCenterCropHint(
    imageWidth: Int,
    imageHeight: Int,
    screenWidth: Int,
    screenHeight: Int,
): WallpaperCropRect {
    if (imageWidth <= 0 || imageHeight <= 0 || screenWidth <= 0 || screenHeight <= 0) {
        return WallpaperCropRect(0, 0, imageWidth.coerceAtLeast(0), imageHeight.coerceAtLeast(0))
    }
    val screenRatio = screenHeight.toDouble() / screenWidth.toDouble()
    val imageRatio = imageHeight.toDouble() / imageWidth.toDouble()

    return if (imageRatio > screenRatio) {
        // Image is taller than the screen: crop top and bottom.
        val targetHeight = (imageWidth * screenRatio).roundToInt().coerceIn(1, imageHeight)
        val top = ((imageHeight - targetHeight) / 2).coerceAtLeast(0)
        WallpaperCropRect(0, top, imageWidth, top + targetHeight)
    } else {
        // Image is wider than the screen: crop left and right.
        val targetWidth = (imageHeight / screenRatio).roundToInt().coerceIn(1, imageWidth)
        val left = ((imageWidth - targetWidth) / 2).coerceAtLeast(0)
        WallpaperCropRect(left, 0, left + targetWidth, imageHeight)
    }
}

/**
 * Creates a screen-sized bitmap placing the fully fitted [original] image centered over
 * a blurred, darkened ambient background generated from the original.
 */
internal fun createFitScreenBitmap(
    original: Bitmap,
    screenWidth: Int,
    screenHeight: Int,
): Bitmap {
    val targetWidth = screenWidth.coerceAtLeast(1)
    val targetHeight = screenHeight.coerceAtLeast(1)
    val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    // 1. Draw ambient blurred/stretched background
    val ambientSample = try {
        // Downsample heavily for smooth ambient blur effect
        val sampleW = (original.width / 16).coerceAtLeast(16)
        val sampleH = (original.height / 16).coerceAtLeast(16)
        val downscaled = original.scale(sampleW, sampleH)
        downscaled.scale(targetWidth, targetHeight)
    } catch (_: Throwable) {
        null
    }

    if (ambientSample != null) {
        canvas.drawBitmap(ambientSample, 0f, 0f, paint)
        // Darken the background scrim so foreground content pops
        paint.color = Color.argb(128, 0, 0, 0)
        canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), paint)
        paint.color = Color.BLACK
    } else {
        canvas.drawColor(Color.BLACK)
    }

    // 2. Calculate centered aspect-fit destination rect for original image
    val scale = minOf(
        targetWidth.toFloat() / original.width.toFloat(),
        targetHeight.toFloat() / original.height.toFloat(),
    )
    val fittedWidth = original.width.toFloat() * scale
    val fittedHeight = original.height.toFloat() * scale
    val left = (targetWidth.toFloat() - fittedWidth) / 2f
    val top = (targetHeight.toFloat() - fittedHeight) / 2f

    val srcRect = Rect(0, 0, original.width, original.height)
    val dstRect = RectF(left, top, left + fittedWidth, top + fittedHeight)
    canvas.drawBitmap(original, srcRect, dstRect, paint)

    return result
}

internal object WallpaperHelper {

    fun applyWallpaper(
        context: Context,
        imageUrl: String,
        template: WallpaperTemplate,
        whichFlags: Int,
        scope: CoroutineScope,
        onDone: () -> Unit = {},
    ) {
        ToastManager.showInfo("Preparing wallpaper...")
        scope.launch(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .size(coil3.size.Size.ORIGINAL)
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                try {
                    val original = result.image.toBitmap()
                    val wallpaperManager = WallpaperManager.getInstance(context)
                    val metrics = context.resources.displayMetrics
                    val screenW = metrics.widthPixels
                    val screenH = metrics.heightPixels

                    when (template) {
                        WallpaperTemplate.FILL_SCREEN -> {
                            val cropHint = calculateCenterCropHint(original.width, original.height, screenW, screenH)
                            wallpaperManager.setBitmap(original, cropHint.toAndroidRect(), true, whichFlags)
                        }
                        WallpaperTemplate.FIT_SCREEN -> {
                            val composite = createFitScreenBitmap(original, screenW, screenH)
                            wallpaperManager.setBitmap(composite, null, true, whichFlags)
                        }
                        WallpaperTemplate.DIRECT -> {
                            wallpaperManager.setBitmap(original, null, true, whichFlags)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        ToastManager.showSuccess("Wallpaper updated")
                        onDone()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        ToastManager.showError("Failed to set wallpaper: ${e.message ?: "error"}")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    ToastManager.showError("Failed to load image for wallpaper")
                }
            }
        }
    }

    fun launchSystemCropper(
        context: Context,
        imageUrl: String,
        scope: CoroutineScope,
        onDone: () -> Unit = {},
    ) {
        ToastManager.showInfo("Opening system wallpaper tool...")
        scope.launch(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .size(coil3.size.Size.ORIGINAL)
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                try {
                    val bitmap = result.image.toBitmap()
                    val wallpaperDir = File(context.cacheDir, "wallpaper").apply { mkdirs() }
                    val tempFile = File(wallpaperDir, "wallpaper_crop_${System.currentTimeMillis()}.png")
                    FileOutputStream(tempFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }

                    val authority = "${context.packageName}.fileprovider"
                    val contentUri = FileProvider.getUriForFile(context, authority, tempFile)

                    withContext(Dispatchers.Main) {
                        val wallpaperManager = WallpaperManager.getInstance(context)
                        val cropIntent = try {
                            wallpaperManager.getCropAndSetWallpaperIntent(contentUri)
                        } catch (_: Exception) {
                            null
                        }

                        if (cropIntent != null) {
                            cropIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(cropIntent)
                        } else {
                            val attachIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                                setDataAndType(contentUri, "image/*")
                                putExtra("mimeType", "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(attachIntent, "Set as Wallpaper"))
                        }
                        onDone()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        ToastManager.showError("Could not open system cropper: ${e.message ?: "error"}")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    ToastManager.showError("Failed to load image for wallpaper")
                }
            }
        }
    }
}
