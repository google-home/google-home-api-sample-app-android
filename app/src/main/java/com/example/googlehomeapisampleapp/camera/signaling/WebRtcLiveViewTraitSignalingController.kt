package com.example.googlehomeapisampleapp.camera.signaling

import android.util.Log
import com.google.home.ConnectivityState
import com.google.home.DeviceType
import com.google.home.DeviceTypeFactory
import com.google.home.HomeDevice
import com.google.home.google.GoogleCameraDevice
import com.google.home.google.GoogleDoorbellDevice
import com.google.home.google.WebRtcLiveView
import com.google.home.trait
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Duration
import kotlin.math.max

/**
 * A [SignalingService] that uses the [WebRtcLiveView] trait to send and receive SDP offers and
 * answers.
 *
 * @param device The [HomeDevice] to get the WebRtcLiveView trait from.
 * @param scope The [CoroutineScope] to use for background tasks.
 */
class WebRtcLiveViewTraitSignalingController
constructor(private val device: HomeDevice, private val scope: CoroutineScope) : SignalingService {
    private var mediaSessionId: String? = null
    private var extensionJob: Job? = null

    override suspend fun sendOffer(
        sdpOffer: SignalingService.Sdp
    ): SignalingService.SendOfferResponse {
        Log.i(TAG, "sendOffer: $sdpOffer")
        val trait = webRtcLiveViewTrait()
        if (trait == null) {
            return SignalingService.SendOfferResponse.Error("WebRtcLiveView trait is null")
        }
        val result = runCatchingCancellable {
            val response = trait.startLiveView(offerSdp = sdpOffer.rawSdp)
            Log.i(TAG, "response: $response")
            check(response.answerSdp.isNotEmpty()) { "Response is empty" }

            mediaSessionId = response.mediaSessionId
            scheduleExtension(response.liveSessionDurationSeconds)
            response.answerSdp
        }
        if (result.isSuccess) {
            return SignalingService.SendOfferResponse.Answer(SignalingService.Sdp(result.getOrThrow()))
        }
        return SignalingService.SendOfferResponse.Error(result.exceptionOrNull()?.message ?: "")
    }

    private fun scheduleExtension(duration: UShort) {
        val sessionId = mediaSessionId
        if (sessionId == null) {
            Log.e(TAG, "Media session ID is null, cannot schedule extension.")
            return
        }
        extensionJob?.cancel()
        var delaySeconds = duration.toLong() - EXTENSION_BUFFER_SECONDS
        if (delaySeconds <= 0) {
            Log.w(TAG, "Live session duration ($duration) is too short to schedule extension.")
            return
        }
        Log.d(TAG, "Scheduling live view extension in $delaySeconds seconds.")
        extensionJob =
            scope.launch {
                while (isActive) {
                    delay(delaySeconds * 1000L)
                    Log.d(TAG, "Extending live view session.")
                    val trait = webRtcLiveViewTrait()
                    if (trait == null) {
                        return@launch
                    }
                    val result = runCatchingCancellable {
                        val response = trait.extendLiveView(sessionId)
                        delaySeconds = response.liveSessionDurationSeconds.toLong() - EXTENSION_BUFFER_SECONDS
                        delaySeconds = max(delaySeconds, 0)
                    }
                    if (result.isFailure) {
                        Log.e(TAG, "Failed to extend live view session.")
                        return@launch
                    }
                }
            }
    }

    override suspend fun dispose() {
        extensionJob?.cancel()
        extensionJob = null
        val sessionId = mediaSessionId
        if (sessionId == null) {
            Log.w(TAG, "Media session ID is null, cannot stop live view session.")
            return
        }
        val trait = webRtcLiveViewTrait()
        try {
            // Stop the live view session. Will throw if the camera is not recording.
            trait?.stopLiveView(sessionId)
            Log.i(TAG, "Stopped live view session. $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop live view session.", e)
        }
    }

    override suspend fun sendAnswer(sdpAnswer: String): SignalingService.SendAnswerResponse {
        // This signaling path starts with an offer from the client via sendOffer, and receives
        // an answer via StartLiveViewResponse. There is no scenario in which the client needs to
        // send an answer with this trait.
        return SignalingService.SendAnswerResponse.Error(
            "Sending an answer is not supported in the WebRtcLiveViewTrait signaling flow."
        )
    }

    override suspend fun configureTalkback(enabled: Boolean): Boolean {
        Log.i(TAG, "configureTalkback: $enabled")

        val mediaSessionId = this.mediaSessionId
        if (mediaSessionId == null) {
            Log.e(TAG, "Media session ID is null, cannot configure talkback.")
            return false
        }
        val trait = webRtcLiveViewTrait()
        if (trait == null) {
            return false
        }
        // WebRtcLiveView should already send the answer sdp
        val result = runCatchingCancellable {
            if (enabled) {
                trait.startTalkback(mediaSessionId)
            } else {
                trait.stopTalkback(mediaSessionId)
            }
        }
        if (result.isFailure) {
            Log.e(TAG, "Failed to configure talkback.", result.exceptionOrNull())
        }
        return result.isSuccess
    }

    private suspend fun webRtcLiveViewTrait(): WebRtcLiveView? {
        val deviceType = device.getCameraDeviceType()
        if (deviceType == null) {
            Log.e(TAG, "Device type is null")
            return null
        }
        val trait: WebRtcLiveView? =
            withTimeout(Duration.ofSeconds(FETCH_TRAIT_TIMEOUT_SECONDS).toMillis()) {
                device
                    .type(deviceType)
                    .trait(WebRtcLiveView)
                    .filterNotNull()
                    .filter { it.metadata.sourceConnectivity?.connectivityState == ConnectivityState.ONLINE }
                    .firstOrNull()
            }

        if (trait == null) {
            Log.e(TAG, "WebRtcLiveView trait is null")
        }
        return trait
    }

    private inline fun <R> runCatchingCancellable(resultProducer: () -> R): Result<R> {
        return try {
            Result.success(resultProducer())
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            Result.failure(e)
        }
    }

    private companion object {
        const val FETCH_TRAIT_TIMEOUT_SECONDS = 10L
        const val EXTENSION_BUFFER_SECONDS = 5L
        const val TAG = "WebRtcLiveViewTraitSignalingController"
    }
}

/**
 * Returns the camera device type for the given [HomeDevice].
 *
 * @return The camera device type, or null if not found.
 */
fun HomeDevice.getCameraDeviceType(): DeviceTypeFactory<out DeviceType>? {
    return if (has(GoogleCameraDevice)) {
        GoogleCameraDevice
    } else if (has(GoogleDoorbellDevice)) {
        GoogleDoorbellDevice
    } else {
        null
    }
}