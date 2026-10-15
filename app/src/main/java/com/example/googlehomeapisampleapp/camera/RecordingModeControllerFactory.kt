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
import com.google.home.google.RecordingMode
import javax.inject.Inject

/**
 * Factory for creating [RecordingModeController] instances.
 *
 * Returns null if the device does not support the [RecordingMode] trait,
 * following the same pattern as [OnOffControllerFactory] and
 * [CameraAvStreamManagementControllerFactory].
 */
class RecordingModeControllerFactory @Inject internal constructor() {

    /**
     * Creates a [RecordingModeController] for the given device.
     *
     * @param device The [HomeDevice] to control.
     * @return A [RecordingModeController] if the device supports recording mode,
     * or null otherwise.
     */
    fun create(device: HomeDevice): RecordingModeController? {
        if (device.has(RecordingMode)) {
            return RecordingModeControllerImpl(device)
        }
        Log.w(TAG, "RecordingMode trait not found on device ${device.id}.")
        return null
    }

    companion object {
        private const val TAG = "RecordingModeControllerFactory"
    }
}