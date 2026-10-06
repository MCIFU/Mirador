package com.mcifu.mirador

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.mcifu.mirador.data.diagnostics.Diagnostics
import com.mcifu.mirador.di.AppContainer

class MiradorApplication : Application(), SingletonImageLoader.Factory {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Diagnostics.install(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = container.createImageLoader()
}
