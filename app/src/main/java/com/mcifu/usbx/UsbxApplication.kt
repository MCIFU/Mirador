package com.mcifu.usbx

import android.app.Application
import com.mcifu.usbx.di.AppContainer

class UsbxApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
