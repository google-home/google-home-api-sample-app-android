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

package com.example.googlehomeapisampleapp.viewmodel.devices

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.googlehomeapisampleapp.HomeModule_ProvideSupportedTraitsFactory
import com.google.home.ConnectivityState
import com.google.home.DecommissionEligibility
import com.google.home.DeviceType
import com.google.home.DeviceTypeFactory
import com.google.home.HomeDevice
import com.google.home.Trait
import com.google.home.TraitFactory
import com.google.home.automation.UnknownDeviceType
import com.google.home.google.Assistant
import com.google.home.google.GoogleCameraDevice
import com.google.home.google.GoogleDisplayDevice
import com.google.home.google.GoogleDoorbellDevice
import com.google.home.google.GoogleTVDevice
import com.google.home.google.WebRtcLiveView
import com.google.home.matter.standard.BooleanState
import com.google.home.matter.standard.ColorTemperatureLightDevice
import com.google.home.matter.standard.ContactSensorDevice
import com.google.home.matter.standard.DimmableLightDevice
import com.google.home.matter.standard.DoorLock
import com.google.home.matter.standard.DoorLockDevice
import com.google.home.matter.standard.DoorLockTrait
import com.google.home.matter.standard.ExtendedColorLightDevice
import com.google.home.matter.standard.FanControl
import com.google.home.matter.standard.FanDevice
import com.google.home.matter.standard.GenericSwitchDevice
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.MediaPlayback
import com.google.home.matter.standard.OccupancySensing
import com.google.home.matter.standard.OccupancySensorDevice
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOffLightDevice
import com.google.home.matter.standard.OnOffLightSwitchDevice
import com.google.home.matter.standard.OnOffPluginUnitDevice
import com.google.home.matter.standard.OnOffSensorDevice
import com.google.home.matter.standard.RootNodeDevice
import com.google.home.matter.standard.SpeakerDevice
import com.google.home.matter.standard.TemperatureMeasurement
import com.google.home.matter.standard.TemperatureSensorDevice
import com.google.home.matter.standard.Thermostat
import com.google.home.matter.standard.ThermostatDevice
import com.google.home.matter.standard.WindowCovering
import com.google.home.matter.standard.WindowCoveringDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
class DeviceViewModel(val device: HomeDevice) : ViewModel() {

  var id: String = device.id.id
  val name = MutableStateFlow(device.name)
  var connectivity: ConnectivityState

  val type: MutableStateFlow<DeviceType>
  val traits: MutableStateFlow<List<Trait>>
  val typeName: MutableStateFlow<String>
  val status: MutableStateFlow<String>
  private val _uiEventFlow = MutableSharedFlow<UiEvent>()
  val uiEventFlow: SharedFlow<UiEvent> = _uiEventFlow

  init {
    // Initialize the connectivity state:
    connectivity = device.sourceConnectivity.connectivityState

    // Initialize dynamic values for a structure:
    type = MutableStateFlow(UnknownDeviceType())
    traits = MutableStateFlow(mutableListOf())
    typeName = MutableStateFlow("--")
    status = MutableStateFlow("--")

    // Subscribe to changes on dynamic values:
    viewModelScope.launch { subscribeToType() }
  }

  /**
   * Renames the device both locally and reflects it in the UI.
   * - Calls `setName()` to update the HomeDevice object
   * - Emits the new name to `name` state flow so the UI updates reactively
   *
   * @param newName The new name for the device.
   */
  fun rename(newName: String) {
    viewModelScope.launch {
      try {
        device.setName(newName)
        name.emit(newName)
      } catch (e: Exception) {
        Log.e("DeviceViewModel", "Error renaming device: ${e.message}")

        // Emit UI event to show error toast
        _uiEventFlow.emit(UiEvent.ShowToast("Failed to rename device. Please try again."))
      }
    }
  }

  /**
   * Deletes the device. Checks for decommission eligibility before attempting to delete.
   */
  fun deleteDevice() {
    viewModelScope.launch {
      try {
        _uiEventFlow.emit(UiEvent.ShowToast("GHP isMatterDevice: ${device.isMatterDevice}"))
        _uiEventFlow.emit(UiEvent.ShowToast("GHP isEligible: ${device.checkDecommissionEligibility()}"))

        val eligibility = device.checkDecommissionEligibility()
        Log.e("DeviceViewModel", "GHP isMatterDevice: ${device.isMatterDevice}")
        Log.e("DeviceViewModel", "GHP Decommission eligibility evaluated: $eligibility")

        if (eligibility is DecommissionEligibility.Eligible || eligibility is DecommissionEligibility.EligibleWithSideEffects) {
          device.decommissionDevice()
          _uiEventFlow.emit(UiEvent.ShowToast("Device deleted successfully."))
          delay(500)
          _uiEventFlow.emit(UiEvent.NavigateBack)
        } else {
          _uiEventFlow.emit(UiEvent.ShowToast("This device cannot be deleted. It is not eligible for decommissioning."))
        }
      } catch (e: Exception) {
        Log.e("DeviceViewModel", "Error deleting device: ${e.message}")
        _uiEventFlow.emit(UiEvent.ShowToast("Error deleting device: ${e.message}"))
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun subscribeToType() {
    // Subscribe to changes on device type, and the traits/attributes within:
    device.types().flatMapLatest { typeSet ->
      /**
       * Fallback type priority order.
       *
       * For multi-functional devices (e.g., a Doorbell that also has a Camera),
       * the first matching type in this list takes precedence.
       *
       * To adjust priority, simply reorder the elements below.
       */
      val fallbackPriorityOrder = listOf(
        ThermostatDevice::class,
        GoogleDoorbellDevice::class,
        WindowCoveringDevice::class,
        FanDevice::class,
        DoorLockDevice::class,
        SpeakerDevice::class,
        GoogleTVDevice::class,
        DimmableLightDevice::class,
        GoogleCameraDevice::class,
        OnOffLightDevice::class,
        TemperatureSensorDevice::class,
      )

      // Find the primary type in a single, chained expression.
      val primaryTypeCandidate: DeviceType =
        // 1. First, try to find the officially marked primary type.
        typeSet.find { it.metadata.isPrimaryType }
        // 2. If not found, use the fallback priority list.
          ?: fallbackPriorityOrder
            .asSequence() // Use a sequence for efficiency (stops after first match)
            .mapNotNull { priorityClass ->
              typeSet.find { priorityClass.isInstance(it) }
            }
            .firstOrNull()
          // 3. If still not found, use the first type if there are any.
          ?: typeSet.firstOrNull()
          // 4. If all else fails, default to UnknownDeviceType.
          ?: UnknownDeviceType()

      // Observe attribute state changes for the primary device type:
      // If the type is Unknown, bypass observation to prevent UI hangs.
      if (primaryTypeCandidate is UnknownDeviceType) {
        flowOf(Pair(primaryTypeCandidate, typeSet))
      } else {
        device.type(primaryTypeCandidate.factory).map { updatedPrimaryType ->
          Pair(updatedPrimaryType, typeSet)
        }
      }
    }.collect { (primaryType, typeSet) ->
      // Set the connectivityState from the primary device type:
      connectivity = primaryType.metadata.sourceConnectivity.connectivityState

      // Container for list of supported traits present on the primary device type:
      // FIX: Pass the current typeSet (all device types) and primaryType to getSupportedTraits
      val supportedTraits: List<Trait> = getSupportedTraits(primaryType.traits(), typeSet, primaryType)

      // Store the primary type as the device type:
      type.emit(primaryType)

      // ------------------------------------------------------------------
      // *** DIRECT NAME OVERRIDE FOR UNRECOGNIZED CAMERA DEVICE ***
      var emittedTypeName = nameMap[primaryType.factory] ?: "Unsupported Device"

      // Check if the device is the generic RootNodeDevice AND matches the target camera's VID/PID
      if (primaryType is RootNodeDevice) {
        val basicInfo = primaryType.standardTraits.basicInformation
        // Re-added .toInt() conversion here:
        if (basicInfo?.vendorId?.toInt() == ONN_CAMERA_VID && basicInfo.productId?.toInt() == ONN_CAMERA_PID) {
          // Manually override the name string
          emittedTypeName = "Camera"
        }
      }

      // Determine the name for this type and store:
      typeName.emit(emittedTypeName)
      // ------------------------------------------------------------------

      // From the primary type, get the supported traits:
      traits.emit(supportedTraits)

      // Publish a device status based on connectivity, deviceType, and available traits:
      status.emit(getDeviceStatus(primaryType, supportedTraits))
    }
  }

  /**
   * Determines which traits reported by the device should be considered "supported"
   * in the sample app, including a whitelist for the Onn camera.
   */
  fun getSupportedTraits(traits: Set<Trait>, allDeviceTypes: Set<DeviceType>, primaryType: DeviceType) : List<Trait> {
    val supportedTraits: MutableList<Trait> = mutableListOf()

    // FIX: Use the passed allDeviceTypes parameter instead of device.types().value
    // Check if this device is the whitelisted Onn camera
    val isWhitelistedCamera = allDeviceTypes.any { deviceType ->
      // Re-added .toInt() conversion here:
      deviceType is RootNodeDevice &&
        deviceType.standardTraits.basicInformation?.vendorId?.toInt() == ONN_CAMERA_VID &&
        deviceType.standardTraits.basicInformation?.productId?.toInt() == ONN_CAMERA_PID
    }

    for (trait in traits) {
      // 1. Check if the trait is in the general supported list
      val isGenerallySupported = trait.factory in HomeModule_ProvideSupportedTraitsFactory().get()

      // 2. Check if the device is the whitelisted camera AND the trait is WebRtcLiveView
      val isCameraTraitOverride = isWhitelistedCamera && trait.factory == WebRtcLiveView

      if (isGenerallySupported || isCameraTraitOverride)
        supportedTraits.add(trait)
    }
    //For GoogleTVDevice, traits are sorted to ensure consistent order (OnOff first, MediaPlayback second).
    // Sort traits only for GoogleTVDevice to ensure consistent UI order
    if (primaryType.factory == GoogleTVDevice) {
      val traitOrder: Map<TraitFactory<out Trait>, Int> = mapOf(
        OnOff to 0,
        MediaPlayback to 1
      )
      return supportedTraits.sortedBy { traitOrder[it.factory] ?: Int.MAX_VALUE }
    }

    return supportedTraits
  }

  companion object {
    // Define the specific VID/PID for your camera
    private const val ONN_CAMERA_VID = 5502
    private const val ONN_CAMERA_PID = 4233

    // Map determining which trait value is going to be displayed as status for this device:
    val statusMap: Map<DeviceTypeFactory<out DeviceType>, TraitFactory<out Trait>> = mapOf(
      ColorTemperatureLightDevice to OnOff,
      ContactSensorDevice to BooleanState,
      DimmableLightDevice to OnOff,
      DoorLockDevice to DoorLock,
      ExtendedColorLightDevice to OnOff,
      FanDevice to FanControl,
      GenericSwitchDevice to OnOff,
      GoogleCameraDevice to WebRtcLiveView,
      GoogleDisplayDevice to OnOff,
      GoogleDoorbellDevice to WebRtcLiveView,
      GoogleTVDevice to OnOff,
      OccupancySensorDevice to OccupancySensing,
      OnOffLightDevice to OnOff,
      OnOffLightSwitchDevice to OnOff,
      OnOffPluginUnitDevice to OnOff,
      OnOffSensorDevice to OnOff,
      SpeakerDevice to MediaPlayback,
      TemperatureSensorDevice to TemperatureMeasurement,
      ThermostatDevice to Thermostat,
      WindowCoveringDevice to WindowCovering,
    )

    // Map determining the user readable value for this device:
    val nameMap: Map<DeviceTypeFactory<out DeviceType>, String> = mapOf(
      ColorTemperatureLightDevice to "Light",
      ContactSensorDevice to "Sensor",
      DimmableLightDevice to "Light",
      DoorLockDevice to "Lock",
      ExtendedColorLightDevice to "Light",
      FanDevice to "Fan",
      GenericSwitchDevice to "Switch",
      GoogleCameraDevice to "Camera",
      GoogleDisplayDevice to "Hub",
      GoogleDoorbellDevice to "Doorbell",
      GoogleTVDevice to "TV",
      OccupancySensorDevice to "Sensor",
      OnOffLightDevice to "Light",
      OnOffLightSwitchDevice to "Switch",
      OnOffPluginUnitDevice to "Outlet",
      OnOffSensorDevice to "Sensor",
      SpeakerDevice to "Speaker",
      TemperatureSensorDevice to "Temperature Sensor",
      ThermostatDevice to "Thermostat",
      WindowCoveringDevice to "Window Covering",
    )

    /**
     * Gets the status string for a device based on its type and traits.
     *
     * @param type The [DeviceType] of the device.
     * @param traits The list of [Trait]s supported by the device.
     * @return A string representing the device's status.
     */
    fun <T : Trait?> getDeviceStatus(type: DeviceType, traits: List<T>): String {

      // ------------------------------------------------------------------
      // *** STATUS OVERRIDE FOR GENERIC CAMERA DEVICE ***
      // Check if the generic RootNodeDevice is the specific camera, and override the status lookup.
      if (type is RootNodeDevice) {
        val basicInfo = type.standardTraits.basicInformation
        // Re-added .toInt() conversion here:
        if (basicInfo?.vendorId?.toInt() == ONN_CAMERA_VID && basicInfo.productId?.toInt() == ONN_CAMERA_PID) {

          // Since we know it's a camera, we bypass the map lookup and manually set the target trait
          // to the one we expect for a camera (WebRtcLiveView).
          val targetTrait: TraitFactory<out Trait> = WebRtcLiveView

          // Proceed with standard status checks using the overridden targetTrait
          if (type.metadata.sourceConnectivity.connectivityState != ConnectivityState.ONLINE &&
            type.metadata.sourceConnectivity.connectivityState != ConnectivityState.PARTIALLY_ONLINE
          )
            return "Offline"

          // Check if the traits list (which is now guaranteed to include WebRtcLiveView if the device reported it)
          // actually contains the required trait.
          if (traits.none { it!!.factory == targetTrait })
          // This indicates the device is online and is the correct model,
          // but failed to report the necessary video trait (WebRtcLiveView).
            return "Video trait not present"

          // Found the trait, now get the status
          return getTraitStatus(traits.first { it!!.factory == targetTrait }, type)
        }
      }
      // ------------------------------------------------------------------

      // Normal flow: Get target trait from the map based on the device's factory
      val targetTrait: TraitFactory<out Trait>? = statusMap[type.factory]

      if (type.metadata.sourceConnectivity.connectivityState != ConnectivityState.ONLINE &&
        type.metadata.sourceConnectivity.connectivityState != ConnectivityState.PARTIALLY_ONLINE
      )
        return "Offline"
      if (type.factory == FanDevice) {
        val fanControlTrait = traits.filterIsInstance<FanControl>().firstOrNull()
        val onOffTrait = traits.filterIsInstance<OnOff>().firstOrNull()

        if (onOffTrait?.onOff == false) return "Off"

        val fanMode = fanControlTrait?.fanMode
        val percentSetting = fanControlTrait?.percentSetting

        return when {
          fanMode != null -> fanMode.toString()
          percentSetting != null -> when {
            percentSetting == 0.toUByte() -> "Off"
            percentSetting <= 33.toUByte() -> "Low"
            percentSetting <= 66.toUByte() -> "Medium"
            else -> "High"
          }
          else -> "On"
        }
      }

      if (targetTrait == null)
        return "Unsupported" // Default for unmapped device types

      if (traits.isEmpty())
        return "Unsupported"

      if (traits.none { it!!.factory == targetTrait })
        return "Unknown"

      return getTraitStatus(traits.first { it!!.factory == targetTrait }, type)
    }

    /**
     * Gets a user-readable status string for a specific trait.
     *
     * @param trait The [Trait] to get the status for.
     * @param type The [DeviceType] of the device.
     * @return A string representing the trait's status.
     */
    fun <T : Trait?> getTraitStatus(trait: T, type: DeviceType): String {
      val status: String = when (trait) {
        is Assistant -> {
          "Assistant Ready"
        }

        is BooleanState -> {
          // BooleanState is special, where the state gains meaning based on the device type:
          when (type.factory) {
            ContactSensorDevice -> {
              if (trait.stateValue == true) "Closed"
              else "Open"
            }

            else -> {
              if (trait.stateValue == true) "True"
              else "False"
            }
          }
        }

        is DoorLock -> {
          if (trait.lockState == DoorLockTrait.DlLockState.Locked) "Locked" else "Unlocked"
        }

        is FanControl -> {
          val fanMode = trait.fanMode
          val percentSetting = trait.percentSetting
          when {
            fanMode != null -> fanMode.toString()
            percentSetting != null -> when {
              percentSetting == 0.toUByte() -> "Off"
              percentSetting <= 33.toUByte() -> "Low"
              percentSetting <= 66.toUByte() -> "Medium"
              else -> "High"
            }
            else -> "Unknown"
          }
        }
        is LevelControl -> {
          trait.currentLevel.toString()
        }

        is MediaPlayback -> {
          val state = trait.currentState
          state?.name ?: "Unknown"
        }

        is OccupancySensing -> {
          if (trait.occupancy?.occupied == true) "Occupied" else "Unoccupied"
        }

        is OnOff -> {
          if (trait.onOff == true) "On" else "Off"
        }

        is TemperatureMeasurement -> {
          val measuredValue = trait.measuredValue
          if (measuredValue != null) {
            val tempCelsius = measuredValue / 100.0
            "%.1f°C".format(tempCelsius)
          } else {
            "Unknown"
          }
        }

        is Thermostat -> {
          trait.systemMode.toString()
        }

        is WebRtcLiveView -> {
          // Check the connectivity state of the device type
          if (type.metadata.sourceConnectivity.connectivityState == ConnectivityState.ONLINE ||
            type.metadata.sourceConnectivity.connectivityState == ConnectivityState.PARTIALLY_ONLINE) {
            "Online"
          } else {
            "Offline"
          }
        }

        is WindowCovering -> {
          val currentPercent100ths = trait.currentPositionLiftPercent100ths ?: 0u
          val targetPercent100ths = trait.targetPositionLiftPercent100ths
          val currentOpen = 100 - (currentPercent100ths.toInt() / 100)
          val status = if (currentOpen == 0) "Closed" else "${currentOpen}% Open"

          if (targetPercent100ths != null && targetPercent100ths != currentPercent100ths) {
            val targetOpen = 100 - (targetPercent100ths.toInt() / 100)
            val targetStatus = if (targetOpen == 0) "Closed" else "${targetOpen}% Open"
            "$status (Target: $targetStatus)"
          } else {
            status
          }
        }

        else -> "Unknown"
      }
      return status
    }
  }

  sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    object NavigateBack : UiEvent()
  }
}