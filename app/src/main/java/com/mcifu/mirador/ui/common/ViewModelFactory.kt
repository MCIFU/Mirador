package com.mcifu.mirador.ui.common

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.mcifu.mirador.MiradorApplication
import com.mcifu.mirador.di.AppContainer

/** Acceso al contenedor de dependencias desde los inicializadores de ViewModel. */
val CreationExtras.appContainer: AppContainer
    get() = (this[APPLICATION_KEY] as MiradorApplication).container
