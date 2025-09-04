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

package com.example.googlehomeapisampleapp.view.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.googlehomeapisampleapp.R
import com.example.googlehomeapisampleapp.view.shared.TabbedMenuView
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.RoomViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.StructureViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.platform.LocalContext

@Composable
fun DevicesAccountButton (homeAppVM: HomeAppViewModel) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    /**
     * UI Row containing:
     * - Account Icon Button: triggers a permission request using PermissionsManager.
     * - Overflow Menu: opens a dropdown with a "Revoke Permissions" option.
     *
     * Selecting "Revoke Permissions" launches an intent to Google’s account management
     * page for manually revoking app access.
     *
     */
    Row {
        IconButton(
            onClick = { homeAppVM.homeApp.permissionsManager.requestPermissions() },
            modifier = Modifier.size(48.dp).background(Color.Transparent)
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "",
                modifier = Modifier.fillMaxSize(),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Revoke Permissions") },
                onClick = {
                    expanded = false
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://myaccount.google.com/u/2/connections?utm_source=3p")
                    )
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

                }
            )
        }
    }
}

@Composable
fun DevicesView(
    homeAppVM: HomeAppViewModel,
    onRequestCreateRoom: () -> Unit = {},
    onRequestRoomSettings: (RoomViewModel) -> Unit = {},
    onRequestMoveDevice: (DeviceViewModel) -> Unit = {}
) {
    val scope: CoroutineScope = rememberCoroutineScope()

    val structureVMs: List<StructureViewModel> = homeAppVM.structureVMs.collectAsState().value
    val selectedStructureVM: StructureViewModel? = homeAppVM.selectedStructureVM.collectAsState().value
    val structureName: String = selectedStructureVM?.name ?: stringResource(R.string.devices_structure_loading)

    var structurePickerExpanded by remember { mutableStateOf(false) }
    var plusMenuExpanded by remember { mutableStateOf(false) }

    var isCommissioningMenuVisible by remember { mutableStateOf(false) }
    var toGoogleOnly: Boolean = false

    Column(modifier = Modifier.fillMaxHeight()) {

        DevicesTopBar(
            title = "",
            leftButton = {
                IconButton(onClick = { plusMenuExpanded = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
                DropdownMenu(expanded = plusMenuExpanded, onDismissRequest = { plusMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Add Room") },
                        onClick = {
                            plusMenuExpanded = false
                            onRequestCreateRoom()
                        }
                    )
                }
            },
            rightButtons = listOf { DevicesAccountButton(homeAppVM) }
        )

        Box (modifier = Modifier.weight(1f)) {

            Column {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    if (structureVMs.size > 1) {
                        TextButton(onClick = { structurePickerExpanded = true }) {
                            Text(text = structureName + " ▾", fontSize = 32.sp)
                        }
                    } else {
                        TextButton(onClick = { structurePickerExpanded = true }) {
                            Text(text = structureName, fontSize = 32.sp)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Box {
                        DropdownMenu(expanded = structurePickerExpanded, onDismissRequest = { structurePickerExpanded = false }) {
                            for (structure in structureVMs) {
                                DropdownMenuItem(
                                    text = { Text(structure.name) },
                                    onClick = {
                                        scope.launch { homeAppVM.selectedStructureVM.emit(structure) }
                                        structurePickerExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(weight = 1f, fill = false)) {
                    DeviceListComponent(
                        homeAppVM = homeAppVM,
                        onRoomClick = onRequestRoomSettings,
                        onDeviceLongPress = onRequestMoveDevice
                    )
                }
            }

            Box(modifier = Modifier.padding(16.dp).align(Alignment.BottomEnd)) {
                Button(onClick = { isCommissioningMenuVisible = true }) {
                    Text(stringResource(R.string.devices_button_add))
                }
                DropdownMenu(
                    expanded = isCommissioningMenuVisible,
                    onDismissRequest = { isCommissioningMenuVisible = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Add Device to Google Fabric Only") },
                        onClick = {
                            isCommissioningMenuVisible = false
                            homeAppVM.homeApp.commissioningManager.requestCommissioning(toGoogleOnly = true)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add Device to Google & 3P Fabric") },
                        onClick = {
                            isCommissioningMenuVisible = false
                            homeAppVM.homeApp.commissioningManager.requestCommissioning(toGoogleOnly = false)
                        }
                    )
                }
            }
        }

        TabbedMenuView(homeAppVM)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeviceListItem(
    deviceVM: DeviceViewModel,
    homeAppVM: HomeAppViewModel,
    onLongPress: (DeviceViewModel) -> Unit
) {
    val scope: CoroutineScope = rememberCoroutineScope()
    val deviceStatus: String = deviceVM.status.collectAsState().value
    val deviceName: String = deviceVM.name.collectAsState().value

    Column(
        Modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .fillMaxWidth()
            .combinedClickable(
                onClick = { scope.launch { homeAppVM.selectedDeviceVM.emit(deviceVM) } },
                onLongClick = { onLongPress(deviceVM) }
            )
    ) {
        Text(deviceName, fontSize = 20.sp)
        Text(deviceStatus, fontSize = 16.sp)
    }
}

@Composable
fun RoomListItem(roomVM: RoomViewModel, onClick: (RoomViewModel) -> Unit) {
    val roomName by roomVM.name.collectAsState()

    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clickable { onClick(roomVM) }
    ) {
        Text(roomName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DeviceListComponent(
    homeAppVM: HomeAppViewModel,
    onRoomClick: (RoomViewModel) -> Unit,
    onDeviceLongPress: (DeviceViewModel) -> Unit
) {
    val selectedStructureVM: StructureViewModel =
        homeAppVM.selectedStructureVM.collectAsState().value ?: return

    val selectedRoomVMs: List<RoomViewModel> =
        selectedStructureVM.roomVMs.collectAsState().value

    val selectedDeviceVMsWithoutRooms: List<DeviceViewModel> =
        selectedStructureVM.deviceVMsWithoutRooms.collectAsState().value

    Column {
        for (roomVM in selectedRoomVMs) {
            RoomListItem(roomVM, onClick = onRoomClick)

            val deviceVMsInRoom: List<DeviceViewModel> = roomVM.deviceVMs.collectAsState().value

            for (deviceVM in deviceVMsInRoom) {
                DeviceListItem(deviceVM, homeAppVM, onLongPress = onDeviceLongPress)
            }
        }

        if (selectedDeviceVMsWithoutRooms.isNotEmpty()) {

            Column (Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                Text("Not in a room", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            for (deviceVM in selectedDeviceVMsWithoutRooms) {
                DeviceListItem(deviceVM, homeAppVM, onLongPress = onDeviceLongPress)
            }

        }
    }
}

@Composable
fun DevicesTopBar(
    title: String,
    leftButton: (@Composable () -> Unit)? = null,
    rightButtons: List<@Composable () -> Unit>
) {
    Box(
        Modifier
            .height(64.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        if (leftButton != null) {
            Row(
                Modifier
                    .height(64.dp)
                    .fillMaxWidth()
                    .background(Color.Transparent),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                leftButton()
            }
        }

        Row(
            Modifier
                .height(64.dp)
                .fillMaxWidth()
                .background(Color.Transparent),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(title, fontSize = 24.sp)
        }

        Row(
            Modifier
                .height(64.dp)
                .fillMaxWidth()
                .background(Color.Transparent),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            for (button in rightButtons) {
                button()
            }
        }
    }
}