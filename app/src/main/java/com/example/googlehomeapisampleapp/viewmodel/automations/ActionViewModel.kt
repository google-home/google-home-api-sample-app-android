
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
import com.google.home.automation.CommandCandidate
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.LevelControlTrait
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOffTrait
import com.google.home.matter.standard.Thermostat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ActionViewModel (val candidateVM: CandidateViewModel? = null) : ViewModel() {

    // List of operations available when creating automation starters:
    enum class Action {
        ON,
        OFF,
        MOVE_TO_LEVEL,
        MODE_HEAT,
        MODE_COOL,
        MODE_OFF,
    }

    open class Actions (val actions : List<Action>)

    val name: MutableStateFlow<String?>
    val description: MutableStateFlow<String?>

    val deviceVM: MutableStateFlow<DeviceViewModel?>
    val trait: MutableStateFlow<Trait?>
    val action: MutableStateFlow<Action?>

    val valueLevel: MutableStateFlow<UByte?>

    init {
        // Initialize containers for name and description:
        name = MutableStateFlow(null)
        description = MutableStateFlow(null)
        // Initialize containers for action attributes:
        deviceVM = MutableStateFlow(null)
        trait = MutableStateFlow(null)
        action = MutableStateFlow(null)

        valueLevel = MutableStateFlow(50u)

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

    private fun parseCandidateVM(candidateVM: CandidateViewModel) {
        viewModelScope.launch {
            val candidate: CommandCandidate = candidateVM.candidate as CommandCandidate
            deviceVM.emit(candidateVM.deviceVM)
            trait.emit(candidateVM.deviceVM?.device?.trait(candidate.trait)?.first())
            action.emit(commandMap.get(candidate.commandDescriptor))
        }
    }

    companion object {

        // List of operations available when comparing booleans:
        object OnOffActions : Actions(listOf(
            Action.ON,
            Action.OFF,
        ))

        // List of operations available when comparing booleans:
        object LevelActions : Actions(listOf(
            Action.MOVE_TO_LEVEL
        ))

        // List of operations available when comparing booleans:
        object ThermostatActions : Actions(listOf(
            Action.MODE_HEAT,
            Action.MODE_COOL,
            Action.MODE_OFF,
        ))

        // Map traits and the comparison operations they support:
        val actionActions: Map<TraitFactory<out Trait>, Actions> = mapOf(
            OnOff to OnOffActions,
            LevelControl to LevelActions,
            // BooleanState - No Actions
            // OccupancySensing - No Actions
            Thermostat to ThermostatActions,
        )

        // Map of supported commands from Discovery API:
        val commandMap: Map<CommandDescriptor, Action> = mapOf(
// TODO: 5.1.2 - Set current supported commands
//             OnOffTrait.OnCommand to Action.ON,
//             OnOffTrait.OffCommand to Action.OFF,
//             LevelControlTrait.MoveToLevelWithOnOffCommand to Action.MOVE_TO_LEVEL
        )
    }
}
