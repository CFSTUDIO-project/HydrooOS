package com.cfstudio.hydrogenos.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cfstudio.hydrogenos.graphics.LiquidGlassSurface

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    backdrop: Bitmap? = null,
    intensity: Float = 0.72f,
    radius: Int = 26,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius.dp)
    Box(modifier.clip(shape)) {
        AndroidView(
            factory = { context -> LiquidGlassSurface(context).apply { setMaterial(backdrop, intensity, radius.toFloat()) } },
            update = { it.setMaterial(backdrop, intensity, radius.toFloat()) },
            modifier = Modifier.matchParentSize()
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    listOf(Color(0x1EFFFFFF), Color(0x060B1622), MaterialTheme.colorScheme.surface.copy(alpha = 0.10f))
                )
            )
        )
        content()
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.ArrowBack,
                contentDescription = "Kembali",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            androidx.compose.material3.Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
