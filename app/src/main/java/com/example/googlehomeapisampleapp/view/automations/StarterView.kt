
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

package com.example.googlehomeapisampleapp.view.automations

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.googlehomeapisampleapp.R
import com.example.googlehomeapisampleapp.view.devices.LevelSlider
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.DraftViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.StarterViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.StructureViewModel
import com.google.home.DeviceType
import com.google.home.Trait
import com.google.home.TraitFactory
import com.google.home.matter.standard.BooleanState
import com.google.home.matter.standard.ContactSensorDevice
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.OccupancySensing
import com.google.home.matter.standard.OccupancySensingTrait
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.Thermostat
import com.google.home.matter.standard.ThermostatTrait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun StarterView (homeAppVM: HomeAppViewModel) {
    val scope: CoroutineScope = rememberCoroutineScope()
    // Selected DraftViewModel and StarterViewModel on screen to select a starter:
    val draftVM: DraftViewModel = homeAppVM.selectedDraftVM.collectAsState().value!!
    val starterVM: StarterViewModel = draftVM.selectedStarterVM.collectAsState().value!!
    val starterVMs: List<StarterViewModel> = draftVM.starterVMs.collectAsState().value
    // Selected StructureViewModel and DeviceViewModels to provide options:
    val structureVM: StructureViewModel = homeAppVM.selectedStructureVM.collectAsState().value!!
    val deviceVMs: List<DeviceViewModel> = structureVM.deviceVMs.collectAsState().value
    // Selected starter attributes for StarterView on screen:
    val starterDeviceVM: MutableState<DeviceViewModel?> = remember {
        mutableStateOf(starterVM.deviceVM.value) }
    val starterType: MutableState<DeviceType?> = remember {
        mutableStateOf(starterVM.deviceVM.value?.type?.value) }
    val starterTrait: MutableState<TraitFactory<out Trait>?> = remember {
        mutableStateOf(starterVM.trait.value) }
    val starterOperation: MutableState<StarterViewModel.Operation?> = remember {
        mutableStateOf(starterVM.operation.value) }
    // Selected starter values for StarterView on screen:
    val starterValueOnOff: MutableState<Boolean?> = remember {
        mutableStateOf(starterVM.valueOnOff.value) }
    val starterValueLevel: MutableState<UByte?> = remember {
        mutableStateOf(starterVM.valueLevel.value) }
    val starterValueBooleanState: MutableState<Boolean?> = remember {
        mutableStateOf(starterVM.valueBooleanState.value) }
    val starterValueOccupancy: MutableState<OccupancySensingTrait.OccupancyBitmap?> = remember {
        mutableStateOf(starterVM.valueOccupancy.value) }
    val starterValueThermostat: MutableState<ThermostatTrait.SystemModeEnum?> = remember {
        mutableStateOf(starterVM.valueThermostat.value) }
    // Variables to track UI state for dropdown views:
    var expandedDeviceSelection: Boolean by remember { mutableStateOf(false) }
    var expandedTraitSelection: Boolean by remember { mutableStateOf(false) }
    var expandedOperationSelection: Boolean by remember { mutableStateOf(false) }
    var expandedBooleanSelection: Boolean by remember { mutableStateOf(false) }
    var expandedOccupancySelection: Boolean by remember { mutableStateOf(false) }
    var expandedThermostatSelection: Boolean by remember { mutableStateOf(false) }

    // Back action for closing view:
    BackHandler {
        scope.launch { draftVM.selectedStarterVM.emit(null) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            Spacer(Modifier.height(64.dp))

            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Text(text = stringResource(R.string.starter_title_select), fontSize = 32.sp)
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                Text(stringResource(R.string.starter_title_device), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            TextButton(onClick = { expandedDeviceSelection = true }) {
                val selectedDeviceName = starterDeviceVM.value?.name?.collectAsState()?.value
                Text(text = (selectedDeviceName ?: stringResource(R.string.starter_text_select)) + " ▾", fontSize = 32.sp)
            }

            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                Box {
                    DropdownMenu(expanded = expandedDeviceSelection, onDismissRequest = { expandedDeviceSelection = false }) {
                        for (deviceVM in deviceVMs) {
                            val deviceName by deviceVM.name.collectAsState()
                            DropdownMenuItem(
                                text = { Text(deviceName) },
                                onClick = {
                                    scope.launch {
                                        starterDeviceVM.value = deviceVM
                                        starterType.value = deviceVM.type.value
                                        starterTrait.value = null
                                        starterOperation.value = null
                                    }
                                    expandedDeviceSelection = false
                                }
                            )
                        }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                Text(stringResource(R.string.starter_title_trait), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            TextButton(onClick = { expandedTraitSelection = true }, enabled = starterDeviceVM.value != null) {
                Text(text = (starterTrait.value?.toString() ?: stringResource(R.string.starter_text_select)) + " ▾", fontSize = 32.sp)
            }

            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                Box {
                    DropdownMenu(expanded = expandedTraitSelection, onDismissRequest = { expandedTraitSelection = false }) {
                        val deviceTraits = starterDeviceVM.value?.traits?.collectAsState()?.value!!
                        for (trait in deviceTraits) {
                            DropdownMenuItem(
                                text = { Text(trait.factory.toString()) },
                                onClick = {
                                    scope.launch {
                                        starterTrait.value = trait.factory
                                        starterOperation.value = null
                                    }
                                    expandedTraitSelection = false
                                }
                            )
                        }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                Text(stringResource(R.string.starter_title_operation), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            TextButton(onClick = { expandedOperationSelection = true }, enabled = starterTrait.value != null) {
                Text(text = (starterOperation.value?.toString() ?: stringResource(R.string.starter_text_select)) + " ▾", fontSize = 32.sp)
            }

            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                Box {
                    DropdownMenu(expanded = expandedOperationSelection, onDismissRequest = { expandedOperationSelection = false }) {
                        // ...
                        if (!StarterViewModel.starterOperations.containsKey(starterTrait.value))
                            return@DropdownMenu

                        val operations: List<StarterViewModel.Operation> = StarterViewModel.starterOperations.get(starterTrait.value ?: OnOff)?.operations!!
                        for (operation in operations) {
                            DropdownMenuItem(
                                text = { Text(operation.toString()) },
                                onClick = {
                                    scope.launch {
                                        starterOperation.value = operation
                                    }
                                    expandedOperationSelection = false
                                }
                            )
                        }
                    }
                }
            }

            if (starterTrait.value != null) {
                Column (Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                    Text(stringResource(R.string.starter_title_value), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            when (starterTrait.value) {
                OnOff -> {
                    TextButton(onClick = { expandedBooleanSelection = true }, enabled = true) {
                        val state: String? = StarterViewModel.valuesOnOff.filter { it.value == starterValueOnOff.value!! }.keys.firstOrNull()?.toString()
                        Text(text = (state ?: stringResource(R.string.starter_text_select)) + " ▾", fontSize = 32.sp)
                    }

                    Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                        Box {
                            DropdownMenu(expanded = expandedBooleanSelection, onDismissRequest = { expandedBooleanSelection = false }) {
                                for (value in StarterViewModel.valuesOnOff.keys) {
                                    DropdownMenuItem(
                                        text = { Text(value.toString()) },
                                        onClick = {
                                            scope.launch {
                                                starterValueOnOff.value = StarterViewModel.valuesOnOff.get(value)
                                            }
                                            expandedBooleanSelection = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                LevelControl -> {
                    Box (Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        LevelSlider(value = starterValueLevel.value?.toFloat()!!, low = 0f, high = 254f, steps = 0,
                                    modifier = Modifier.padding(top = 16.dp),
                                    onValueChangeFinished = { value : Float -> starterValueLevel.value = value.toUInt().toUByte() },
                                    isEnabled = true
                        )
                    }
                }
                BooleanState -> {
                    // BooleanState is special, where the state gains meaning based on the device type:
                    when (starterType.value?.factory) {
                        ContactSensorDevice -> {
                            TextButton(onClick = { expandedBooleanSelection = true }, enabled = true) {
                                val state: String? = StarterViewModel.valuesContact.filter { it.value == starterValueBooleanState.value!! }.keys.firstOrNull()?.toString()
                                Text(text = (state ?: stringResource(R.string.starter_text_select)) + " ▾", fontSize = 32.sp)
                            }

                            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                                Box {
                                    DropdownMenu(expanded = expandedBooleanSelection, onDismissRequest = { expandedBooleanSelection = false }) {
                                        for (value in StarterViewModel.valuesContact.keys) {
                                            DropdownMenuItem(
                                                text = { Text(value.toString()) },
                                                onClick = {
                                                    scope.launch {
                                                        starterValueBooleanState.value = StarterViewModel.valuesContact.get(value)
                                                    }
                                                    expandedBooleanSelection = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        else -> {  } // Raw state value for BooleanState (True/False)
                    }
                }
                OccupancySensing -> {
                    TextButton(onClick = { expandedOccupancySelection = true }, enabled = true) {
                        val state: String? = StarterViewModel.valuesOccupancy.filter { it.value == starterValueOccupancy.value!! }.keys.firstOrNull()?.toString()
                        Text(text = (state ?: stringResource(R.string.starter_text_select)) + " ▾", fontSize = 32.sp)
                    }

                    Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                        Box {
                            DropdownMenu(expanded = expandedOccupancySelection, onDismissRequest = { expandedOccupancySelection = false }) {
                                for (value in StarterViewModel.valuesOccupancy.keys) {
                                    DropdownMenuItem(
                                        text = { Text(value.toString()) },
                                        onClick = {
                                            scope.launch {
                                                starterValueOccupancy.value = StarterViewModel.valuesOccupancy.get(value)
                                            }
                                            expandedOccupancySelection = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Thermostat -> {
                    TextButton(onClick = { expandedThermostatSelection = true }, enabled = true) {
                        val state: String? = StarterViewModel.valuesThermostat.filter { it.value == starterValueThermostat.value!! }.keys.firstOrNull()?.toString()
                        Text(text = (state ?: stringResource(R.string.starter_text_select)) + " ▾", fontSize = 32.sp)
                    }

                    Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                        Box {
                            DropdownMenu(expanded = expandedThermostatSelection, onDismissRequest = { expandedThermostatSelection = false }) {
                                for (value in StarterViewModel.valuesThermostat.keys) {
                                    DropdownMenuItem(
                                        text = { Text(value.toString()) },
                                        onClick = {
                                            scope.launch {
                                                starterValueThermostat.value = StarterViewModel.valuesThermostat.get(value)
                                            }
                                            expandedThermostatSelection = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // Buttons to save changes for the starter on draft automation:
        Column(modifier = Modifier.padding(16.dp).align(Alignment.BottomCenter)) {
            // Check on whether all options are selected:
            val isOptionsSelected: Boolean =
                        starterDeviceVM.value != null &&
                        starterTrait.value != null &&
                        starterOperation.value != null
            // Check on whether at least one value provided:
            val isValueProvided: Boolean =
                        starterValueOnOff.value != null ||
                        starterValueLevel.value != null

            if (starterVMs.contains(starterVM)) {
                // Update starter button:
                Button(
                    enabled = isOptionsSelected && isValueProvided,
                    onClick = {
                        scope.launch {
                            starterVM.deviceVM.emit(starterDeviceVM.value)
                            starterVM.trait.emit(starterTrait.value)
                            starterVM.operation.emit(starterOperation.value)
                            starterVM.valueOnOff.emit(starterValueOnOff.value!!)
                            starterVM.valueLevel.emit(starterValueLevel.value!!)
                            starterVM.valueBooleanState.emit(starterValueBooleanState.value!!)
                            starterVM.valueOccupancy.emit(starterValueOccupancy.value!!)
                            starterVM.valueThermostat.emit(starterValueThermostat.value!!)

                            draftVM.selectedStarterVM.emit(null)
                        }
                    })
                { Text(stringResource(R.string.starter_button_update)) }
                // Remove starter button:
                Button(
                    enabled = true,
                    onClick = {
                        scope.launch {
                            draftVM.starterVMs.value.remove(starterVM)
                            draftVM.selectedStarterVM.emit(null)
                        }
                    })
                { Text(stringResource(R.string.starter_button_remove)) }
            } else {
                // Save starter button:
                Button(
                    enabled = isOptionsSelected && isValueProvided,
                    onClick = {
                        scope.launch {
                            starterVM.deviceVM.emit(starterDeviceVM.value)
                            starterVM.trait.emit(starterTrait.value)
                            starterVM.operation.emit(starterOperation.value)
                            starterVM.valueOnOff.emit(starterValueOnOff.value!!)
                            starterVM.valueLevel.emit(starterValueLevel.value!!)
                            starterVM.valueBooleanState.emit(starterValueBooleanState.value!!)
                            starterVM.valueOccupancy.emit(starterValueOccupancy.value!!)
                            starterVM.valueThermostat.emit(starterValueThermostat.value!!)

                            draftVM.starterVMs.value.add(starterVM)
                            draftVM.selectedStarterVM.emit(null)
                        }
                    })
                { Text(stringResource(R.string.starter_button_create)) }
            }
        }
    }
}
