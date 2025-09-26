package com.example.googlehomeapisampleapp.camera.livestreamplayer

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

private const val TAG = "WebRtcPlayerExtensions"

/**
 * Sealed interface for PeerConnection observer responses.
 *
 * @property CreateSuccess The response for a successful create offer/answer.
 * @property CreateFailure The response for a failed create offer/answer.
 * @property SetSuccess The response for a successful set local/remote description.
 * @property SetFailure The response for a failed set local/remote description.
 */
sealed interface PeerConnectionObserverResponse {
    /**
     * The response for a successful create offer/answer.
     *
     * @property rawSdp The raw SDP string.
     */
    data class CreateSuccess(val rawSdp: String) : PeerConnectionObserverResponse

    /**
     * The response for a failed create offer/answer.
     *
     * @property error The error message.
     */
    data class CreateFailure(val error: String) : PeerConnectionObserverResponse

    /** The response for a successful set local/remote description. */
    class SetSuccess() : PeerConnectionObserverResponse

    /**
     * The response for a failed set local/remote description.
     *
     * @property error The error message.
     */
    data class SetFailure(val error: String) : PeerConnectionObserverResponse
}

internal suspend fun PeerConnection.setRemoteDescription(
    type: SessionDescription.Type,
    sdp: String,
): PeerConnectionObserverResponse = suspendCancellableCoroutine { cont ->
    setRemoteDescription(DefaultSdpObserver(cont), SessionDescription(type, sdp))
}

internal suspend fun PeerConnection.setLocalDescription(
    type: SessionDescription.Type,
    sdp: String,
): PeerConnectionObserverResponse = suspendCancellableCoroutine { cont ->
    setLocalDescription(DefaultSdpObserver(cont), SessionDescription(type, sdp))
}

internal suspend fun PeerConnection.createAnswer(
    mediaConstraints: MediaConstraints
): PeerConnectionObserverResponse = suspendCancellableCoroutine { cont ->
    createAnswer(DefaultSdpObserver(cont), mediaConstraints)
}

internal suspend fun PeerConnection.createOffer(
    mediaConstraints: MediaConstraints
): PeerConnectionObserverResponse = suspendCancellableCoroutine { cont ->
    createOffer(DefaultSdpObserver(cont), mediaConstraints)
}

/**
 * Creates a data channel to pass the webrtcliveview trait send request
 *
 * @return The created data channel, or null if creation fails.
 */
internal fun PeerConnection.createDataChannel(name: String): DataChannel {
    val init = DataChannel.Init()
    init.ordered = true
    val dataChannel = requireNotNull(this.createDataChannel(name, init))
    return dataChannel
}

/**
 * Default implementation of [SdpObserver] that returns the result of a [Continuation] when an SDP
 * operation completes.
 */
internal class DefaultSdpObserver(private val cont: Continuation<PeerConnectionObserverResponse>) :
    SdpObserver {

    override fun onCreateSuccess(sdp: SessionDescription) {
        Log.d(TAG, "onCreateSuccess: ${sdp.description}")
        cont.resume(PeerConnectionObserverResponse.CreateSuccess(sdp.description))
    }

    override fun onSetSuccess() {
        Log.d(TAG, "onSetSuccess")
        cont.resume(PeerConnectionObserverResponse.SetSuccess())
    }

    override fun onCreateFailure(error: String) {
        Log.d(TAG, "onCreateFailure: $error")
        cont.resume(PeerConnectionObserverResponse.CreateFailure(error))
    }

    override fun onSetFailure(error: String) {
        Log.d(TAG, "onSetFailure: $error")
        cont.resume(PeerConnectionObserverResponse.SetFailure(error))
    }
}

fun PeerConnectionFactory.createPeerConnection(
    rtcConfig: PeerConnection.RTCConfiguration,
    callback: (PeerConnection) -> Unit,
): Flow<PeerConnectionObserverEvent> = callbackFlow {
    val peerConnection =
        requireNotNull(
            createPeerConnection(
                rtcConfig,
                object : PeerConnection.Observer {
                    override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
                        Log.i(TAG, "onSignalingChange: $newState")
                        val unused = trySend(PeerConnectionObserverEvent.SignalingChangeEvent(newState))
                        if (newState == PeerConnection.SignalingState.CLOSED) {
                            channel.close()
                        }
                    }

                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                        Log.i(TAG, "onConnectionChange: $newState")
                        val unused = trySend(PeerConnectionObserverEvent.ConnectionChangeEvent(newState))
                    }

                    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                        Log.i(TAG, "onIceConnectionChange: $newState")
                        val unused = trySend(PeerConnectionObserverEvent.IceConnectionChangeEvent(newState))
                    }

                    override fun onIceConnectionReceivingChange(receiving: Boolean) {
                        Log.i(TAG, "onIceConnectionReceivingChange: $receiving")
                        val unused =
                            trySend(PeerConnectionObserverEvent.IceConnectionReceivingChangeEvent(receiving))
                    }

                    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                        Log.i(TAG, "onIceGatheringChange: $newState")
                        val unused = trySend(PeerConnectionObserverEvent.IceGatheringChangeEvent(newState))
                    }

                    override fun onIceCandidate(candidate: IceCandidate?) {
                        Log.i(TAG, "onIceCandidate: $candidate")
                        val unused = trySend(PeerConnectionObserverEvent.IceCandidateEvent(candidate))
                    }

                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                        Log.i(TAG, "onIceCandidatesRemoved: ${candidates?.size}")
                        val unused = trySend(PeerConnectionObserverEvent.IceCandidatesRemovedEvent(candidates))
                    }

                    override fun onAddStream(stream: MediaStream?) {
                        Log.i(TAG, "onAddStream: $stream")
                        val unused = trySend(PeerConnectionObserverEvent.AddStreamEvent(stream))
                    }

                    override fun onRemoveStream(stream: MediaStream?) {
                        Log.i(TAG, "onRemoveStream: $stream")
                        val unused = trySend(PeerConnectionObserverEvent.RemoveStreamEvent(stream))
                    }

                    override fun onDataChannel(dataChannel: DataChannel?) {
                        Log.i(TAG, "onDataChannel: $dataChannel")
                        val unused = trySend(PeerConnectionObserverEvent.DataChannelEvent(dataChannel))
                    }

                    override fun onRenegotiationNeeded() {
                        Log.i(TAG, "onRenegotiationNeeded")
                        val unused = trySend(PeerConnectionObserverEvent.RenegotiationNeededEvent)
                    }

                    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                        Log.i(TAG, "onAddTrack: receiver=$receiver, mediaStreams=$mediaStreams")
                        val unused = trySend(PeerConnectionObserverEvent.AddTrackEvent(receiver, mediaStreams))
                    }

                    override fun onTrack(transceiver: RtpTransceiver?) {
                        Log.i(TAG, "onTrack: $transceiver")
                        val unused = trySend(PeerConnectionObserverEvent.TrackEvent(transceiver))
                    }
                },
            )
        )
    callback(peerConnection)
    // Send the SetupPlayerEvent to indicate that the peer connection is ready for use.
    val unused = trySend(PeerConnectionObserverEvent.SetupPlayerEvent)
    awaitClose {}
}

/** Events from the PeerConnection observer. */
sealed interface PeerConnectionObserverEvent {
    data class SignalingChangeEvent(val newState: PeerConnection.SignalingState?) :
        PeerConnectionObserverEvent

    data class ConnectionChangeEvent(val newState: PeerConnection.PeerConnectionState?) :
        PeerConnectionObserverEvent

    data class IceConnectionChangeEvent(val newState: PeerConnection.IceConnectionState?) :
        PeerConnectionObserverEvent

    data class IceConnectionReceivingChangeEvent(val receiving: Boolean) :
        PeerConnectionObserverEvent

    data class IceGatheringChangeEvent(val newState: PeerConnection.IceGatheringState?) :
        PeerConnectionObserverEvent

    data class IceCandidateEvent(val candidate: IceCandidate?) : PeerConnectionObserverEvent

    data class IceCandidatesRemovedEvent(val candidates: Array<out IceCandidate>?) :
        PeerConnectionObserverEvent

    data class AddStreamEvent(val stream: MediaStream?) : PeerConnectionObserverEvent

    data class RemoveStreamEvent(val stream: MediaStream?) : PeerConnectionObserverEvent

    data class DataChannelEvent(val dataChannel: DataChannel?) : PeerConnectionObserverEvent

    object RenegotiationNeededEvent : PeerConnectionObserverEvent

    object SetupPlayerEvent : PeerConnectionObserverEvent

    data class AddTrackEvent(val receiver: RtpReceiver?, val mediaStreams: Array<out MediaStream>?) :
        PeerConnectionObserverEvent

    data class TrackEvent(val transceiver: RtpTransceiver?) : PeerConnectionObserverEvent
}