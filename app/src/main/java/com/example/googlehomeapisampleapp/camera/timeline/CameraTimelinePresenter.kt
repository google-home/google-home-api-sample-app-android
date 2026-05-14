package com.example.googlehomeapisampleapp.camera.timeline

import android.util.Log
import com.example.googlehomeapisampleapp.camera.timeline.repository.TimelineData
import com.example.googlehomeapisampleapp.camera.timeline.repository.TimelineRange
import com.example.googlehomeapisampleapp.camera.timeline.repository.TimelineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CameraTimelinePresenter"
private const val FETCH_DEBOUNCE_MS = 300L
private const val BASE_SECONDS_PER_PIXEL = 18f  // 3600s / 200px

@OptIn(FlowPreview::class)
@Singleton
class CameraTimelinePresenter @Inject constructor(
    private val timelineRepository: TimelineRepository,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var observerJob: Job? = null

    fun present(
        deviceId: String,
        initialTimestamp: Instant?,
    ): Flow<CameraTimelineUiState> {
        Log.d(TAG, "Presenting timeline for device: $deviceId")

        var currentTime = initialTimestamp ?: Instant.now()
        var zoomLevel = 1f
        var panOffsetSeconds = 0L
        var isLive = initialTimestamp == null

        val stateFlow = MutableStateFlow(
            CameraTimelineUiState(
                timelineData = TimelineData(),
                currentTime = currentTime,
                isLive = isLive,
                onPan = {},
                onZoom = { _, _ -> },
                onFlingStart = {},
                onDateSelected = {},
                onTick = {},
                onSwitchToLive = {},
            )
        )
        val fetchTriggerFlow = MutableStateFlow(0L)
        coroutineScope.launch {
            fetchTriggerFlow
                .debounce(FETCH_DEBOUNCE_MS)
                .collect {
                    val displayTime = stateFlow.value.currentTime
                    val halfWindowSeconds = (12 * 3600 / zoomLevel).toLong()
                    val fetchRange = TimelineRange(
                        start = displayTime.minusSeconds(halfWindowSeconds),
                        endInclusive = displayTime.plusSeconds(halfWindowSeconds),
                    )
                    timelineRepository.fetchTimelinePeriod(deviceId, fetchRange)
                }
        }

        fun updateUiState(
            displayTime: Instant,
            triggerFetch: Boolean = true,
        ) {
            val now = Instant.now()
            val clampedTime = displayTime.coerceAtMost(now)


            stateFlow.update { current ->
                current.copy(
                    currentTime = clampedTime,
                    isLive = isLive,
                    onPan = { deltaPixels ->
                        val adjustedSecondsPerPixel = BASE_SECONDS_PER_PIXEL / zoomLevel
                        if (isLive && deltaPixels != 0f) isLive = false
                        panOffsetSeconds = (panOffsetSeconds + (deltaPixels * adjustedSecondsPerPixel).toLong())
                            .coerceAtLeast(0L)
                        val newTime = currentTime.minusSeconds(panOffsetSeconds).coerceAtMost(Instant.now())
                        updateUiState(newTime)
                    },
                    onZoom = { scaleFactor, _ ->
                        zoomLevel = (zoomLevel * scaleFactor).coerceIn(0.5f, 4f)
                        updateUiState(clampedTime)
                    },
                    onFlingStart = { _ ->
                        if (isLive) isLive = false
                        updateUiState(clampedTime)
                    },
                    onDateSelected = { timestampMillis ->
                        isLive = false
                        currentTime = Instant.ofEpochMilli(timestampMillis)
                        panOffsetSeconds = 0L
                        updateUiState(currentTime)
                    },
                    onTick = { newTime ->
                        if (isLive) {
                            currentTime = newTime
                            updateUiState(newTime)
                        }
                    },
                    onSwitchToLive = {
                        isLive = true
                        currentTime = Instant.now()
                        panOffsetSeconds = 0L
                        updateUiState(currentTime)
                    },
                )
            }

            if (triggerFetch) {
                fetchTriggerFlow.value = System.currentTimeMillis()
            }
        }
        observerJob?.cancel()
        observerJob = coroutineScope.launch {
            timelineRepository.observeTimelines(deviceId).collect { data ->
                stateFlow.update { current -> current.copy(timelineData = data) }
            }
        }

        updateUiState(currentTime, triggerFetch = true)
        return stateFlow
    }
}