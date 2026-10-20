/* Copyright 2025 Google LLC */
package com.example.googlehomeapisampleapp.camera.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun Playhead(currentTimeProvider: () -> Instant) {
    val density = LocalDensity.current
    val textEndX = with(density) { CameraTimelineDefaults.maxPlayheadWidth.toPx() }
    val playheadOffsetPx = with(density) { CameraTimelineDefaults.playheadOffset.toPx() }
    val primaryColor = MaterialTheme.colorScheme.primary
    val textMeasurer = rememberTextMeasurer()
    val textStyle = remember(primaryColor) {
        TextStyle(
            color = primaryColor,
            fontSize = 12.sp,
            textAlign = TextAlign.Right,
            fontFeatureSettings = "tnum",
        )
    }

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("h:mm:ss a", Locale.getDefault())
    }

    Canvas(modifier = Modifier.fillMaxSize().testTag("Playhead")) {
        val currentTime = currentTimeProvider()
        val timeText = currentTime.atZone(ZoneId.systemDefault()).format(timeFormatter)

        val paddingPx = 8.dp.toPx()
        val lineStartX = textEndX + paddingPx
        val textLayoutResult = textMeasurer.measure(timeText, style = textStyle)
        val textWidth = textLayoutResult.size.width
        val textHeight = textLayoutResult.size.height
        val textVertCenter = playheadOffsetPx - (textHeight / 2)
        val textLeft = textEndX - textWidth

        drawText(textLayoutResult, topLeft = Offset(textLeft, textVertCenter))

        drawLine(
            color = primaryColor,
            start = Offset(lineStartX, playheadOffsetPx),
            end = Offset(size.width, playheadOffsetPx),
            strokeWidth = 1.dp.toPx(),
        )
    }
}