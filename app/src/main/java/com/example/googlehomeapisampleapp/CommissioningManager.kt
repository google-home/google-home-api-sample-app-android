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

package com.example.googlehomeapisampleapp

import android.app.Activity.RESULT_OK
import android.content.ComponentName
import android.content.Context
import android.content.IntentSender
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import com.example.googlehomeapisampleapp.commissioning.ChipClient
import com.example.googlehomeapisampleapp.commissioning.ThirdPartyCommissioningService
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningClient
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.home.matter.commissioning.CommissioningResult
import com.google.android.gms.home.matter.commissioning.CommissioningWindow
import com.google.android.gms.home.matter.commissioning.ShareDeviceRequest
import com.google.android.gms.home.matter.common.DeviceDescriptor
import com.google.android.gms.home.matter.common.Discriminator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

private const val TAG = "CommissioningManager"

// Conceptual default values for the commissioning window
internal object CommissioningDefaults {
    const val OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS = 180
    const val ITERATION = 10000L
    const val TEST_VENDOR_ID = 0xFFF1
    const val TEST_PRODUCT_ID = 0x8000
}

class CommissioningManager(val context: Context, val scope: CoroutineScope, val activity: ComponentActivity) {

    val commissioningResult: MutableStateFlow<CommissioningResult?>
    val launcher: ActivityResultLauncher<IntentSenderRequest>

    // Share functionality members
    val shareDeviceLauncher: ActivityResultLauncher<IntentSenderRequest>
    private val chipClient: ChipClient = ChipClient(context)
    private var lastCommissionedDeviceDescriptor: DeviceDescriptor? = null

    // Links Home API ID (String) to Matter Node ID (Long)
    private val operationalNodeIdMap: MutableMap<String, Long> = mutableMapOf()

    init {
        // StateFlow to carry the result from the latest device commissioning:
        commissioningResult = MutableStateFlow(null)

        // Activity launcher to call commissioning callback and deliver the result:
        launcher = activity.registerForActivityResult(StartIntentSenderForResult()) { result ->
            scope.launch { commissioningCallback(result) }
        }

        // Launcher for the sharing intent result
        shareDeviceLauncher = activity.registerForActivityResult(StartIntentSenderForResult()) {
                result ->
            val resultCode = result.resultCode
            MainActivity.showDebug(this, "result code: $resultCode")

            if (resultCode == RESULT_OK) {
                MainActivity.showInfo(this, "Share Device Successful!")
            } else {
                MainActivity.showError(this, "Share Device Failed: $resultCode")
            }
        }
    }

    private suspend fun commissioningCallback(activityResult: ActivityResult) {
        try {
            // Try to convert ActivityResult into CommissioningResult:
            val result: CommissioningResult = CommissioningResult.fromIntentSenderResult(
                activityResult.resultCode, activityResult.data)

            // Save the DeviceDescriptor
            lastCommissionedDeviceDescriptor = result.commissionedDeviceDescriptor
            Log.i(TAG, "saving lastCommissionedDeviceDescriptor: ${lastCommissionedDeviceDescriptor}")

            // The Matter Node ID (Long) comes from the custom service's token
            val matterNodeId = result.token?.toLongOrNull()
            Log.i(TAG, "matterNodeId = $matterNodeId")
            // The Smart Home ID (String) comes from the deviceIds list
            val smartHomeId = result.deviceIds?.firstOrNull()

            // DIAGNOSTICS: Check values being used for mapping
            Log.i(TAG, "COMMISSIONING RESULT: Token (Matter Node ID) = $matterNodeId, SmartHomeId = $smartHomeId")

            if (smartHomeId != null && matterNodeId != null) {
                // MAPPING SAVED: Link the Home API ID to the Matter Node ID
                Log.i(TAG, "MAPPING SAVED: SmartHomeId=$smartHomeId -> MatterNodeId=$matterNodeId")
                operationalNodeIdMap[smartHomeId] = matterNodeId
            } else {
                Log.e(TAG, "MAPPING FAILED: SmartHomeId or MatterNodeId was null. Cannot save share credentials.")
            }

            // Store the CommissioningResult in the StateFlow:
            commissioningResult.emit(result)
            // Record the commissioning success status:
            MainActivity.showDebug(this, "Commissioning Success!")

            val deviceIds = result.deviceIds
            if (deviceIds != null) {
                for (deviceId in deviceIds) {
                    MainActivity.showDebug(this, "Commissioned Device ID: $deviceId")
                }
            }

        } catch (exception: ApiException) {
            // Record the exception for commissioning failure:
            MainActivity.showError(this, "Commissioning Result: " + exception.status)
        } catch (e: Exception) {
            MainActivity.showError(this, "Commissioning Callback Error: ${e.message}")
            Log.e(TAG, "Error in commissioningCallback", e)
        }
    }

    fun requestCommissioning(toGoogleOnly: Boolean) {
        // Retrieve the onboarding payload used when commissioning devices:
        val payload = activity.intent?.getStringExtra(Matter.EXTRA_ONBOARDING_PAYLOAD)

        scope.launch {
            // Create a commissioning request to store the device in Google's Fabric:
            val builder = CommissioningRequest.builder()
                .setStoreToGoogleFabric(true)
                .setOnboardingPayload(payload)

            // If not toGoogleOnly, add the custom commissioner service
            if (!toGoogleOnly) {
                builder.setCommissioningService(
                    ComponentName(context, ThirdPartyCommissioningService::class.java)
                )
            }

            val request = builder.build()

            // Initialize client and sender for commissioning intent:
            val client: CommissioningClient = Matter.getCommissioningClient(context)
            val sender: IntentSender = client.commissionDevice(request).await()
            // Launch the commissioning intent on the launcher:
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    private fun generateNewCommissioningData(): Pair<Long, Int> {
        val newPasscode: Long = Random.nextLong(100000, 1000000)
        val newDiscriminator: Int = Random.nextInt(4096)
        return Pair(newPasscode, newDiscriminator)
    }

    fun requestShareDevice(smartHomeId: String) {
        scope.launch {
            try {
                Log.i(TAG, "requestShareDevice() called for SmartHomeId: $smartHomeId")

                // Look up the Matter Operational Node ID (Long)
                val matterNodeId = operationalNodeIdMap[smartHomeId]
                    ?: throw IllegalStateException("Matter Node ID not found for ID: $smartHomeId. Did you commission the device and is the app still running?")

                // Generate unique, temporary credentials
                val (newPasscode, newDiscriminator) = generateNewCommissioningData()
                val windowDurationSeconds: Long = CommissioningDefaults.OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS.toLong()
                Log.i(TAG, "Generated New Passcode: $newPasscode, Discriminator: $newDiscriminator")

                // Reopen commissioning window (requires pointer)
                // Retries are handled inside awaitGetConnectedDevicePointer()
                val connectedDevicePointer = chipClient.awaitGetConnectedDevicePointer(matterNodeId)

                chipClient.awaitOpenPairingWindowWithPIN(
                    connectedDevicePointer,
                    CommissioningDefaults.OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS,
                    CommissioningDefaults.ITERATION,
                    newDiscriminator,
                    newPasscode,
                )
                Log.i(TAG, "Device pairing window successfully commanded to open with new credentials.")

                // Add stabilization delay to bridge the network timing gap
                delay(1000)
                Log.i(TAG, "Waited 1 second for device to start advertising new window.")

                // Set up the CommissioningWindow object
                val commissioningWindow =
                    CommissioningWindow.builder()
                        .setDiscriminator(Discriminator.forLongValue(newDiscriminator))
                        .setPasscode(newPasscode)
                        .setWindowOpenMillis(SystemClock.elapsedRealtime())
                        .setDurationSeconds(windowDurationSeconds)
                        .build()

                // Get Device Descriptor
                val deviceDescriptor =
                    lastCommissionedDeviceDescriptor ?: // Use saved descriptor
                    DeviceDescriptor.builder() // Fallback to test IDs if not saved
                        .setVendorId(CommissioningDefaults.TEST_VENDOR_ID)
                        .setProductId(CommissioningDefaults.TEST_PRODUCT_ID)
                        .build()

                // Create the ShareDeviceRequest
                val shareDeviceRequest = deviceDescriptor?.let {
                    ShareDeviceRequest.builder()
                        .setDeviceDescriptor(it)
                        .setDeviceName("Share Target")
                        .setCommissioningWindow(commissioningWindow)
                        .build()
                }

                // Launch the sharing intent
                val client: CommissioningClient = Matter.getCommissioningClient(context)
                val sender: IntentSender? = shareDeviceRequest?.let { client.shareDevice(it).await() }

                // Launch the intent using the shareDeviceLauncher.
                sender?.let { IntentSenderRequest.Builder(it).build() }
                    ?.let { shareDeviceLauncher.launch(it) }

                MainActivity.showInfo(this, "shareDeviceLauncher launched")

            } catch (e: Exception) {
                MainActivity.showError(this, "Error sharing device: ${e.message}")
            }
        }
    }
}