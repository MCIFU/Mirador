package com.mcifu.usbx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mcifu.usbx.ui.navigation.UsbxNavHost
import com.mcifu.usbx.ui.theme.UsbxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UsbxTheme {
                Surface(Modifier.fillMaxSize()) {
                    UsbxNavHost()
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
