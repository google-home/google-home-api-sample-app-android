
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
import com.example.googlehomeapisampleapp.MainActivity
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.google.home.Trait
import com.google.home.TraitFactory
import com.google.home.automation.Command
import com.google.home.automation.DraftAutomation
import com.google.home.automation.TypedExpression
import com.google.home.automation.automation
import com.google.home.automation.equals
import com.google.home.automation.greaterThan
import com.google.home.automation.greaterThanOrEquals
import com.google.home.automation.lessThan
import com.google.home.automation.lessThanOrEquals
import com.google.home.automation.notEquals
import com.google.home.google.SimplifiedThermostat
import com.google.home.google.SimplifiedThermostatTrait
import com.google.home.matter.standard.BooleanState
import com.google.home.matter.standard.BooleanState.Companion.stateValue
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.LevelControl.Companion.currentLevel
import com.google.home.matter.standard.LevelControlTrait
import com.google.home.matter.standard.OccupancySensing
import com.google.home.matter.standard.OccupancySensing.Companion.occupancy
import com.google.home.matter.standard.OccupancySensingTrait
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOff.Companion.onOff
import com.google.home.matter.standard.Thermostat
import com.google.home.matter.standard.Thermostat.Companion.systemMode
import com.google.home.matter.standard.ThermostatTrait
import kotlinx.coroutines.flow.MutableStateFlow

class DraftViewModel (
    val candidateVM: CandidateViewModel? = null,
    private val presetDraft: DraftAutomation? = null,
    val isLocked: Boolean = false,
) : ViewModel() {

    private val DRAFT_NAME: String = "New Automation"
    private val DRAFT_DESCRIPTION: String = "New custom automation"

    val name : MutableStateFlow<String>
    val description : MutableStateFlow<String>

    val starterVMs : MutableStateFlow<MutableList<StarterViewModel>>
    val actionVMs : MutableStateFlow<MutableList<ActionViewModel>>

    val selectedStarterVM: MutableStateFlow<StarterViewModel?>
    val selectedActionVM: MutableStateFlow<ActionViewModel?>

    init {
        name = MutableStateFlow(presetDraft?.name ?: (candidateVM?.name ?: DRAFT_NAME))
        description = MutableStateFlow(presetDraft?.description ?: (candidateVM?.description ?: DRAFT_DESCRIPTION))

        starterVMs = MutableStateFlow(mutableListOf())
        actionVMs = MutableStateFlow(mutableListOf())

        selectedStarterVM = MutableStateFlow(null)
        selectedActionVM = MutableStateFlow(null)

        if (candidateVM != null)
            parseCandidateVM(candidateVM)
    }

    fun parseCandidateVM (candidateVM: CandidateViewModel) {
        when (candidateVM.type) {
            CandidateViewModel.CandidateType.CommandCandidate -> {
                val actionVM = ActionViewModel(candidateVM)
                actionVMs.value.add(actionVM)
            }
            else -> {
                // Unsupported Candidate Type.
            }
        }
    }

    fun getDraftAutomation() : DraftAutomation {
        presetDraft?.let { return it }
        val name: String = name.value
        val description: String = description.value

        val starterVMs: List<StarterViewModel> = starterVMs.value
        val actionVMs: List<ActionViewModel> = actionVMs.value

        return automation {
            this.name = name
            this.description = description
            this.isActive = true
            // The sequential block wrapping all nodes:
            sequential {
                // The select block wrapping all starters:
                select {
                    // Iterate through the selected starters:
                    for (starterVM in starterVMs) {
                        // The sequential block for each starter (should wrap the Starter Expression!)
                        sequential {
                            val starterDeviceVM: DeviceViewModel = starterVM.deviceVM.value!!
                            val starterTrait: TraitFactory<out Trait> = starterVM.trait.value!!
                            val starterOperation: StarterViewModel.Operation = starterVM.operation.value!!
                            // The Starter Expression that the DSL will check for:
                            val starterExpression : TypedExpression<out Trait> = starter<_>(
                                starterDeviceVM.device,
                                starterDeviceVM.type.value.factory,
                                starterVM.trait.value!!)

                            when (starterTrait) {
                                OnOff -> {
                                    val onOffValue: Boolean = starterVM.valueOnOff.value
                                    val onOffExpression: TypedExpression<out OnOff> =
                                        starterExpression as TypedExpression<out OnOff>
                                    when (starterOperation) {
                                        StarterViewModel.Operation.EQUALS ->
                                            condition { expression = onOffExpression.onOff equals onOffValue }
                                        StarterViewModel.Operation.NOT_EQUALS ->
                                            condition { expression = onOffExpression.onOff notEquals onOffValue }
                                        else -> { MainActivity.showError(this, "Unexpected operation for OnOff") }
                                    }
                                }
                                LevelControl -> {
                                    val levelValue: UByte = starterVM.valueLevel.value
                                    val levelExpression : TypedExpression<out LevelControl> =
                                        starterExpression as TypedExpression<out LevelControl>
                                    when (starterOperation) {
                                        StarterViewModel.Operation.GREATER_THAN ->
                                            condition { expression = levelExpression.currentLevel greaterThan levelValue }
                                        StarterViewModel.Operation.GREATER_THAN_OR_EQUALS ->
                                            condition { expression = levelExpression.currentLevel greaterThanOrEquals levelValue }
                                        StarterViewModel.Operation.LESS_THAN ->
                                            condition { expression = levelExpression.currentLevel lessThan levelValue }
                                        StarterViewModel.Operation.LESS_THAN_OR_EQUALS ->
                                            condition { expression = levelExpression.currentLevel lessThanOrEquals levelValue }
                                        else -> { MainActivity.showError(this, "Unexpected operation for LevelControl") }
                                    }
                                }
                                BooleanState -> {
                                    val booleanStateValue: Boolean = starterVM.valueBooleanState.value
                                    val booleanStateExpression: TypedExpression<out BooleanState> =
                                        starterExpression as TypedExpression<out BooleanState>
                                    when (starterOperation) {
                                        StarterViewModel.Operation.EQUALS ->
                                            condition { expression = booleanStateExpression.stateValue equals booleanStateValue }
                                        StarterViewModel.Operation.NOT_EQUALS ->
                                            condition { expression = booleanStateExpression.stateValue notEquals booleanStateValue }
                                        else -> { MainActivity.showError(this, "Unexpected operation for BooleanState") }
                                    }
                                }
                                OccupancySensing -> {
                                    val occupancyValue: OccupancySensingTrait.OccupancyBitmap = starterVM.valueOccupancy.value
                                    val occupancyExpression: TypedExpression<out OccupancySensing> =
                                        starterExpression as TypedExpression<out OccupancySensing>
                                    when (starterOperation) {
                                        StarterViewModel.Operation.EQUALS ->
                                            condition { expression = occupancyExpression.occupancy equals occupancyValue }
                                        StarterViewModel.Operation.NOT_EQUALS ->
                                            condition { expression =  occupancyExpression.occupancy notEquals occupancyValue }
                                        else -> { MainActivity.showError(this, "Unexpected operation for Occupancy") }
                                    }
                                }
                                Thermostat -> {
                                    val thermostatValue: ThermostatTrait.SystemModeEnum = starterVM.valueThermostat.value
                                    val thermostatExpression: TypedExpression<out Thermostat> =
                                        starterExpression as TypedExpression<out Thermostat>
                                    when (starterOperation) {
                                        StarterViewModel.Operation.EQUALS ->
                                            condition { expression = thermostatExpression.systemMode equals thermostatValue }
                                        StarterViewModel.Operation.NOT_EQUALS ->
                                            condition { expression = thermostatExpression.systemMode notEquals thermostatValue }
                                        else -> { MainActivity.showError(this, "Unexpected operation for Thermostat") }
                                    }
                                }
                                else -> { MainActivity.showError(this, "Unsupported starter for automation creation") }
                            }
                        }
                    }
                    // Function to allow manual execution of the automation:
                    manualStarter()
                }
                // Parallel block wrapping all actions:
                parallel {
                    // Iterate through the selected actions:
                    for (actionVM in actionVMs) {
                        val actionDeviceVM: DeviceViewModel = actionVM.deviceVM.value!!
                        // Action Expression that the DSL will check for:
                        action(actionDeviceVM.device, actionDeviceVM.type.value.factory) {

                            val actionCommand: Command = when (actionVM.action.value) {
                                ActionViewModel.Action.ON -> { OnOff.on() }
                                ActionViewModel.Action.OFF -> { OnOff.off() }
                                ActionViewModel.Action.MOVE_TO_LEVEL -> {
                                    LevelControl.moveToLevelWithOnOff(
                                        actionVM.valueLevel.value!!,
                                        0u,
                                        LevelControlTrait.OptionsBitmap(),
                                        LevelControlTrait.OptionsBitmap()
                                    )
                                }
                                ActionViewModel.Action.MODE_HEAT -> { SimplifiedThermostat
                                    .setSystemMode(SimplifiedThermostatTrait.SystemModeEnum.Heat) }
                                ActionViewModel.Action.MODE_COOL -> { SimplifiedThermostat
                                    .setSystemMode(SimplifiedThermostatTrait.SystemModeEnum.Cool) }
                                ActionViewModel.Action.MODE_OFF -> { SimplifiedThermostat
                                    .setSystemMode(SimplifiedThermostatTrait.SystemModeEnum.Off) }
                                else -> {
                                    MainActivity.showError(this, "Unexpected operation")
                                    return@action
                                }
                            }
                            command(actionCommand)
                        }
                    }
                }
            }
        }
    }
}