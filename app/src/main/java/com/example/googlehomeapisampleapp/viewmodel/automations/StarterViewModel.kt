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
import com.google.home.DeviceType
import com.google.home.Trait
import com.google.home.TraitFactory
import com.google.home.matter.standard.BooleanState
import com.google.home.matter.standard.FanControl
import com.google.home.matter.standard.FanControlTrait
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.OccupancySensing
import com.google.home.matter.standard.OccupancySensingTrait
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.Thermostat
import com.google.home.matter.standard.ThermostatTrait
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class StarterViewModel() : ViewModel() {

  // List of operations available when creating automation starters:
  enum class Operation {
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    GREATER_THAN_OR_EQUALS,
    LESS_THAN,
    LESS_THAN_OR_EQUALS
  }

  open class Operations(val operations: List<Operation>)

  val name: MutableStateFlow<String?> = MutableStateFlow(null)
  val description: MutableStateFlow<String?> = MutableStateFlow(null)

  // Initialize containers for starter attributes:
  val deviceVM: MutableStateFlow<DeviceViewModel?> = MutableStateFlow(null)
  val trait: MutableStateFlow<TraitFactory<out Trait>?> = MutableStateFlow(null)
  val operation: MutableStateFlow<Operation?> = MutableStateFlow(null)

  // Initialize containers for potential starter value:
  val valueOnOff: MutableStateFlow<Boolean> = MutableStateFlow(true)
  val valueLevel: MutableStateFlow<UByte> = MutableStateFlow(50u)
  val valueBooleanState: MutableStateFlow<Boolean> = MutableStateFlow(true)
  val valueOccupancy: MutableStateFlow<OccupancySensingTrait.OccupancyBitmap> =
    MutableStateFlow(OccupancySensingTrait.OccupancyBitmap())
  val valueThermostat: MutableStateFlow<ThermostatTrait.SystemModeEnum> =
    MutableStateFlow(ThermostatTrait.SystemModeEnum.Off)
  val valueFanMode: MutableStateFlow<FanControlTrait.FanModeEnum> =
    MutableStateFlow(FanControlTrait.FanModeEnum.Off)

  init {

    // Subscribe to changes on dynamic values:
    viewModelScope.launch { subscribeToDevice() }
    viewModelScope.launch { subscribeToTrait() }
  }

  private suspend fun subscribeToDevice() {
    // Subscribe to device selection, to automatically determine the name of the starter:
    deviceVM.collect { deviceVM ->
      val deviceType: DeviceType? = deviceVM?.type?.value
      name.emit(deviceType.toString())
    }
  }

  private suspend fun subscribeToTrait() {
    // Subscribe to trait selection, to automatically determine the description of the starter:
    trait.collect { trait ->
      description.emit(trait?.factory.toString())
    }
  }

  companion object {

    // List of operations available when comparing booleans:
    object BooleanOperations : Operations(
      listOf(
        Operation.EQUALS,
        Operation.NOT_EQUALS
      )
    )

    // List of operations available when comparing booleans:
    object OccupancyOperations : Operations(
      listOf(
        Operation.EQUALS,
        Operation.NOT_EQUALS
      )
    )

    // List of operations available when comparing values:
    object LevelOperations : Operations(
      listOf(
        Operation.GREATER_THAN,
        Operation.GREATER_THAN_OR_EQUALS,
        Operation.LESS_THAN,
        Operation.LESS_THAN_OR_EQUALS
      )
    )

    // List of operations available when comparing fan modes (enum comparison):
    object FanModeOperations : Operations(
      listOf(
        Operation.EQUALS,
        Operation.NOT_EQUALS
      )
    )

    // Map traits and the comparison operations they support:
    val starterOperations: Map<TraitFactory<out Trait>, Operations> = mapOf(
      OnOff to BooleanOperations,
      LevelControl to LevelOperations,
      BooleanState to BooleanOperations,
      OccupancySensing to OccupancyOperations,
      Thermostat to BooleanOperations,
      FanControl to FanModeOperations,
    )

    enum class OnOffValue {
      On,
      Off,
    }

    val valuesOnOff: Map<OnOffValue, Boolean> = mapOf(
      OnOffValue.On to true,
      OnOffValue.Off to false,
    )

    enum class ContactValue {
      Open,
      Closed,
    }

    val valuesContact: Map<ContactValue, Boolean> = mapOf(
      ContactValue.Closed to true,
      ContactValue.Open to false,
    )

    enum class OccupancyValue {
      Occupied,
      NotOccupied,
    }

    val valuesOccupancy: Map<OccupancyValue, OccupancySensingTrait.OccupancyBitmap?> = mapOf(
      OccupancyValue.Occupied to OccupancySensingTrait.OccupancyBitmap(true),
      OccupancyValue.NotOccupied to OccupancySensingTrait.OccupancyBitmap(false),
    )

    enum class ThermostatValue {
      Heat,
      Cool,
      Off,
    }

    val valuesThermostat: Map<ThermostatValue, ThermostatTrait.SystemModeEnum> = mapOf(
      ThermostatValue.Heat to ThermostatTrait.SystemModeEnum.Heat,
      ThermostatValue.Cool to ThermostatTrait.SystemModeEnum.Cool,
      ThermostatValue.Off to ThermostatTrait.SystemModeEnum.Off,
    )

    enum class FanModeValue {
      Off,
      Low,
      Medium,
      High,
    }

    val valuesFanMode: Map<FanModeValue, FanControlTrait.FanModeEnum> = mapOf(
      FanModeValue.Off to FanControlTrait.FanModeEnum.Off,
      FanModeValue.Low to FanControlTrait.FanModeEnum.Low,
      FanModeValue.Medium to FanControlTrait.FanModeEnum.Medium,
      FanModeValue.High to FanControlTrait.FanModeEnum.High,
    )
  }
}