package com.mcifu.usbx.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mcifu.usbx.domain.model.UsbStorage
import com.mcifu.usbx.ui.browser.BrowserScreen
import com.mcifu.usbx.ui.home.HomeScreen

@Composable
fun UsbxNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(onOpenStorage = { storage -> navController.navigate(storage.rootRoute()) })
        }
        composable<BrowserRoute> { entry ->
            val depth = entry.toRoute<BrowserRoute>().path.lastIndex
            BrowserScreen(
                onNavigateUp = { navController.navigateUp() },
                onNavigateToDepth = { target ->
                    // Cada carpeta es una entrada de la pila: subir N niveles = N pops.
                    repeat(depth - target) { navController.popBackStack() }
                },
                onOpenFolder = { route -> navController.navigate(route) },
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
