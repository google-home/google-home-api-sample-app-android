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
import com.example.googlehomeapisampleapp.camera.signaling.SignalingService
import com.google.errorprone.annotations.CanIgnoreReturnValue
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import javax.inject.Inject

/** Builder class for creating [WebRtcPlayer] instances. */
class WebRtcPlayerBuilder
@Inject
internal constructor(private val peerConnectionFactoryProvider: PeerConnectionFactoryProvider) {

  private var signalingService: SignalingService? = null
  private var microphonePermissionGranted: Boolean = false

  /**
   * Sets the [SignalingService] to use for the player.
   *
   * @param signalingService The [SignalingService] to use for the player.
   * @return This builder.
   */
  @CanIgnoreReturnValue
  fun setSignalingService(signalingService: SignalingService): WebRtcPlayerBuilder {
    this.signalingService = signalingService
    return this
  }

  /**
   * Sets the microphone permission status.
   *
   * @param granted Whether the microphone permission is granted.
   * @return This builder.
   */
  @CanIgnoreReturnValue
  fun setMicrophonePermission(granted: Boolean): WebRtcPlayerBuilder {
    this.microphonePermissionGranted = granted
    return this
  }

  /**
   * Builds a [WebRtcPlayer] instance.
   *
   * @return The created [WebRtcPlayer], or null if creation fails.
   */
  fun build(): WebRtcPlayer? {
    peerConnectionFactoryProvider.initializeFactory(microphonePermissionGranted)
    val peerConnectionFactory: PeerConnectionFactory =
      peerConnectionFactoryProvider.getPeerConnectionFactory()
    val rtcConfig = PeerConnection.RTCConfiguration(emptyList())

    val mediaConstraints =
      MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
      }

    val localSignalingService = signalingService
    if (localSignalingService == null) {
      Log.e(TAG, "SignalingService not set, cannot create player.")
      return null
    }

    val talkbackController =
      WebRtcTalkbackController(
        peerConnectionFactory,
        localSignalingService,
        peerConnectionFactoryProvider.getAudioDeviceModule(),
      )

    return WebRtcPlayer(
      peerConnectionFactory,
      rtcConfig,
      localSignalingService,
      peerConnectionFactoryProvider.getEglBaseContext(),
      mediaConstraints,
      talkbackController,
    )
  }

  companion object {
    private const val TAG = "WebRtcPlayerBuilder"
  }
}