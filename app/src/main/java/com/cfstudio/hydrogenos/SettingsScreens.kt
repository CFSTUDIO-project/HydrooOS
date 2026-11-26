package com.cfstudio.hydrogenos

import android.app.WallpaperManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cfstudio.hydrogenos.data.HydrogenPreferenceStore
import com.cfstudio.hydrogenos.data.HydrogenPreferences
import com.cfstudio.hydrogenos.data.isNewerVersionCode
import com.cfstudio.hydrogenos.data.loadWallpaperBitmap
import com.cfstudio.hydrogenos.data.wallpaperColors
import com.cfstudio.hydrogenos.data.wallpaperNames
import com.cfstudio.hydrogenos.ui.GlassCard
import com.cfstudio.hydrogenos.ui.ScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun WallpaperStudioScreen(
    activity: MainActivity,
    preferences: HydrogenPreferences,
    onBack: () -> Unit,
    onApplyPreset: (Int) -> Unit,
    onCustomSelected: (Uri) -> Unit,
    onSystemApplied: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var applying by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onCustomSelected(uri)
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        ScreenHeader("Wallpaper Studio", "Hydrogen originals · 12 compositions", onBack)
        Spacer(Modifier.height(13.dp))
        GlassCard(Modifier.fillMaxWidth().height(104.dp), null, 0.65f) {
            Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your screen, your atmosphere", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Pilih komposisi Hydrogen atau gunakan foto sendiri.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
                }
                Button(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp)) {
                    Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("My photo", fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("HYDROGEN COLLECTION", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
            Text("12 WALLPAPERS", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2), modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(13.dp),
            contentPadding = PaddingValues(bottom = 15.dp)
        ) {
            itemsIndexed(wallpaperNames) { index, name ->
                val selected = preferences.customWallpaperUri.isBlank() && preferences.wallpaperIndex == index
                Column {
                    Box(Modifier.fillMaxWidth().height(208.dp).clip(RoundedCornerShape(22.dp))) {
                        WallpaperArt(index, Modifier.fillMaxSize())
                        if (selected) Surface(Modifier.align(Alignment.TopEnd).padding(9.dp), shape = CircleShape, color = Color(0xDD0A3947)) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF9CF1E5), modifier = Modifier.padding(6.dp).size(17.dp))
                        }
                        TextButton(
                            onClick = { onApplyPreset(index) },
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(7.dp),
                            colors = ButtonDefaults.textButtonColors(containerColor = Color(0xAA081725), contentColor = Color.White)
                        ) { Text(if (selected) "Selected" else "Apply", fontSize = 11.sp) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(name, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("HYDROGEN / " + (index + 1), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp, letterSpacing = 1.sp)
                }
            }
        }
        Button(
            onClick = {
                applying = true
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            val manager = WallpaperManager.getInstance(context)
                            if (preferences.customWallpaperUri.isNotBlank()) {
                                context.contentResolver.openInputStream(Uri.parse(preferences.customWallpaperUri))?.use { manager.setStream(it) }
                                    ?: error("File foto tidak dapat dibaca")
                            } else {
                                val bitmap = loadWallpaperBitmap(context, preferences.wallpaperIndex, "") ?: error("Wallpaper tidak dapat dimuat")
                                manager.setBitmap(bitmap)
                                bitmap.recycle()
                            }
                        }
                    }
                    applying = false
                    onSystemApplied(if (result.isSuccess) "Wallpaper sistem Android berhasil diterapkan" else "Gagal menerapkan wallpaper sistem: " + (result.exceptionOrNull()?.message ?: "unknown error"))
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp), enabled = !applying
        ) { Text(if (applying) "Applying…" else "Terapkan ke wallpaper sistem") }
        Text("Aksi ini mengubah wallpaper sistem Android. Wallpaper internal Hydrogen disimpan terpisah.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun SettingsScreen(
    activity: MainActivity,
    preferences: HydrogenPreferences,
    store: HydrogenPreferenceStore,
    onBack: () -> Unit,
    onWallpapers: () -> Unit,
    onAbout: () -> Unit,
    onReplayIntro: () -> Unit,
    onStatus: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        ScreenHeader("Settings", "Shape the Hydrogen experience", onBack)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(13.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
            item {
                SettingsSection("PERSONALIZATION", "Material, color and visual tone", Icons.Outlined.Palette) {
                    SettingCard("Appearance", "Dark, light, or follow the device") {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            ChoiceChip("Dark", preferences.theme == "dark") { scope.launch { store.setTheme("dark") } }
                            ChoiceChip("Light", preferences.theme == "light") { scope.launch { store.setTheme("light") } }
                            ChoiceChip("System", preferences.theme == "system") { scope.launch { store.setTheme("system") } }
                        }
                    }
                    SettingCard("Accent color", "Applied to controls and active states") {
                        Row(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            AccentDot("aqua", Color(0xFF62DCE8), preferences.accent == "aqua") { scope.launch { store.setAccent("aqua") } }
                            AccentDot("ice", Color(0xFF9BE8F4), preferences.accent == "ice") { scope.launch { store.setAccent("ice") } }
                            AccentDot("mint", Color(0xFF73E3C0), preferences.accent == "mint") { scope.launch { store.setAccent("mint") } }
                            AccentDot("blue", Color(0xFF74B9FF), preferences.accent == "blue") { scope.launch { store.setAccent("blue") } }
                            AccentDot("violet", Color(0xFFA7A0FF), preferences.accent == "violet") { scope.launch { store.setAccent("violet") } }
                        }
                    }
                    SettingCard("Glass intensity", "Wallpaper refraction on API 33+; reduced material on older Android") {
                        LabeledSlider((preferences.glassIntensity * 100).toInt().toString() + "%", preferences.glassIntensity, { scope.launch { store.setGlassIntensity(it) } })
                    }
                    NavigationSetting("Wallpaper Studio", "12 original artworks and your own photo", Icons.Outlined.PhotoLibrary, onWallpapers)
                }
            }
            item {
                SettingsSection("HOME SCREEN", "Grid, labels and dock", Icons.Outlined.Tune) {
                    SettingCard("Grid columns", "Number of columns on the Home Screen") {
                        LabeledSlider(preferences.gridColumns.toString() + " columns", preferences.gridColumns.toFloat(), { scope.launch { store.setGridColumns(it.toInt()) } }, 3f..6f, 1f)
                    }
                    SettingCard("App icon size", "Adjust icon dimensions on Home and Drawer") {
                        LabeledSlider(preferences.iconSize.toString() + " dp", preferences.iconSize.toFloat(), { scope.launch { store.setIconSize(it.toInt()) } }, 42f..76f, 2f)
                    }
                    SettingSwitch("Icon labels", "Show app names below icons", preferences.showLabels) { scope.launch { store.setShowLabels(it) } }
                    SettingCard("Dock items", "Maximum number of favorite apps in the dock") {
                        LabeledSlider(preferences.dockCount.toString() + " items", preferences.dockCount.toFloat(), { scope.launch { store.setDockCount(it.toInt()) } }, 2f..6f, 1f)
                    }
                }
            }
            item {
                SettingsSection("APP DRAWER", "Search and application presentation", Icons.Outlined.Apps) {
                    SettingCard("Presentation", "Choose a grid or compact list") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChip("Grid", preferences.drawerMode == "grid") { scope.launch { store.setDrawerMode("grid") } }
                            ChoiceChip("List", preferences.drawerMode == "list") { scope.launch { store.setDrawerMode("list") } }
                        }
                    }
                    SettingCard("Sort applications", "Use the app name or package identifier") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChip("Name", preferences.sortMode == "name") { scope.launch { store.setSortMode("name") } }
                            ChoiceChip("Package", preferences.sortMode == "package") { scope.launch { store.setSortMode("package") } }
                        }
                    }
                }
            }
            item {
                SettingsSection("MOTION & PERFORMANCE", "Choose a comfortable level of effects", Icons.Outlined.Speed) {
                    SettingCard("Animation speed", "Controls Hydrogen screen transitions") {
                        LabeledSlider((preferences.animationSpeed * 100).toInt().toString() + "%", preferences.animationSpeed, { scope.launch { store.setAnimationSpeed(it) } }, 0.5f..1.5f, 0.1f)
                    }
                    SettingSwitch("Reduce Motion", "Shorten non-essential transitions and effects", preferences.reduceMotion) { scope.launch { store.setReduceMotion(it) } }
                    SettingSwitch("Battery Saver", "Use a lighter Home material and reduce decorative effects", preferences.batterySaver) { scope.launch { store.setBatterySaver(it) } }
                }
            }
            item {
                SettingsSection("WIDGETS & SYSTEM", "Native Android integrations", Icons.Outlined.Widgets) {
                    NavigationSetting("Add Home widget", "Choose a real Android AppWidget provider", Icons.Outlined.Widgets) { activity.startWidgetPicker() }
                    SettingCard("Widget frame height", "Resize the host area; providers receive updated size hints") {
                        LabeledSlider(preferences.widgetHeight.toString() + " dp", preferences.widgetHeight.toFloat(), { scope.launch { store.setWidgetHeight(it.toInt()) } }, 120f..260f, 8f)
                    }
                    if (preferences.widgetId >= 0) {
                        NavigationSetting("Remove current widget", "Remove the saved widget from Hydrogen Home", Icons.Outlined.Refresh) {
                            activity.removeWidget(preferences.widgetId)
                            onStatus("Widget removed from Hydrogen Home")
                        }
                    }
                    NavigationSetting("Default Home app", "Open Android's Home app selection", Icons.Outlined.CheckCircle) { activity.openDefaultLauncherSettings() }
                    SettingInfo("Notifications", "Notification badges and cross-app notification reading are not enabled in V1. No Notification Listener or notification permission is requested.")
                }
            }
            item {
                SettingsSection("PRIVACY & DIAGNOSTICS", "Local data and app status", Icons.Outlined.Info) {
                    SettingInfo("Local storage", "Preferences, wallpaper selection and widget identifier are stored on this device. App labels and icons come from Android.")
                    SettingInfo("Package visibility", "Only launchable activities are queried. QUERY_ALL_PACKAGES is not requested.")
                    SettingInfo("Default launcher status", if (activity.isHydrogenDefaultHome()) "Hydrogen is currently the default Home role holder." else "Hydrogen is not selected as the default Home app.")
                    NavigationSetting("About Hydrogen OS", "Version, update status and acknowledgements", Icons.Outlined.Info, onAbout)
                    NavigationSetting("Replay brand intro", "Play the 6.5-second droplet sequence again", Icons.Outlined.Refresh, onReplayIntro)
                }
            }
        }
    }
}

@Composable
fun AboutScreen(
    activity: MainActivity,
    preferences: HydrogenPreferences,
    backdrop: android.graphics.Bitmap?,
    onBack: () -> Unit,
    onStatus: (String) -> Unit,
) {
    var checking by remember { mutableStateOf(false) }
    var updateTitle by remember { mutableStateOf("Not Found") }
    var updateDetail by remember { mutableStateOf("Update feed belum dikonfigurasi.") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        ScreenHeader("About Hydrogen OS", "Product identity and release details", onBack)
        Spacer(Modifier.height(18.dp))
        GlassCard(Modifier.fillMaxWidth().height(250.dp), backdrop, if (preferences.batterySaver) 0f else preferences.glassIntensity, radius = 32) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0x442EE0E5), Color(0x183C9EC2), Color(0x66101E35))))) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 28.dp, y = (-42).dp).size(190.dp).clip(CircleShape).background(Color(0x2748DCE7)))
                Column(Modifier.align(Alignment.CenterStart).padding(22.dp)) {
                    Text("HYDROGEN", color = Color(0xFFB6F7F5), fontSize = 11.sp, letterSpacing = 3.sp)
                    Text("Hydrogen OS", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp)
                    Spacer(Modifier.height(7.dp))
                    Text("V1", color = Color(0xFFB6F4F1), fontSize = 43.sp, fontWeight = FontWeight.Light, letterSpacing = 4.sp)
                    Text("Berdasarkan Android", color = Color(0xFFE0F4F6), fontSize = 12.sp)
                }
                Column(Modifier.align(Alignment.BottomEnd).padding(15.dp), horizontalAlignment = Alignment.End) {
                    Text(updateTitle, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (BuildConfig.UPDATE_FEED_URL.isBlank()) "No release feed configured" else "Release metadata", color = Color(0xFFC7E9EE), fontSize = 9.sp)
                    TextButton(onClick = {
                        checking = true
                        scope.launch {
                            val result = checkUpdateFeed(BuildConfig.UPDATE_FEED_URL)
                            updateTitle = result.first
                            updateDetail = result.second
                            checking = false
                            onStatus(result.second)
                        }
                    }, enabled = !checking, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(if (checking) "Checking…" else "Periksa  →", color = Color(0xFFECFFFF), fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        Text(updateDetail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(18.dp))
        SettingsSection("RELEASE INFORMATION", "Verified local build metadata", Icons.Outlined.Info) {
            SettingInfo("Application", "com.cfstudio.hydrogenos")
            SettingInfo("Version", "V1 · " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
            SettingInfo("Android", "Minimum API " + Build.VERSION_CODES.O + " · Compile API 36")
            SettingInfo("Default Home", if (activity.isHydrogenDefaultHome()) "Selected by Android" else "Not selected")
            SettingInfo("Renderer", "AGSL wallpaper displacement on API 33+; translucent fallback below API 33. Intro uses an OpenGL ES 2.0 mesh.")
            SettingInfo("Update source", if (BuildConfig.UPDATE_FEED_URL.isBlank()) "Not configured" else BuildConfig.UPDATE_FEED_URL)
        }
        Spacer(Modifier.height(12.dp))
        Text("Hydrogen OS V1 is a third-party Android launcher. It does not replace Android firmware, the lock screen, System UI, status bar, or notification shade.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun WallpaperArt(index: Int, modifier: Modifier = Modifier) {
    val colors = remember(index) { wallpaperColors(index).map { Color(it) } }
    Canvas(modifier) {
        drawRect(brush = Brush.linearGradient(colors, start = androidx.compose.ui.geometry.Offset.Zero, end = androidx.compose.ui.geometry.Offset(size.width, size.height)))
        drawCircle(brush = Brush.radialGradient(listOf(Color(0x6677F2F2), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.18f), radius = size.width * 0.72f), radius = size.width * 0.72f, center = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.18f))
        drawCircle(brush = Brush.radialGradient(listOf(Color(0x5564D7F0), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.70f), radius = size.width * 0.55f), radius = size.width * 0.55f, center = androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.70f))
        for (layer in 0..4) {
            val path = Path()
            for (step in 0..64) {
                val y = -size.height * 0.08f + step / 64f * size.height * 1.16f
                val x = size.width * (0.48f + 0.30f * kotlin.math.sin(y / size.height * 7.2f + index * 0.38f + layer * 0.32f))
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color.White.copy(alpha = 0.055f + layer * 0.008f), style = Stroke(width = (18f + layer * 10f) * size.width / 360f, cap = StrokeCap.Round))
            if (layer == 0 || layer == 3) drawPath(path, Color(0x55E8FAFF), style = Stroke(width = 1.2f * size.width / 180f, cap = StrokeCap.Round))
        }
    }
}

private suspend fun checkUpdateFeed(feedUrl: String): Pair<String, String> = withContext(Dispatchers.IO) {
    if (feedUrl.isBlank()) return@withContext "Not Found" to "Update feed belum dikonfigurasi. Tidak ada request jaringan yang diklaim telah dilakukan."
    if (!feedUrl.startsWith("https://", ignoreCase = true)) return@withContext "Feed invalid" to "Update feed harus menggunakan HTTPS."
    var connection: HttpURLConnection? = null
    try {
        connection = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
        }
        val code = connection.responseCode
        if (code !in 200..299) return@withContext "Connection unavailable" to "Server update merespons HTTP " + code + "."
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val version = json.optString("version", "")
        if (version.isBlank()) return@withContext "Feed invalid" to "Metadata tidak memiliki field version."
        val remoteCode = json.optInt("versionCode", -1)
        if (remoteCode < 0) return@withContext "Feed invalid" to "Metadata harus menyediakan versionCode numerik."
        val date = json.optString("date", "Tanggal tidak disediakan")
        val changelog = json.optString("changelog", "Changelog tidak disediakan")
        if (!isNewerVersionCode(remoteCode, BuildConfig.VERSION_CODE)) {
            "Not Found" to "Tidak ada versi lebih baru. Feed terbaru: " + version + " (" + remoteCode + ") · " + date
        } else {
            "Update found: " + version to date + " · " + changelog + ". Unduhan dan pemasangan tidak dilakukan otomatis."
        }
    } catch (error: Exception) {
        "Connection unavailable" to "Pemeriksaan update gagal: " + (error.message ?: "network error")
    } finally {
        connection?.disconnect()
    }
}

@Composable
private fun SettingsSection(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), null, 0.55f, radius = 25) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF193D4B), modifier = Modifier.size(38.dp)) {
                    Icon(icon, null, tint = Color(0xFF9DEBF0), modifier = Modifier.padding(9.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
            }
            content()
        }
    }
}

@Composable
private fun SettingCard(title: String, description: String, control: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 14.sp)
        }
        control()
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 14.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingInfo(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun NavigationSetting(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 5.dp, horizontal = 2.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 14.sp)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 10.sp) }, modifier = Modifier.height(35.dp))
}

@Composable
private fun AccentDot(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(onClick = onClick, modifier = Modifier.size(28.dp), shape = CircleShape, color = color, border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else null) {}
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValue: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    step: Float = 0.01f,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
        val count = if (step >= 1f) ((range.endInclusive - range.start) / step).toInt() - 1 else 0
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValue, valueRange = range, steps = count.coerceAtLeast(0))
    }
}
