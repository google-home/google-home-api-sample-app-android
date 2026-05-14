/* Copyright 2025 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.example.googlehomeapisampleapp.camera.timeline.repository

import com.google.home.google.CameraTimelineTrait.TimelineMode as TraitTimelineMode
import com.google.home.google.CameraTimelineTrait.TimelinePeriodStruct
import com.google.home.google.CameraTimelineTrait.VideoUnavailableReason
import java.time.Instant

/**
 * A timeline period for the timeline.
 *
 * @property startTime The start time of the period.
 * @property endTime The end time of the period.
 *
 * NOTE: Implements [ClosedRange] with inclusive bounds on both ends. Two adjacent
 * periods where one ends exactly when the next begins will appear to overlap.
 * [TimelineRange] has the same caveat. Callers must account for this when
 * checking containment or overlap.
 */
sealed class TimelinePeriod(open val startTime: Instant, open val endTime: Instant) :
    ClosedRange<Instant> {
    override val start: Instant = startTime
    override val endInclusive: Instant = endTime

    data class Unspecified(override val startTime: Instant, override val endTime: Instant) :
        TimelinePeriod(startTime, endTime)

    data class Video(override val startTime: Instant, override val endTime: Instant) :
        TimelinePeriod(startTime, endTime)

    data class Events(
        override val startTime: Instant,
        override val endTime: Instant,
        val sessionId: String,
        // thumbnailUrl is intentionally null: thumbnail URLs are only available
        // via the History API, not from CameraTimelineTrait.
        val thumbnailUrl: String?,
    ) : TimelinePeriod(startTime, endTime)

    data class NoVideo(
        override val startTime: Instant,
        override val endTime: Instant,
        val noVideoReason: NoVideoReason,
    ) : TimelinePeriod(startTime, endTime)
}

/**
 * A time range used for fetching and caching timeline data.
 *
 * NOTE: Uses [ClosedRange] with inclusive bounds — adjacent ranges that share
 * an endpoint will appear to overlap. See [TimelinePeriod] for details.
 */
data class TimelineRange(override val start: Instant, override val endInclusive: Instant) :
    ClosedRange<Instant> {
    val end: Instant = endInclusive
}

/** Defines the different types of data that can be displayed on the timeline. */
enum class TimelineMode {
    UNSPECIFIED,
    VIDEO,
    EVENTS,
    CAMERA_STATES,
}

/** Maps the trait's timeline mode to the local [TimelineMode]. */
fun TraitTimelineMode.toTimelineMode(): TimelineMode {
    return when (this) {
        TraitTimelineMode.Video -> TimelineMode.VIDEO
        TraitTimelineMode.Events -> TimelineMode.EVENTS
        TraitTimelineMode.CameraStates -> TimelineMode.CAMERA_STATES
        else -> TimelineMode.UNSPECIFIED
    }
}

/** Maps [TimelinePeriodStruct] to the local [TimelinePeriod] using the given [TimelineMode]. */
fun TimelinePeriodStruct.toTimelinePeriod(mode: TimelineMode): TimelinePeriod {
    val startTime = Instant.ofEpochMilli(this.startTimeMillis.toLong())
    val endTime = Instant.ofEpochMilli(this.endTimeMillis.toLong())

    return when (mode) {
        TimelineMode.EVENTS -> {
            val noVideoPeriodData = this.noVideoPeriodData.getOrNull()
            val sessionId = noVideoPeriodData?.cameraHistoryItem?.getOrNull()?.sessionId ?: ""
            TimelinePeriod.Events(
                startTime = startTime,
                endTime = endTime,
                sessionId = sessionId,
                thumbnailUrl = null,
            )
        }
        TimelineMode.CAMERA_STATES -> {
            val noVideoPeriodData = this.noVideoPeriodData.getOrNull()
            val noVideoReason = noVideoPeriodData?.reason?.toNoVideoReason() ?: NoVideoReason.UNSPECIFIED
            TimelinePeriod.NoVideo(
                startTime = startTime,
                endTime = endTime,
                noVideoReason = noVideoReason,
            )
        }
        TimelineMode.VIDEO -> {
            TimelinePeriod.Video(startTime = startTime, endTime = endTime)
        }
        TimelineMode.UNSPECIFIED -> {
            TimelinePeriod.Unspecified(startTime = startTime, endTime = endTime)
        }
    }
}

/**
 * Holds the different types of data that can be displayed on the timeline.
 *
 * Each list of [TimelinePeriod] MUST be sorted by [TimelinePeriod.startTime] in ascending order
 * (oldest to newest).
 */
data class TimelineData(
    val videoTimelines: List<TimelinePeriod.Video> = emptyList(),
    val eventTimelines: List<TimelinePeriod.Events> = emptyList(),
    val cameraStateTimelines: List<TimelinePeriod.NoVideo> = emptyList(),
) {
    init {
        require(videoTimelines.zipWithNext().all { (a, b) -> a.startTime <= b.startTime }) {
            "videoTimelines must be sorted by startTime ascending"
        }
        require(eventTimelines.zipWithNext().all { (a, b) -> a.startTime <= b.startTime }) {
            "eventTimelines must be sorted by startTime ascending"
        }
        require(cameraStateTimelines.zipWithNext().all { (a, b) -> a.startTime <= b.startTime }) {
            "cameraStateTimelines must be sorted by startTime ascending"
        }
    }
}

enum class NoVideoReason {
    UNSPECIFIED,
    USER,
    SCHEDULE,
    OCCUPANCY,
    NO_EVENTS,
    CHARGING,
    PRIVACY_SWITCH,
    THERMAL_OVERRIDE,
    DEVICE_UPDATING,
    USED_BY_DUO,
    NOT_CONNECTED,
    BATTERY_OVERRIDE,
    UNMOUNTED,
    BATTERY_FAULT,
    STILL_IMAGE_ONLY,
    ETR_MODE,
}

fun VideoUnavailableReason.toNoVideoReason(): NoVideoReason {
    return when (this) {
        VideoUnavailableReason.User -> NoVideoReason.USER
        VideoUnavailableReason.Schedule -> NoVideoReason.SCHEDULE
        VideoUnavailableReason.Occupancy -> NoVideoReason.OCCUPANCY
        VideoUnavailableReason.NoEvents -> NoVideoReason.NO_EVENTS
        VideoUnavailableReason.Charging -> NoVideoReason.CHARGING
        VideoUnavailableReason.PrivacySwitch -> NoVideoReason.PRIVACY_SWITCH
        VideoUnavailableReason.ThermalOverride -> NoVideoReason.THERMAL_OVERRIDE
        VideoUnavailableReason.DeviceUpdating -> NoVideoReason.DEVICE_UPDATING
        VideoUnavailableReason.UsedByDuo -> NoVideoReason.USED_BY_DUO
        VideoUnavailableReason.NotConnected -> NoVideoReason.NOT_CONNECTED
        VideoUnavailableReason.BatteryOverride -> NoVideoReason.BATTERY_OVERRIDE
        VideoUnavailableReason.Unmounted -> NoVideoReason.UNMOUNTED
        VideoUnavailableReason.BatteryFault -> NoVideoReason.BATTERY_FAULT
        VideoUnavailableReason.StillImageOnly -> NoVideoReason.STILL_IMAGE_ONLY
        VideoUnavailableReason.EtrMode -> NoVideoReason.ETR_MODE
        else -> NoVideoReason.UNSPECIFIED
    }
}