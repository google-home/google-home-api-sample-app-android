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

package com.example.googlehomeapisampleapp.extension.basicinformation

import android.util.Log
import com.example.googlehomeapisampleapp.viewmodel.devices.BasicInformationUiState
import com.google.home.HomeDevice
import com.google.home.google.ExtendedBasicInformation
import com.google.home.google.GoogleCameraDevice
import com.google.home.google.GoogleDoorbellDevice
import com.google.home.matter.standard.BasicInformation
import com.google.home.matter.standard.RootNodeDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Returns a [Flow] of [BasicInformationUiState] reconciling standard [BasicInformation]
 * and Google-proprietary [ExtendedBasicInformation].
 */
fun HomeDevice.observeBasicInformationUiState(): Flow<BasicInformationUiState> = flow {
  emit(BasicInformationUiState.Loading) // Initial Loading State
  try {
    // Wait until device types are loaded (ensuring a non-empty set of types)
    val deviceTypes = types().first { it.isNotEmpty() }
    val rootNode = deviceTypes.filterIsInstance<RootNodeDevice>().firstOrNull()
    val cameraDeviceType = deviceTypes.firstOrNull { it is GoogleCameraDevice || it is GoogleDoorbellDevice }

    val traitFlow: Flow<BasicInformationUiState> = if (rootNode != null) {
      // For Matter devices: observe both RootNodeDevice and camera/doorbell endpoints reactively
      val rootNodeFlow = type(RootNodeDevice)
      val cameraFlow = cameraDeviceType?.let { type(it.factory) } ?: flowOf(null)

      combine(rootNodeFlow, cameraFlow) { updatedRootNode, updatedCamera ->
        val basicInfo = updatedRootNode.trait(BasicInformation)
        val extendedBasicInfo = updatedCamera?.trait(ExtendedBasicInformation)
          ?: updatedRootNode.trait(ExtendedBasicInformation)

        val basicSerialNumber = basicInfo?.serialNumber
        val extSerialNumber = extendedBasicInfo?.getSerialNumber()?.serialNumber
        val serialNumber = if (!basicSerialNumber.isNullOrEmpty()) {
          basicSerialNumber
        } else if (!extSerialNumber.isNullOrEmpty()) {
          extSerialNumber
        } else {
          null
        }

        val successState: BasicInformationUiState = BasicInformationUiState.Success(
          serialNumber = serialNumber,
          vendorName = basicInfo?.vendorName,
          productName = basicInfo?.productName,
          hardwareVersion = basicInfo?.hardwareVersionString,
          softwareVersion = basicInfo?.softwareVersionString,
          vendorId = basicInfo?.vendorId?.let { "0x%04X".format(it.toInt()) },
          productId = basicInfo?.productId?.let { "0x%04X".format(it.toInt()) },
          nodeLabel = basicInfo?.nodeLabel,
          location = basicInfo?.location,
          manufacturingDate = basicInfo?.manufacturingDate,
          partNumber = basicInfo?.partNumber,
          productUrl = basicInfo?.productUrl,
          productLabel = basicInfo?.productLabel,
        )
        successState
      }
    } else if (cameraDeviceType != null) {
      // For Nest/Cloud cameras & doorbells: observe ExtendedBasicInformation
      type(cameraDeviceType.factory).map { updatedCamera ->
        val extendedBasicInfo = updatedCamera.trait(ExtendedBasicInformation)
        val extSerialNumber = extendedBasicInfo?.getSerialNumber()?.serialNumber

        val successState: BasicInformationUiState = BasicInformationUiState.Success(
          serialNumber = extSerialNumber,
          vendorName = null,
          productName = null,
          hardwareVersion = null,
          softwareVersion = null,
          vendorId = null,
          productId = null,
          nodeLabel = null,
          location = null,
          manufacturingDate = null,
          partNumber = null,
          productUrl = null,
          productLabel = null,
        )
        successState
      }
    } else {
      flowOf<BasicInformationUiState>(
        BasicInformationUiState.Success(
          serialNumber = null,
          vendorName = null,
          productName = null,
          hardwareVersion = null,
          softwareVersion = null,
          vendorId = null,
          productId = null,
          nodeLabel = null,
          location = null,
          manufacturingDate = null,
          partNumber = null,
          productUrl = null,
          productLabel = null,
        )
      )
    }

    emitAll(traitFlow)
  } catch (e: Exception) {
    Log.e("BasicInfoExtension", "Failed to fetch device metadata", e)
    emit(BasicInformationUiState.Error)
  }
}
