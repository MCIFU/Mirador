package com.mcifu.mirador.ui.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.mcifu.mirador.data.diagnostics.Diagnostics
import com.mcifu.mirador.data.image.RegionDecoding
import com.mcifu.mirador.domain.ZoomTransform
import com.mcifu.mirador.ui.multiview.ImagePane
import com.mcifu.mirador.data.storage.contentUri
import com.mcifu.mirador.domain.model.FileItem
import com.mcifu.mirador.ui.common.Formatters
import com.mcifu.mirador.ui.common.icon
import com.mcifu.mirador.ui.common.label
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/** Zoom máximo: 400 % del tamaño real de la foto (suficiente para revisar enfoque y detalle). */
private const val MAX_ZOOM_FACTOR = 4f

/** Doble toque: amplía 2,5× respecto al encaje en pantalla alrededor del dedo, o vuelve a encajar. */
private val DoubleTapZoom = DoubleClickToZoomListener { state, centroid ->
    val transformation = state.contentTransformation
    val zoomed = (state.zoomFraction ?: 0f) > 0.05f
    if (zoomed || !transformation.isSpecified) {
        state.resetZoom()
    } else {
        val base = maxOf(transformation.scale.scaleX, transformation.scale.scaleY)
        state.zoomTo(zoomFactor = (base * 2.5f).coerceAtMost(MAX_ZOOM_FACTOR), centroid = centroid)
    }
}

/**
 * Imagen ampliable. Pellizcar, doble toque y arrastrar cuando está ampliada. Con la imagen
 * ampliada, el arrastre horizontal mueve la foto y solo pasa a la siguiente al llegar al borde.
 *
 * Al ampliar se cargan teselas de la foto original (subsampling), así que el zoom es nítido
 * sin cargar nunca la foto completa en memoria.
 */
@Composable
internal fun ImagePage(
    item: FileItem,
    isCurrent: Boolean,
    rotation: Int,
    targetSize: Size,
    onTap: () -> Unit,
    onZoomedIn: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var failed by remember(item.uri) { mutableStateOf(false) }
    var errorDetail by remember(item.uri) { mutableStateOf<String?>(null) }
    /** Visor alternativo (sin teselas) si el avanzado no consigue mostrar la foto. */
    var useFallback by remember(item.uri) { mutableStateOf(false) }
    var fallbackZoom by remember(item.uri) { mutableStateOf(ZoomTransform()) }
    // Zoom por teselas solo si Android sabe decodificar el formato por regiones; si no
    // (BMP, algunos HEIC), zoom sobre la imagen completa a tamaño de pantalla.
    val subsampling by produceState(RegionDecoding.knownSupport(item), item.uri) {
        if (value == null) value = RegionDecoding.supports(context, item, item.contentUri)
    }
    val request = remember(item.uri, targetSize, subsampling) {
        subsampling?.let { allowed ->
            viewerImageRequest(context, item, targetSize, allowSubsampling = allowed, onError = { failed = true })
        }
    }
    val zoomState = rememberZoomableImageState(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = MAX_ZOOM_FACTOR)))
    val zoomable = zoomState.zoomableState

    // Al salir de la página, vuelve a encajar: la próxima vez se ve la foto completa.
    LaunchedEffect(isCurrent) {
        if (!isCurrent) zoomable.resetZoom(animationSpec = snap())
    }
    // El nivel de zoom se observa fuera de la composición: leerlo aquí recompondría la página
    // en cada fotograma del pellizco.
    LaunchedEffect(isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        snapshotFlow { (zoomable.zoomFraction ?: 0f) > 0.05f }
            .distinctUntilChanged()
            .collect { zoomed -> if (zoomed) onZoomedIn() }
    }
    LaunchedEffect(rotation) { zoomable.resetZoom() }

    val animatedRotation by animateFloatAsState(rotation.toFloat(), tween(260), label = "rotation")

    // Si en 4 s el visor avanzado no ha mostrado la foto, se pasa al alternativo, que la lee por
    // otra vía y tiene su propio zoom. Queda anotado en el informe de diagnóstico.
    LaunchedEffect(item.uri, isCurrent, failed) {
        if (!isCurrent || useFallback) return@LaunchedEffect
        delay(if (failed) 0 else 4_000)
        if (!zoomState.isImageDisplayed) {
            Diagnostics.log("VISOR", "Visor avanzado sin imagen (fallo=$failed, subsampling=$subsampling): ${item.name} → visor alternativo")
            failed = false
            useFallback = true
        }
    }
    LaunchedEffect(zoomState.isImageDisplayed) {
        if (zoomState.isImageDisplayed) Diagnostics.log("VISOR", "Imagen mostrada (visor avanzado): ${item.name}")
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (useFallback && errorDetail != null) {
            PageMessage(
                icon = { Icon(Icons.Outlined.BrokenImage, null, tint = Color.White, modifier = Modifier.size(48.dp)) },
                title = "No se puede mostrar esta imagen",
                body = errorDetail,
                action = { OutlinedButton(onClick = onOpenWith) { Text("Abrir con otra aplicación", color = Color.White) } },
                onTap = onTap,
            )
        } else if (useFallback) {
            ImagePane(
                item = item,
                transform = fallbackZoom,
                onTransform = {
                    fallbackZoom = it
                    if (it.isZoomed) onZoomedIn()
                },
                onTap = onTap,
                onError = { t -> errorDetail = "${t.javaClass.simpleName}: ${t.message}" },
                alternativeSource = true,
            )
        } else {
            if (request != null) Box(Modifier.fillMaxSize().rotatedContent(rotation) { animatedRotation }) {
                ZoomableAsyncImage(
                    model = request,
                    contentDescription = item.name,
                    state = zoomState,
                    onClick = { _: Offset -> onTap() },
                    onDoubleClick = DoubleTapZoom,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!zoomState.isImageDisplayed) DelayedSpinner()
        }
    }
}

/** Cualquier otro archivo (ámbito "Todos los archivos"): ficha con abrir / abrir con. */
@Composable
internal fun FilePage(
    item: FileItem,
    onTap: () -> Unit,
    onOpen: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    PageMessage(
        modifier = modifier,
        icon = { Icon(item.type.icon(), null, tint = Color.White, modifier = Modifier.size(96.dp)) },
        title = item.name,
        body = "${item.type.label()} · ${Formatters.size(context, item.size)}",
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onOpen) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    Text("Abrir", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onOpenWith) {
                    Icon(Icons.Outlined.Apps, contentDescription = null, tint = Color.White)
                    Text("Abrir con…", color = Color.White, modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        onTap = onTap,
    )
}

@Composable
private fun PageMessage(
    icon: @Composable () -> Unit,
    title: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().tapToToggle(onTap).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        if (body != null) {
            Text(body, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
        }
        action?.invoke()
    }
}

/** El indicador solo aparece si la imagen tarda: con la precarga normalmente no se ve nunca. */
@Composable
private fun DelayedSpinner() {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(250)
        show = true
    }
    if (show) CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(36.dp))
}

private fun Modifier.tapToToggle(onTap: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, onClick = onTap)

