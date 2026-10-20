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
import android.view.Surface
import com.example.googlehomeapisampleapp.camera.signaling.SignalingService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceEglRenderer
import org.webrtc.VideoTrack

/**
 * Player class for handling WebRTC connections and media playback.
 * Restored with stable EGL initialization to support Settings toggles.
 */
class WebRtcPlayer(
  private val peerConnectionFactory: PeerConnectionFactory,
  private val rtcConfig: PeerConnection.RTCConfiguration,
  private val signalingService: SignalingService,
  private val eglBaseContext: EglBase.Context,
  private val mediaConstraints: MediaConstraints,
  private val talkbackController: WebRtcTalkbackController?,
) : LiveStreamPlayer {

  override val isTalkbackSupported = talkbackController != null
  override val isTalkbackEnabled: Flow<Boolean> =
    talkbackController?.isTalkbackEnabled ?: flowOf(false)

  private val peerConnectionEvents: Flow<PeerConnectionObserverEvent>
  private val peerConnection: MutableStateFlow<PeerConnection?> = MutableStateFlow(null)
  private var dataChannel: DataChannel? = null
  private var renderTarget: Surface? = null

  // The EglRenderer must be managed carefully to survive setting toggles
  private val eglRenderer: EglRenderer = SurfaceEglRenderer("WebRtcPlayer")
  private var isEglInitialized = false

  private var remoteAudioTrack: AudioTrack? = null
  private var remoteVideoTrack: VideoTrack? = null

  private val _state = MutableStateFlow(LiveStreamPlayerState.NOT_STARTED)
  override val state: Flow<LiveStreamPlayerState> = _state

  override suspend fun toggleTalkback(enabled: Boolean) {
    talkbackController?.toggleTalkback(enabled)
  }

  init {
    peerConnectionEvents =
      peerConnectionFactory.createPeerConnection(rtcConfig) { peerConnection.value = it }
    _state.value = LiveStreamPlayerState.READY
  }

  override suspend fun start() {
    Log.d(TAG, "start")
    _state.value = LiveStreamPlayerState.STARTING
    peerConnectionEvents.collect { event -> handlePeerConnectionEvent(event) }
  }

  private suspend fun handlePeerConnectionEvent(event: PeerConnectionObserverEvent) {
    when (event) {
      is PeerConnectionObserverEvent.SetupPlayerEvent -> setUpPeerConnection()
      is PeerConnectionObserverEvent.IceGatheringChangeEvent -> {
        if (event.newState == PeerConnection.IceGatheringState.COMPLETE) {
          sendAndHandleOffer()
        }
      }
      is PeerConnectionObserverEvent.AddTrackEvent -> handleAddTrackEvent(event)
      is PeerConnectionObserverEvent.ConnectionChangeEvent -> {
        if (event.newState == PeerConnection.PeerConnectionState.CLOSED ||
          event.newState == PeerConnection.PeerConnectionState.DISCONNECTED ||
          event.newState == PeerConnection.PeerConnectionState.FAILED
        ) {
          dispose()
        }
      }
      else -> {}
    }
  }

  private suspend fun setUpPeerConnection() {
    if (_state.value != LiveStreamPlayerState.STARTING) return
    val pc = peerConnection.filterNotNull().first()
    val transceiverInit = RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)

    pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, transceiverInit)
    pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, transceiverInit)

    talkbackController?.initialize(pc)
    dataChannel = requireNotNull(pc.createDataChannel("data-channel1"))
    createOffer()
  }

  private suspend fun createOffer() {
    val pc = requireNotNull(peerConnection.value)
    val offerResponse = pc.createOffer(mediaConstraints) as? PeerConnectionObserverResponse.CreateSuccess
      ?: throw IllegalStateException("Offer creation failed")
    pc.setLocalDescription(SessionDescription.Type.OFFER, offerResponse.rawSdp)
  }

  private suspend fun sendAndHandleOffer() {
    val description = requireNotNull(peerConnection.value?.localDescription).description
    val response = signalingService.sendOffer(SignalingService.Sdp(description))

    when (response) {
      is SignalingService.SendOfferResponse.Answer -> {
        peerConnection.value?.setRemoteDescription(SessionDescription.Type.ANSWER, response.sdp.rawSdp)
      }
      else -> Log.e(TAG, "Unexpected signaling response")
    }
  }

  override fun attachRenderer(renderTarget: Surface) {
    if (_state.value == LiveStreamPlayerState.STOPPING) return
    this.renderTarget = renderTarget
    renderTarget?.let { renderToSurface(it) }
  }

  override fun detachRenderer() {
    Log.d(TAG, "detachRenderer")
    remoteVideoTrack?.removeSink(eglRenderer)
    renderTarget = null
    if (isEglInitialized) {
      eglRenderer.releaseEglSurface {}
      eglRenderer.release()
      isEglInitialized = false
    }
  }

  private fun renderToSurface(surface: Surface) {
    remoteVideoTrack?.let { track ->
      if (!isEglInitialized) {
        Log.d(TAG, "Initializing EglRenderer for surface")
        eglRenderer.init(eglBaseContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
        isEglInitialized = true
      }
      eglRenderer.createEglSurface(surface)
      track.addSink(eglRenderer)
    }
  }

  private fun handleAddTrackEvent(event: PeerConnectionObserverEvent.AddTrackEvent) {
    val track = event.receiver?.track()
    when (track?.kind()) {
      MediaStreamTrack.AUDIO_TRACK_KIND -> {
        remoteAudioTrack = track as AudioTrack
        remoteAudioTrack?.setEnabled(true)
      }
      MediaStreamTrack.VIDEO_TRACK_KIND -> {
        remoteVideoTrack = track as VideoTrack
        if (_state.value == LiveStreamPlayerState.STARTING || _state.value == LiveStreamPlayerState.READY) {
          _state.value = LiveStreamPlayerState.STREAMING
          renderTarget?.let { renderToSurface(it) }
        }
      }
    }
  }

  override suspend fun dispose() {
    if (_state.value == LiveStreamPlayerState.DISPOSED || _state.value == LiveStreamPlayerState.STOPPING) return
    _state.value = LiveStreamPlayerState.STOPPING
    peerConnection.value?.close()
    remoteAudioTrack?.dispose()
    remoteVideoTrack?.dispose()
    withTimeoutOrNull(DISPOSE_TIMEOUT_MS) { talkbackController?.dispose() }
    withTimeoutOrNull(DISPOSE_TIMEOUT_MS) { signalingService.dispose() }
    detachRenderer()
    dataChannel?.close()
    _state.value = LiveStreamPlayerState.DISPOSED
  }

  companion object {
    private const val TAG = "WebRtcPlayer"
    private const val DISPOSE_TIMEOUT_MS = 5000L
  }
}