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
import com.google.home.HistoryFilter
import com.google.home.HistoryItem
import com.google.home.HomeBrief
import com.google.home.Id
import com.google.home.StateChangeEvent
import com.google.home.annotation.HomeExperimentalApi
import com.google.home.google.CameraHistory
import com.google.home.google.CameraHistoryTrait
import com.google.home.google.ExtendedThermostat
import com.google.home.google.ExtendedThermostatTrait
import com.google.home.matter.standard.DoorLock
import com.google.home.matter.standard.DoorLockTrait
import com.google.home.matter.standard.Thermostat
import com.google.home.matter.standard.ThermostatTrait
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed interface HistoryUiDataModel : HistoryEventUi {
    val eventId: String
    val timestamp: Instant
    val entityName: String
    val details: List<String> get() = emptyList()
    override val id: String get() = eventId

    /**
     * Shared interface for trait state-change events in the activity feed.
     *
     * Encapsulates the logic for computing event titles and detail bullets:
     * - If 1 property changed: title is the specific change, details is empty.
     * - If multiple properties changed: title is a summary ("<Trait> Updated"), and details contains all changes.
     * - If 0 properties changed: title defaults to "<Trait> Updated", details is empty.
     */
    sealed interface StateChangeUiModel : HistoryUiDataModel {
        val traitDisplayName: String
        val changeDetails: List<String>

        val eventTitle: String
            get() = when {
                changeDetails.size == 1 -> changeDetails.first()
                else -> "$traitDisplayName Updated"
            }

        override val details: List<String>
            get() = if (changeDetails.size > 1) changeDetails else emptyList()
    }

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
        val shortCaption: String? = null,
    ) : HistoryUiDataModel

    data class DoorLockEvent(
        override val eventId: String,
        override val timestamp: Instant,
        override val entityName: String,
        val eventTitle: String,
        val isUnlock: Boolean = false,
        override val details: List<String> = emptyList(),
    ) : HistoryUiDataModel

    data class ThermostatStateChange(
        override val eventId: String,
        override val timestamp: Instant,
        override val entityName: String,
        val oldRunningMode: ThermostatRunningMode,
        val newRunningMode: ThermostatRunningMode,
        val oldTemperature: Double?,
        val newTemperature: Double?,
        val oldSystemMode: ThermostatSystemMode,
        val newSystemMode: ThermostatSystemMode,
        val oldCoolingSetpoint: Double?,
        val newCoolingSetpoint: Double?,
        val oldHeatingSetpoint: Double?,
        val newHeatingSetpoint: Double?,
    ) : StateChangeUiModel {
        override val traitDisplayName: String get() = "Thermostat"
        override val changeDetails: List<String>
            get() = buildList {
                if (oldTemperature != newTemperature) {
                    val oldTemp = oldTemperature?.let { "%.1f°C".format(it) } ?: "N/A"
                    val newTemp = newTemperature?.let { "%.1f°C".format(it) } ?: "N/A"
                    add("Temperature: $oldTemp → $newTemp")
                }
                if (oldRunningMode != newRunningMode) {
                    add("Running Mode: ${newRunningMode.displayName}")
                }
                if (oldSystemMode != newSystemMode) {
                    add("System Mode: ${newSystemMode.displayName}")
                }
                if (oldCoolingSetpoint != newCoolingSetpoint) {
                    val newCool = newCoolingSetpoint?.let { "%.1f°C".format(it) } ?: "N/A"
                    add("Cooling Setpoint: $newCool")
                }
                if (oldHeatingSetpoint != newHeatingSetpoint) {
                    val newHeat = newHeatingSetpoint?.let { "%.1f°C".format(it) } ?: "N/A"
                    add("Heating Setpoint: $newHeat")
                }
            }
    }

    data class ExtendedThermostatStateChange(
        override val eventId: String,
        override val timestamp: Instant,
        override val entityName: String,
        val oldEcoMode: ThermostatEcoMode,
        val newEcoMode: ThermostatEcoMode,
    ) : StateChangeUiModel {
        override val traitDisplayName: String get() = "Thermostat"
        override val changeDetails: List<String>
            get() = buildList {
                if (oldEcoMode != newEcoMode) {
                    add("Eco Mode: ${newEcoMode.displayName}")
                }
            }
    }

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

enum class HistoryDeviceTypeFilter(val label: String) {
    All("All"),
    Camera("Cameras"),
    DoorLock("Locks"),
    Thermostat("Thermostats"),
}

fun HistoryDeviceTypeFilter.toApiFilter(): HistoryFilter? = when (this) {
    HistoryDeviceTypeFilter.All -> null
    HistoryDeviceTypeFilter.Camera -> {
        HistoryFilter.event(CameraHistory.HistoryItemEvent.Companion)
            .OR(HistoryFilter.trait(CameraHistory.Companion))
    }
    HistoryDeviceTypeFilter.DoorLock -> {
        HistoryFilter.event(DoorLock.LockOperationEvent.Companion)
            .OR(HistoryFilter.event(DoorLock.LockOperationErrorEvent.Companion))
            .OR(HistoryFilter.event(DoorLock.DoorLockAlarmEvent.Companion))
            .OR(HistoryFilter.trait(DoorLock.Companion))
    }
    HistoryDeviceTypeFilter.Thermostat -> {
        HistoryFilter.trait(Thermostat.Companion)
            .OR(HistoryFilter.trait(ExtendedThermostat.Companion))
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

/** Enum representing a running state of a thermostat. */
enum class ThermostatRunningMode(val displayName: String) {
    HEAT("Heat"),
    COOL("Cool"),
    OFF("Off"),
    UNKNOWN("Unknown"),
}

fun ThermostatTrait.ThermostatRunningModeEnum?.toThermostatRunningMode(): ThermostatRunningMode =
    when (this) {
        ThermostatTrait.ThermostatRunningModeEnum.Heat -> ThermostatRunningMode.HEAT
        ThermostatTrait.ThermostatRunningModeEnum.Cool -> ThermostatRunningMode.COOL
        ThermostatTrait.ThermostatRunningModeEnum.Off -> ThermostatRunningMode.OFF
        else -> ThermostatRunningMode.UNKNOWN
    }

/** Enum representing the system mode of a thermostat. */
enum class ThermostatSystemMode(val displayName: String) {
    OFF("Off"),
    AUTO("Auto"),
    COOL("Cool"),
    HEAT("Heat"),
    EMERGENCY_HEAT("Emergency Heat"),
    PRECOOLING("Precooling"),
    FAN_ONLY("Fan Only"),
    DRY("Dry"),
    SLEEP("Sleep"),
    UNKNOWN("Unknown"),
}

fun ThermostatTrait.SystemModeEnum?.toThermostatSystemMode(): ThermostatSystemMode =
    when (this) {
        ThermostatTrait.SystemModeEnum.Off -> ThermostatSystemMode.OFF
        ThermostatTrait.SystemModeEnum.Heat -> ThermostatSystemMode.HEAT
        ThermostatTrait.SystemModeEnum.Cool -> ThermostatSystemMode.COOL
        ThermostatTrait.SystemModeEnum.Auto -> ThermostatSystemMode.AUTO
        ThermostatTrait.SystemModeEnum.EmergencyHeat -> ThermostatSystemMode.EMERGENCY_HEAT
        ThermostatTrait.SystemModeEnum.Precooling -> ThermostatSystemMode.PRECOOLING
        ThermostatTrait.SystemModeEnum.FanOnly -> ThermostatSystemMode.FAN_ONLY
        ThermostatTrait.SystemModeEnum.Dry -> ThermostatSystemMode.DRY
        ThermostatTrait.SystemModeEnum.Sleep -> ThermostatSystemMode.SLEEP
        else -> ThermostatSystemMode.UNKNOWN
    }

/** Enum representing the eco mode of a thermostat. */
enum class ThermostatEcoMode(val displayName: String) {
    INACTIVE("Inactive"),
    MANUAL_ECO("Manual Eco"),
    AUTO_ECO("Auto Eco"),
    UNKNOWN("Unknown"),
}

fun ExtendedThermostatTrait.EcoMode?.toThermostatEcoMode(): ThermostatEcoMode =
    when (this) {
        ExtendedThermostatTrait.EcoMode.Inactive -> ThermostatEcoMode.INACTIVE
        ExtendedThermostatTrait.EcoMode.ManualEco -> ThermostatEcoMode.MANUAL_ECO
        ExtendedThermostatTrait.EcoMode.AutoEco -> ThermostatEcoMode.AUTO_ECO
        else -> ThermostatEcoMode.UNKNOWN
    }

@OptIn(HomeExperimentalApi::class)
private fun handleCameraEvent(item: HistoryItem, event: CameraHistory.HistoryItemEvent): HistoryUiDataModel.CameraEvent {
    val eventTypes = event.eventTracks?.flatMap { it.eventTypes }?.toSet()
    val detected = EVENT_TYPE_PRIORITY.firstOrNull { eventTypes?.contains(it) == true }
        ?: CameraHistoryTrait.EventType.Unknown
    val shortCaption = event.captions?.find { it.captionType == CameraHistoryTrait.CaptionType.Short }?.captionText

    return HistoryUiDataModel.CameraEvent(
        eventId = item.id.id,
        timestamp = item.timestamp,
        entityName = item.entityName ?: "Camera",
        eventType = HistoryUiEventType.fromEventType(detected),
        mediaUrl = MediaUrl.fromCameraHistoryMediaUrl(event.mediaUrl),
        deviceId = item.entityId.id,
        shortCaption = shortCaption,
    )
}

@OptIn(HomeExperimentalApi::class)
private fun handleThermostatStateChange(
    item: HistoryItem,
    newState: Thermostat,
    oldState: Thermostat?,
): HistoryUiDataModel.ThermostatStateChange {
    val oldTemp = oldState?.localTemperature?.let { it / 100.0 }
    val newTemp = newState.localTemperature?.let { it / 100.0 }
    val oldCool = oldState?.occupiedCoolingSetpoint?.let { it / 100.0 }
    val newCool = newState.occupiedCoolingSetpoint?.let { it / 100.0 }
    val oldHeat = oldState?.occupiedHeatingSetpoint?.let { it / 100.0 }
    val newHeat = newState.occupiedHeatingSetpoint?.let { it / 100.0 }

    return HistoryUiDataModel.ThermostatStateChange(
        eventId = item.id.id,
        timestamp = item.timestamp,
        entityName = item.entityName ?: "Thermostat",
        oldRunningMode = oldState?.thermostatRunningMode.toThermostatRunningMode(),
        newRunningMode = newState.thermostatRunningMode.toThermostatRunningMode(),
        oldTemperature = oldTemp,
        newTemperature = newTemp,
        oldSystemMode = oldState?.systemMode.toThermostatSystemMode(),
        newSystemMode = newState.systemMode.toThermostatSystemMode(),
        oldCoolingSetpoint = oldCool,
        newCoolingSetpoint = newCool,
        oldHeatingSetpoint = oldHeat,
        newHeatingSetpoint = newHeat,
    )
}

@OptIn(HomeExperimentalApi::class)
private fun handleExtendedThermostatStateChange(
    item: HistoryItem,
    newState: ExtendedThermostat,
    oldState: ExtendedThermostat?,
): HistoryUiDataModel.ExtendedThermostatStateChange {
    return HistoryUiDataModel.ExtendedThermostatStateChange(
        eventId = item.id.id,
        timestamp = item.timestamp,
        entityName = item.entityName ?: "Thermostat",
        oldEcoMode = oldState?.ecoModeState?.ecoMode.toThermostatEcoMode(),
        newEcoMode = newState.ecoModeState?.ecoMode.toThermostatEcoMode(),
    )
}

private data class LockDisplayInfo(
    val title: String,
    val isUnlock: Boolean = false,
    val details: List<String> = emptyList(),
)

/**
 * Maps door lock activity to [HistoryUiDataModel.DoorLockEvent].
 *
 * Handles two distinct sources of lock data:
 * 1. Discrete Matter cluster events ([DoorLock.LockOperationEvent], [DoorLock.LockOperationErrorEvent],
 *    [DoorLock.DoorLockAlarmEvent]): Emitted by native Matter locks representing point-in-time operations
 *    with metadata (e.g., operation source, user index, credentials).
 * 2. Trait state snapshots ([DoorLock]): Received inside a [StateChangeEvent] when the lock attribute
 *    [DoorLock.lockState] transitions (e.g., from Cloud-to-Cloud locks or Playground devices that report
 *    state changes rather than discrete cluster events).
 */
@OptIn(HomeExperimentalApi::class)
private fun handleDoorLockEvent(item: HistoryItem, event: Any?): HistoryUiDataModel.DoorLockEvent {
    val displayInfo = when (event) {
        // Discrete Matter operation event (e.g., lock, unlock, unlatch via keypad, manual, or app)
        is DoorLock.LockOperationEvent -> {
            val isUnlock = event.lockOperationType == DoorLockTrait.LockOperationTypeEnum.Unlock
            val title = when (event.lockOperationType) {
                DoorLockTrait.LockOperationTypeEnum.Lock -> "Door Locked"
                DoorLockTrait.LockOperationTypeEnum.Unlock -> "Door Unlocked"
                DoorLockTrait.LockOperationTypeEnum.Unlatch -> "Door Unlatched"
                else -> event.lockOperationType?.name ?: "Lock Operation"
            }
            LockDisplayInfo(title = title, isUnlock = isUnlock)
        }
        // Discrete Matter error event
        is DoorLock.LockOperationErrorEvent -> {
            val errorName = event.operationError?.name ?: "Unknown"
            val opName = event.lockOperationType?.name ?: "Operation"
            LockDisplayInfo(
                title = "Lock Error: $errorName",
                details = listOf("Operation: $opName"),
            )
        }
        // Discrete Matter alarm event
        is DoorLock.DoorLockAlarmEvent -> {
            val alarmName = event.alarmCode?.name ?: "Unknown"
            LockDisplayInfo(title = "Lock Alarm: $alarmName")
        }
        // Trait state change (lockState attribute update)
        is DoorLock -> {
            val isUnlock = event.lockState == DoorLockTrait.DlLockState.Unlocked
            val title = when (event.lockState) {
                DoorLockTrait.DlLockState.Locked -> "Door Locked"
                DoorLockTrait.DlLockState.Unlocked -> "Door Unlocked"
                DoorLockTrait.DlLockState.NotFullyLocked -> "Door Not Fully Locked"
                DoorLockTrait.DlLockState.Unlatched -> "Door Unlatched"
                else -> "Door Lock Updated"
            }
            LockDisplayInfo(title = title, isUnlock = isUnlock)
        }
        else -> LockDisplayInfo(title = "Door Lock Updated")
    }

    return HistoryUiDataModel.DoorLockEvent(
        eventId = item.id.id,
        timestamp = item.timestamp,
        entityName = item.entityName ?: "Door Lock",
        eventTitle = displayInfo.title,
        isUnlock = displayInfo.isUnlock,
        details = displayInfo.details,
    )
}

@OptIn(HomeExperimentalApi::class)
fun HistoryItem.toUiDataModel(): HistoryUiDataModel {
    val event = this.event
    return when (event) {
        is CameraHistory.HistoryItemEvent -> handleCameraEvent(this, event)
        // Discrete Matter events emitted by native Matter locks
        is DoorLock.LockOperationEvent,
        is DoorLock.LockOperationErrorEvent,
        is DoorLock.DoorLockAlarmEvent -> handleDoorLockEvent(this, event)
        is StateChangeEvent<*> -> {
            val newState = event.newState
            val oldState = event.oldState
            when (newState) {
                is Thermostat -> handleThermostatStateChange(this, newState, oldState as? Thermostat)
                is ExtendedThermostat -> handleExtendedThermostatStateChange(this, newState, oldState as? ExtendedThermostat)
                // Attribute state changes (e.g. Cloud-to-Cloud locks or devices reporting lockState updates)
                is DoorLock -> handleDoorLockEvent(this, newState)
                else -> HistoryUiDataModel.DefaultEvent(
                    eventId = id.id,
                    timestamp = timestamp,
                    entityName = entityName ?: "Event",
                    eventName = event.eventName ?: event.javaClass.simpleName,
                )
            }
        }
        else -> {
            HistoryUiDataModel.DefaultEvent(
                eventId = id.id,
                timestamp = timestamp,
                entityName = entityName ?: "Event",
                eventName = event.eventName ?: event.javaClass.simpleName,
            )
        }
    }
}

/** Formats an [Instant] to a human-readable time string. */
internal fun Instant.formatToTime(): String =
    DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault()).format(this)
