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
import org.webrtc.SessionDescription
import org.webrtc.SurfaceEglRenderer
import org.webrtc.VideoTrack

/**
 * Player class for handling WebRTC connections and media playback. This class should be used to
 * start a WebRTC connection and stream media to a Surface. Once it is stopping, or disposed, it
 * should not be used again.
 *
 * @param peerConnectionFactory The factory to use for creating peer connections.
 * @param rtcConfig The RTC configuration to use for the peer connection.
 * @param signalingService The signaling service to use for offer/answer exchange.
 * @param eglBaseContext The EGL base context to use for rendering.
 * @param mediaConstraints The media constraints to use for offer/answer exchange.
 * @param talkbackController Controller for talkback functionality.
 */
class WebRtcPlayer
constructor(
    private val peerConnectionFactory: PeerConnectionFactory,
    private val rtcConfig: PeerConnection.RTCConfiguration,
    private val signalingService: SignalingService,
    private val eglBaseContext: EglBase.Context,
    private val mediaConstraints: MediaConstraints,
    private val talkbackController: WebRtcTalkbackController?,
) : LiveStreamPlayer {

    override val supportsTalkback = talkbackController != null
    override val isTalkbackEnabled: Flow<Boolean> =
        talkbackController?.isTalkbackEnabled ?: flowOf(false)

    private val peerConnectionEvents: Flow<PeerConnectionObserverEvent>
    private val peerConnection: MutableStateFlow<PeerConnection?> = MutableStateFlow(null)
    private var dataChannel: DataChannel? = null
    private var renderTarget: Surface? = null
    private val eglRenderer: EglRenderer = SurfaceEglRenderer("WebRtcPlayer")

    private var remoteAudioTrack: AudioTrack? = null
    private var remoteVideoTrack: VideoTrack? = null

    private val _state = MutableStateFlow(LiveStreamPlayerState.NOT_STARTED)
    override val state: Flow<LiveStreamPlayerState> = _state

    override suspend fun toggleTalkback(enabled: Boolean) {
        talkbackController?.toggleTalkback(enabled)
    }

    init {
        peerConnectionEvents =
            peerConnectionFactory.createPeerConnection(rtcConfig, { peerConnection.value = it })
        _state.value = LiveStreamPlayerState.READY
    }

    override suspend fun start() {
        Log.d(TAG, "start")
        _state.value = LiveStreamPlayerState.STARTING
        peerConnectionEvents.collect { event -> handlePeerConnectionEvent(event) }
    }

    private suspend fun handlePeerConnectionEvent(event: PeerConnectionObserverEvent) {
        Log.d(TAG, "handlePeerConnectionEvents: $event")
        when (event) {
            is PeerConnectionObserverEvent.SetupPlayerEvent -> {
                setUpPeerConnection()
            }
            is PeerConnectionObserverEvent.IceGatheringChangeEvent -> {
                if (event.newState == PeerConnection.IceGatheringState.COMPLETE) {
                    sendAndHandleOffer()
                }
            }
            is PeerConnectionObserverEvent.AddTrackEvent -> {
                handleAddTrackEvent(event)
            }

            is PeerConnectionObserverEvent.ConnectionChangeEvent -> {
                if (
                    event.newState == PeerConnection.PeerConnectionState.CLOSED ||
                    event.newState == PeerConnection.PeerConnectionState.DISCONNECTED ||
                    event.newState == PeerConnection.PeerConnectionState.FAILED
                ) {
                    dispose()
                }
            }
            else -> {}
        }
    }

    /** Called at the start to set up the peer connection and start ice gathering. */
    private suspend fun setUpPeerConnection() {
        if (_state.value != LiveStreamPlayerState.STARTING) {
            return
        }

        val peerConnection = peerConnection.filterNotNull().first()
        // Set up player
        talkbackController?.initialize(peerConnection)
        // Data channel must be configured for webrtcliveview trait.
        dataChannel = requireNotNull(peerConnection.createDataChannel("data-channel1"))
        // Create offer to start ice gathering.
        createOffer()
    }

    /** Called when ice gathering is complete to send offer and handle response. */
    private suspend fun sendAndHandleOffer() {
        // Send offer to start the WebRTC connection.
        val sendOfferResponse = sendOffer()
        Log.d(TAG, "sendOfferResponse:")
        handleSendOfferResponse(sendOfferResponse)
    }

    // Create offer and set local description so that peerconnection can start gathering ice
    // candidates.
    // The offer should not be sent to the signaling service, until ice candidates are gathered.
    private suspend fun createOffer() {
        val peerConnection = requireNotNull(peerConnection.value)
        val createOfferResponse = peerConnection.createOffer(mediaConstraints)
        if (createOfferResponse !is PeerConnectionObserverResponse.CreateSuccess) {
            throw IllegalStateException("Failed to create offer $createOfferResponse")
        }
        Log.d(TAG, "Created offer: ${createOfferResponse.rawSdp}")
        val setLocalResponse =
            peerConnection.setLocalDescription(SessionDescription.Type.OFFER, createOfferResponse.rawSdp)
        if (setLocalResponse !is PeerConnectionObserverResponse.SetSuccess) {
            throw IllegalStateException("Failed to set local description $setLocalResponse")
        }
        Log.d(TAG, "Set local description")
    }

    // Called after ice candidates are gathered
    private suspend fun sendOffer(): SignalingService.SendOfferResponse {
        // Start signaling with the new ice candidates.
        val description = requireNotNull(peerConnection.value?.localDescription).description
        return signalingService.sendOffer(SignalingService.Sdp(description))
    }

    private suspend fun handleSendOfferResponse(
        sendOfferResponse: SignalingService.SendOfferResponse
    ) {
        when (sendOfferResponse) {
            is SignalingService.SendOfferResponse.Answer -> handleAnswer(sendOfferResponse)
            is SignalingService.SendOfferResponse.Offer -> handleOffer(sendOfferResponse)
            is SignalingService.SendOfferResponse.Error -> {
                Log.e(TAG, "Failed to send offer: ${sendOfferResponse.errorMessage}")
            }
        }
    }

    private suspend fun handleAnswer(sendOfferResponse: SignalingService.SendOfferResponse.Answer) {
        val setRemoteResponse =
            peerConnection.value?.setRemoteDescription(
                SessionDescription.Type.ANSWER,
                sendOfferResponse.sdp.rawSdp,
            )
        if (setRemoteResponse !is PeerConnectionObserverResponse.SetSuccess) {
            throw IllegalStateException("Failed to set remote description")
        }
        Log.d(TAG, "Set remote description with answer: ${sendOfferResponse.sdp.rawSdp}")
    }

    private suspend fun handleOffer(sendOfferResponse: SignalingService.SendOfferResponse.Offer) {
        val peerConnection = requireNotNull(peerConnection.value)
        val setRemoteResponse =
            peerConnection.setRemoteDescription(
                SessionDescription.Type.OFFER,
                sendOfferResponse.sdp.rawSdp,
            )
        require(setRemoteResponse !is PeerConnectionObserverResponse.SetSuccess)
        Log.d(TAG, "Set remote description with offer: ${sendOfferResponse.sdp.rawSdp}")
        // create answer
        val createAnswerResponse = peerConnection.createAnswer(mediaConstraints)
        if (createAnswerResponse !is PeerConnectionObserverResponse.CreateSuccess) {
            throw IllegalStateException("Failed to create answer")
        }

        val answerSdp = createAnswerResponse.rawSdp
        Log.d(TAG, "Created answer: $answerSdp")

        // set local description
        val setLocalResponse =
            peerConnection.setLocalDescription(SessionDescription.Type.ANSWER, answerSdp)
        if (setLocalResponse !is PeerConnectionObserverResponse.SetSuccess) {
            throw IllegalStateException("Failed to set local description")
        }
        Log.d(TAG, "Set local description")

        // send answer
        val sendAnswerResponse = signalingService.sendAnswer(answerSdp)
        if (sendAnswerResponse !is SignalingService.SendAnswerResponse.Ok) {
            throw IllegalStateException("Failed to send answer")
        }
        Log.d(TAG, "Sent answer")
    }

    // Attached after holder is created
    override fun attachRenderer(renderTarget: Surface) {
        Log.d(TAG, "attachRenderer")
        if (_state.value == LiveStreamPlayerState.STOPPING) {
            return
        }
        this.renderTarget = renderTarget
        renderToSurface(renderTarget)
    }

    override fun detachRenderer() {
        Log.d(TAG, "detachRenderer")
        remoteVideoTrack?.removeSink(eglRenderer)
        eglRenderer.releaseEglSurface { renderTarget = null }
        eglRenderer.release()
    }

    override suspend fun dispose() {
        Log.d(TAG, "dispose")
        // If the player is already disposed or stopping, it does not need to be called again.
        if (
            _state.value == LiveStreamPlayerState.DISPOSED ||
            _state.value == LiveStreamPlayerState.STOPPING
        ) {
            return
        }
        _state.value = LiveStreamPlayerState.STOPPING
        peerConnection.value?.close()
        remoteAudioTrack?.dispose()
        remoteVideoTrack?.dispose()
        withTimeoutOrNull(DISPOSE_TIMEOUT_MS) { talkbackController?.dispose() }
        withTimeoutOrNull(DISPOSE_TIMEOUT_MS) { signalingService.dispose() }
        detachRenderer()
        dataChannel?.close()
        dataChannel = null
        remoteAudioTrack = null
        remoteVideoTrack = null
        _state.value = LiveStreamPlayerState.DISPOSED
    }

    private fun handleAddTrackEvent(event: PeerConnectionObserverEvent.AddTrackEvent) {
        val track = event.receiver?.track()
        Log.d(TAG, "handleAddTrackEvent: $track")
        when (track?.kind()) {
            MediaStreamTrack.AUDIO_TRACK_KIND -> handleAudioTrack(track as AudioTrack)
            MediaStreamTrack.VIDEO_TRACK_KIND -> handleVideoTrack(track as VideoTrack)
            else -> {}
        }
    }

    private fun handleAudioTrack(track: AudioTrack) {
        remoteAudioTrack = track
        remoteAudioTrack?.setEnabled(true)
    }

    private fun handleVideoTrack(track: VideoTrack) {
        Log.d(TAG, "handleVideoTrack: $track")
        remoteVideoTrack = track
        if (_state.value == LiveStreamPlayerState.STARTING) {
            _state.value = LiveStreamPlayerState.STREAMING
            renderTarget?.let { renderToSurface(it) }
        }
    }

    private fun renderToSurface(surface: Surface) {
        remoteVideoTrack?.let {
            Log.d(TAG, "Rendering video to surface")
            eglRenderer.init(eglBaseContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
            eglRenderer.createEglSurface(surface)
            it.addSink(eglRenderer)
        }
    }

    private companion object {
        const val TAG = "WebRtcPlayer"
        const val DISPOSE_TIMEOUT_MS = 1000L
    }
}