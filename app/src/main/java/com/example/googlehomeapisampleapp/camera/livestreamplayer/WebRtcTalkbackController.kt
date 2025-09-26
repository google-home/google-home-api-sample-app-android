package com.example.googlehomeapisampleapp.camera.livestreamplayer

import android.util.Log
import com.example.googlehomeapisampleapp.camera.signaling.SignalingService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Controller for WebRTC talkback.
 *
 * @property peerConnectionFactory The factory to use for creating peer connections.
 * @property signalingService The signaling service to use for talkback.
 * @property audioDeviceModule The audio device module to use for talkback.
 */
class WebRtcTalkbackController(
    private val peerConnectionFactory: PeerConnectionFactory,
    private val signalingService: SignalingService,
    private val audioDeviceModule: JavaAudioDeviceModule?,
) : TalkbackController {

    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private val _isTalkbackEnabled = MutableStateFlow(false)
    override val isTalkbackEnabled: Flow<Boolean> = _isTalkbackEnabled

    /**
     * Initialize the talkback controller.
     *
     * @param peerConnection The peer connection to use for talkback.
     */
    fun initialize(peerConnection: PeerConnection) {
        localAudioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("talkback", localAudioSource)
        localAudioTrack?.setEnabled(false)
        peerConnection.addTrack(localAudioTrack)
        audioDeviceModule?.setMicrophoneMute(true) // start muted
        _isTalkbackEnabled.value = false
        Log.d(TAG, "Talkback initialized")
    }

    private suspend fun startTalkback() {
        Log.d(TAG, "Starting talkback")
        if (signalingService.configureTalkback(true)) {
            localAudioTrack?.setEnabled(true)
            _isTalkbackEnabled.value = true
            audioDeviceModule?.setMicrophoneMute(false)
        }
    }

    private suspend fun stopTalkback() {
        Log.d(TAG, "Stopping talkback")
        if (signalingService.configureTalkback(false)) {
            localAudioTrack?.setEnabled(false)
            _isTalkbackEnabled.value = false
            audioDeviceModule?.setMicrophoneMute(true)
        }
    }

    override suspend fun toggleTalkback(enabled: Boolean) {
        Log.d(TAG, "Toggling talkback: $enabled")
        if (enabled) {
            startTalkback()
        } else {
            stopTalkback()
        }
    }

    /** Dispose of the audio resources. */
    suspend fun dispose() {
        if (_isTalkbackEnabled.value) {
            stopTalkback()
        }
        localAudioSource?.dispose()
        localAudioTrack?.dispose()
        localAudioSource = null
        localAudioTrack = null
    }

    companion object {
        private const val TAG = "WebRtcTalkbackController"
    }
}