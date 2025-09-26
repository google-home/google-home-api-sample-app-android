package com.example.googlehomeapisampleapp.camera.livestreamplayer

import android.util.Log
import com.google.home.DeviceType
import com.google.home.DeviceTypeFactory
import com.google.home.HomeDevice
import com.google.home.HomeException
import com.google.home.google.PushAvStreamTransport
import com.google.home.google.PushAvStreamTransportTrait.TransportStatusEnum
import com.google.home.trait
import com.example.googlehomeapisampleapp.camera.signaling.getCameraDeviceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

/**
 * Interface for controlling On/Off state.
 *
 * @property isRecording Flow that emits whether the device is recording.
 */
interface OnOffController {
    /** Flow that emits whether the device is recording. */
    val isRecording: Flow<Boolean>

    /**
     * Sets recording on or off.
     *
     * @param enabled Whether to enable or disable recording.
     * @return Whether the recording was set successfully.
     */
    suspend fun setRecording(enabled: Boolean): Boolean
}

/**
 * Implementation of [OnOffController] using [PushAvStreamTransport] trait from [HomeDevice].
 *
 * @property device The device to control.
 */
class PushAvStreamTransportOnOffController(private val device: HomeDevice) : OnOffController {
    private val deviceType: DeviceTypeFactory<out DeviceType> =
        requireNotNull(device.getCameraDeviceType())

    override val isRecording: Flow<Boolean> =
        device.type(deviceType).trait(PushAvStreamTransport).filterNotNull().map { it.isRecording() }

    override suspend fun setRecording(enabled: Boolean): Boolean {
        Log.d(TAG, "Set recording: $enabled")

        val pushAvStreamTransport = device.pushAvStreamTransport()
        if (pushAvStreamTransport == null) {
            Log.w(TAG, "PushAvStreamTransport is null")
            return false
        }
        if (isRecording.firstOrNull() == enabled) {
            Log.d(TAG, "Recording is already in the desired state: $enabled")
            return true
        }
        try {
            if (enabled) {
                pushAvStreamTransport.setTransportStatus(TransportStatusEnum.Active)
            } else {
                pushAvStreamTransport.setTransportStatus(TransportStatusEnum.Inactive)
            }
            return true
        } catch (e: HomeException) {
            // Send Command errors encountered
            Log.e(TAG, "Failed to set recording.", e)
            return false
        }
    }

    companion object {
        private const val TAG = "OnOffController"
    }
}