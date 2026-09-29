package com.mcifu.usbx.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** Ámbito de transiciones compartidas de toda la navegación (null en previsualizaciones). */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** Ámbito de animación de la pantalla de navegación actual. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

private val MediaBoundsTransform = BoundsTransform { _, _ -> tween(durationMillis = 320) }

/** Clave común para que la miniatura del explorador y la página del visor se reconozcan. */
fun mediaSharedKey(documentId: String) = "media:$documentId"

/**
 * Transición "miniatura → pantalla completa": la foto crece desde su celda al abrirla y vuelve a
 * ella al cerrar el visor. Si no hay transición en curso, no hace nada.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.mediaSharedBounds(documentId: String, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedScope.current ?: return this
    return with(sharedScope) {
        this@mediaSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(mediaSharedKey(documentId)),
            animatedVisibilityScope = animatedScope,
            boundsTransform = MediaBoundsTransform,
        )
    }
}
