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
import com.google.home.google.ZoneManagement
import javax.inject.Inject

/**
 * Factory for creating [ActivityZoneController] instances.
 *
 * Returns null if the device does not support the [ZoneManagement] trait,
 * following the same pattern as [OnOffControllerFactory] and
 * [RecordingModeControllerFactory].
 */
class ActivityZoneControllerFactory @Inject internal constructor() {

    /**
     * Creates an [ActivityZoneController] for the given device.
     *
     * @param device The [HomeDevice] to control.
     * @return An [ActivityZoneController] if the device supports zone management,
     * or null otherwise.
     */
    fun create(device: HomeDevice): ActivityZoneController? {
        if (device.has(ZoneManagement)) {
            Log.d(TAG, "ZoneManagement supported on device ${device.id}")
            return ActivityZoneControllerImpl(device)
        }
        Log.w(TAG, "ZoneManagement trait NOT found on device ${device.id}.")
        return null
    }

    companion object {
        private const val TAG = "ActivityZoneControllerFactory"
    }
}