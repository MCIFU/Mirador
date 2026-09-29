package com.mcifu.usbx.ui.common

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.mcifu.usbx.UsbxApplication
import com.mcifu.usbx.di.AppContainer

/** Acceso al contenedor de dependencias desde los inicializadores de ViewModel. */
val CreationExtras.appContainer: AppContainer
    get() = (this[APPLICATION_KEY] as UsbxApplication).container
