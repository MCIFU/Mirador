package com.mcifu.mirador.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Barra de desplazamiento rápido para carpetas con cientos o miles de elementos.
 * Aparece al hacer scroll y se oculta sola; mientras está oculta no captura toques.
 *
 * @param progress posición actual (0..1).
 * @param onScrollTo recibe la posición deseada (0..1) mientras se arrastra.
 */
@Composable
fun FastScroller(
    enabled: Boolean,
    isScrolling: Boolean,
    progress: () -> Float,
    onScrollTo: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isScrolling, dragging) {
        if (isScrolling || dragging) {
            visible = true
        } else {
            delay(1_200)
            visible = false
        }
    }

    val thumbHeight = 52.dp
    val thumbWidth by animateDpAsState(if (dragging) 10.dp else 6.dp, label = "thumbWidth")

    BoxWithConstraints(modifier.fillMaxHeight().width(36.dp)) {
        val density = LocalDensity.current
        val trackPx = with(density) { (maxHeight - thumbHeight).toPx() }.coerceAtLeast(1f)
        val thumbHeightPx = with(density) { thumbHeight.toPx() }

        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            val fraction = if (dragging) dragFraction else progress().coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(36.dp)
                    .pointerInput(trackPx) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                dragFraction = ((offset.y - thumbHeightPx / 2) / trackPx).coerceIn(0f, 1f)
                                onScrollTo(dragFraction)
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, _ ->
                            change.consume()
                            dragFraction = ((change.position.y - thumbHeightPx / 2) / trackPx).coerceIn(0f, 1f)
                            onScrollTo(dragFraction)
                        }
                    }
                    .semantics { contentDescription = "Desplazamiento rápido" },
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, (fraction * trackPx).roundToInt()) }
                        .padding(end = 4.dp)
                        .systemGestureExclusion()
                        .width(thumbWidth)
                        .height(thumbHeight)
                        .clip(CircleShape)
                        .background(
                            if (dragging) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        ),
                )
            }
        }
    }
}
