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

import com.google.home.ConnectivityState
import com.google.home.DeviceType
import com.google.home.DeviceTypeFactory
import com.google.home.Trait
import com.google.home.TraitFactory
import com.google.home.google.Assistant
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
import com.google.home.matter.standard.SpeakerDevice
import com.google.home.matter.standard.TemperatureMeasurement
import com.google.home.matter.standard.TemperatureSensorDevice
import com.google.home.matter.standard.WindowCovering
import com.google.home.matter.standard.WindowCoveringDevice

object DeviceStatusFormatter {
  // Map determining which trait value is going to be displayed as status for this device:
  val statusMap: Map<DeviceTypeFactory<out DeviceType>, TraitFactory<out Trait>> =
    mapOf(
      ColorTemperatureLightDevice to OnOff,
      ContactSensorDevice to BooleanState,
      DimmableLightDevice to OnOff,
      DoorLockDevice to DoorLock,
      ExtendedColorLightDevice to OnOff,
      FanDevice to FanControl,
      GenericSwitchDevice to OnOff,
      OccupancySensorDevice to OccupancySensing,
      OnOffLightDevice to OnOff,
      OnOffLightSwitchDevice to OnOff,
      OnOffPluginUnitDevice to OnOff,
      OnOffSensorDevice to OnOff,
      SpeakerDevice to MediaPlayback,
      TemperatureSensorDevice to TemperatureMeasurement,
      WindowCoveringDevice to WindowCovering,
    )

  // Map determining the user readable value for this device:
  val nameMap: Map<DeviceTypeFactory<out DeviceType>, String> =
    mapOf(
      ColorTemperatureLightDevice to "Light",
      ContactSensorDevice to "Sensor",
      DimmableLightDevice to "Light",
      DoorLockDevice to "Lock",
      ExtendedColorLightDevice to "Light",
      FanDevice to "Fan",
      GenericSwitchDevice to "Switch",
      OccupancySensorDevice to "Sensor",
      OnOffLightDevice to "Light",
      OnOffLightSwitchDevice to "Switch",
      OnOffPluginUnitDevice to "Outlet",
      OnOffSensorDevice to "Sensor",
      SpeakerDevice to "Speaker",
      TemperatureSensorDevice to "Temperature Sensor",
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
    // Normal flow: Get target trait from the map based on the device's factory
    val targetTrait: TraitFactory<out Trait>? = statusMap[type.factory]

    if (
      type.metadata.sourceConnectivity.connectivityState != ConnectivityState.ONLINE &&
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
        percentSetting != null ->
          when {
            percentSetting == 0.toUByte() -> "Off"
            percentSetting <= 33.toUByte() -> "Low"
            percentSetting <= 66.toUByte() -> "Medium"
            else -> "High"
          }
        else -> "On"
      }
    }

    if (targetTrait == null) return "Unsupported" // Default for unmapped device types

    if (traits.isEmpty()) return "Unsupported"

    if (traits.none { it!!.factory == targetTrait }) return "Unknown"

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
    val status: String =
      when (trait) {
        is Assistant -> {
          "Assistant Ready"
        }

        is BooleanState -> {
          // BooleanState is special, where the state gains meaning based on the device type:
          when (type.factory) {
            ContactSensorDevice -> {
              if (trait.stateValue == true) "Closed" else "Open"
            }

            else -> {
              if (trait.stateValue == true) "True" else "False"
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
            percentSetting != null ->
              when {
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
