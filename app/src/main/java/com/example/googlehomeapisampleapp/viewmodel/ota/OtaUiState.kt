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

package com.example.googlehomeapisampleapp.viewmodel.ota

import com.google.home.matter.standard.OtaSoftwareUpdateRequestorTrait.UpdateStateEnum

sealed interface OtaUiState {
    data object Loading : OtaUiState
    data class UpToDate(val currentVersionString: String? = null) : OtaUiState
    data object Checking : OtaUiState
    data class Downloading(val progressPercent: Int?, val currentVersionString: String? = null) : OtaUiState
    data class Installing(val currentVersionString: String? = null) : OtaUiState
    data class Delayed(val reason: String, val currentVersionString: String? = null) : OtaUiState
    data class Failed(val currentVersionString: String? = null) : OtaUiState
}

fun mapUpdateStateToUiState(
    updateState: UpdateStateEnum?,
    progress: UByte?,
    versionString: String?
): OtaUiState {
    return when (updateState) {
        UpdateStateEnum.Idle -> OtaUiState.UpToDate(currentVersionString = versionString)
        UpdateStateEnum.Querying -> OtaUiState.Checking
        UpdateStateEnum.Downloading -> OtaUiState.Downloading(
            progressPercent = progress?.toInt(),
            currentVersionString = versionString
        )
        UpdateStateEnum.Applying -> OtaUiState.Installing(currentVersionString = versionString)
        UpdateStateEnum.DelayedOnQuery -> OtaUiState.Delayed("waiting after a busy response", versionString)
        UpdateStateEnum.DelayedOnApply -> OtaUiState.Delayed("waiting due to the next action", versionString)
        UpdateStateEnum.DelayedOnUserConsent -> OtaUiState.Delayed("waiting on user consent", versionString)
        UpdateStateEnum.RollingBack -> OtaUiState.Failed(currentVersionString = versionString)
        UpdateStateEnum.Unknown, UpdateStateEnum.UnknownValue, null -> OtaUiState.Checking
    }
}
