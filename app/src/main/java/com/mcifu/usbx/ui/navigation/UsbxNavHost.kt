package com.mcifu.usbx.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mcifu.usbx.domain.model.UsbStorage
import com.mcifu.usbx.ui.browser.BrowserScreen
import com.mcifu.usbx.ui.home.HomeScreen
import com.mcifu.usbx.ui.viewer.ImageViewerScreen

private const val KEY_LAST_VIEWED = "last_viewed_document"

@Composable
fun UsbxNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(onOpenStorage = { storage -> navController.navigate(storage.rootRoute()) })
        }
        composable<BrowserRoute> { entry ->
            val depth = entry.toRoute<BrowserRoute>().path.lastIndex
            val lastViewed by entry.savedStateHandle
                .getStateFlow<String?>(KEY_LAST_VIEWED, null)
                .collectAsStateWithLifecycle()
            BrowserScreen(
                onNavigateUp = { navController.navigateUp() },
                onNavigateToDepth = { target ->
                    // Cada carpeta es una entrada de la pila: subir N niveles = N pops.
                    repeat(depth - target) { navController.popBackStack() }
                },
                onOpenFolder = { route -> navController.navigate(route) },
                onOpenImage = { route -> navController.navigate(route) },
                onGoHome = { navController.popBackStack(HomeRoute, inclusive = false) },
                returnedFromDocumentId = lastViewed,
                onReturnHandled = { entry.savedStateHandle[KEY_LAST_VIEWED] = null },
            )
        }
        composable<ImageViewerRoute> {
            ImageViewerScreen(
                onBack = { lastDocumentId ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(KEY_LAST_VIEWED, lastDocumentId)
                    navController.popBackStack()
                },
                onGoHome = { navController.popBackStack(HomeRoute, inclusive = false) },
            )
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
