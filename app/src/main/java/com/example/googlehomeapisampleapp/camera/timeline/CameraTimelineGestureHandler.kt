/* Copyright 2025 Google LLC */
package com.example.googlehomeapisampleapp.camera.timeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import kotlinx.coroutines.launch

internal fun Modifier.handleTimelineGestures(
    onGesture: (centroid: Offset, zoomChange: Float, panChange: Float) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
): Modifier = composed {
    val offsetY = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val updatedOnGesture by rememberUpdatedState(onGesture)
    val updatedOnGestureStart by rememberUpdatedState(onGestureStart)
    val updatedOnGestureEnd by rememberUpdatedState(onGestureEnd)
    var isInteracting by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker1D(isDataDifferential = true) }

    pointerInput(Unit) {
        val decay = splineBasedDecay<Float>(this)

        detectTransformGestures(
            onGesture = { centroid, pan, zoom, _ ->
                if (!isInteracting) {
                    isInteracting = true
                    velocityTracker.resetTracking()
                    updatedOnGestureStart()
                }
                velocityTracker.addDataPoint(
                    System.currentTimeMillis(),
                    pan.y,
                )
                updatedOnGesture(centroid, zoom, pan.y)
            },
        )
    }.pointerInput("fling") {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent()
                val event = currentEvent
                if (event.changes.all { !it.pressed } && isInteracting) {
                    val velocity = velocityTracker.calculateVelocity()
                    isInteracting = false
                    coroutineScope.launch {
                        offsetY.snapTo(0f)
                        var lastValue = 0f
                        try {
                            offsetY.animateDecay(
                                velocity,
                                splineBasedDecay(this@awaitPointerEventScope),
                            ) {
                                val delta = value - lastValue
                                lastValue = value
                                updatedOnGesture(Offset.Zero, 1f, delta)
                            }
                        } finally {
                            updatedOnGestureEnd()
                        }
                    }
                }
            }
        }
    }
}