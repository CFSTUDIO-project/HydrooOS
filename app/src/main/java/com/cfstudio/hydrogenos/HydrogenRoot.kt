package com.cfstudio.hydrogenos

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cfstudio.hydrogenos.data.HydrogenPreferenceStore
import com.cfstudio.hydrogenos.data.HydrogenPreferences
import com.cfstudio.hydrogenos.data.LaunchableApp
import com.cfstudio.hydrogenos.data.loadLaunchableApps
import com.cfstudio.hydrogenos.data.loadWallpaperBitmap
import com.cfstudio.hydrogenos.graphics.DropletIntroView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HydrogenRoot(activity: MainActivity, preferenceStore: HydrogenPreferenceStore) {
    val stored by preferenceStore.flow.collectAsState(initial = null)
    if (stored == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF050B16)), contentAlignment = Alignment.Center) {
            HydrogenMark(Modifier.size(58.dp))
        }
        return
    }
    val prefs = stored ?: HydrogenPreferences()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var screen by remember(prefs.introSeen, prefs.onboardingDone) {
        mutableStateOf(if (!prefs.introSeen) "intro" else if (!prefs.onboardingDone) "onboarding" else "home")
    }
    var replayDestination by remember { mutableStateOf("home") }
    var message by remember { mutableStateOf("") }

    val backdrop by produceState<Bitmap?>(null, prefs.wallpaperIndex, prefs.customWallpaperUri) {
        value = loadWallpaperBitmap(context, prefs.wallpaperIndex, prefs.customWallpaperUri)
    }
    LaunchedEffect(Unit) { apps = loadLaunchableApps(context) }

    SideEffect {
        activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
        activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val dark = when (prefs.theme) {
            "light" -> false
            "system" -> isSystemInDarkTheme()
            else -> true
        }
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    HydrogenTheme(prefs) {
        Box(Modifier.fillMaxSize()) {
            HydrogenBackdrop(backdrop)
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    if (prefs.reduceMotion || prefs.batterySaver) fadeIn(tween(80)) togetherWith fadeOut(tween(70))
                    else {
                        val duration = (260 / prefs.animationSpeed).toInt().coerceIn(130, 520)
                        fadeIn(tween(duration, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(tween((duration * 0.82f).toInt(), easing = FastOutSlowInEasing))
                    }
                },
                label = "Hydrogen navigation"
            ) { page ->
                when (page) {
                    "intro" -> IntroScreen(
                        reduceMotion = prefs.reduceMotion,
                        onSkip = {
                            scope.launch { preferenceStore.setIntroSeen(true) }
                            screen = if (!prefs.onboardingDone) "onboarding" else replayDestination
                        },
                        onFinished = {
                            scope.launch { preferenceStore.setIntroSeen(true) }
                            screen = if (!prefs.onboardingDone) "onboarding" else replayDestination
                        }
                    )
                    "onboarding" -> OnboardingScreen(activity, {
                        scope.launch { preferenceStore.setOnboardingDone(true) }
                        screen = "home"
                    }, {
                        scope.launch { preferenceStore.setOnboardingDone(true) }
                        screen = "home"
                    })
                    "drawer" -> AppDrawerScreen(
                        apps, prefs,
                        onBack = { screen = "home" },
                        onLaunch = { launchInstalledApp(activity, it) },
                        onToggleFavorite = { app -> scope.launch { preferenceStore.toggleFavorite(app.packageName) } },
                        onAppInfo = { openAppInfo(activity, it) }
                    )
                    "wallpapers" -> WallpaperStudioScreen(
                        activity, prefs,
                        onBack = { screen = "home" },
                        onApplyPreset = { index ->
                            scope.launch {
                                preferenceStore.setCustomWallpaper("")
                                preferenceStore.setWallpaper(index)
                                message = "Wallpaper Hydrogen diterapkan"
                            }
                        },
                        onCustomSelected = { uri ->
                            scope.launch {
                                preferenceStore.setCustomWallpaper(uri.toString())
                                message = "Wallpaper pilihanmu disimpan"
                            }
                        },
                        onSystemApplied = { message = it }
                    )
                    "settings" -> SettingsScreen(
                        activity, prefs, preferenceStore,
                        onBack = { screen = "home" },
                        onWallpapers = { screen = "wallpapers" },
                        onAbout = { screen = "about" },
                        onReplayIntro = { replayDestination = "settings"; screen = "intro" },
                        onStatus = { message = it }
                    )
                    "about" -> AboutScreen(
                        activity, prefs, backdrop,
                        onBack = { screen = "settings" },
                        onStatus = { message = it }
                    )
                    else -> HomeScreen(
                        activity, apps, prefs, backdrop, preferenceStore,
                        onDrawer = { screen = "drawer" },
                        onSettings = { screen = "settings" },
                        onWallpapers = { screen = "wallpapers" },
                        onAbout = { screen = "about" },
                        onRefreshApps = { scope.launch { apps = loadLaunchableApps(context) } },
                        onLaunch = { launchInstalledApp(activity, it) },
                        onToggleFavorite = { app -> scope.launch { preferenceStore.toggleFavorite(app.packageName) } },
                        onAppInfo = { openAppInfo(activity, it) }
                    )
                }
            }
            if (message.isNotBlank()) {
                LaunchedEffect(message) {
                    delay(2400)
                    message = ""
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 42.dp),
                    color = Color(0xE61B3547), shape = CircleShape, tonalElevation = 8.dp
                ) {
                    Text(message, Modifier.padding(horizontal = 18.dp, vertical = 11.dp), color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun HydrogenTheme(preferences: HydrogenPreferences, content: @Composable () -> Unit) {
    val dark = when (preferences.theme) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    val accent = when (preferences.accent) {
        "ice" -> Color(0xFF9BE8F4)
        "mint" -> Color(0xFF73E3C0)
        "violet" -> Color(0xFFA7A0FF)
        "blue" -> Color(0xFF74B9FF)
        else -> Color(0xFF62DCE8)
    }
    val scheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = Color(0xFF06202A), secondary = Color(0xFF91DDE5),
        background = Color(0xFF050B16), surface = Color(0xFF122334),
        onSurface = Color(0xFFEAF7FA), onSurfaceVariant = Color(0xFFA7BCC6)
    ) else lightColorScheme(
        primary = accent, onPrimary = Color(0xFF06202A), secondary = Color(0xFF256D7A),
        background = Color(0xFFEAF5F7), surface = Color(0xFFF3FAFC),
        onSurface = Color(0xFF10202A), onSurfaceVariant = Color(0xFF506570)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun HydrogenBackdrop(bitmap: Bitmap?) {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF061426), Color(0xFF07516A), Color(0xFF07172B))))) {
        bitmap?.let {
            Image(it.asImageBitmap(), "Hydrogen wallpaper", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x16030A16), Color(0x12030A16), Color(0xA8050B16)))))
        Box(Modifier.align(Alignment.TopEnd).offset(x = 95.dp, y = (-95).dp).size(270.dp).blur(90.dp).background(Color(0x505CE8E8), CircleShape))
    }
}

@Composable
private fun IntroScreen(reduceMotion: Boolean, onSkip: () -> Unit, onFinished: () -> Unit) {
    var phase by remember { mutableFloatStateOf(0f) }
    val viewRef = remember { arrayOfNulls<DropletIntroView>(1) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewRef[0]?.onPause()
                Lifecycle.Event.ON_RESUME -> viewRef[0]?.onResume()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            viewRef[0]?.onPause()
            viewRef[0] = null
        }
    }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            delay(300)
            phase = 1f
            onFinished()
        } else {
            androidx.compose.animation.core.Animatable(0f).animateTo(1f, tween(6500, easing = LinearEasing)) {
                phase = value
            }
            onFinished()
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF020814), Color(0xFF09283C), Color(0xFF061421))))) {
        Box(Modifier.align(Alignment.TopCenter).offset(y = 130.dp).size(300.dp).blur(100.dp).alpha(0.24f + phase * 0.08f).background(Color(0xFF5DDCE8), CircleShape))
        AndroidView(
            factory = { ctx -> DropletIntroView(ctx).also { viewRef[0] = it } },
            modifier = Modifier.fillMaxSize(),
            onRelease = { view -> view.onPause(); if (viewRef[0] === view) viewRef[0] = null }
        )
        Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 30.dp).alpha((phase * 5.2f).coerceIn(0f, 1f)), horizontalAlignment = Alignment.CenterHorizontally) {
            HydrogenMark(Modifier.size(104.dp))
            Spacer(Modifier.height(24.dp))
            Text("HYDROGEN OS", fontSize = 27.sp, letterSpacing = 4.2.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF2FCFF))
            Spacer(Modifier.height(10.dp))
            Text("Beyond the Home Screen.", fontSize = 13.sp, color = Color(0xFFB6D9E2), letterSpacing = 1.2.sp)
            Spacer(Modifier.height(22.dp))
            Text("V1", fontSize = 24.sp, fontWeight = FontWeight.Light, letterSpacing = 5.sp, color = Color(0xFF8AEAF0), modifier = Modifier.alpha(((phase - 0.62f) / 0.18f).coerceIn(0f, 1f)))
        }
        TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Text("Lewati", color = Color(0xFFD8F5F7)) }
        LinearProgressIndicator(progress = { phase.coerceIn(0f, 1f) }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp), color = Color(0xFF62DCE8), trackColor = Color(0x223F7C8B))
    }
}

@Composable
private fun OnboardingScreen(activity: MainActivity, onComplete: () -> Unit, onSkip: () -> Unit) {
    var selected by remember { mutableStateOf(activity.isHydrogenDefaultHome()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowSelected = activity.isHydrogenDefaultHome()
                selected = nowSelected
                if (nowSelected) onComplete()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = 48.dp, bottom = 30.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HydrogenMark(Modifier.size(42.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("HYDROGEN OS", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                    Text("FIRST-RUN SETUP · V1", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 1.3.sp)
                }
            }
            Spacer(Modifier.height(38.dp))
            Text("Your home,\nreimagined.", color = MaterialTheme.colorScheme.onSurface, fontSize = 38.sp, lineHeight = 43.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(13.dp))
            Text("Hydrogen OS adalah launcher Android pihak ketiga. Pilih sebagai Home app agar tombol Home membuka tampilan ini.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(28.dp))
            GlassCard(Modifier.fillMaxWidth().height(175.dp), null, 0.6f) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        HydrogenMark(Modifier.size(78.dp))
                        Column {
                            Text("HOME EXPERIENCE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 1.4.sp)
                            Text("Hydrogen OS", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                            Text("Berdasarkan Android", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(25.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = if (selected) Color(0xFF163E36) else Color(0xFF493D25), modifier = Modifier.size(34.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (selected) "✓" else "1", color = if (selected) Color(0xFF8AE7C3) else Color(0xFFFFD58B), fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Default Home launcher", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                    Text(if (selected) "Hydrogen sudah dipilih oleh Android" else "Belum dipilih · perlu konfirmasi sistem", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                TextButton(onClick = { selected = activity.isHydrogenDefaultHome() }) { Text("Periksa") }
            }
            Spacer(Modifier.height(14.dp))
            Text("Notifikasi dan akses Notification Listener tidak diperlukan untuk fungsi inti launcher. Tidak ada akses notifikasi yang diminta.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { if (!selected) activity.requestHomeRole() else onComplete() }, Modifier.fillMaxWidth().height(54.dp)) {
                Text(if (selected) "Mulai Hydrogen OS" else "Pilih sebagai Home app")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.ArrowForward, null)
            }
            TextButton(onClick = onSkip, Modifier.align(Alignment.CenterHorizontally)) { Text("Lewati untuk sekarang") }
        }
    }
}

@Composable
fun HydrogenMark(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val stroke = size.minDimension * 0.055f
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val ringRadius = size.minDimension * 0.36f
        drawCircle(Brush.linearGradient(listOf(Color(0xFFE5FCFF), Color(0xFF56DDE8), Color(0xFF8DF1D9))), ringRadius, center, style = Stroke(width = stroke))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.30f, size.height * 0.36f)
            lineTo(size.width * 0.50f, size.height * 0.65f)
            lineTo(size.width * 0.70f, size.height * 0.36f)
        }
        drawPath(path, Brush.linearGradient(listOf(Color(0xFF9DF2F4), Color(0xFF47C9D7))), style = Stroke(width = stroke * 1.18f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(Color(0xFFEDFFFF), stroke * 0.82f, androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.12f))
        drawCircle(Color(0xFF91ECE0), stroke * 0.68f, androidx.compose.ui.geometry.Offset(size.width * 0.80f, size.height * 0.70f))
    }
}
