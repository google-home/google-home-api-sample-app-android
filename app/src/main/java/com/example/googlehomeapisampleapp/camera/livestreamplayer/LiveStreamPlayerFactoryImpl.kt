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
import com.example.googlehomeapisampleapp.camera.signaling.SignalingServiceFactory
import com.google.home.HomeDevice
import com.google.home.google.WebRtcLiveView
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Provider

/** Factory class for creating live stream players based on the available traits. */
class LiveStreamPlayerFactoryImpl
@Inject
internal constructor(
  private val webRtcPlayerBuilderProvider: Provider<WebRtcPlayerBuilder>,
  private val signalingServiceFactoryProvider: Provider<SignalingServiceFactory>,
) : LiveStreamPlayerFactory {

  /**
   * Creates a [LiveStreamPlayer] from a [HomeDevice].
   *
   * @param device The device to create stream for
   * @param scope The [CoroutineScope] to use for the player.
   * @return The created [LiveStreamPlayer], or null if creation fails.
   */
  override suspend fun createPlayerFromDevice(
    device: HomeDevice,
    scope: CoroutineScope,
    microphonePermissionGranted: Boolean,
  ): LiveStreamPlayer? {
    if (device.has(WebRtcLiveView)) {
      return createPlayerFromWebRtcLiveView(device, scope, microphonePermissionGranted)
    }
    Log.e(TAG, "No supported camera trait found on device ${device.id}")
    return null
  }

  /**
   * Creates a [LiveStreamPlayer] from a [WebRtcLiveView] trait.
   *
   * @param device The [HomeDevice] containing the WebRtcLiveView trait to create the player from.
   * @param scope The [CoroutineScope] to use for the player.
   * @return The created [LiveStreamPlayer], or null if creation fails.
   */
  private fun createPlayerFromWebRtcLiveView(
    device: HomeDevice,
    scope: CoroutineScope,
    microphonePermissionGranted: Boolean,
  ): LiveStreamPlayer? {
    Log.i(TAG, "createPlayerFromWebRtcLiveView")
    val signalingService =
      signalingServiceFactoryProvider.get().createWebRtcLiveViewTraitSignalingService(device, scope)
    return webRtcPlayerBuilderProvider.get().apply {
      setSignalingService(signalingService)
      setMicrophonePermission(microphonePermissionGranted)
    }.build()
  }

  companion object {
    private const val TAG = "LiveStreamPlayerFactoryImpl"
  }
}
