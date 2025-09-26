/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.googlehomeapisampleapp.commissioning

import android.content.Context
import android.util.Log
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.ControllerParams
import chip.devicecontroller.GetConnectedDeviceCallbackJni.GetConnectedDeviceCallback
import chip.devicecontroller.NetworkCredentials
import chip.devicecontroller.OpenCommissioningCallback
import chip.platform.AndroidBleManager
import chip.platform.AndroidChipPlatform
import chip.platform.ChipMdnsCallbackImpl
import chip.platform.DiagnosticDataProviderImpl
import chip.platform.NsdManagerServiceBrowser
import chip.platform.NsdManagerServiceResolver
import chip.platform.PreferencesConfigurationManager
import chip.platform.PreferencesKeyValueStoreManager
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val TAG = "ChipClient"

class ChipClient (context: Context) {
    /* 0xFFF4 is a test vendor ID, replace with your assigned company ID */
    private val VENDOR_ID = 0xFFF4

    // Lazily instantiate [ChipDeviceController] and hold a reference to it.
    val chipDeviceController: ChipDeviceController by lazy {
        ChipDeviceController.loadJni()
        AndroidChipPlatform(
            AndroidBleManager(),
            PreferencesKeyValueStoreManager(context),
            PreferencesConfigurationManager(context),
            NsdManagerServiceResolver(context),
            NsdManagerServiceBrowser(context),
            ChipMdnsCallbackImpl(),
            DiagnosticDataProviderImpl(context))
        ChipDeviceController(
            ControllerParams.newBuilder().setUdpListenPort(0).setControllerVendorId(VENDOR_ID).build())
    }

    /**
     * Suspends until a PASE connection is established with the device.
     *
     * @param deviceId The device ID to connect to.
     * @param ipAddress The IP address of the device.
     * @param port The port number for the connection.
     * @param setupPinCode The setup PIN code for PASE.
     * @throws IllegalStateException if pairing fails with an error code.
     * @throws Exception if an error occurs during the connection process.
     */
    suspend fun awaitEstablishPaseConnection(
        deviceId: Long,
        ipAddress: String,
        port: Int,
        setupPinCode: Long
    ) {
        return suspendCoroutine { continuation ->
            chipDeviceController.setCompletionListener(
                object : BaseCompletionListener() {
                    override fun onConnectDeviceComplete() {
                        super.onConnectDeviceComplete()
                        continuation.resume(Unit)
                    }

                    // Note that an error in processing is not necessarily communicated via onError().
                    // onCommissioningComplete with a "code != 0" also denotes an error in processing.
                    override fun onPairingComplete(code: Int) {
                        super.onPairingComplete(code)
                        if (code != 0) {
                            continuation.resumeWithException(
                                IllegalStateException("Pairing failed with error code [${code}]"))
                        } else {
                            continuation.resume(Unit)
                        }
                    }

                    override fun onError(error: Throwable) {
                        super.onError(error)
                        continuation.resumeWithException(error)
                    }

                    override fun onReadCommissioningInfo(
                        vendorId: Int,
                        productId: Int,
                        wifiEndpointId: Int,
                        threadEndpointId: Int
                    ) {
                        super.onReadCommissioningInfo(vendorId, productId, wifiEndpointId, threadEndpointId)
                        continuation.resume(Unit)
                    }

                    override fun onCommissioningStatusUpdate(nodeId: Long, stage: String?, errorCode: Int) {
                        super.onCommissioningStatusUpdate(nodeId, stage, errorCode)
                        continuation.resume(Unit)
                    }
                })

            // Temporary workaround to remove interface indexes from ipAddress
            // due to https://github.com/project-chip/connectedhomeip/pull/19394/files
            chipDeviceController.establishPaseConnection(
                deviceId, stripLinkLocalInIpAddress(ipAddress), port, setupPinCode)
        }
    }

    fun stripLinkLocalInIpAddress(ipAddress: String): String {
        return ipAddress.replace("%.*".toRegex(), "")
    }

    /**
     * Suspends until the commissioning process for a device is complete.
     *
     * This method uses [ChipDeviceController.commissionDevice] to initiate the commissioning
     * process and suspends the coroutine until either [onCommissioningComplete] or [onError]
     * is called on the [BaseCompletionListener].
     *
     * @param deviceId The node ID of the device to be commissioned.
     * @param networkCredentials Optional network credentials to be used during commissioning.
     * @throws IllegalStateException if the commissioning fails with a non-zero error code.
     * @throws Throwable if an error occurs during the commissioning process.
     */
    suspend fun awaitCommissionDevice(deviceId: Long, networkCredentials: NetworkCredentials?) {
        return suspendCoroutine { continuation ->
            chipDeviceController.setCompletionListener(
                object : BaseCompletionListener() {
                    // Note that an error in processing is not necessarily communicated via onError().
                    // onCommissioningComplete with an "errorCode != 0" also denotes an error in processing.
                    override fun onCommissioningComplete(nodeId: Long, errorCode: Int) {
                        super.onCommissioningComplete(nodeId, errorCode)
                        if (errorCode != 0) {
                            continuation.resumeWithException(
                                IllegalStateException("Commissioning failed with error code [${errorCode}]"))
                        } else {
                            continuation.resume(Unit)
                        }
                    }

                    override fun onError(error: Throwable) {
                        super.onError(error)
                        continuation.resumeWithException(error)
                    }
                })
            chipDeviceController.commissionDevice(deviceId, networkCredentials)
        }
    }

    /**
     * Opens a commissioning window on a connected device using a PIN.
     *
     * @param connectedDevicePointer A pointer to the connected device.
     * @param duration The duration for which the commissioning window will be open, in seconds.
     * @param iteration The number of iterations for the PASE verifier.
     * @param discriminator The discriminator for the device.
     * @param setupPinCode The setup PIN code for commissioning.
     * @throws IllegalStateException if opening the pairing window fails.
     */
    suspend fun awaitOpenPairingWindowWithPIN(
        connectedDevicePointer: Long,
        duration: Int,
        iteration: Long,
        discriminator: Int,
        setupPinCode: Long
    ) {
        return suspendCoroutine { continuation ->
            Log.d(TAG, "Calling chipDeviceController.openPairingWindowWithPIN")
            val callback: OpenCommissioningCallback =
                object : OpenCommissioningCallback {
                    override fun onError(status: Int, deviceId: Long) {
                        Log.e(TAG,
                            "ShareDevice: awaitOpenPairingWindowWithPIN.onError: status [${status}] device [${deviceId}]")
                        continuation.resumeWithException(
                            java.lang.IllegalStateException(
                                "Failed opening the pairing window with status [${status}]"))
                    }

                    override fun onSuccess(deviceId: Long, manualPairingCode: String?, qrCode: String?) {
                        Log.d(TAG,
                            "ShareDevice: awaitOpenPairingWindowWithPIN.onSuccess: deviceId [${deviceId}]")
                        continuation.resume(Unit)
                    }
                }
            chipDeviceController.openPairingWindowWithPINCallback(
                connectedDevicePointer, duration, iteration, discriminator, setupPinCode, callback)
        }
    }

    /**
     * Wrapper around [ChipDeviceController.getConnectedDevicePointer] to return the value directly.
     * MODIFIED: Includes retries to increase robustness against transient connection failures.
     */
    suspend fun awaitGetConnectedDevicePointer(nodeId: Long): Long {
        val maxRetries = 3
        val retryDelayMs = 2000L // 2 second delay between retries
        var lastError: Exception? = null

        for (i in 1..maxRetries) {
            try {
                return suspendCoroutine { continuation ->
                    chipDeviceController.getConnectedDevicePointer(
                        nodeId,
                        object : GetConnectedDeviceCallback {
                            override fun onDeviceConnected(devicePointer: Long) {
                                Log.d(TAG, "Got connected device pointer (Attempt $i)")
                                continuation.resume(devicePointer)
                        }

                            override fun onConnectionFailure(nodeId: Long, error: Exception) {
                                val errorMessage = "Unable to get connected device with nodeId $nodeId (Attempt $i)"
                                Log.e(TAG, errorMessage, error)
                                continuation.resumeWithException(IllegalStateException(errorMessage, error))
                            }
                        })
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Failed to get pointer on attempt $i. Retrying in ${retryDelayMs}ms...")
                if (i < maxRetries) {
                    delay(retryDelayMs)
                }
            }
        }

        // If the loop finishes without success, throw the last error
        throw lastError ?: IllegalStateException("Failed to get connected device pointer after $maxRetries retries for nodeId $nodeId.")
    }
}