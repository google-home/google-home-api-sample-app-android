package com.example.googlehomeapisampleapp.camera.livestreamplayer


import android.util.Log
import com.google.home.HomeDevice
import com.google.home.google.WebRtcLiveView
import com.example.googlehomeapisampleapp.camera.signaling.SignalingServiceFactory
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope

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
    ): LiveStreamPlayer? {

        if (device.has(WebRtcLiveView)) {
            return createPlayerFromWebRtcLiveView(device, scope)
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
    fun createPlayerFromWebRtcLiveView(device: HomeDevice, scope: CoroutineScope): LiveStreamPlayer? {
        Log.i(TAG, "createPlayerFromWebRtcLiveView")
        val signalingService =
            signalingServiceFactoryProvider.get().createWebRtcLiveViewTraitSignalingService(device, scope)
        return webRtcPlayerBuilderProvider.get().apply { setSignalingService(signalingService) }.build()
    }

    companion object {
        private const val TAG = "LiveStreamPlayerFactoryImpl"
    }
}