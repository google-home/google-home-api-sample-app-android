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

package com.example.googlehomeapisampleapp.repository

import com.example.googlehomeapisampleapp.viewmodel.automations.DraftViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.google.home.Structure
import com.google.home.automation.DraftAutomation
import com.google.home.automation.and
import com.google.home.automation.automation
import com.google.home.automation.between
import com.google.home.automation.equals
import com.google.home.automation.lessThan
import com.google.home.automation.notEquals
import com.google.home.google.GoogleCameraDevice
import com.google.home.google.GoogleTVDevice
import com.google.home.google.SimplifiedThermostat
import com.google.home.google.SimplifiedThermostatTrait
import com.google.home.google.Time
import com.google.home.google.Time.Companion.currentTime
import com.google.home.google.Time.Companion.sunriseTime
import com.google.home.google.Time.Companion.sunsetTime
import com.google.home.google.VideoAnalysis
import com.google.home.matter.standard.ColorTemperatureLightDevice
import com.google.home.matter.standard.DimmableLightDevice
import com.google.home.matter.standard.DoorLock
import com.google.home.matter.standard.DoorLock.Companion.lockState
import com.google.home.matter.standard.DoorLockDevice
import com.google.home.matter.standard.DoorLockTrait
import com.google.home.matter.standard.ExtendedColorLightDevice
import com.google.home.matter.standard.FanDevice
import com.google.home.matter.standard.OccupancySensing
import com.google.home.matter.standard.OccupancySensorDevice
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOff.Companion.onOff
import com.google.home.matter.standard.OnOffLightDevice
import com.google.home.matter.standard.OnOffPluginUnitDevice
import com.google.home.matter.standard.SpeakerDevice
import com.google.home.matter.standard.TemperatureMeasurement
import com.google.home.matter.standard.TemperatureMeasurement.Companion.measuredValue
import com.google.home.matter.standard.TemperatureSensorDevice
import com.google.home.matter.standard.ThermostatDevice
import com.google.home.matter.standard.WindowCovering
import com.google.home.matter.standard.WindowCoveringDevice

/**
 * Device capability flags computed in a single pass over the device list.
 * Used by both [AutomationsRepository] availability checks and the UI layer
 * to avoid redundant iteration over the device list.
 */
data class DeviceCapabilities(
  val hasEnoughLights: Boolean = false,
  val hasLightsAndThermostat: Boolean = false,
  val hasRequiredDevicesForSleep: Boolean = false,
  val hasWindowCoveringDevices: Boolean = false,
  val hasRequiredDevicesForLightAndTVPeriodic: Boolean = false,
  val hasCameraAndLight: Boolean = false,
)

/**
 * Classifies all devices in a single pass and returns the capability flags
 * needed by every predefined automation.
 *
 * Previously, each automation's availability check iterated the device list
 * independently (some multiple times internally), resulting in O(M × N)
 * iterations for M automations and N devices. This reduces it to O(N).
 */
fun computeDeviceCapabilities(deviceVMs: List<DeviceViewModel>): DeviceCapabilities {
  var lightCount = 0
  var hasThermostat = false
  var hasDoorLock = false
  var hasSpeaker = false
  var hasFan = false
  var hasPlug = false
  var hasTemperatureSensor = false
  var hasWindowCovering = false
  var hasOccupancySensor = false
  var hasGoogleTV = false
  var hasCamera = false

  for (vm in deviceVMs) {
    when (vm.type.value.factory) {
      OnOffLightDevice, DimmableLightDevice,
      ColorTemperatureLightDevice, ExtendedColorLightDevice -> lightCount++
      ThermostatDevice -> {
        hasThermostat = true
        hasTemperatureSensor = true // Thermostat also serves as temperature source
      }
      DoorLockDevice -> hasDoorLock = true
      SpeakerDevice -> hasSpeaker = true
      FanDevice -> hasFan = true
      OnOffPluginUnitDevice -> hasPlug = true
      TemperatureSensorDevice -> hasTemperatureSensor = true
      WindowCoveringDevice -> hasWindowCovering = true
      OccupancySensorDevice -> hasOccupancySensor = true
      GoogleTVDevice -> hasGoogleTV = true
      GoogleCameraDevice -> hasCamera = true
    }
  }

  return DeviceCapabilities(
    hasEnoughLights = lightCount >= 2,
    hasLightsAndThermostat = lightCount >= 1 && hasThermostat && hasDoorLock,
    hasRequiredDevicesForSleep = hasSpeaker && hasFan && hasPlug,
    hasWindowCoveringDevices = hasTemperatureSensor && hasWindowCovering,
    hasRequiredDevicesForLightAndTVPeriodic = lightCount >= 1 && hasGoogleTV && hasOccupancySensor,
    hasCameraAndLight = hasCamera && lightCount >= 1,
  )
}

class AutomationsRepository {
  /**
   * Creates an OnOff light automation draft
   * This should only be called when the user actually selects the predefined automation
   */
  fun createOnOffLightAutomationDraft(deviceVMs: List<DeviceViewModel>): DraftViewModel? {
    val onOffLights = getOnOffCapableLights(deviceVMs)
    if (onOffLights.size < 2) return null

    val deviceA = onOffLights[0]
    val deviceB = onOffLights[1]

    val presetDraft = createOnOffDraftAutomation(deviceA, deviceB)

    // Return a locked DraftViewModel that uses the preset
    return DraftViewModel(
      candidateVM = null,
      presetDraft = presetDraft,
      isLocked = true,
      automationType = DraftViewModel.AutomationType.ON_OFF
    )
  }

  /**
   * Creates a "Speaker and Fan" automation draft
   * Plays ocean wave sounds on speaker, turns on fan and plug when user says "Hey Google, I can't sleep"
   */
  fun createSpeakerAndFanAutomationDraft(
    deviceVMs: List<DeviceViewModel>,
    structure: com.google.home.Structure,
  ): DraftViewModel? {
    var speaker: DeviceViewModel? = null
    var fan: DeviceViewModel? = null
    var plug: DeviceViewModel? = null

    for (vm in deviceVMs) {
      when (vm.type.value.factory) {
        SpeakerDevice -> speaker = vm
        FanDevice -> fan = vm
        OnOffPluginUnitDevice -> plug = vm
      }
      if (speaker != null && fan != null && plug != null) break
    }

    // Need at least speaker, fan, and plug
    if (speaker == null || fan == null || plug == null) return null

    // Use the passed structure instead of getting it from device
    val presetDraft = createSpeakerAndFanDraftAutomation(structure, speaker, fan, plug)

    return DraftViewModel(
      candidateVM = null,
      presetDraft = presetDraft,
      isLocked = true,
      automationType = DraftViewModel.AutomationType.SPEAKER_AND_FAN
    )
  }

  /**
   * Creates a Window Covering automation draft
   * Closes window covering when temperature drops below 15°C and it's dark outside
   */
  fun createWindowCoveringAutomationDraft(
    deviceVMs: List<DeviceViewModel>,
    structure: Structure
  ): DraftViewModel? {
    var temperatureSensor: DeviceViewModel? = null
    var windowCovering: DeviceViewModel? = null

    // Single pass to find required devices
    for (vm in deviceVMs) {
      when (vm.type.value.factory) {
        TemperatureSensorDevice -> if (temperatureSensor == null) temperatureSensor = vm
        ThermostatDevice -> if (temperatureSensor == null) temperatureSensor = vm
        WindowCoveringDevice -> if (windowCovering == null) windowCovering = vm
      }
      if (temperatureSensor != null && windowCovering != null) break
    }

    if (temperatureSensor == null || windowCovering == null) return null

    val presetDraft = createWindowCoveringDraftAutomation(
      structure,
      temperatureSensor,
      windowCovering
    )

    return DraftViewModel(
      candidateVM = null,
      presetDraft = presetDraft,
      isLocked = true,
      automationType = DraftViewModel.AutomationType.WINDOW_COVERING
    )
  }

  /**
   * Creates a Light and TV Periodic automation draft
   * Broadcasts alarm, turns on lights and TV to deter intruders when motion is detected
   */
  fun createLightAndTVPeriodicAutomationDraft(
    deviceVMs: List<DeviceViewModel>,
    structure: Structure
  ): DraftViewModel? {
    val lights = getOnOffCapableLights(deviceVMs)
    var occupancySensor: DeviceViewModel? = null
    var googleTV: DeviceViewModel? = null

    for (vm in deviceVMs) {
      val factory = vm.type.value.factory
      when (factory) {
        OccupancySensorDevice -> if (occupancySensor == null) occupancySensor = vm
        com.google.home.google.GoogleTVDevice -> if (googleTV == null) googleTV = vm
      }
      if (occupancySensor != null && googleTV != null) break
    }

    if (lights.isEmpty() || occupancySensor == null || googleTV == null) {
      return null
    }

    val presetDraft = createLightAndTVPeriodicDraftAutomation(structure, occupancySensor, lights, googleTV)

    return DraftViewModel(
      candidateVM = null,
      presetDraft = presetDraft,
      isLocked = true,
      automationType = DraftViewModel.AutomationType.LIGHT_AND_TV_PERIODIC
    )
  }

  /**
   * Creates a light and thermostat automation draft.
   * Turn on lights and set thermostat to Auto mode when door is unlocked.
   */
  fun createLightAndThermostatAutomationDraft(deviceVMs: List<DeviceViewModel>): DraftViewModel? {
    var doorLock: DeviceViewModel? = null
    var thermostat: DeviceViewModel? = null
    val lights = mutableListOf<DeviceViewModel>()

    for (vm in deviceVMs) {
      val factory = vm.type.value.factory
      when (factory) {
        DoorLockDevice -> if (doorLock == null) doorLock = vm
        ThermostatDevice -> if (thermostat == null) thermostat = vm
        OnOffLightDevice, DimmableLightDevice,
        ColorTemperatureLightDevice, ExtendedColorLightDevice,
          -> lights.add(vm)
      }
    }

    if (doorLock == null || thermostat == null || lights.isEmpty()) {
      return null
    }

    val presetDraft = createLightAndThermostatDraftAutomation(doorLock, thermostat, lights)

    return DraftViewModel(
      candidateVM = null,
      presetDraft = presetDraft,
      isLocked = true,
      automationType = DraftViewModel.AutomationType.LIGHT_AND_THERMOSTAT
    )
  }
  /**
   * Creates a draft automation that turns on a light when the camera detects a person,
   * using a Natural Language camera starter.
   *
   * Note: [VideoAnalysis.QueryMatchedEvent.query] supports custom natural language strings.
   */
  fun createCameraSceneDetectedLightAutomationDraft(
    deviceVMs: List<DeviceViewModel>
  ): DraftViewModel? {
    val camera = deviceVMs.firstOrNull { it.type.value.factory == GoogleCameraDevice }
      ?: return null
    val light = getOnOffCapableLights(deviceVMs).firstOrNull()
      ?: return null

    val presetDraft = createCameraSceneDetectedLightDraftAutomation(camera, light)

    return DraftViewModel(
      candidateVM = null,
      presetDraft = presetDraft,
      isLocked = true,
      automationType = DraftViewModel.AutomationType.CUSTOM
    )
  }

  private fun createOnOffDraftAutomation(
    deviceA: DeviceViewModel,
    deviceB: DeviceViewModel,
  ): DraftAutomation {
    return automation {
      name = "On/Off Light Automation"
      description = "Turn off ${deviceB.name.value} when ${deviceA.name.value} turns off"
      isActive = true

      sequential {
        select {
          sequential {
            val onOffStarter = starter(deviceA.device, deviceA.type.value.factory, OnOff)
            condition {
              expression = onOffStarter.onOff equals false
            }
          }
          manualStarter()
        }

        parallel {
          action(deviceB.device, deviceB.type.value.factory) {
            command(OnOff.off())
          }
        }
      }
    }
  }

  private fun createSpeakerAndFanDraftAutomation(
    structure: com.google.home.Structure,
    speaker: DeviceViewModel,
    fan: DeviceViewModel,
    plug: DeviceViewModel,
  ): DraftAutomation {
    return automation {
      name = "Speaker and Fan Automation"
      description =
        "Play ocean sounds on ${speaker.name.value}, turn on ${fan.name.value} and ${plug.name.value}"
      isActive = true

      sequential {
        // Voice starter: "Hey Google, I can't sleep"
        select {
          // Use the event-based starter with parameters builder
          // The query() returns a Parameter that goes in the parameters block
          starter(
            structure,
            com.google.home.google.VoiceStarter.OkGoogleEvent
          ) {
            parameter(com.google.home.google.VoiceStarter.OkGoogleEvent.query("I can't sleep"))
          }
          manualStarter()
        }

        // Execute actions in parallel
        parallel {
          // Tell speaker to play ocean wave sounds using Assistant
          action(speaker.device, speaker.type.value.factory) {
            command(com.google.home.google.AssistantFulfillment.okGoogle("Play ocean wave sounds"))
          }

          // Turn on the fan
          action(fan.device, fan.type.value.factory) {
            command(OnOff.on())
          }

          // Turn on the plug
          action(plug.device, plug.type.value.factory) {
            command(OnOff.on())
          }
        }
      }
    }
  }

  private fun createWindowCoveringDraftAutomation(
    structure: Structure,
    temperatureSensor: DeviceViewModel,
    windowCovering: DeviceViewModel
  ): DraftAutomation {
    return automation {
      name = "Close Window Covering"
      description = "Close ${windowCovering.name.value} when temperature drops below 15°C (59°F) and it's dark outside."
      isActive = true

      sequential {
        // Starters: temperature changes or manual execution
        select {
          starter(temperatureSensor.device, TemperatureSensorDevice, TemperatureMeasurement)
          manualStarter()
        }

        // State readers for conditions
        val tempReader = stateReader(temperatureSensor.device, TemperatureSensorDevice, TemperatureMeasurement)
        val timeReader = stateReader(structure, Time)

        // Condition: temperature < 15°C AND it's dark (NOT daytime)
        // measuredValue is in hundredths of a degree Celsius, so 15°C = 1500
        val tempBelowThreshold = tempReader.measuredValue lessThan 1500
        val isDaytime = timeReader.currentTime.between(
          timeReader.sunriseTime,
          timeReader.sunsetTime
        )

        condition {
          expression = tempBelowThreshold and (isDaytime notEquals true)
        }

        // Close the window covering
        action(windowCovering.device, WindowCoveringDevice) {
          command(WindowCovering.downOrClose())
        }
      }
    }
  }

  private fun createLightAndTVPeriodicDraftAutomation(
    structure: Structure,
    occupancySensor: DeviceViewModel,
    lights: List<DeviceViewModel>,
    googleTV: DeviceViewModel
  ): DraftAutomation {
    return automation {
      name = "Deter intruders"
      description = "When motion is detected, broadcast alarm and turn on lights and TV to deter intruders."
      isActive = true

      sequential {
        select {
          starter(
            occupancySensor.device,
            OccupancySensorDevice,
            OccupancySensing
          )
          manualStarter()
        }

        // Broadcast alarm first (sequential)
        action(structure) {
          command(com.google.home.google.AssistantBroadcast.broadcast("ALARM! ALARM! ALARM!"))
        }

        // Turn on all lights (sequential, not parallel)
        for (light in lights) {
          action(light.device, light.type.value.factory) {
            command(OnOff.on())
          }
        }

        // Turn on TV (sequential)
        action(googleTV.device, googleTV.type.value.factory) {
          command(OnOff.on())
        }
      }
    }
  }

  /**
   * This automation intentionally lacks a manualStarter() and as such cannot be manually triggered.
   * Using a device type `lock` as a starter requires pin-verification which cannot be simulated.
   **/
  private fun createLightAndThermostatDraftAutomation(
    doorLock: DeviceViewModel,
    thermostat: DeviceViewModel,
    lights: List<DeviceViewModel>,
  ): DraftAutomation {
    return automation {
      name = "Turn on lights and thermostat"
      description = "Turn on lights and set thermostat to Auto mode when the door is unlocked."
      isActive = true

      sequential {
        val doorLockStarter = starter(doorLock.device, doorLock.type.value.factory, DoorLock)

        condition {
          expression = doorLockStarter.lockState equals DoorLockTrait.DlLockState.Unlocked
        }
        parallel {
          // Turn on each light
          for (light in lights) {
            action(light.device, light.type.value.factory) {
              command(OnOff.on())
            }
          }

          // Set thermostat to auto mode
          action(thermostat.device, thermostat.type.value.factory) {
            command(SimplifiedThermostat.setSystemMode(SimplifiedThermostatTrait.SystemModeEnum.Auto))
          }
        }
      }
    }
  }

  /**
   * Creates a draft automation that turns on a light when the camera detects a person,
   * using a natural language query. Kept generic and location-independent (no
   * "in yard"/"on counter" specifics) so it's reliably testable on any camera regardless
   * of where it's physically installed. No Familiar Face usage (see b/500710336 scope).
   */
  private fun createCameraSceneDetectedLightDraftAutomation(
    camera: DeviceViewModel,
    light: DeviceViewModel,
  ): DraftAutomation {
    return automation {
      name = "Camera Natural Language Scene Alert"
      description = "Turns on the light when a person is detected by the camera."
      isActive = true

      sequential {
        select {
          // You can customize this query string using natural language
          // (e.g., "the dog is on the couch", "person detected in yard") -
          // kept generic here so the predefined template is testable on any camera.
          starter(
            camera.device,
            camera.type.value.factory,
            VideoAnalysis.QueryMatchedEvent
          ) {
            parameter(VideoAnalysis.QueryMatchedEvent.query("a person is detected"))
          }
          manualStarter()
        }

        action(light.device, light.type.value.factory) {
          command(OnOff.on())
        }
      }
    }
  }

  private fun getOnOffCapableLights(deviceVMs: List<DeviceViewModel>): List<DeviceViewModel> {
    return deviceVMs.filter { vm ->
      val factory = vm.type.value.factory
      factory == OnOffLightDevice ||
        factory == DimmableLightDevice ||
        factory == ColorTemperatureLightDevice ||
        factory == ExtendedColorLightDevice
    }
  }
}