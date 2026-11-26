package com.cfstudio.hydrogenos.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.hydrogenDataStore by preferencesDataStore(name = "hydrogen_settings")

/** Persisted launcher configuration. Defaults are intentionally conservative for battery and legibility. */
data class HydrogenPreferences(
    val introSeen: Boolean = false,
    val onboardingDone: Boolean = false,
    val wallpaperIndex: Int = 0,
    val customWallpaperUri: String = "",
    val theme: String = "dark",
    val accent: String = "aqua",
    val gridColumns: Int = 4,
    val iconSize: Int = 58,
    val showLabels: Boolean = true,
    val dockCount: Int = 4,
    val widgetHeight: Int = 156,
    val drawerMode: String = "grid",
    val sortMode: String = "name",
    val glassIntensity: Float = 0.72f,
    val animationSpeed: Float = 1f,
    val reduceMotion: Boolean = false,
    val batterySaver: Boolean = false,
    val favoritePackages: Set<String> = emptySet(),
    val widgetId: Int = -1,
)

class HydrogenPreferenceStore(private val context: Context) {
    private object Keys {
        val introSeen = booleanPreferencesKey("intro_seen")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val wallpaperIndex = intPreferencesKey("wallpaper_index")
        val customWallpaperUri = stringPreferencesKey("custom_wallpaper_uri")
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val gridColumns = intPreferencesKey("grid_columns")
        val iconSize = intPreferencesKey("icon_size")
        val showLabels = booleanPreferencesKey("show_labels")
        val dockCount = intPreferencesKey("dock_count")
        val widgetHeight = intPreferencesKey("widget_height")
        val drawerMode = stringPreferencesKey("drawer_mode")
        val sortMode = stringPreferencesKey("sort_mode")
        val glassIntensity = floatPreferencesKey("glass_intensity")
        val animationSpeed = floatPreferencesKey("animation_speed")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val batterySaver = booleanPreferencesKey("battery_saver")
        val favorites = stringSetPreferencesKey("favorite_packages")
        val widgetId = intPreferencesKey("widget_id")
    }

    val flow: Flow<HydrogenPreferences> = context.hydrogenDataStore.data.map { p ->
        HydrogenPreferences(
            introSeen = p[Keys.introSeen] ?: false,
            onboardingDone = p[Keys.onboardingDone] ?: false,
            wallpaperIndex = (p[Keys.wallpaperIndex] ?: 0).coerceIn(0, 11),
            customWallpaperUri = p[Keys.customWallpaperUri].orEmpty(),
            theme = p[Keys.theme] ?: "dark",
            accent = p[Keys.accent] ?: "aqua",
            gridColumns = (p[Keys.gridColumns] ?: 4).coerceIn(3, 6),
            iconSize = (p[Keys.iconSize] ?: 58).coerceIn(42, 76),
            showLabels = p[Keys.showLabels] ?: true,
            dockCount = (p[Keys.dockCount] ?: 4).coerceIn(2, 6),
            widgetHeight = (p[Keys.widgetHeight] ?: 156).coerceIn(120, 260),
            drawerMode = p[Keys.drawerMode] ?: "grid",
            sortMode = p[Keys.sortMode] ?: "name",
            glassIntensity = (p[Keys.glassIntensity] ?: 0.72f).coerceIn(0f, 1f),
            animationSpeed = (p[Keys.animationSpeed] ?: 1f).coerceIn(0.5f, 1.5f),
            reduceMotion = p[Keys.reduceMotion] ?: false,
            batterySaver = p[Keys.batterySaver] ?: false,
            favoritePackages = p[Keys.favorites].orEmpty(),
            widgetId = p[Keys.widgetId] ?: -1,
        )
    }

    suspend fun setIntroSeen(value: Boolean) = put(Keys.introSeen, value)
    suspend fun setOnboardingDone(value: Boolean) = put(Keys.onboardingDone, value)
    suspend fun setWallpaper(index: Int) = put(Keys.wallpaperIndex, index.coerceIn(0, 11))
    suspend fun setCustomWallpaper(uri: String) = put(Keys.customWallpaperUri, uri)
    suspend fun setTheme(value: String) = put(Keys.theme, value)
    suspend fun setAccent(value: String) = put(Keys.accent, value)
    suspend fun setGridColumns(value: Int) = put(Keys.gridColumns, value.coerceIn(3, 6))
    suspend fun setIconSize(value: Int) = put(Keys.iconSize, value.coerceIn(42, 76))
    suspend fun setShowLabels(value: Boolean) = put(Keys.showLabels, value)
    suspend fun setDockCount(value: Int) = put(Keys.dockCount, value.coerceIn(2, 6))
    suspend fun setWidgetHeight(value: Int) = put(Keys.widgetHeight, value.coerceIn(120, 260))
    suspend fun setDrawerMode(value: String) = put(Keys.drawerMode, value)
    suspend fun setSortMode(value: String) = put(Keys.sortMode, value)
    suspend fun setGlassIntensity(value: Float) = put(Keys.glassIntensity, value.coerceIn(0f, 1f))
    suspend fun setAnimationSpeed(value: Float) = put(Keys.animationSpeed, value.coerceIn(0.5f, 1.5f))
    suspend fun setReduceMotion(value: Boolean) = put(Keys.reduceMotion, value)
    suspend fun setBatterySaver(value: Boolean) = put(Keys.batterySaver, value)
    suspend fun setWidgetId(value: Int) = put(Keys.widgetId, value)

    suspend fun toggleFavorite(packageName: String) {
        context.hydrogenDataStore.edit { p ->
            val current = p[Keys.favorites].orEmpty().toMutableSet()
            if (!current.add(packageName)) current.remove(packageName)
            p[Keys.favorites] = current
        }
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.hydrogenDataStore.edit { it[key] = value }
    }
}
