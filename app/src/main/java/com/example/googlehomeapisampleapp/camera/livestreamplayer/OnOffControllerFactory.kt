package com.example.googlehomeapisampleapp.camera.livestreamplayer

import android.util.Log
import com.example.googlehomeapisampleapp.camera.signaling.getCameraDeviceType
import com.google.home.ConnectivityState
import com.google.home.HomeDevice
import com.google.home.google.PushAvStreamTransport
import com.google.home.google.PushAvStreamTransportTrait.TransportStatusEnum
import com.google.home.trait
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/** Factory for creating [OnOffController] instances. */
class OnOffControllerFactory @Inject internal constructor() {

    /**
     * Creates an [OnOffController] from a [HomeDevice].
     *
     * @param device The device to create the controller for.
     * @return The created [OnOffController], or null if the device does not support On/Off.
     */
    suspend fun create(device: HomeDevice): OnOffController? {
        if (device.has(PushAvStreamTransport)) {
            return PushAvStreamTransportOnOffController(device)
        }

        Log.w(
            TAG,
            "No PushAvStreamTransport trait found on device ${device.id}, cannot create OnOffController.",
        )
        return null
    }

    companion object {
        private const val TAG = "OnOffControllerFactory"
    }
}

internal suspend fun HomeDevice.pushAvStreamTransport(): PushAvStreamTransport? {
    val deviceType = getCameraDeviceType() ?: return null

    return type(deviceType).trait(PushAvStreamTransport).firstOrNull {
        it?.metadata?.sourceConnectivity?.connectivityState == ConnectivityState.ONLINE
    }
}

internal suspend fun PushAvStreamTransport.isRecording(): Boolean {
    return currentConnections?.any { it.transportStatus == TransportStatusEnum.Active } ?: false
}