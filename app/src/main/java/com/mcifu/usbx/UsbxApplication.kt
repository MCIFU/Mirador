package com.mcifu.usbx

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.mcifu.usbx.data.diagnostics.Diagnostics
import com.mcifu.usbx.di.AppContainer

class UsbxApplication : Application(), SingletonImageLoader.Factory {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Diagnostics.install(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = container.createImageLoader()
}
