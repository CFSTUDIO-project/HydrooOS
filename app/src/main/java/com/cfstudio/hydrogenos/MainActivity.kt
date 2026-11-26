package com.cfstudio.hydrogenos

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.app.role.RoleManager
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.cfstudio.hydrogenos.data.HydrogenPreferenceStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val preferences by lazy { HydrogenPreferenceStore(applicationContext) }
    private val widgetHost by lazy { AppWidgetHost(this, WIDGET_HOST_ID) }
    private val widgetViews = mutableMapOf<Int, android.appwidget.AppWidgetHostView>()
    private val widgetSizes = mutableMapOf<Int, Pair<Int, Int>>()

    private val widgetPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (result.resultCode == RESULT_OK && id >= 0) {
            val manager = AppWidgetManager.getInstance(this)
            val provider = manager.getAppWidgetInfo(id)
            if (provider?.configure != null) {
                val config = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = provider.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                }
                widgetConfigurator.launch(config)
            } else {
                saveWidget(id)
            }
        } else {
            if (pendingWidgetId >= 0) widgetHost.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = -1
        }
    }

    private val widgetConfigurator = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
            ?: pendingWidgetId
        if (result.resultCode == RESULT_OK && id >= 0) saveWidget(id)
        else if (pendingWidgetId >= 0) widgetHost.deleteAppWidgetId(pendingWidgetId)
        pendingWidgetId = -1
    }

    private var pendingWidgetId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent { HydrogenRoot(activity = this, preferenceStore = preferences) }
    }

    override fun onStart() {
        super.onStart()
        runCatching { widgetHost.startListening() }
    }

    override fun onStop() {
        runCatching { widgetHost.stopListening() }
        super.onStop()
    }

    fun requestHomeRole() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME_ROLE)
                    return
                }
            }
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    fun isHydrogenDefaultHome(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
            } else {
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == packageName
            }
        } catch (_: Exception) { false }
    }

    fun openDefaultLauncherSettings() {
        requestHomeRole()
    }

    fun startWidgetPicker() {
        try {
            val id = widgetHost.allocateAppWidgetId()
            pendingWidgetId = id
            val pick = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            widgetPicker.launch(pick)
        } catch (_: Exception) {
            if (pendingWidgetId >= 0) widgetHost.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = -1
        }
    }

    private fun saveWidget(id: Int) {
        lifecycleScope.launch { preferences.setWidgetId(id) }
        pendingWidgetId = -1
    }

    fun createWidgetView(id: Int): android.view.View? {
        return try {
            val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id) ?: return null
            widgetHost.createView(this, id, info).apply {
                setAppWidget(id, info)
                widgetViews[id] = this
            }
        } catch (_: Exception) { null }
    }

    fun updateWidgetDimensions(id: Int, widthPx: Int, heightPx: Int) {
        val view = widgetViews[id] ?: return
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val widthDp = (widthPx / density).toInt().coerceAtLeast(1)
        val heightDp = (heightPx / density).toInt().coerceAtLeast(1)
        val newSize = widthDp to heightDp
        if (widgetSizes[id] == newSize) return
        widgetSizes[id] = newSize
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.updateAppWidgetSize(Bundle(), mutableListOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
            } else {
                @Suppress("DEPRECATION")
                view.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
            }
        }
    }

    fun removeWidget(id: Int) {
        widgetViews.remove(id)
        widgetSizes.remove(id)
        runCatching { widgetHost.deleteAppWidgetId(id) }
        lifecycleScope.launch { preferences.setWidgetId(-1) }
    }

    companion object {
        private const val WIDGET_HOST_ID = 0x484F53
        private const val REQUEST_HOME_ROLE = 4401
    }
}
