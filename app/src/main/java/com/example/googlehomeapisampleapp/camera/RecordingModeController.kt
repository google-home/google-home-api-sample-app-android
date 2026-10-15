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

package com.example.googlehomeapisampleapp.camera

import android.util.Log
import com.google.home.HomeDevice
import com.google.home.google.GoogleCameraDevice
import com.google.home.google.GoogleDoorbellDevice
import com.google.home.google.RecordingMode
import com.google.home.google.RecordingModeTrait
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transform

/**
 * Represents a single recording mode option with its availability status
 * and human-readable label.
 *
 * @param recordingMode The SDK enum value for this recording mode.
 * @param index The index used by the SDK to identify this mode.
 * @param available Whether this mode can currently be selected on this device.
 * @param readableString A human-readable label shown in the UI.
 */
data class RecordingModeOption(
    val recordingMode: RecordingModeTrait.RecordingModeEnum,
    val index: Int,
    val available: Boolean,
    val readableString: String,
)

/**
 * Interface for reading and updating the camera's recording mode via the
 * [RecordingMode] GHP trait.
 */
interface RecordingModeController {
    /**
     * Emits the full list of recording mode options supported by the device,
     * each annotated with availability and a human-readable label.
     */
    val recordingModeOptions: Flow<List<RecordingModeOption>>

    /**
     * Emits the index of the currently selected recording mode, or null if unknown.
     */
    val selectedRecordingModeIndex: Flow<Int?>

    /**
     * Updates the device's selected recording mode.
     *
     * @param index The index of the mode to select, from [recordingModeOptions].
     * @return true if the update succeeded, false otherwise.
     */
    suspend fun setRecordingMode(index: Int): Boolean
}

/**
 * Production implementation of [RecordingModeController].
 *
 * Resolves the correct device type at construction time. If the device is
 * neither a [GoogleCameraDevice] nor a [GoogleDoorbellDevice], the controller
 * safely emits empty state and no-ops on writes rather than hanging indefinitely.
 */
class RecordingModeControllerImpl(private val device: HomeDevice) : RecordingModeController {
    private val TAG = "RecordingModeController"

    // Resolved once at construction. Null means unsupported device type —
    // flows will emit empty/null and writes will no-op safely.
    private val deviceType = when {
        device.has(GoogleCameraDevice) -> GoogleCameraDevice
        device.has(GoogleDoorbellDevice) -> GoogleDoorbellDevice
        else -> null
    }

    override val recordingModeOptions: Flow<List<RecordingModeOption>> =
        if (deviceType == null) {
            Log.w(TAG, "Device ${device.id} has RecordingMode trait but unknown device type.")
            flowOf(emptyList())
        } else {
            device.type(deviceType)
                .transform { type ->
                    val trait = type.trait(RecordingMode)
                    if (trait == null) {
                        emit(emptyList())
                        return@transform
                    }
                    val supported = trait.supportedRecordingModes
                        ?.map { it.recordingMode } ?: emptyList()
                    val available = trait.availableRecordingModes
                        ?.map { it.toInt() } ?: emptyList()

                    emit(supported.mapIndexed { index, mode ->
                        RecordingModeOption(
                            recordingMode = mode,
                            index = index,
                            available = available.contains(index),
                            readableString = mode.toReadableString(),
                        )
                    })
                }
                .distinctUntilChanged()
        }

    override val selectedRecordingModeIndex: Flow<Int?> =
        if (deviceType == null) {
            flowOf(null)
        } else {
            device.type(deviceType)
                .transform { type ->
                    val trait = type.trait(RecordingMode)
                    emit(trait?.selectedRecordingMode?.toInt())
                }
                .distinctUntilChanged()
        }

    override suspend fun setRecordingMode(index: Int): Boolean {
        // Guard against unsupported device type to avoid indefinite suspension
        val resolvedType = deviceType ?: run {
            Log.w(TAG, "Cannot set recording mode — unsupported device type for ${device.id}")
            return false
        }
        return try {
            val type = device.type(resolvedType).first()
            val trait = type.trait(RecordingMode) ?: run {
                Log.w(TAG, "RecordingMode trait not available on device ${device.id}")
                return false
            }
            trait.update {
                setSelectedRecordingMode(index.toUByte())
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set recording mode to index $index on device ${device.id}", e)
            false
        }
    }

    companion object {
        private fun RecordingModeTrait.RecordingModeEnum.toReadableString(): String =
            when (this) {
                RecordingModeTrait.RecordingModeEnum.Disabled -> "Disabled"
                RecordingModeTrait.RecordingModeEnum.Cvr -> "Continuous Video Recording"
                RecordingModeTrait.RecordingModeEnum.Ebr -> "Event Based Recording"
                RecordingModeTrait.RecordingModeEnum.Etr -> "Event Triggered Recording"
                RecordingModeTrait.RecordingModeEnum.LiveView -> "Live View"
                RecordingModeTrait.RecordingModeEnum.Images -> "Still Images"
                else -> "Unknown"
            }
    }
}