package com.example.googlehomeapisampleapp.camera.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.googlehomeapisampleapp.camera.timeline.repository.TimelineData
import com.example.googlehomeapisampleapp.camera.timeline.repository.TimelinePeriod
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
internal fun TimelineTrack(
    timelineDataProvider: () -> TimelineData,
    currentTimeProvider: () -> Instant,
    pixelsPerHourProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val videoColor = MaterialTheme.colorScheme.primary
    val videoBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val cameraStateColor = MaterialTheme.colorScheme.secondary
    val eventColor = MaterialTheme.colorScheme.tertiary
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val timeMarkerTextStyle = remember(labelColor) {
        TextStyle(
            color = labelColor,
            fontSize = 10.sp,
            textAlign = TextAlign.Left,
            fontFeatureSettings = "tnum",
        )
    }

    val textLayoutCache = remember { mutableMapOf<String, TextLayoutResult>() }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h a", Locale.getDefault()) }

    val trackStartOffset = with(density) { CameraTimelineDefaults.maxPlayheadWidth.toPx() + 20.dp.toPx() }
    val videoWidthPx = with(density) { CameraTimelineDefaults.videoSegmentWidth.toPx() }
    val cameraStateWidthPx = with(density) { CameraTimelineDefaults.cameraStateSegmentWidth.toPx() }
    val eventWidthPx = with(density) { CameraTimelineDefaults.eventSegmentWidth.toPx() }
    val spacingPx = with(density) { CameraTimelineDefaults.segmentSpacing.toPx() }

    val playheadOffsetPx = with(density) { CameraTimelineDefaults.playheadOffset.toPx() }
    val cameraStateXOffset = trackStartOffset + videoWidthPx + spacingPx
    val eventXOffset = cameraStateXOffset + cameraStateWidthPx + spacingPx

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("TimelineTrack")
            .graphicsLayer(clip = true)
            .drawWithCache {
                val gradientHeightPx = with(this) { CameraTimelineDefaults.gradientHeight.toPx() }
                val playheadOffsetPx = with(this) { CameraTimelineDefaults.playheadOffset.toPx() }
                val gradientBrush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, backgroundColor, backgroundColor, Color.Transparent),
                    startY = playheadOffsetPx - gradientHeightPx / 2,
                    endY = playheadOffsetPx + gradientHeightPx / 2,
                    tileMode = TileMode.Clamp,
                )
                onDrawWithContent {
                    drawContent()
                    drawGradientOverlay(gradientBrush, CameraTimelineDefaults.playheadOffset)
                }
            }
    ) {
        val currentTime = currentTimeProvider()
        val pixelsPerHour = pixelsPerHourProvider()
        val maxTime = currentTimeProvider()

        val backgroundYOffset = calculateBackgroundTop(currentTime, maxTime, pixelsPerHour)
        val visibleRange = getVisibleTimeRange(pixelsPerHour, currentTime, size.height)

        drawTimeMarkers(
            currentTimeProvider = currentTimeProvider,
            maxTime = maxTime,
            visibleRange = visibleRange,
            pixelsPerHour = pixelsPerHour,
            playheadOffsetPx = playheadOffsetPx,
            lineColor = lineColor,
            textLayoutCache = textLayoutCache,
            textMeasurer = textMeasurer,
            textStyle = timeMarkerTextStyle,
            timeFormatter = timeFormatter,
        )

        val timelineData = timelineDataProvider()

        drawTimelineBar(
            timelinePeriods = timelineData.videoTimelines,
            visibleRange = visibleRange,
            currentTime = currentTime,
            backgroundYOffset = backgroundYOffset,
            pixelsPerHour = pixelsPerHour,
            color = videoColor,
            backgroundColor = videoBackgroundColor,
            xOffset = trackStartOffset,
            width = videoWidthPx,
            mergeOverlappingTimelinePeriods = true,
        )

        drawTimelineBar(
            timelinePeriods = timelineData.cameraStateTimelines,
            visibleRange = visibleRange,
            currentTime = currentTime,
            backgroundYOffset = backgroundYOffset,
            pixelsPerHour = pixelsPerHour,
            color = cameraStateColor,
            backgroundColor = videoBackgroundColor,
            xOffset = cameraStateXOffset,
            width = cameraStateWidthPx,
        )

        drawTimelineBar(
            timelinePeriods = timelineData.eventTimelines,
            visibleRange = visibleRange,
            currentTime = currentTime,
            backgroundYOffset = backgroundYOffset,
            pixelsPerHour = pixelsPerHour,
            color = eventColor,
            backgroundColor = videoBackgroundColor,
            xOffset = eventXOffset,
            width = eventWidthPx,
        )
    }
}

private fun DrawScope.drawTimeMarkers(
    currentTimeProvider: () -> Instant,
    maxTime: Instant,
    visibleRange: ClosedRange<Instant>,
    pixelsPerHour: Float,
    playheadOffsetPx: Float,
    lineColor: Color,
    textLayoutCache: MutableMap<String, TextLayoutResult>,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    timeFormatter: DateTimeFormatter,
) {
    val currentTime = currentTimeProvider()
    val dashWidthPx = CameraTimelineDefaults.markerDashWidth.toPx()
    val paddingPx = 8.dp.toPx()

    val endDateTime = visibleRange.endInclusive.atZone(ZoneId.systemDefault())
    var currentMarker = endDateTime.truncatedTo(ChronoUnit.HOURS)
    if (currentMarker.isBefore(endDateTime)) {
        currentMarker = currentMarker.plusHours(1)
    }

    val maxMarkerTime = maxTime.atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.HOURS)
    if (currentMarker.isAfter(maxMarkerTime)) {
        currentMarker = maxMarkerTime
    }

    while (currentMarker.toInstant().isAfter(visibleRange.start)) {
        val markerInstant = currentMarker.toInstant()
        val timeText = currentMarker.format(timeFormatter)
        val textLayoutResult = textLayoutCache.getOrPut(timeText) {
            textMeasurer.measure(timeText, style = textStyle)
        }

        val yOffsetPx = markerInstant.toTimelineY(currentTime, pixelsPerHour, playheadOffsetPx)

        val dashStartX = paddingPx
        val dashEndX = dashStartX + dashWidthPx

        val textX = dashEndX + paddingPx
        val textHeight = textLayoutResult.size.height
        val textVertCenter = yOffsetPx - (textHeight / 2)

        drawText(textLayoutResult, topLeft = Offset(textX, textVertCenter))

        drawLine(
            color = lineColor,
            start = Offset(dashStartX, yOffsetPx),
            end = Offset(dashEndX, yOffsetPx),
            strokeWidth = 1.dp.toPx(),
        )

        currentMarker = currentMarker.minusHours(1)
    }
}

private fun Density.calculateBackgroundTop(
    currentTime: Instant,
    maxTime: Instant,
    pixelsPerHour: Float,
): Float {
    val playheadOffsetPx = CameraTimelineDefaults.playheadOffset.toPx()
    val maxTimeY = maxTime.toTimelineY(currentTime, pixelsPerHour, playheadOffsetPx)
    return maxOf(0f, maxTimeY)
}

private fun DrawScope.drawGradientOverlay(gradientBrush: Brush, timeMarkerOffset: androidx.compose.ui.unit.Dp) {
    val heightPx = CameraTimelineDefaults.gradientHeight.toPx()
    val playheadWidthPx = CameraTimelineDefaults.maxPlayheadWidth.toPx()
    val playheadOffsetPx = timeMarkerOffset.toPx()

    drawRect(
        brush = gradientBrush,
        topLeft = Offset(x = 0f, y = playheadOffsetPx - heightPx / 2),
        size = Size(width = playheadWidthPx, height = heightPx),
    )
}

private fun DrawScope.drawTimelineBar(
    timelinePeriods: List<TimelinePeriod>,
    visibleRange: ClosedRange<Instant>,
    currentTime: Instant,
    backgroundYOffset: Float,
    pixelsPerHour: Float,
    color: Color,
    backgroundColor: Color,
    xOffset: Float,
    width: Float,
    mergeOverlappingTimelinePeriods: Boolean = false,
) {
    val playheadOffsetPx = CameraTimelineDefaults.playheadOffset.toPx()

    drawRect(
        color = backgroundColor,
        topLeft = Offset(xOffset, backgroundYOffset),
        size = Size(width, size.height - backgroundYOffset),
    )

    val startIndex = timelinePeriods.indexOfVisibleRangeStart(visibleRange.start)

    if (startIndex == -1) {
        return
    }

    var i = startIndex
    while (i < timelinePeriods.size) {
        val timeline = timelinePeriods[i]
        if (timeline.startTime > visibleRange.endInclusive) {
            break
        }

        val mergedStartTime = timeline.startTime
        var mergedEndTime = timeline.endTime

        if (mergeOverlappingTimelinePeriods) {
            while (i + 1 < timelinePeriods.size) {
                val nextTimeline = timelinePeriods[i + 1]
                if (nextTimeline.startTime <= mergedEndTime) {
                    mergedEndTime = maxOf(mergedEndTime, nextTimeline.endTime)
                    i++
                } else {
                    break
                }
            }
        }

        val rectTop = mergedEndTime.toTimelineY(currentTime, pixelsPerHour, playheadOffsetPx)
        val durationSeconds = mergedEndTime.epochSecond - mergedStartTime.epochSecond
        val rectHeight = durationSeconds.secondsToPixels(pixelsPerHour)

        drawRoundRect(
            color = color,
            topLeft = Offset(xOffset, rectTop),
            size = Size(width, rectHeight),
            cornerRadius = CornerRadius(5.dp.toPx()),
        )
        i++
    }
}

private fun List<TimelinePeriod>.indexOfVisibleRangeStart(visibleRangeStart: Instant): Int {
    val index = binarySearchBy(visibleRangeStart) { it.endTime }
    return if (index < 0) -(index + 1) else index
}

internal fun Density.getVisibleTimeRange(
    pixelsPerHour: Float,
    currentTime: Instant,
    heightPx: Float,
): ClosedRange<Instant> {
    val playheadOffsetPx = CameraTimelineDefaults.playheadOffset.toPx()

    val hoursPast = (heightPx - playheadOffsetPx) / pixelsPerHour
    val hoursFuture = playheadOffsetPx / pixelsPerHour

    val startVisible = currentTime.minusSeconds(((hoursPast + 2) * SECONDS_IN_HOUR).toLong())
    val endVisible = currentTime.plusSeconds(((hoursFuture + 2) * SECONDS_IN_HOUR).toLong())
    return startVisible..endVisible
}

private fun Instant.toTimelineY(
    currentTime: Instant,
    pixelsPerHour: Float,
    playheadOffsetPx: Float,
): Float {
    val diffSeconds = currentTime.epochSecond - this.epochSecond
    return playheadOffsetPx + (diffSeconds / SECONDS_IN_HOUR.toFloat() * pixelsPerHour)
}

private fun Long.secondsToPixels(pixelsPerHour: Float): Float {
    return (this / SECONDS_IN_HOUR.toFloat()) * pixelsPerHour
}

private const val SECONDS_IN_HOUR = 3600