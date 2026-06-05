/* Copyright 2026 Google LLC

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
package com.example.googlehomeapisampleapp.history

import android.util.Log
import com.google.home.BasicCameraEventDetails
import com.google.home.HomeBrief
import com.google.home.HistoryItem
import com.google.home.Id
import com.google.home.annotation.HomeExperimentalApi
import com.google.home.google.CameraHistory
import com.google.home.google.CameraHistoryTrait
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed interface HistoryUiDataModel : HistoryEventUi {
    val eventId: String
    val timestamp: Instant
    val entityName: String
    override val id: String get() = eventId

    data class DefaultEvent(
        override val eventId: String,
        override val timestamp: Instant,
        override val entityName: String,
        val eventName: String? = null,
    ) : HistoryUiDataModel

    data class CameraEvent(
        override val eventId: String,
        override val timestamp: Instant,
        override val entityName: String,
        val eventType: HistoryUiEventType,
        val mediaUrl: MediaUrl,
        val deviceId: String,
        val shortCaption: String? = null
    ) : HistoryUiDataModel

    /**
     * Represents a Home Brief (AI-generated daily summary) in the activity feed.
     *
     * @param briefId Unique identifier for this brief (used as stable list key).
     * @param body The full AI-generated summary text.
     * @param generateTime When the brief was generated; used as the display timestamp.
     * @param keyCameraEvents Associated camera events with thumbnail/preview URLs.
     */
    data class HomeBriefEvent(
        val briefId: String,
        val body: String,
        val generateTime: Instant?,
        val keyCameraEvents: List<HomeBriefCameraEvent>,
    ) : HistoryUiDataModel {
        override val eventId: String get() = briefId
        override val timestamp: Instant
            get() = generateTime
                ?: keyCameraEvents.mapNotNull { it.startTime }.maxOrNull()
                ?: Instant.EPOCH
        override val entityName: String get() = "Home Brief"
    }
}

/**
 * Lightweight camera event data carried inside a [HistoryUiDataModel.HomeBriefEvent].
 * Contains only what the UI needs (thumbnail/preview URLs and timing).
 */
data class HomeBriefCameraEvent(
    val sessionId: String,
    val entityObjectId: Id,
    val startTime: Instant?,
    val previewUrl: String,
    val thumbnailUrl: String,
)

/** Maps a SDK [BasicCameraEventDetails] to our UI model. */
@OptIn(HomeExperimentalApi::class)
fun BasicCameraEventDetails.toUiModel() = HomeBriefCameraEvent(
    sessionId = sessionId,
    entityObjectId = entityObjectId,
    startTime = startTime,
    previewUrl = previewUrl,
    thumbnailUrl = thumbnailUrl,
)

/** Maps a SDK [HomeBrief] to [HistoryUiDataModel.HomeBriefEvent]. */
@OptIn(HomeExperimentalApi::class)
fun HomeBrief.toUiDataModel() = HistoryUiDataModel.HomeBriefEvent(
    briefId = id.id,
    body = body,
    generateTime = generateTime,
    keyCameraEvents = keyCameraEvents.map { it.toUiModel() },
)

/**
 * Shared subtitle formatting used in both HistoryView and HistoryVideoPlayerScreen.
 * Centralising here avoids duplicating the "\u2022" separator pattern across composables.
 */
val HistoryUiDataModel.displaySubtitle: String
    get() = "${timestamp.formatToTime()} \u2022 $entityName"

/**
 * UI representation of media URLs associated with a camera history event.
 *
 * Maps from [CameraHistoryTrait.MediaUrl] SDK type. Video URLs (DASH, MP4, HLS)
 * require an active Google Home Premium subscription; they will be empty otherwise.
 */
data class MediaUrl(
    val previewUrl: String = "",
    val thumbnailUrl: String = "",
    val dashManifestUrl: String = "",
    val mp4DownloadUrl: String = "",
    val hlsMasterPlaylistUrl: String = "",
) {
    /**
     * Returns true if any playable video format (MP5, HLS, or MPEG-DASH) is available.
     * Checking all three ensures hasVideo accurately reflects
     */
    val hasVideo: Boolean
        get() = mp4DownloadUrl.isNotBlank() ||
                dashManifestUrl.isNotBlank() ||
                hlsMasterPlaylistUrl.isNotBlank()

    /** Returns true if an MP4 is available for download. */
    val canDownload: Boolean get() = mp4DownloadUrl.isNotBlank()

    companion object {
        fun fromCameraHistoryMediaUrl(mediaUrl: CameraHistoryTrait.MediaUrl?): MediaUrl {
            return MediaUrl(
                previewUrl = mediaUrl?.preview_url ?: "",
                thumbnailUrl = mediaUrl?.thumbnail_url ?: "",
                dashManifestUrl = mediaUrl?.dash_manifest_url ?: "",
                mp4DownloadUrl = mediaUrl?.mp4_download_url ?: "",
                hlsMasterPlaylistUrl = mediaUrl?.hls_master_playlist_url ?: "",
            )
        }
    }
}

enum class HistoryUiEventType {
    Motion, Person, Doorbell, Animal, Vehicle, Unknown;

    companion object {
        fun fromEventType(eventType: CameraHistoryTrait.EventType): HistoryUiEventType = when (eventType) {
            CameraHistoryTrait.EventType.Motion -> Motion
            CameraHistoryTrait.EventType.Person -> Person
            CameraHistoryTrait.EventType.Doorbell -> Doorbell
            CameraHistoryTrait.EventType.Animal -> Animal
            CameraHistoryTrait.EventType.Vehicle -> Vehicle
            else -> Unknown
        }
    }
}

private val EVENT_TYPE_PRIORITY = listOf(
    CameraHistoryTrait.EventType.Doorbell,
    CameraHistoryTrait.EventType.Person,
    CameraHistoryTrait.EventType.Motion,
    CameraHistoryTrait.EventType.Vehicle,
    CameraHistoryTrait.EventType.Animal,
)

@OptIn(HomeExperimentalApi::class)
fun HistoryItem.toUiDataModel(): HistoryUiDataModel {
    val event = this.event
    Log.d("HistoryUiDataModel", "toUiDataModel: event type = ${event.javaClass.simpleName}, eventName = ${event.eventName}")
    return when (event) {
        is CameraHistory.HistoryItemEvent -> {
            val eventTypes = event.eventTracks?.flatMap { it.eventTypes }?.toSet()
            val detected = EVENT_TYPE_PRIORITY.firstOrNull { eventTypes?.contains(it) == true }
                ?: CameraHistoryTrait.EventType.Unknown

            Log.d("HistoryUiDataModel", "CameraEvent captions: ${event.captions}")
            // Extract the short caption if it exists
            val shortCaption = event.captions?.find { it.captionType == CameraHistoryTrait.CaptionType.Short }?.captionText
            Log.d("HistoryUiDataModel", "Extracted shortCaption: $shortCaption")

            HistoryUiDataModel.CameraEvent(
                eventId = id.id,
                timestamp = timestamp,
                entityName = entityName ?: "Camera",
                eventType = HistoryUiEventType.fromEventType(detected),
                mediaUrl = MediaUrl.fromCameraHistoryMediaUrl(event.mediaUrl),
                deviceId = entityId.id,
                shortCaption = shortCaption // Add this argument
            )
        }
        else -> {
            Log.d("HistoryUiDataModel", "DefaultEvent: fallback handling")
            HistoryUiDataModel.DefaultEvent(
                eventId = id.id,
                timestamp = timestamp,
                entityName = entityName ?: "Event",
                eventName = event.eventName,
            )
        }
    }
}

/** Formats an [Instant] to a human-readable time string. */
internal fun Instant.formatToTime(): String =
    DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault()).format(this)
