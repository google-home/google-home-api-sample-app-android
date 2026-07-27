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

package com.ioconnect2026.googlehomeapisampleapp.viewmodel.devices

import android.util.Log
import com.ioconnect2026.googlehomeapisampleapp.HomeModule_ProvideSupportedTraitsFactory
import com.google.home.ConnectivityState
import com.google.home.DecommissionEligibility
import com.google.home.DeviceType
import com.google.home.HomeDevice
import com.google.home.Trait
import com.google.home.automation.UnknownDeviceType
import com.google.home.matter.standard.DimmableLightDevice
import com.google.home.matter.standard.DoorLockDevice
import com.google.home.matter.standard.FanDevice
import com.google.home.matter.standard.OnOffLightDevice
import com.google.home.matter.standard.SpeakerDevice
import com.google.home.matter.standard.TemperatureSensorDevice
import com.google.home.matter.standard.WindowCoveringDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * ViewModel for a single [HomeDevice]. This ViewModel provides access to device properties,
 * connectivity state, and allows for device actions like renaming and deleting.
 *
 * @property device The [HomeDevice] instance this ViewModel represents.
 */
class DeviceViewModel(val device: HomeDevice, private val scope: CoroutineScope) {

  var id: String = device.id.id

  private val _name = MutableStateFlow(device.name)
  val name: StateFlow<String> = _name.asStateFlow()

  var connectivity: ConnectivityState

  private val _type = MutableStateFlow<DeviceType>(UnknownDeviceType())
  val type: StateFlow<DeviceType> = _type.asStateFlow()

  private val _traits = MutableStateFlow<List<Trait>>(mutableListOf())
  val traits: StateFlow<List<Trait>> = _traits.asStateFlow()

  private val _typeName = MutableStateFlow("--")
  val typeName: StateFlow<String> = _typeName.asStateFlow()

  private val _status = MutableStateFlow("--")
  val status: StateFlow<String> = _status.asStateFlow()

  private val _uiEventFlow = MutableSharedFlow<UiEvent>()
  val uiEventFlow: SharedFlow<UiEvent> = _uiEventFlow

  init {
    // Initialize the connectivity state:
    connectivity = device.sourceConnectivity.connectivityState

    // Subscribe to changes on dynamic values:
    scope.launch { subscribeToType() }
  }

  /**
   * Renames the device both locally and reflects it in the UI.
   * - Calls `setName()` to update the HomeDevice object
   * - Emits the new name to `name` state flow so the UI updates reactively
   *
   * @param newName The new name for the device.
   */
  fun rename(newName: String) {
    scope.launch {
      try {
        device.setName(newName)
        _name.emit(newName)
      } catch (e: Exception) {
        Log.e("DeviceViewModel", "Error renaming device: ${e.message}")

        // Emit UI event to show error toast
        _uiEventFlow.emit(UiEvent.ShowToast("Failed to rename device. Please try again."))
      }
    }
  }

  /** Deletes the device. Checks for decommission eligibility before attempting to delete. */
  fun deleteDevice() {
    scope.launch {
      try {
        _uiEventFlow.emit(UiEvent.ShowToast("GHP isMatterDevice: ${device.isMatterDevice}"))
        _uiEventFlow.emit(
          UiEvent.ShowToast("GHP isEligible: ${device.checkDecommissionEligibility()}")
        )

        val eligibility = device.checkDecommissionEligibility()
        Log.e("DeviceViewModel", "GHP isMatterDevice: ${device.isMatterDevice}")
        Log.e("DeviceViewModel", "GHP Decommission eligibility evaluated: $eligibility")

        if (
          eligibility is DecommissionEligibility.Eligible ||
            eligibility is DecommissionEligibility.EligibleWithSideEffects
        ) {
          device.decommissionDevice()
          _uiEventFlow.emit(UiEvent.ShowToast("Device deleted successfully."))
          delay(500)
          _uiEventFlow.emit(UiEvent.NavigateBack)
        } else {
          _uiEventFlow.emit(
            UiEvent.ShowToast(
              "This device cannot be deleted. It is not eligible for decommissioning."
            )
          )
        }
      } catch (e: Exception) {
        Log.e("DeviceViewModel", "Error deleting device: ${e.message}")
        _uiEventFlow.emit(UiEvent.ShowToast("Error deleting device: ${e.message}"))
      }
    }
  }

  private fun determinePrimaryType(typeSet: Set<DeviceType>): DeviceType {
    val fallbackPriorityOrder =
      listOf(
        WindowCoveringDevice::class,
        FanDevice::class,
        DoorLockDevice::class,
        SpeakerDevice::class,
        DimmableLightDevice::class,
        OnOffLightDevice::class,
        TemperatureSensorDevice::class,
      )
    return typeSet.find { it.metadata.isPrimaryType }
      ?: fallbackPriorityOrder
        .asSequence()
        .mapNotNull { priorityClass -> typeSet.find { priorityClass.isInstance(it) } }
        .firstOrNull()
      ?: typeSet.firstOrNull()
      ?: UnknownDeviceType()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun subscribeToType() {
    device
      .types()
      .flatMapLatest { typeSet ->
        val primaryTypeCandidate = determinePrimaryType(typeSet)
        if (primaryTypeCandidate is UnknownDeviceType) {
          flowOf(Pair(primaryTypeCandidate, typeSet))
        } else {
          device.type(primaryTypeCandidate.factory).map { updatedPrimaryType ->
            Pair(updatedPrimaryType, typeSet)
          }
        }
      }
      .collect { (primaryType, typeSet) ->
        connectivity = primaryType.metadata.sourceConnectivity.connectivityState
        val supportedTraits = getSupportedTraits(primaryType.traits(), typeSet, primaryType)
        _type.emit(primaryType)
        val emittedTypeName =
          DeviceStatusFormatter.nameMap[primaryType.factory] ?: "Unsupported Device"
        _typeName.emit(emittedTypeName)
        _traits.emit(supportedTraits)
        _status.emit(DeviceStatusFormatter.getDeviceStatus(primaryType, supportedTraits))
      }
  }

  /**
   * Determines which traits reported by the device should be considered "supported" in the sample
   * app, including a whitelist for the Onn camera.
   */
  fun getSupportedTraits(
    traits: Set<Trait>,
    allDeviceTypes: Set<DeviceType>,
    primaryType: DeviceType,
  ): List<Trait> {
    val supportedTraits: MutableList<Trait> = mutableListOf()

    for (trait in traits) {
      val isGenerallySupported = trait.factory in HomeModule_ProvideSupportedTraitsFactory().get()
      if (isGenerallySupported) supportedTraits.add(trait)
    }

    return supportedTraits
  }

  sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()

    object NavigateBack : UiEvent()
  }
}
