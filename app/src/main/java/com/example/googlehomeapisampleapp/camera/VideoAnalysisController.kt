/* Copyright 2026 Google LLC

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

package com.example.googlehomeapisampleapp.camera

import android.util.Log
import com.google.home.DeviceType
import com.google.home.HomeDevice
import com.google.home.google.AvStreamAnalysis
import com.google.home.google.AvStreamAnalysisTrait.EnablementStatusEnum
import com.google.home.google.AvStreamAnalysisTrait.EventTriggerEnablement
import com.google.home.google.AvStreamAnalysisTrait.EventTriggerTypeEnum
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Interface for reading and updating Gemini AI Feature settings via [AvStreamAnalysis]
 * (VideoAnalysisDescription trigger) GHP trait for a specific device endpoint.
 *
 * Note: To view AI event captions and descriptions in camera event history,
 * Recording (e.g. CVR/EBR/etc) must also be enabled on the device.
 */
interface VideoAnalysisController {
  /** Label for UI display (e.g. "Gemini AI Features" or "Gemini AI Features (Lens 1)"). */
  val label: String

  /** Emits whether VideoAnalysis AI features are supported on this device endpoint. */
  val isAiFeaturesSupported: Flow<Boolean>

  /** Emits whether VideoAnalysis AI features are currently active on this device endpoint. */
  val isAiFeaturesEnabled: Flow<Boolean>

  /**
   * Toggles Gemini AI feature analysis on or off.
   *
   * @param enabled Desired enablement state.
   * @return true if update succeeded, false otherwise.
   */
  suspend fun setAiFeaturesEnabled(enabled: Boolean): Boolean
}

/**
 * Controls AI description features via [AvStreamAnalysis] (VideoAnalysisDescription trigger)
 * bound to a specific [DeviceType] endpoint.
 * Incorporates optimistic state updates to handle cases where trait attribute streams on cloud endpoints are null.
 */
class VideoAnalysisControllerImpl(
  private val device: HomeDevice,
  private val endpointType: DeviceType,
  override val label: String = "Gemini AI Features"
) : VideoAnalysisController {

  private val avStreamAnalysisFlow: Flow<AvStreamAnalysis?> = device.type(endpointType.factory)
    .map { type -> type?.trait(AvStreamAnalysis) }
    .distinctUntilChanged()

  // Local optimistic state flow to maintain immediate UI responsiveness across remote calls
  private val _optimisticAiEnabled = MutableStateFlow<Boolean?>(null)

  // Reactive flow checking if VideoAnalysisDescription event trigger is supported on this device endpoint
  override val isAiFeaturesSupported: Flow<Boolean> =
    avStreamAnalysisFlow
      .map { avStreamAnalysis ->
        val supportedTriggers = avStreamAnalysis?.supportedEventTriggers

        val isSupported = supportedTriggers?.contains(
          EventTriggerTypeEnum.VideoAnalysisDescription
        ) == true

        Log.d(TAG, "isAiFeaturesSupported ($label): device=${device.id}, isSupported=$isSupported")
        isSupported
      }
      .distinctUntilChanged()

  // Reactive flow emitting the current enablement state of AI perception features
  @OptIn(ExperimentalCoroutinesApi::class)
  override val isAiFeaturesEnabled: Flow<Boolean> =
    combine(
      avStreamAnalysisFlow,
      _optimisticAiEnabled
    ) { avStreamAnalysis, optimisticState ->
      val enabledTriggers = avStreamAnalysis?.enabledEventTriggers
      val actualBackendState = enabledTriggers?.contains(
        EventTriggerTypeEnum.VideoAnalysisDescription
      ) == true

      actualBackendState to optimisticState
    }.onEach { (actualBackendState, optimisticState) ->
      // Clear optimistic override once cloud backend state catches up
      if (optimisticState != null && actualBackendState == optimisticState) {
        _optimisticAiEnabled.value = null
      }
    }.map { (actualBackendState, optimisticState) ->
      val isEnabled = optimisticState ?: actualBackendState
      Log.d(TAG, "isAiFeaturesEnabled ($label): device=${device.id}, isEnabled=$isEnabled (optimistic=$optimisticState, actual=$actualBackendState)")
      isEnabled
    }.distinctUntilChanged()

  // Execute RPC or trait update to enable/disable AI perception features on this endpoint
  override suspend fun setAiFeaturesEnabled(enabled: Boolean): Boolean {
    return try {
      Log.d(TAG, "setAiFeaturesEnabled ($label) requested for device=${device.id}, enabled=$enabled")
      val typeInstance = device.type(endpointType.factory).first()
      val avStreamAnalysis = typeInstance?.trait(AvStreamAnalysis)
        ?: run {
          Log.w(TAG, "AvStreamAnalysis trait unavailable for $label on device ${device.id}")
          return false
        }

      val update = listOf(
        EventTriggerEnablement(
          eventTriggerType = EventTriggerTypeEnum.VideoAnalysisDescription,
          enablementStatus = if (enabled) EnablementStatusEnum.Enabled else EnablementStatusEnum.Disabled
        )
      )
      // Set optimistic state immediately to update UI while RPC executes
      _optimisticAiEnabled.value = enabled
      // Execute trigger update RPC call
      avStreamAnalysis.setOrUpdateEventDetectionTriggers(update)
      Log.d(TAG, "Successfully updated setOrUpdateEventDetectionTriggers for $label on device ${device.id}")
      true
    } catch (e: Exception) {
      // Clear optimistic state on RPC failure so UI falls back to actual backend state
      _optimisticAiEnabled.value = null
      // Preserve coroutine structured concurrency cancellation
      if (e is CancellationException) {
        throw e
      }
      Log.e(TAG, "Exception setting AI features state to $enabled for $label on device ${device.id}", e)
      false
    }
  }

  companion object {
    private const val TAG = "VideoAnalysisController"
  }
}
