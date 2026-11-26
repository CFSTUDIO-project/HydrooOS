package com.cfstudio.hydrogenos

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.widget.TextView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cfstudio.hydrogenos.data.HydrogenPreferenceStore
import com.cfstudio.hydrogenos.data.HydrogenPreferences
import com.cfstudio.hydrogenos.data.LaunchableApp
import com.cfstudio.hydrogenos.ui.GlassCard
import com.cfstudio.hydrogenos.ui.ScreenHeader
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun launchInstalledApp(activity: MainActivity, app: LaunchableApp) {
    runCatching {
        val launcherApps = activity.getSystemService(LauncherApps::class.java)
        launcherApps.startMainActivity(
            ComponentName(app.packageName, app.className),
            app.user,
            null,
            ActivityOptions.makeBasic().toBundle()
        )
    }
}

fun openAppInfo(activity: MainActivity, app: LaunchableApp) {
    runCatching {
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    activity: MainActivity,
    apps: List<LaunchableApp>,
    preferences: HydrogenPreferences,
    backdrop: Bitmap?,
    store: HydrogenPreferenceStore,
    onDrawer: () -> Unit,
    onSettings: () -> Unit,
    onWallpapers: () -> Unit,
    onAbout: () -> Unit,
    onRefreshApps: () -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    onToggleFavorite: (LaunchableApp) -> Unit,
    onAppInfo: (LaunchableApp) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val favorites = apps.filter { it.packageName in preferences.favoritePackages }
    val homeApps = (if (favorites.isNotEmpty()) favorites else apps).take(8)
    val dockApps = (if (favorites.isNotEmpty()) favorites else apps).take(preferences.dockCount)
    val effectiveGlass = if (preferences.batterySaver) 0f else preferences.glassIntensity
    var selectedApp by remember { mutableStateOf<LaunchableApp?>(null) }
    var time by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = LocalDateTime.now()
            delay(30_000)
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HydrogenMark(Modifier.size(37.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("HYDROGEN", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp, fontSize = 12.sp)
                Text("HOME  ·  V1", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, letterSpacing = 1.6.sp)
            }
            IconButton(onClick = onWallpapers) { Icon(Icons.Outlined.Wallpaper, contentDescription = "Wallpaper", tint = MaterialTheme.colorScheme.onSurface) }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.weight(1.05f).height(preferences.widgetHeight.dp), backdrop, effectiveGlass) {
                Column(Modifier.fillMaxSize().padding(17.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("${time.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))}", color = Color(0xFFD4EEF3), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(time.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White, fontSize = 37.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.6).sp)
                    Text("Based on Android  ·  V1", color = Color(0xFFB3D4DD), fontSize = 9.sp, letterSpacing = 0.5.sp)
                }
            }
            GlassCard(Modifier.weight(0.95f).height(preferences.widgetHeight.dp), backdrop, effectiveGlass) {
                if (preferences.widgetId >= 0) {
                    AndroidView(
                        factory = { activity.createWidgetView(preferences.widgetId) ?: TextView(it).apply { text = "Widget unavailable"; setTextColor(android.graphics.Color.WHITE) } },
                        modifier = Modifier.fillMaxSize().padding(4.dp).onSizeChanged { size ->
                            activity.updateWidgetDimensions(preferences.widgetId, size.width, size.height)
                        }
                    )
                } else {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Icon(Icons.Outlined.Widgets, contentDescription = null, tint = Color(0xFFB9EDF1), modifier = Modifier.size(22.dp))
                        Column {
                            Text("Your space", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("Tambahkan widget Android", color = Color(0xFFD0E8EC), fontSize = 10.sp)
                        }
                        TextButton(onClick = { activity.startWidgetPicker() }, contentPadding = PaddingValues(0.dp)) { Text("ADD WIDGET  +", fontSize = 9.sp, letterSpacing = 0.9.sp) }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your apps", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            TextButton(onClick = onDrawer) { Text("All apps"); Spacer(Modifier.width(5.dp)); Icon(Icons.Outlined.Apps, contentDescription = null, modifier = Modifier.size(17.dp)) }
        }

        if (apps.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth().weight(1f), backdrop, effectiveGlass) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Apps, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Belum ada aplikasi yang terdeteksi", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                    Text("Periksa kembali akses package visibility atau muat ulang daftar aplikasi.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextButton(onClick = onRefreshApps) { Text("Muat ulang") }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(preferences.gridColumns),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                gridItems(homeApps, key = { it.packageName + it.className }) { app ->
                    AppIconTile(
                        app = app,
                        iconSize = preferences.iconSize,
                        showLabel = preferences.showLabels,
                        favorite = app.packageName in preferences.favoritePackages,
                        onClick = { onLaunch(app) },
                        onLongClick = { selectedApp = app }
                    )
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth().height(76.dp), backdrop, effectiveGlass, radius = 24) {
            Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                dockApps.forEach { app ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Image(app.icon, app.label, Modifier.size((preferences.iconSize * 0.72f).dp).clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = { onLaunch(app) }, onLongClick = { selectedApp = app }), contentScale = ContentScale.Fit)
                    }
                }
                IconButton(onClick = onDrawer) { Icon(Icons.Outlined.Apps, contentDescription = "App drawer", tint = MaterialTheme.colorScheme.onSurface) }
            }
        }
    }

    selectedApp?.let { app ->
        AppActionsDialog(
            app = app,
            isFavorite = app.packageName in preferences.favoritePackages,
            onDismiss = { selectedApp = null },
            onLaunch = { selectedApp = null; onLaunch(app) },
            onFavorite = { selectedApp = null; onToggleFavorite(app) },
            onInfo = { selectedApp = null; onAppInfo(app) }
        )
    }
}

@Composable
fun AppDrawerScreen(
    apps: List<LaunchableApp>,
    preferences: HydrogenPreferences,
    onBack: () -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    onToggleFavorite: (LaunchableApp) -> Unit,
    onAppInfo: (LaunchableApp) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<LaunchableApp?>(null) }
    val visible = remember(apps, query, preferences.sortMode) {
        apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
            .let { list -> if (preferences.sortMode == "package") list.sortedBy { it.packageName.lowercase() } else list.sortedBy { it.label.lowercase() } }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        ScreenHeader("App Library", "${apps.size} launchable activities", onBack)
        Spacer(Modifier.height(14.dp))
        GlassCard(Modifier.fillMaxWidth().height(54.dp), null, 0.65f, radius = 18) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f),
                    singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { inner -> if (query.isEmpty()) Text("Search apps or package", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp); inner() }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("${visible.size} RESULTS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, letterSpacing = 1.2.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (preferences.drawerMode == "list") {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(visible, key = { it.packageName + it.className }) { app ->
                    AppListRow(app, favorite = app.packageName in preferences.favoritePackages, onClick = { onLaunch(app) }, onLongClick = { selectedApp = app })
                }
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(preferences.gridColumns), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                gridItems(visible, key = { it.packageName + it.className }) { app ->
                    AppIconTile(app, preferences.iconSize, preferences.showLabels, app.packageName in preferences.favoritePackages, onClick = { onLaunch(app) }, onLongClick = { selectedApp = app })
                }
            }
        }
    }
    selectedApp?.let { app ->
        AppActionsDialog(app, app.packageName in preferences.favoritePackages, { selectedApp = null }, { selectedApp = null; onLaunch(app) }, { selectedApp = null; onToggleFavorite(app) }, { selectedApp = null; onAppInfo(app) })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIconTile(
    app: LaunchableApp,
    iconSize: Int,
    showLabel: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size((iconSize + 9).dp), contentAlignment = Alignment.Center) {
            Image(app.icon, app.label, Modifier.size(iconSize.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Fit)
            if (favorite) Surface(Modifier.align(Alignment.TopEnd).size(14.dp), shape = CircleShape, color = Color(0xE61A3544)) {
                Icon(Icons.Outlined.Star, null, tint = Color(0xFF8DE3E8), modifier = Modifier.padding(2.dp))
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(app.label, color = Color(0xFFEAF7FA), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListRow(app: LaunchableApp, favorite: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().height(66.dp), null, 0.55f, radius = 17) {
        Row(Modifier.fillMaxSize().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(app.icon, app.label, Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (favorite) Icon(Icons.Outlined.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AppActionsDialog(
    app: LaunchableApp,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onFavorite: () -> Unit,
    onInfo: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14283A),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFB8D1DA),
        title = { Text(app.label) },
        text = { Text(app.packageName, fontSize = 11.sp) },
        confirmButton = { TextButton(onClick = onLaunch) { Text("Open") } },
        dismissButton = {
            Row {
                TextButton(onClick = onFavorite) { Text(if (isFavorite) "Remove favorite" else "Add favorite") }
                TextButton(onClick = onInfo) { Text("App info") }
            }
        }
    )
}
