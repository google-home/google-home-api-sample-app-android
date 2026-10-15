package com.example.googlehomeapisampleapp.camera.timeline.repository

import android.util.Log
import com.example.googlehomeapisampleapp.HomeClientProvider
import com.google.home.google.CameraTimeline
import com.google.home.google.CameraTimelineTrait.TimelineMode as TraitTimelineMode
import com.google.home.google.GoogleCameraDevice
import com.google.home.google.GoogleDoorbellDevice
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import java.time.Duration as JavaDuration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class GhpTimelineDataSource @Inject constructor(
    private val homeClientProvider: HomeClientProvider
) : TimelineDataSource {

    override fun fetch(deviceId: String, range: TimelineRange): Flow<TimelineData> = flow {
        val chunks = range.split(JavaDuration.ofHours(11))
        val trait = getCameraTimelineTrait(deviceId)

        for (chunk in chunks) {
            var pageToken: String? = null
            do {
                val response = withRetry {
                    withTimeout(20.seconds) {
                        trait.listTimelinePeriods(
                            startTimeMillis = chunk.start.toEpochMilli().toULong(),
                            endTimeMillis = chunk.endInclusive.toEpochMilli().toULong(),
                            modes = listOf(
                                TraitTimelineMode.Video,
                                TraitTimelineMode.Events,
                                TraitTimelineMode.CameraStates,
                            ),
                            optionalArgs = {
                                if (!pageToken.isNullOrEmpty()) {
                                    this.pageToken = pageToken!!
                                }
                            },
                        )
                    }
                } ?: break

                val intervals = response.timelinePeriodsStreams
                    .flatMap { stream ->
                        val mode = stream.timelineMode.toTimelineMode()
                        stream.periods.map { period ->
                            val timelinePeriod = period.toTimelinePeriod(mode)
                            mode to timelinePeriod
                        }
                    }
                    .groupBy({ pair -> pair.first }, { pair -> pair.second })

                if (intervals.isNotEmpty()) {
                    emit(
                        TimelineData(
                            videoTimelines = intervals[TimelineMode.VIDEO]?.map { it as TimelinePeriod.Video }
                                ?: emptyList(),
                            eventTimelines = intervals[TimelineMode.EVENTS]?.map { it as TimelinePeriod.Events }
                                ?: emptyList(),
                            cameraStateTimelines = intervals[TimelineMode.CAMERA_STATES]?.map { it as TimelinePeriod.NoVideo }
                                ?: emptyList(),
                        )
                    )
                }
                pageToken = response.nextPageToken
            } while (!pageToken.isNullOrEmpty())
        }
    }

    private fun TimelineRange.split(maxDuration: JavaDuration): List<TimelineRange> {
        if (JavaDuration.between(start, endInclusive) <= maxDuration) {
            return listOf(this)
        }
        val results = mutableListOf<TimelineRange>()
        var currentStart = start
        while (currentStart < endInclusive) {
            var nextEnd = currentStart.plus(maxDuration)
            if (nextEnd > endInclusive) {
                nextEnd = endInclusive
            }
            results.add(TimelineRange(currentStart, nextEnd))
            currentStart = nextEnd
        }
        return results
    }

    private suspend fun <T> withRetry(times: Int = 1, block: suspend () -> T): T? {
        var currentAttempt = 0
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if (e is TimeoutCancellationException) {
                    Log.w(TAG, "Timeout fetching timelines")
                } else {
                    Log.w(TAG, "Error fetching timelines", e)
                }

                if (currentAttempt < times) {
                    currentAttempt++
                } else {
                    return null
                }
            }
        }
    }

    private suspend fun getCameraTimelineTrait(deviceId: String): CameraTimeline {
        val homeClient = homeClientProvider.getClient()
        val devices = homeClient.devices().first()
        val device = devices.find { it.id.id == deviceId }
            ?: throw IllegalArgumentException("Device not found: $deviceId")

        val cameraType = listOf(GoogleCameraDevice, GoogleDoorbellDevice).first { device.has(it) }
        val deviceType = device.type(cameraType).first()
        return deviceType.trait(CameraTimeline)
            ?: throw IllegalStateException("CameraTimeline trait not found")
    }

    companion object {
        private const val TAG = "GhpTimelineDataSource"
    }
}