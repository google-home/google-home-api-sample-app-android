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

import com.google.home.HomeDevice
import kotlinx.coroutines.CoroutineScope

/** Interface for a factory class for creating live stream players based on the available traits. */
interface LiveStreamPlayerFactory {
  /**
   * Creates a [LiveStreamPlayer] from a [HomeDevice].
   *
   * @param device The device to create stream for
   * @param scope The [CoroutineScope] to use for the player.
   * @param microphonePermissionGranted Whether the microphone permission is granted.
   * @return The created [LiveStreamPlayer], or null if creation fails.
   */
  suspend fun createPlayerFromDevice(
    device: HomeDevice,
    scope: CoroutineScope,
    microphonePermissionGranted: Boolean,
  ): LiveStreamPlayer?
}