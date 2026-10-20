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
import com.google.home.HomeDevice
import com.google.home.google.AvStreamAnalysis
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Factory for creating [VideoAnalysisController] instances.
 *
 * Discovers all endpoints on a device supporting [AvStreamAnalysis] and instantiates
 * a dedicated [VideoAnalysisController] per endpoint.
 */
class VideoAnalysisControllerFactory @Inject internal constructor() {

  /**
   * Creates a list of [VideoAnalysisController] instances for all compatible endpoints on [device].
   *
   * @param device The [HomeDevice] to inspect and instantiate controllers for.
   * @return A list of [VideoAnalysisController] instances (one per camera endpoint), or an empty list.
   */
  suspend fun createAll(device: HomeDevice): List<VideoAnalysisController> {
    val types = device.types().first()
    val matchingEndpoints = types.filter { type -> type.trait(AvStreamAnalysis) != null }

    if (matchingEndpoints.isEmpty()) {
      Log.w(TAG, "Device ${device.id} does not support AvStreamAnalysis trait on any endpoint.")
      return emptyList()
    }

    return matchingEndpoints.mapIndexed { index, endpoint ->
      val label = if (matchingEndpoints.size > 1) {
        "Gemini AI Features (Lens ${index + 1})"
      } else {
        "Gemini AI Features"
      }
      Log.d(TAG, "Creating VideoAnalysisController for device ${device.id} on endpoint ${endpoint.factory} ($label)")
      VideoAnalysisControllerImpl(device, endpoint, label)
    }
  }

  companion object {
    private const val TAG = "VideoAnalysisControllerFactory"
  }
}
