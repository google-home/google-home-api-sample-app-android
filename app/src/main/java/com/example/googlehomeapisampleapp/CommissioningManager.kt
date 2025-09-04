
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

import android.content.ComponentName
import android.content.Context
import android.content.IntentSender
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import com.example.googlehomeapisampleapp.commissioning.ThirdPartyCommissioningService
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningClient
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.home.matter.commissioning.CommissioningResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CommissioningManager(val context: Context, val scope: CoroutineScope, val activity: ComponentActivity) {

    val commissioningResult: MutableStateFlow<CommissioningResult?>
    val launcher: ActivityResultLauncher<IntentSenderRequest>

    init {
        // StateFlow to carry the result from the latest device commissioning:
        commissioningResult = MutableStateFlow(null)

        // Activity launcher to call commissioning callback and deliver the result:
        launcher = activity.registerForActivityResult(StartIntentSenderForResult()) { result ->
            scope.launch { commissioningCallback(result) }
        }
    }

    private suspend fun commissioningCallback(activityResult: ActivityResult) {
        try {
            // Try to convert ActivityResult into CommissioningResult:
            val result: CommissioningResult = CommissioningResult.fromIntentSenderResult(
                activityResult.resultCode, activityResult.data)
            // Store the CommissioningResult in the StateFlow:
            commissioningResult.emit(result)
            // Record the commissioning success status:
            MainActivity.showDebug(this, "Commissioning Success!")
        } catch (exception: ApiException) {
            // Record the exception for commissioning failure:
            MainActivity.showError(this, "Commissioning Result: " + exception.status)
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
}

