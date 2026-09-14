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

enum class FabricType {
  GOOGLE_FABRIC,
  THIRD_PARTY_FABRIC,
  GOOGLE_CAMERA
}

// Conceptual default values for the commissioning window
internal object CommissioningDefaults {
  const val OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS = 180
  const val ITERATION = 10000L
  const val TEST_VENDOR_ID = 0xFFF1
  const val TEST_PRODUCT_ID = 0x8000
}

class CommissioningManager(
  val context: Context,
  val scope: CoroutineScope,
  val activity: ComponentActivity,
) {

  // OTA Screen Callback
  /**
   * Callback invoked when a device is successfully commissioned.
   * This triggers the OTA information screen, passing the primary deviceId and all deviceIds.
   */
  var onDeviceCommissioned: ((String, List<String>) -> Unit)? = null

  /**
   * Tracks whether we're expecting an OTA-capable device based on commissioning flow.
   */
  private var expectingOtaDevice = false

  val commissioningResult: MutableStateFlow<CommissioningResult?> = MutableStateFlow(null)
  val launcher: ActivityResultLauncher<IntentSenderRequest>

  // Share functionality members
  val shareDeviceLauncher: ActivityResultLauncher<IntentSenderRequest>
  private val chipClient: ChipClient = ChipClient(context)
  private var lastCommissionedDeviceDescriptor: DeviceDescriptor? = null

  // Links Home API ID (String) to Matter Node ID (Long)
  private val operationalNodeIdMap: MutableMap<String, Long> = mutableMapOf()

  init {

    // Activity launcher to call commissioning callback and deliver the result:
    launcher = activity.registerForActivityResult(StartIntentSenderForResult()) { result ->
      scope.launch { commissioningCallback(result) }
    }

    // Launcher for the sharing intent result
    shareDeviceLauncher =
      activity.registerForActivityResult(StartIntentSenderForResult()) { result ->
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
    if (activityResult.resultCode != RESULT_OK) {
      // Log the cancellation code, which is often returned by the system on silent failure
      Log.e(
        TAG,
        "Commissioning process cancelled by system or user. Code: ${activityResult.resultCode}"
      )
      expectingOtaDevice = false
      return
    }

    try {
      // Try to convert ActivityResult into CommissioningResult:
      val result: CommissioningResult = CommissioningResult.fromIntentSenderResult(
        activityResult.resultCode, activityResult.data
      )

      // Save the DeviceDescriptor
      lastCommissionedDeviceDescriptor = result.commissionedDeviceDescriptor
      Log.i(TAG, "saving lastCommissionedDeviceDescriptor: $lastCommissionedDeviceDescriptor")

      // The Matter Node ID (Long) comes from the custom service's token
      val matterNodeId = result.token?.toLongOrNull()
      Log.i(TAG, "matterNodeId = $matterNodeId")
      // The Smart Home ID (String) comes from the deviceIds list
      val smartHomeId = result.deviceIds?.firstOrNull()

      // DIAGNOSTICS: Check values being used for mapping
      Log.i(
        TAG,
        "COMMISSIONING RESULT: Token (Matter Node ID) = $matterNodeId, SmartHomeId = $smartHomeId"
      )

      // Only require matterNodeId (token) if we need to support the Share Device flow.
      if (smartHomeId != null && matterNodeId != null) {
        // MAPPING SAVED: This path is primarily for devices commissioned to the 3P Fabric
        Log.i(TAG, "MAPPING SAVED: SmartHomeId=$smartHomeId -> MatterNodeId=$matterNodeId")
        operationalNodeIdMap[smartHomeId] = matterNodeId
      } else if (smartHomeId != null) {
        // MAPPING SKIPPED: This path is executed for successful Google-only commissions (CAMERA flow)
        Log.w(
          TAG,
          "MAPPING SKIPPED: Commissioned to Google Fabric. SmartHomeId received, but MatterNodeId is null."
        )
      } else {
        Log.e(TAG, "MAPPING FAILED: SmartHomeId was null. Cannot save share credentials.")
      }

      // Store the CommissioningResult in the StateFlow:
      commissioningResult.emit(result)
      // Record the commissioning success status:
      MainActivity.showDebug(this, "Commissioning Success!")

      val deviceIds = result.deviceIds ?: emptyList()
      Log.i(TAG, "Commissioning completed successfully. deviceIds=$deviceIds")
      for (deviceId in deviceIds) {
        MainActivity.showDebug(this, "Commissioned Device ID: $deviceId")
      }

      if (expectingOtaDevice && deviceIds.isNotEmpty()) {
        val primaryId = deviceIds.first()
        Log.i(TAG, "Triggering OTA screen for primaryId=$primaryId, deviceIds=$deviceIds")
        onDeviceCommissioned?.invoke(primaryId, deviceIds)
      }

    } catch (exception: ApiException) {
      // Record the exception for commissioning failure:
      MainActivity.showError(
        this,
        "Commissioning Result: API Failure - Code: ${exception.statusCode} - Status: ${exception.status.statusMessage}"
      )
    } catch (e: Exception) {
      MainActivity.showError(this, "Commissioning Callback Error: ${e.message}")
      Log.e(TAG, "Error in commissioningCallback", e)
    } finally {
      expectingOtaDevice = false
    }
  }

  fun requestCommissioning(
    fabricType: FabricType,
    payload: String? = null,
  ) {
    // Set flag to true for all commissioning flows so OTA screen can be shown after commissioning
    expectingOtaDevice = true

    // Retrieve the onboarding payload from the Activity Intent *only* if the payload wasn't provided
    val activityIntentPayload = activity.intent?.getStringExtra(Matter.EXTRA_ONBOARDING_PAYLOAD)

    scope.launch {
      // Determine the final payload to use (prefers argument payload)
      val finalPayload = payload ?: activityIntentPayload

      // Create a commissioning request to store the device in Google's Fabric:
      val builder = CommissioningRequest.builder()
        .setOnboardingPayload(finalPayload)

      when (fabricType) {
        FabricType.GOOGLE_FABRIC, FabricType.GOOGLE_CAMERA -> {
          builder.setStoreToGoogleFabric(true)
        }

        FabricType.THIRD_PARTY_FABRIC -> {
          builder.setStoreToGoogleFabric(true)
          builder.setCommissioningService(
            ComponentName(context, ThirdPartyCommissioningService::class.java)
          )
        }
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
        val windowDurationSeconds: Long =
          CommissioningDefaults.OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS.toLong()
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
        val shareDeviceRequest = deviceDescriptor.let {
          ShareDeviceRequest.builder()
            .setDeviceDescriptor(it)
            .setDeviceName("Share Target")
            .setCommissioningWindow(commissioningWindow)
            .build()
        }

        // Launch the sharing intent
        val client: CommissioningClient = Matter.getCommissioningClient(context)
        val sender: IntentSender? = shareDeviceRequest.let { client.shareDevice(it).await() }

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