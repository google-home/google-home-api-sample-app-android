package com.example.googlehomeapisampleapp.camera.signaling

import com.google.home.HomeDevice
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/** Factory for creating instances of [SignalingService]. */
class SignalingServiceFactory @Inject constructor() {

    fun createWebRtcLiveViewTraitSignalingService(
        device: HomeDevice,
        scope: CoroutineScope,
    ): SignalingService {
        return WebRtcLiveViewTraitSignalingController(device, scope)
    }
}