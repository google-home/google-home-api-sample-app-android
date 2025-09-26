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

package com.example.googlehomeapisampleapp.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.example.googlehomeapisampleapp.ui.theme.GoogleHomeAPISampleAppTheme
import com.example.googlehomeapisampleapp.view.automations.ActionView
import com.example.googlehomeapisampleapp.view.automations.AutomationView
import com.example.googlehomeapisampleapp.view.automations.AutomationsView
import com.example.googlehomeapisampleapp.view.automations.CandidatesView
import com.example.googlehomeapisampleapp.view.automations.DraftView
import com.example.googlehomeapisampleapp.view.automations.StarterView
import com.example.googlehomeapisampleapp.view.devices.DeviceView
import com.example.googlehomeapisampleapp.view.devices.DevicesView
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.ActionViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.AutomationViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.CandidateViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.DraftViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.StarterViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.RoomViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAppView (homeAppVM: HomeAppViewModel) {
    /** Value tracking whether a user is signed-in on the app **/
    val isSignedIn: Boolean = homeAppVM.homeApp.permissionsManager.isSignedIn.collectAsState().value

    /** Values tracking what is being selected on the app **/
    val selectedTab: HomeAppViewModel.NavigationTab by homeAppVM.selectedTab.collectAsState()
    val selectedDeviceVM: DeviceViewModel? by homeAppVM.selectedDeviceVM.collectAsState()
    val selectedAutomationVM: AutomationViewModel? by homeAppVM.selectedAutomationVM.collectAsState()
    val selectedCandidateVMs: List<CandidateViewModel>? by homeAppVM.selectedCandidateVMs.collectAsState()
    val selectedDraftVM: DraftViewModel? by homeAppVM.selectedDraftVM.collectAsState()
    val selectedStarterVM: StarterViewModel? = selectedDraftVM?.selectedStarterVM?.collectAsState()?.value
    val selectedActionVM: ActionViewModel? = selectedDraftVM?.selectedActionVM?.collectAsState()?.value
    val showCreateRoom = remember { mutableStateOf(false) }
    val roomSettingsFor = remember { mutableStateOf<RoomViewModel?>(null) }
    val moveDeviceFor = remember { mutableStateOf<DeviceViewModel?>(null) }

    /**
     * Periodically refreshes permissions while the user is signed in.
     *
     * This loop helps ensure that permission state remains accurate in case
     * it changes outside the app(e.g., in Google Home or system settings).
     */
    LaunchedEffect(isSignedIn) {
        while (homeAppVM.homeApp.permissionsManager.isSignedIn.value) {
            homeAppVM.homeApp.permissionsManager.refreshPermissions()
            delay(2000)
        }
    }


    // Apply theme on the top-level view:
    GoogleHomeAPISampleAppTheme {
        // Top-level external frame for the views:
        Column(modifier = Modifier.fillMaxSize()) {
            // Top spacer to allocate space for status bar / camera notch:
            Spacer(modifier = Modifier.height(48.dp).fillMaxWidth().background(Color.Transparent))

            // Primary frame to hold content:
            Column (modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Transparent)) {

                /** Navigation Flow, displays a view depending on the viewmodel state **/

                // If not signed-in, show WelcomeView:
                if (!isSignedIn) {
                    WelcomeView(homeAppVM)
                }

                // If a device is selected, show the device controls:
                if (selectedDeviceVM != null) {
                    DeviceView(homeAppVM)
                }

                // If an automation is selected, show the automation details:
                if (selectedAutomationVM != null) {
                    AutomationView(homeAppVM)
                }

                // If a starter is selected for a draft automation, show the starter editor:
                if (selectedStarterVM != null) {
                    StarterView(homeAppVM)
                }

                // If an action is selected for a draft automation, show the action editor:
                if (selectedActionVM != null) {
                    ActionView(homeAppVM)
                }

                // If a draft automation is selected, show the draft editor:
                if (selectedDraftVM != null) {
                    DraftView(homeAppVM)
                }

                // If the automation candidates are selected, show the candidates:
                if (selectedCandidateVMs != null) {
                    CandidatesView(homeAppVM)
                }

                // If nothing above is selected, then show one of the two main views:
                when (selectedTab) {
                    HomeAppViewModel.NavigationTab.DEVICES -> DevicesView(
                        homeAppVM = homeAppVM,
                        onRequestCreateRoom = { showCreateRoom.value = true },
                        onRequestRoomSettings = { room -> roomSettingsFor.value = room },
                        onRequestMoveDevice = { device -> moveDeviceFor.value = device }
                    )
                    HomeAppViewModel.NavigationTab.AUTOMATIONS -> AutomationsView(homeAppVM)
                }
            }

            if (showCreateRoom.value) {
                var roomName = remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showCreateRoom.value = false },
                    title = { Text("Create Room") },
                    text = {
                        OutlinedTextField(
                            value = roomName.value,
                            onValueChange = { roomName.value = it },
                            label = { Text("Room name") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val name = roomName.value.trim()
                            if (name.isNotEmpty()) { homeAppVM.createRoomInSelectedStructure(name) }
                            showCreateRoom.value = false
                        }) { Text("Create") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateRoom.value = false }) { Text("Cancel") }
                    }
                )
            }


            roomSettingsFor.value?.let { activeRoom ->
                ModalBottomSheet(onDismissRequest = { roomSettingsFor.value = null }) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("Room settings", modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                homeAppVM.deleteRoomFromSelectedStructure(activeRoom)
                                roomSettingsFor.value = null
                            }) { Text("Delete") }
                        }
                        val renameText = remember(activeRoom.id) {
                            mutableStateOf(activeRoom.name.value)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = renameText.value,
                                onValueChange = { renameText.value = it },
                                label = { Text("Room name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
                            TextButton(onClick = {
                                val newName = renameText.value.trim()
                                if (newName.isNotEmpty() && newName != activeRoom.name.value) {
                                    homeAppVM.viewModelScope.launch {
                                        activeRoom.renameRoom(newName)
                                    }
                                }
                                roomSettingsFor.value = null
                            }) { Text("Save") }
                        }

                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { roomSettingsFor.value = null }) { Text("Close") }
                    }
                }
            }

            moveDeviceFor.value?.let { deviceToMove ->
                ModalBottomSheet(onDismissRequest = { moveDeviceFor.value = null }) {
                    val structureVM = homeAppVM.selectedStructureVM.collectAsState().value
                    val rooms: List<RoomViewModel> =
                        structureVM?.roomVMs?.collectAsState()?.value ?: emptyList()

                    val expanded = remember { mutableStateOf(false) }
                    val selectedRoom = remember { mutableStateOf<RoomViewModel?>(null) }

                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Move \"${deviceToMove.name.collectAsState().value}\" to…")
                        Spacer(Modifier.height(12.dp))

                        ExposedDropdownMenuBox(
                            expanded = expanded.value,
                            onExpandedChange = { expanded.value = !expanded.value }
                        ) {
                            TextField(
                                readOnly = true,
                                value = selectedRoom.value?.name?.value ?: "Select a room",
                                onValueChange = {},
                                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded.value,
                                onDismissRequest = { expanded.value = false }
                            ) {
                                rooms.forEach { room ->
                                    val roomName by room.name.collectAsState()
                                    DropdownMenuItem(
                                        text = { Text(roomName) },
                                        onClick = {
                                            selectedRoom.value = room
                                            expanded.value = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                selectedRoom.value?.let { target ->
                                    homeAppVM.moveDeviceToRoom(deviceToMove, target)
                                    moveDeviceFor.value = null
                                }
                            },
                            enabled = selectedRoom.value != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Move") }

                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { moveDeviceFor.value = null }) { Text("Close") }
                    }
                }
            }
        }
    }
}