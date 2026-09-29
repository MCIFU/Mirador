package com.mcifu.usbx.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import com.mcifu.usbx.data.image.FullImage
import com.mcifu.usbx.data.storage.contentUri
import com.mcifu.usbx.data.thumbnail.toThumbnail
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.OrientationMode
import kotlinx.coroutines.delay

/** Petición de imagen a tamaño de pantalla. Misma clave en la página y en la precarga. */
internal fun viewerImageRequest(
    context: Context,
    item: FileItem,
    targetSize: Size,
    allowSubsampling: Boolean = true,
    onError: () -> Unit = {},
): ImageRequest =
    ImageRequest.Builder(context)
        .data(if (allowSubsampling) item.contentUri else FullImage(item.contentUri, item.mimeType))
        .size(targetSize)
        .memoryCacheKey("viewer:${item.uri}:${item.lastModified}:${targetSize.width}x${targetSize.height}")
        // Mientras llega la foto se muestra su miniatura (ya en memoria desde el explorador):
        // la transición desde la cuadrícula nunca pasa por una pantalla negra.
        .placeholderMemoryCacheKey(item.toThumbnail().cacheKey)
        .listener(onError = { _, _ -> onError() })
        .build()

/**
 * Precarga en caché de memoria las dos imágenes a cada lado de la actual con el mismo tamaño
 * y clave que usa la página: al deslizar, la imagen ya está decodificada.
 */
@Composable
internal fun PreloadNeighbours(
    items: List<FileItem>,
    currentPage: Int,
    targetSize: Size,
    isImage: (FileItem) -> Boolean,
) {
    val context = LocalContext.current
    LaunchedEffect(items, targetSize, currentPage) {
        // Pequeña espera: al pasar fotos muy rápido no se encolan precargas que no se verán.
        delay(120)
        val loader = SingletonImageLoader.get(context)
        listOf(currentPage + 1, currentPage - 1, currentPage + 2, currentPage - 2)
            .mapNotNull { items.getOrNull(it) }
            .filter(isImage)
            .forEach { loader.enqueue(viewerImageRequest(context, it, targetSize)) }
    }
}

/** Oculta barras de estado y navegación cuando se ocultan los controles (modo inmersivo). */
@Composable
internal fun SystemBarsEffect(visible: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(visible) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        if (visible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            val light = !isDarkUi(view)
            controller.isAppearanceLightStatusBars = light
            controller.isAppearanceLightNavigationBars = light
        }
    }
}

/**
 * Aplica la orientación elegida mientras el visor está abierto y la libera al salir.
 * "Auto" respeta el bloqueo de rotación del sistema; "Horizontal" fuerza apaisado aunque
 * la rotación automática esté desactivada.
 */
@Composable
internal fun OrientationEffect(mode: OrientationMode) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(mode) {
        activity.requestedOrientation = when (mode) {
            OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}

/**
 * Gira el contenido en pasos de 90°. En 90°/270° se mide con ancho y alto intercambiados para
 * que la imagen girada siga ocupando la pantalla entera. La rotación es visual: no modifica
 * el archivo del USB.
 */
internal fun Modifier.rotatedContent(targetDegrees: Int, animatedDegrees: () -> Float): Modifier =
    layout { measurable, constraints ->
        val swap = Math.floorMod(targetDegrees / 90, 2) == 1 && constraints.hasBoundedWidth && constraints.hasBoundedHeight
        val childConstraints = if (swap) {
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            )
        } else {
            constraints
        }
        val placeable = measurable.measure(childConstraints)
        val width = if (swap) placeable.height else placeable.width
        val height = if (swap) placeable.width else placeable.height
        layout(width, height) {
            placeable.placeWithLayer((width - placeable.width) / 2, (height - placeable.height) / 2) {
                rotationZ = animatedDegrees()
            }
        }
    }

private fun isDarkUi(view: View): Boolean =
    (view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
