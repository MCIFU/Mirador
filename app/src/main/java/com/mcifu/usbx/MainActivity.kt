package com.mcifu.usbx

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mcifu.usbx.domain.model.ThemeMode
import com.mcifu.usbx.data.diagnostics.Diagnostics
import com.mcifu.usbx.ui.navigation.HomeRoute
import com.mcifu.usbx.ui.navigation.UsbxNavHost
import com.mcifu.usbx.ui.navigation.ViewerRoute
import com.mcifu.usbx.ui.navigation.toViewerRoute
import com.mcifu.usbx.ui.theme.UsbxTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Ruta inicial cuando otra app pide abrir un archivo ("Abrir con USBX"). */
    private var externalRoute by mutableStateOf<ViewerRoute?>(null)
    private var resolvingExternal by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewIntent = intent?.takeIf { it.action == Intent.ACTION_VIEW && it.data != null }
        if (viewIntent != null) {
            resolvingExternal = true
            splash.setKeepOnScreenCondition { resolvingExternal }
            val uri = viewIntent.data!!
            lifecycleScope.launch {
                externalRoute = try {
                    (application as UsbxApplication).container.externalOpenResolver
                        .resolve(uri, viewIntent.type)
                        .toViewerRoute()
                } catch (e: Exception) {
                    Diagnostics.log("EXTERNO", "No se pudo preparar $uri", e)
                    null
                }
                resolvingExternal = false
            }
        }

        val settings = (application as UsbxApplication).container.settingsRepository
        setContent {
            val appearance by settings.appearance.collectAsStateWithLifecycle()
            val dark = when (appearance.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            UsbxTheme(darkTheme = dark, dynamicColor = appearance.dynamicColor) {
                Surface(Modifier.fillMaxSize()) {
                    when {
                        resolvingExternal -> Box(Modifier.fillMaxSize().background(Color.Black))
                        else -> UsbxNavHost(
                            startRoute = externalRoute ?: HomeRoute,
                            onExit = ::finish,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Por si algún evento de montaje llegó mientras el proceso estaba congelado.
        (application as UsbxApplication).container.storageRepository.refresh()
    }
}
