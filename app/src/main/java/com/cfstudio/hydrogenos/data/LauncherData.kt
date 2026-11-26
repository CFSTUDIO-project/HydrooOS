package com.cfstudio.hydrogenos.data

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Process
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

/** A launchable activity discovered from Android, never a hard-coded app catalogue. */
data class LaunchableApp(
    val label: String,
    val packageName: String,
    val className: String,
    val user: android.os.UserHandle,
    val icon: ImageBitmap,
)

private val palettes = arrayOf(
    intArrayOf(0xFF030A1B.toInt(), 0xFF08769F.toInt(), 0xFF0CDDCB.toInt()),
    intArrayOf(0xFF040818.toInt(), 0xFF234EB4.toInt(), 0xFF47CDF2.toInt()),
    intArrayOf(0xFF050C18.toInt(), 0xFF0A7D70.toInt(), 0xFF63E2B8.toInt()),
    intArrayOf(0xFF0B0A20.toInt(), 0xFF423096.toInt(), 0xFF31AFDE.toInt()),
    intArrayOf(0xFF030E1B.toInt(), 0xFF0E507A.toInt(), 0xFFA9E8EF.toInt()),
    intArrayOf(0xFF090D19.toInt(), 0xFF5D2B67.toInt(), 0xFF3094B7.toInt()),
    intArrayOf(0xFF04111B.toInt(), 0xFF0B6C85.toInt(), 0xFFC8E4E2.toInt()),
    intArrayOf(0xFF0C0D18.toInt(), 0xFF374F72.toInt(), 0xFF7BAECB.toInt()),
    intArrayOf(0xFF020B14.toInt(), 0xFF034B59.toInt(), 0xFF3AB69B.toInt()),
    intArrayOf(0xFF0D0E18.toInt(), 0xFF5A553F.toInt(), 0xFFBACDA5.toInt()),
    intArrayOf(0xFF04081A.toInt(), 0xFF213F7D.toInt(), 0xFF82C7E0.toInt()),
    intArrayOf(0xFF070E16.toInt(), 0xFF185F65.toInt(), 0xFF6CBFB0.toInt()),
)

val wallpaperNames = listOf(
    "Tidal Signal", "Polar Current", "Emerald Drift", "Deep Aurora",
    "Glass Horizon", "Violet Tide", "Aqua Veil", "Blue Hour",
    "Pressure Wave", "Mineral Light", "Celestial Flow", "Silent Current"
)

fun wallpaperColors(index: Int): IntArray = palettes[index.coerceIn(0, palettes.lastIndex)]

suspend fun loadWallpaperBitmap(context: Context, index: Int, customUri: String): Bitmap? = withContext(Dispatchers.IO) {
    try {
        if (customUri.isNotBlank()) {
            val uri = Uri.parse(customUri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val maxDimension = 2048
            var sample = 1
            while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } else {
            renderWallpaper(index)
        }
    } catch (_: Exception) {
        null
    }
}

private fun renderWallpaper(index: Int): Bitmap {
    val width = 720
    val height = 1600
    val colors = wallpaperColors(index)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = RadialGradient(width * 0.24f, height * 0.20f, width * 0.62f, intArrayOf(0x5577F2F2, 0x00000000), null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = RadialGradient(width * 0.82f, height * 0.72f, width * 0.55f, intArrayOf(0x5564D7F0, 0x00000000), null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null
    for (layer in 0..4) {
        val path = Path()
        for (step in 0..80) {
            val y = -100f + step / 80f * (height + 200f)
            val x = width * (0.48f + 0.30f * sin(y / height * 7.2f + index * 0.38f + layer * 0.32f))
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.color = ((0x12 + layer * 4) shl 24) or 0x00E8F8FF
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 18f + layer * 10f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(path, paint)
        if (layer == 0 || layer == 3) {
            paint.color = 0x3CFFFFFF
            paint.strokeWidth = 2.3f
            canvas.drawPath(path, paint)
        }
    }
    paint.style = Paint.Style.FILL
    paint.shader = null
    paint.color = 0x20020A14
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    return bitmap
}

suspend fun loadLaunchableApps(context: Context): List<LaunchableApp> = withContext(Dispatchers.IO) {
    runCatching {
        val service = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        service.getActivityList(null, Process.myUserHandle()).mapNotNull { info ->
            runCatching {
                val iconBitmap = info.getIcon(0).toBitmap(144, 144).asImageBitmap()
                LaunchableApp(
                    label = info.label?.toString()?.takeIf(String::isNotBlank) ?: info.applicationInfo.packageName,
                    packageName = info.applicationInfo.packageName,
                    className = info.name,
                    user = info.user,
                    icon = iconBitmap,
                )
            }.getOrNull()
        }.distinctBy { it.packageName to it.className }.sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())
}
