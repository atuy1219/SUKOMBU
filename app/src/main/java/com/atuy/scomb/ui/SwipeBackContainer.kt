package com.atuy.scomb.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun shouldCompleteBackGesture(
    progress: Float,
    velocity: Float,
    minFlingVelocity: Float
): Boolean = progress > 0f && when {
    velocity <= -minFlingVelocity -> false
    velocity >= minFlingVelocity -> true
    else -> progress >= 0.33f
}

@Composable
internal fun SwipeBackContainer(
    enabled: Boolean,
    onBack: () -> Unit,
    content: @Composable (() -> Unit) -> Unit
) {
    var width by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    var predictiveBack by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    val latestOnBack by rememberUpdatedState(onBack)
    val scope = rememberCoroutineScope()
    val minFlingVelocity = with(LocalDensity.current) { 600.dp.toPx() }

    suspend fun settle(finish: Boolean, velocity: Float = 0f) {
        if (completed) return
        settling = true
        try {
            val target = if (finish) 1f else 0f
            animate(
                initialValue = progress,
                targetValue = target,
                initialVelocity = if (width > 0) velocity / width else 0f,
                animationSpec = tween(
                    (220 * abs(target - progress)).toInt().coerceAtLeast(80),
                    easing = FastOutSlowInEasing
                )
            ) { value, _ -> progress = value }
            if (finish) {
                completed = true
                latestOnBack()
            }
        } finally {
            settling = false
        }
    }

    val navigateBack: () -> Unit = {
        if (enabled && !settling && !predictiveBack && !completed) {
            settling = true
            scope.launch { settle(finish = true) }
        }
    }
    PredictiveBackHandler(enabled = enabled && !completed) { events ->
        predictiveBack = true
        try {
            events.collect { event -> progress = event.progress.coerceIn(0f, 1f) }
            settle(finish = true)
        } catch (_: CancellationException) {
            scope.launch { settle(finish = false) }
        } finally {
            predictiveBack = false
        }
    }

    Box(
        Modifier.fillMaxSize()
            .clipToBounds()
            .onSizeChanged { width = it.width }
            // Exposed space belongs to this gesture until the detail has left the stack.
            .pointerInput(Unit) { detectTapGestures {} }
    ) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { translationX = progress * width }
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (width > 0) progress = (progress + delta / width).coerceIn(0f, 1f)
                    },
                    orientation = Orientation.Horizontal,
                    enabled = enabled && !settling && !predictiveBack && !completed,
                    onDragStopped = { velocity ->
                        settle(shouldCompleteBackGesture(progress, velocity, minFlingVelocity), velocity)
                    }
                )
        ) {
            content(navigateBack)
        }
    }
}
