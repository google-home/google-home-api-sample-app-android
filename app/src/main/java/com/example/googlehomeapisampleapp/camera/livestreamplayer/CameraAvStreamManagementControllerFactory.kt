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

package com.example.googlehomeapisampleapp.camera.livestreamplayer

import android.util.Log
import com.google.home.HomeDevice
import com.google.home.google.CameraAvStreamManagement
import javax.inject.Inject

/** Factory for creating [CameraAvStreamManagementController] instances. */
class CameraAvStreamManagementControllerFactory @Inject internal constructor() {

  /**
   * Creates a [CameraAvStreamManagementController] from a [HomeDevice].
   *
   * @param device The device to create the controller for.
   * @return The created [CameraAvStreamManagementController], or null if the device does not support the required trait.
   */
  fun create(device: HomeDevice): CameraAvStreamManagementController? {
    if (device.has(CameraAvStreamManagement.Companion)) {
      return CameraAvStreamManagementControllerImpl(device)
    }

    Log.w(
      TAG,
      "CameraAvStreamManagementTrait not found on device ${device.id}, cannot create controller.",
    )
    return null
  }

  companion object {
    private const val TAG = "CameraAvStreamManagementControllerFactory"
  }
}