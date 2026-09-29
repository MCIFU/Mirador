package com.mcifu.usbx.ui.navigation

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
import com.mcifu.usbx.domain.model.UsbStorage
import com.mcifu.usbx.ui.browser.BrowserScreen
import com.mcifu.usbx.ui.common.LocalNavAnimatedScope
import com.mcifu.usbx.ui.common.LocalSharedTransitionScope
import com.mcifu.usbx.ui.home.HomeScreen
import com.mcifu.usbx.ui.viewer.ViewerScreen

private const val KEY_LAST_VIEWED = "last_viewed_document"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun UsbxNavHost() {
    val navController = rememberNavController()

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(navController = navController, startDestination = HomeRoute) {
                composable<HomeRoute> {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        HomeScreen(onOpenStorage = { storage -> navController.navigate(storage.rootRoute()) })
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
                            onGoHome = { navController.popBackStack(HomeRoute, inclusive = false) },
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
                                navController.popBackStack()
                            },
                            onGoHome = { navController.popBackStack(HomeRoute, inclusive = false) },
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
