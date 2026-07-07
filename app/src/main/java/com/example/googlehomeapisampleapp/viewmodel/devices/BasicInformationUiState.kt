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

package com.example.googlehomeapisampleapp.viewmodel.devices

/** Encapsulates BasicInformation (and ExtendedBasicInformation) metadata with loading/error states. */
sealed interface BasicInformationUiState {
  data object Loading : BasicInformationUiState
  data object Error : BasicInformationUiState
  data class Success(
    val serialNumber: String?,
    val vendorName: String?,
    val productName: String?,
    val hardwareVersion: String?,
    val softwareVersion: String?,
    val vendorId: String?,
    val productId: String?,
    val nodeLabel: String?,
    val location: String?,
    val manufacturingDate: String?,
    val partNumber: String?,
    val productUrl: String?,
    val productLabel: String?,
  ) : BasicInformationUiState
}
