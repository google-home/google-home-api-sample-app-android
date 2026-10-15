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
import com.google.home.DeviceType
import com.google.home.DeviceTypeFactory
import com.google.home.HomeDevice
import com.google.home.google.CameraAvStreamManagement
import com.google.home.google.GoogleCameraDevice
import com.google.home.google.GoogleDoorbellDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform

/** Interface for controlling Audio Recording settings using CameraAvStreamManagement. */
interface CameraAvStreamManagementController {
  val isRecordingMicrophoneMuted: Flow<Boolean>
  suspend fun setRecordingMicrophoneMuted(muted: Boolean): Boolean
}

/** Implementation that avoids ClassCastException via standard SDK accessors. */
class CameraAvStreamManagementControllerImpl(private val device: HomeDevice) :
  CameraAvStreamManagementController {

  private val supportedType: DeviceTypeFactory<out DeviceType>? =
    listOf(GoogleCameraDevice, GoogleDoorbellDevice).firstOrNull { device.has(it) }

  override val isRecordingMicrophoneMuted: Flow<Boolean> = if (supportedType != null) {
    device.type(supportedType)
      .transform { typeInstance ->
        typeInstance?.trait(CameraAvStreamManagement)?.let { emit(it) }
      }
      .map { it.recordingMicrophoneMuted ?: false }
      .distinctUntilChanged()
  } else {
    flowOf(false)
  }

  override suspend fun setRecordingMicrophoneMuted(muted: Boolean): Boolean {
    return try {
      val types = device.types().first()
      val typeInstance = types.firstOrNull { it is GoogleCameraDevice || it is GoogleDoorbellDevice }
      val trait = typeInstance?.trait(CameraAvStreamManagement) ?: return false

      trait.update { setRecordingMicrophoneMuted(muted) }
      true
    } catch (e: Exception) {
      Log.e("CameraAvStreamCtrl", "Failed to update microphone mute state.", e)
      false
    }
  }
}