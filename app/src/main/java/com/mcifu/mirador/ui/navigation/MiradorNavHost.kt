package com.mcifu.mirador.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mcifu.mirador.domain.model.UsbStorage
import com.mcifu.mirador.ui.browser.BrowserScreen
import com.mcifu.mirador.ui.common.LocalNavAnimatedScope
import com.mcifu.mirador.ui.common.LocalSharedTransitionScope
import com.mcifu.mirador.ui.common.DiagnosticsDialog
import com.mcifu.mirador.ui.home.HomeScreen
import com.mcifu.mirador.ui.settings.SettingsScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mcifu.mirador.ui.multiview.MultiviewScreen
import com.mcifu.mirador.ui.viewer.ViewerScreen

private const val KEY_LAST_VIEWED = "last_viewed_document"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MiradorNavHost(
    startRoute: Any = HomeRoute,
    /** Cierra la actividad (visor abierto desde otra app, al volver atrás). */
    onExit: () -> Unit = {},
) {
    val navController = rememberNavController()

    fun goHome() {
        if (!navController.popBackStack(HomeRoute, inclusive = false)) {
            navController.navigate(HomeRoute) { popUpTo(0) { inclusive = true } }
        }
    }

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(navController = navController, startDestination = startRoute) {
                composable<HomeRoute> {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        HomeScreen(
                            onOpenStorage = { storage -> navController.navigate(storage.rootRoute()) },
                            onOpenSettings = { navController.navigate(SettingsRoute) },
                        )
                    }
                }
                composable<BrowserRoute> { entry ->
                    val depth = entry.toRoute<BrowserRoute>().path.lastIndex
                    val lastViewed by entry.savedStateHandle
                        .getStateFlow<String?>(KEY_LAST_VIEWED, null)
                        .collectAsStateWithLifecycle()
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        BrowserScreen(
                            onNavigateUp = { navController.navigateUp() },
                            onNavigateToDepth = { target ->
                                // Cada carpeta es una entrada de la pila: subir N niveles = N pops.
                                repeat(depth - target) { navController.popBackStack() }
                            },
                            onOpenFolder = { route -> navController.navigate(route) },
                            onOpenViewer = { route -> navController.navigate(route) },
                            onOpenMultiview = { route -> navController.navigate(route) },
                            onGoHome = ::goHome,
                            returnedFromDocumentId = lastViewed,
                            onReturnHandled = { entry.savedStateHandle[KEY_LAST_VIEWED] = null },
                        )
                    }
                }
                composable<ViewerRoute> {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        ViewerScreen(
                            onBack = { lastDocumentId ->
                                navController.previousBackStackEntry?.savedStateHandle?.set(KEY_LAST_VIEWED, lastDocumentId)
                                // Abierto desde otra app: el visor es la primera pantalla; Atrás vuelve a esa app.
                                if (!navController.popBackStack()) onExit()
                            },
                            onGoHome = ::goHome,
                            onOpenMultiview = { route -> navController.navigate(route) },
                        )
                    }
                }
                composable<SettingsRoute> {
                    var diagnostics by remember { mutableStateOf(false) }
                    SettingsScreen(onBack = { navController.popBackStack() }, onDiagnostics = { diagnostics = true })
                    if (diagnostics) DiagnosticsDialog(storages = emptyList(), onDismiss = { diagnostics = false })
                }
                composable<MultiviewRoute> {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        MultiviewScreen(
                            onBack = { navController.popBackStack() },
                            onGoHome = ::goHome,
                        )
                    }
                }
            }
        }
    }
}

private fun UsbStorage.rootRoute(): BrowserRoute {
    val access = requireNotNull(access) { "Almacenamiento sin autorizar" }
    return BrowserRoute(
        storageId = id,
        treeUri = access.treeUri.toString(),
        documentId = access.rootDocumentId,
        path = listOf(label),
    )
}
