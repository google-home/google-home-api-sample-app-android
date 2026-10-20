/* Copyright 2025 Google LLC */
package com.example.googlehomeapisampleapp.camera.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.Instant

private const val DEFAULT_PIXELS_PER_HOUR = 200f

@Composable
fun CameraTimeline(
    uiState: CameraTimelineUiState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .handleTimelineGestures(
                    onGesture = { _, zoomChange, panChange ->
                        if (zoomChange != 1f) {
                            uiState.onZoom(zoomChange, 0f)
                        } else {
                            uiState.onPan(panChange)
                        }
                    },
                    onGestureStart = {},
                    onGestureEnd = {},
                )
        ) {
            TimelineTrack(
                timelineDataProvider = { uiState.timelineData },
                currentTimeProvider = { uiState.currentTime },
                pixelsPerHourProvider = { DEFAULT_PIXELS_PER_HOUR },
                modifier = Modifier.fillMaxSize(),
            )
            Playhead(
                currentTimeProvider = { uiState.currentTime },
            )

            DateChip(
                currentTime = uiState.currentTime,
                onDateSelected = uiState.onDateSelected,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (!uiState.isLive) 56.dp else 8.dp),
            )

            if (!uiState.isLive) {
                Button(
                    onClick = { uiState.onSwitchToLive() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Switch to Live View")
                }
            }
        }
    }
    val currentOnTick by rememberUpdatedState(uiState.onTick)
    val currentIsLive by rememberUpdatedState(uiState.isLive)

    LaunchedEffect(currentIsLive) {
        if (currentIsLive) {
            while (true) {
                delay(1000)
                currentOnTick(Instant.now())
            }
        }
    }
}