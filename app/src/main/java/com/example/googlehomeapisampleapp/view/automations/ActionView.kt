
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
import com.example.googlehomeapisampleapp.viewmodel.automations.ActionViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.DraftViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.google.home.Trait
import com.google.home.matter.standard.LevelControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ActionView (homeAppVM: HomeAppViewModel) {
    val scope: CoroutineScope = rememberCoroutineScope()
    // Selected DraftViewModel and ActionViewModel on screen to select an action:
    val draftVM: DraftViewModel = homeAppVM.selectedDraftVM.collectAsState().value!!
    val actionVM: ActionViewModel = draftVM.selectedActionVM.collectAsState().value!!
    val actionVMs: List<ActionViewModel> = draftVM.actionVMs.collectAsState().value
    // Selected StructureViewModel and DeviceViewModels to provide options:
    val structureVM = homeAppVM.selectedStructureVM.collectAsState().value!!
    val deviceVMs = structureVM.deviceVMs.collectAsState().value
    // Selected values for ActionView on screen:
    val actionDeviceVM: MutableState<DeviceViewModel?> = remember {
        mutableStateOf(actionVM.deviceVM.value) }
    val actionTrait: MutableState<Trait?> = remember {
        mutableStateOf(actionVM.trait.value) }
    val actionAction: MutableState<ActionViewModel.Action?> = remember {
        mutableStateOf(actionVM.action.value) }
    val actionValueLevel: MutableState<UByte?> = remember {
        mutableStateOf(actionVM.valueLevel.value) }
    // Variables to track UI state for dropdown views:
    var expandedDeviceSelection: Boolean by remember { mutableStateOf(false) }
    var expandedTraitSelection: Boolean by remember { mutableStateOf(false) }
    var expandedActionSelection: Boolean by remember { mutableStateOf(false) }

    // Back action for closing view:
    BackHandler {
        scope.launch { draftVM.selectedActionVM.emit(null) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            Spacer(Modifier.height(64.dp))

            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Text(text = stringResource(R.string.action_title_select), fontSize = 32.sp)
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                Text(stringResource(R.string.action_title_device), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            TextButton(onClick = { expandedDeviceSelection = true }) {
                Text(text = (actionDeviceVM.value?.name ?: stringResource(R.string.action_text_select)) + " ▾", fontSize = 32.sp)
            }

            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                Box {
                    DropdownMenu(expanded = expandedDeviceSelection, onDismissRequest = { expandedDeviceSelection = false }) {
// TODO: 4.2.1 - Action devices selection dropdown
//                         for (deviceVM in deviceVMs) {
//                             DropdownMenuItem(
//                                 text = { Text(deviceVM.name) },
//                                 onClick = {
//                                     scope.launch {
//                                         actionDeviceVM.value = deviceVM
//                                         actionTrait.value = null
//                                         actionAction.value = null
//                                     }
//                                     expandedDeviceSelection = false
//                                 }
//                             )
//                         }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                Text(stringResource(R.string.action_title_trait), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            TextButton(onClick = { expandedTraitSelection = true }, enabled = actionDeviceVM.value != null) {
                Text(text = (actionTrait.value?.factory?.toString() ?: stringResource(R.string.action_text_select)) + " ▾", fontSize = 32.sp)
            }

            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                Box {
                    DropdownMenu(expanded = expandedTraitSelection, onDismissRequest = { expandedTraitSelection = false }) {
// TODO: 4.2.2 - Action device traits selection dropdown
//                         val deviceTraits: List<Trait> = actionDeviceVM.value?.traits?.collectAsState()?.value!!
//                         for (trait in deviceTraits) {
//                             DropdownMenuItem(
//                                 text = { Text(trait.factory.toString()) },
//                                 onClick = {
//                                     scope.launch {
//                                         actionTrait.value = trait
//                                         actionAction.value = null
//                                     }
//                                     expandedTraitSelection = false
//                                 }
//                             )
//                         }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                Text(stringResource(R.string.action_title_command), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            TextButton(onClick = { expandedActionSelection = true }, enabled = actionTrait.value != null) {
                Text(text = (actionAction.value?.toString() ?: stringResource(R.string.action_text_select)) + " ▾", fontSize = 32.sp)
            }

            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                Box {
                    DropdownMenu(expanded = expandedActionSelection, onDismissRequest = { expandedActionSelection = false }) {
                        // ...
                        if (!ActionViewModel.actionActions.containsKey(actionTrait.value?.factory))
                            return@DropdownMenu
// TODO: 4.2.3 - Action device trait actions (commands) selection dropdown
//                         val actions: List<ActionViewModel.Action> = ActionViewModel.actionActions.get(actionTrait.value?.factory)?.actions!!
//                         for (action in actions) {
//                             DropdownMenuItem(
//                                 text = { Text(action.toString()) },
//                                 onClick = {
//                                     scope.launch {
//                                         actionAction.value = action
//                                     }
//                                     expandedActionSelection = false
//                                 }
//                             )
//                         }
                    }
                }
            }

            when (actionTrait.value?.factory) {
                 LevelControl -> {
// TODO: 4.2.4 - Action device trait action(command) values selection widget
//                     Column (Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
//                         Text(stringResource(R.string.action_title_value), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
//                     }
//                     Box (Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
//                         LevelSlider(value = actionValueLevel.value?.toFloat()!!, low = 0f, high = 254f, steps = 0,
//                             modifier = Modifier.padding(top = 16.dp),
//                             onValueChange = { value : Float -> actionValueLevel.value = value.toUInt().toUByte() },
//                             isEnabled = true
//                         )
//                     }

                }
                else -> {  }
            }

        }
        // Buttons to save changes for the action on draft automation:
        Column(modifier = Modifier.padding(16.dp).align(Alignment.BottomCenter)) {
            // Check on whether all options are selected:
            val isOptionsSelected: Boolean =
                        actionDeviceVM.value != null &&
                        actionTrait.value != null &&
                        actionAction.value != null

            if (actionVMs.contains(actionVM)) {
                // Update action button:
                Button(
                    enabled = isOptionsSelected,
                    onClick = {
                        scope.launch {
                            actionVM.deviceVM.emit(actionDeviceVM.value)
                            actionVM.trait.emit(actionTrait.value)
                            actionVM.action.emit(actionAction.value)
                            actionVM.valueLevel.emit(actionValueLevel.value)

                            draftVM.selectedActionVM.emit(null)
                        }
                    })
                { Text(stringResource(R.string.action_button_update)) }
                // Remove action button:
                Button(
                    enabled = true,
                    onClick = {
                        scope.launch {
                            draftVM.actionVMs.value.remove(actionVM)
                            draftVM.selectedActionVM.emit(null)
                        }
                    })
                { Text(stringResource(R.string.action_button_remove)) }
            } else {
                // Save action button:
                Button(
                    enabled = isOptionsSelected,
                    onClick = {
                        scope.launch {
// TODO: 4.2.5 - store all action ViewModel variables into draft ViewModel
//                             actionVM.deviceVM.emit(actionDeviceVM.value)
//                             actionVM.trait.emit(actionTrait.value)
//                             actionVM.action.emit(actionAction.value)
//                             actionVM.valueLevel.emit(actionValueLevel.value)
//
//                             draftVM.actionVMs.value.add(actionVM)
//                             draftVM.selectedActionVM.emit(null)
                        }
                    })
                { Text(stringResource(R.string.action_button_create)) }
            }
        }
    }
}

