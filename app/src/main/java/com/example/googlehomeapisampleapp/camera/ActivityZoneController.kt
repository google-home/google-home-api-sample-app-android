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
import com.google.home.ConnectivityState
import com.google.home.HomeDevice
import com.google.home.HomeException
import com.google.home.google.GoogleCameraDevice
import com.google.home.google.GoogleDoorbellDevice
import com.google.home.google.ZoneManagement
import com.google.home.google.ZoneManagementTrait
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withTimeout

/**
 * Interface for reading and managing activity zones on a camera device
 * via the [ZoneManagement] GHP trait.
 */
interface ActivityZoneController {
    /**
     * Emits the current list of activity zones configured on the device.
     * Emits an empty list if no zones are configured or the trait is unavailable.
     */
    val activityZones: Flow<List<ActivityZone>>

    /**
     * Emits the maximum 2D Cartesian coordinate bounds supported by the device.
     * Required for scaling zone vertices correctly.
     */
    val twoDCartesianMax: Flow<ZoneManagementTrait.TwoDCartesianVertexStruct?>

    /**
     * Creates a new activity zone on the device.
     *
     * @param zone The [ActivityZone] to create.
     * @return [ZoneUpdateStatus.Success] on success, [ZoneUpdateStatus.Failure] otherwise.
     */
    suspend fun addZone(zone: ActivityZone): ZoneUpdateStatus

    /**
     * Deletes an existing activity zone from the device.
     *
     * @param zoneId The ID of the zone to delete.
     * @return [ZoneUpdateStatus.Success] on success, [ZoneUpdateStatus.Failure] otherwise.
     */
    suspend fun deleteZone(zoneId: Int): ZoneUpdateStatus
}

/**
 * Production implementation of [ActivityZoneController] backed by the
 * [ZoneManagement] GHP trait.
 *
 * Supports both [GoogleCameraDevice] and [GoogleDoorbellDevice].
 * If the device is neither, all flows emit empty/null and writes no-op safely.
 */
class ActivityZoneControllerImpl(private val device: HomeDevice) : ActivityZoneController {
    private val TAG = "ActivityZoneController"

    private val deviceType = when {
        device.has(GoogleCameraDevice) -> GoogleCameraDevice
        device.has(GoogleDoorbellDevice) -> GoogleDoorbellDevice
        else -> null
    }

    override val activityZones: Flow<List<ActivityZone>> =
        if (deviceType == null) {
            Log.w(TAG, "Device ${device.id} has ZoneManagement but unknown device type.")
            flowOf(emptyList())
        } else {
            device.type(deviceType)
                .transform { type ->
                    val trait = type.trait(ZoneManagement)
                    if (trait == null) {
                        emit(emptyList())
                        return@transform
                    }
                    val maxVertex = trait.twoDCartesianMax
                    val zones = trait.zones?.map { struct ->
                        ActivityZone.fromZoneInformationStruct(struct, maxVertex)
                    } ?: emptyList()
                    emit(zones)
                }
                .distinctUntilChanged()
        }

    override val twoDCartesianMax: Flow<ZoneManagementTrait.TwoDCartesianVertexStruct?> =
        if (deviceType == null) {
            flowOf(null)
        } else {
            device.type(deviceType)
                .transform { type ->
                    val trait = type.trait(ZoneManagement)
                    emit(trait?.twoDCartesianMax)
                }
                .distinctUntilChanged()
        }

    override suspend fun addZone(zone: ActivityZone): ZoneUpdateStatus {
        val resolvedType = deviceType ?: return ZoneUpdateStatus.Failure(
            "Cannot add zone — unsupported device type for ${device.id}"
        )
        Log.d(TAG, "addZone: Starting for device ${device.id}")
        Log.d(TAG, "addZone: zone=$zone")
        return try {
            withTimeout(TIMEOUT_MS) {
                Log.d(TAG, "addZone: Getting online trait...")
                val trait = getOnlineTrait(resolvedType)
                if (trait == null) {
                    Log.e(TAG, "addZone: Trait is null or device offline!")
                    return@withTimeout ZoneUpdateStatus.Failure(
                        "ZoneManagement trait not available or device offline."
                    )
                }
                Log.d(TAG, "addZone: Calling createTwoDCartesianZone...")
                trait.createTwoDCartesianZone(zone.toTwoDCartesianZoneStruct())
                Log.d(TAG, "addZone: Success!")
                ZoneUpdateStatus.Success
            }
        } catch (e: HomeException) {
            Log.e(TAG, "addZone: HomeException — ${e.message}", e)
            ZoneUpdateStatus.Failure("Failed to create zone: ${e.message}")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "addZone: Timed out after ${TIMEOUT_MS}ms")
            ZoneUpdateStatus.Failure("Timeout creating zone.")
        } catch (e: Exception) {
            Log.e(TAG, "addZone: Unexpected error — ${e.message}", e)
            ZoneUpdateStatus.Failure("Unexpected error: ${e.message}")
        }
    }

    override suspend fun deleteZone(zoneId: Int): ZoneUpdateStatus {
        val resolvedType = deviceType ?: return ZoneUpdateStatus.Failure(
            "Cannot delete zone — unsupported device type for ${device.id}"
        )
        return try {
            withTimeout(TIMEOUT_MS) {
                val trait = getOnlineTrait(resolvedType)
                    ?: return@withTimeout ZoneUpdateStatus.Failure(
                        "ZoneManagement trait not available or device offline."
                    )
                trait.removeZone(zoneId = zoneId.toUShort())
                ZoneUpdateStatus.Success
            }
        } catch (e: HomeException) {
            Log.e(TAG, "Failed to delete activity zone $zoneId on device ${device.id}", e)
            ZoneUpdateStatus.Failure("Failed to delete zone: ${e.message}")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Timeout deleting activity zone $zoneId on device ${device.id}")
            ZoneUpdateStatus.Failure("Timeout deleting zone.")
        }
    }

    /**
     * Returns the [ZoneManagement] trait only when the device is online,
     * or null if unavailable.
     */
    private suspend fun getOnlineTrait(
        resolvedDeviceType: com.google.home.DeviceTypeFactory<*>
    ): ZoneManagement? {
        return try {
            Log.d(TAG, "getOnlineTrait: Waiting for online trait...")
            val result = device.type(resolvedDeviceType).first { type ->
                val trait = type.trait(ZoneManagement)
                val isOnline = trait?.metadata?.sourceConnectivity?.connectivityState == ConnectivityState.ONLINE
                Log.d(TAG, "getOnlineTrait: trait=$trait, isOnline=$isOnline")
                isOnline
            }.trait(ZoneManagement)
            Log.d(TAG, "getOnlineTrait: Got trait = $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getOnlineTrait: Failed — ${e.message}", e)
            null
        }
    }

    companion object {
        private const val TIMEOUT_MS = 8000L
    }
}