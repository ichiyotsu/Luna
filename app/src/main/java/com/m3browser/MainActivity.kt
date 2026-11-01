package com.m3browser

import android.app.SearchManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.m3browser.ui.BrowserApp
import com.m3browser.ui.M3BrowserTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    lateinit var store: Store
    lateinit var controller: BrowserController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        store = Store(applicationContext)
        controller = BrowserController(this) { entry -> lifecycleScope.launch { store.addHistory(entry) } }
        controller.newTab()
        handleIntent(intent, replaceCurrent = true)
        setContent {
            val settings by store.settings.collectAsState(initial = com.m3browser.AppSettings())
            M3BrowserTheme(settings) { BrowserApp(controller, store) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent, replaceCurrent = false)
    }

    private fun handleIntent(intent: Intent?, replaceCurrent: Boolean) {
        intent ?: return
        val url = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(SearchManager.QUERY)
                ?.let { UrlUtils.toUrl(it, SearchEngine.BING) }
            else -> null
        } ?: return
        val tab = controller.current
        if (replaceCurrent && tab != null && tab.isHome) controller.load(tab, url) else controller.newTab(url)
    }

    override fun onPause() { super.onPause(); controller.onPause() }
    override fun onResume() { super.onResume(); controller.onResume() }
    override fun onDestroy() { controller.destroyAll(); super.onDestroy() }
}
