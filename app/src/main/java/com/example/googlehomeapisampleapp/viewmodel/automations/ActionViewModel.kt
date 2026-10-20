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

package com.example.googlehomeapisampleapp.viewmodel.automations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.google.home.CommandDescriptor
import com.google.home.DeviceType
import com.google.home.Trait
import com.google.home.TraitFactory
import com.google.home.annotation.HomeExperimentalApi
import com.google.home.automation.CommandCandidate
import com.google.home.matter.standard.FanControl
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.LevelControlTrait
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOffTrait
import com.google.home.matter.standard.Thermostat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

class ActionViewModel(candidateVM: CandidateViewModel? = null) : ViewModel() {

  // List of operations available when creating automation starters:
  enum class Action {
    ON,
    OFF,
    MOVE_TO_LEVEL,
    MODE_HEAT,
    MODE_COOL,
    MODE_OFF,
    FAN_OFF,
    FAN_LOW,
    FAN_MEDIUM,
    FAN_HIGH,
  }

  open class Actions(val actions: List<Action>)

  val name: MutableStateFlow<String?> = MutableStateFlow(null)
  val description: MutableStateFlow<String?> = MutableStateFlow(null)

  // Initialize containers for action attributes:
  val deviceVM: MutableStateFlow<DeviceViewModel?> = MutableStateFlow(null)
  val trait: MutableStateFlow<Trait?> = MutableStateFlow(null)
  val action: MutableStateFlow<Action?> = MutableStateFlow(null)

  val valueLevel: MutableStateFlow<UByte?> = MutableStateFlow(50u)

  init {

    if (candidateVM != null)
      parseCandidateVM(candidateVM)

    // Subscribe to changes on dynamic values:
    viewModelScope.launch { subscribeToDevice() }
    viewModelScope.launch { subscribeToTrait() }
  }

  private suspend fun subscribeToDevice() {
    // Subscribe to device selection, to automatically determine the name of the action:
    deviceVM.collect { deviceVM ->
      val deviceType: DeviceType? = deviceVM?.type?.value
      name.emit(deviceType.toString())
    }
  }

  private suspend fun subscribeToTrait() {
    // Subscribe to trait selection, to automatically determine the description of the action:
    trait.collect { trait ->
      description.emit(trait?.factory.toString())
    }
  }

  @OptIn(HomeExperimentalApi::class)
  private fun parseCandidateVM(candidateVM: CandidateViewModel) {
    viewModelScope.launch {
      val candidate: CommandCandidate = candidateVM.candidate as CommandCandidate
      deviceVM.emit(candidateVM.deviceVM)
      val candidateTrait =
        candidateVM.deviceVM?.traits?.mapNotNull { allDeviceTraits -> allDeviceTraits.firstOrNull() }
      trait.emit(candidateTrait?.firstOrNull())
      action.emit(commandMap[candidate.commandDescriptor])
    }
  }

  companion object {

    // List of operations available when comparing booleans:
    object OnOffActions : Actions(
      listOf(
        Action.ON,
        Action.OFF,
      )
    )

    // List of operations available when comparing booleans:
    object LevelActions : Actions(
      listOf(
        Action.MOVE_TO_LEVEL
      )
    )

    // List of operations available when comparing booleans:
    object ThermostatActions : Actions(
      listOf(
        Action.MODE_HEAT,
        Action.MODE_COOL,
        Action.MODE_OFF,
      )
    )

    // List of actions available for FanControl trait:
    object FanControlActions : Actions(
      listOf(
        Action.FAN_OFF,
        Action.FAN_LOW,
        Action.FAN_MEDIUM,
        Action.FAN_HIGH,
      )
    )

    // Map traits and the actions they support:
    val actionActions: Map<TraitFactory<out Trait>, Actions> = mapOf(
      OnOff to OnOffActions,
      LevelControl to LevelActions,
      // BooleanState - No Actions
      // OccupancySensing - No Actions
      Thermostat to ThermostatActions,
      FanControl to FanControlActions,
    )

    // Map of supported commands from Discovery API:
    @OptIn(HomeExperimentalApi::class) val commandMap: Map<CommandDescriptor, Action> = mapOf(
      OnOffTrait.OnCommand to Action.ON,
      OnOffTrait.OffCommand to Action.OFF,
      LevelControlTrait.MoveToLevelWithOnOffCommand to Action.MOVE_TO_LEVEL
    )
  }
}
