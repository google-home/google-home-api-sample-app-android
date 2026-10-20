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
import com.example.googlehomeapisampleapp.camera.signaling.getCameraDeviceType
import com.google.home.ConnectivityState
import com.google.home.HomeDevice
import com.google.home.google.PushAvStreamTransport
import com.google.home.google.PushAvStreamTransportTrait.TransportStatusEnum
import com.google.home.trait
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/** Factory for creating [OnOffController] instances. */
class OnOffControllerFactory @Inject internal constructor() {

  /**
   * Creates an [OnOffController] from a [HomeDevice].
   *
   * @param device The device to create the controller for.
   * @return The created [OnOffController], or null if the device does not support On/Off.
   */
  fun create(device: HomeDevice): OnOffController? {
    if (device.has(PushAvStreamTransport)) {
      return PushAvStreamTransportOnOffController(device)
    }

    Log.w(
      TAG,
      "No PushAvStreamTransport trait found on device ${device.id}, cannot create OnOffController.",
    )
    return null
  }

  companion object {
    private const val TAG = "OnOffControllerFactory"
  }
}

internal suspend fun HomeDevice.pushAvStreamTransport(): PushAvStreamTransport? {
  val deviceType = getCameraDeviceType() ?: return null

  return type(deviceType).trait(PushAvStreamTransport).firstOrNull {
    it?.metadata?.sourceConnectivity?.connectivityState == ConnectivityState.ONLINE
  }
}

internal fun PushAvStreamTransport.isRecording(): Boolean {
  return currentConnections?.any { it.transportStatus == TransportStatusEnum.Active } ?: false
}