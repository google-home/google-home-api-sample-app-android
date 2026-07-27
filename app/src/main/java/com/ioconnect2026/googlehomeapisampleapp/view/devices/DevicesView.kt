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

package com.ioconnect2026.googlehomeapisampleapp.view.devices

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.ioconnect2026.googlehomeapisampleapp.R
import com.ioconnect2026.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.ioconnect2026.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.ioconnect2026.googlehomeapisampleapp.viewmodel.structures.RoomViewModel
import com.ioconnect2026.googlehomeapisampleapp.viewmodel.structures.StructureViewModel
import kotlinx.coroutines.CoroutineScope

const val TAG = "DevicesView"

/**
 * Composable for displaying the account button and overflow menu in the Devices view.
 *
 * @param homeAppVM The [HomeAppViewModel] providing the data and logic.
 */
@Composable
fun DevicesAccountButton(onSignInWithGoogle: () -> Unit, onRequestPermissions: (Boolean) -> Unit) {
  val context = LocalContext.current
  var expanded by remember { mutableStateOf(false) }
  /**
   * UI Row containing:
   * - Account Icon Button: triggers a permission request using PermissionsManager.
   * - Overflow Menu: opens a dropdown with a "Revoke Permissions" option.
   *
   * Selecting "Revoke Permissions" launches an intent to Google’s account management page for
   * manually revoking app access.
   */
  Row {
    IconButton(
      onClick = { onRequestPermissions(true) },
      modifier = Modifier.size(48.dp).background(Color.Transparent),
    ) {
      Icon(
        imageVector = Icons.Default.AccountCircle,
        contentDescription = "",
        modifier = Modifier.fillMaxSize(),
        tint = MaterialTheme.colorScheme.primary,
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
          val intent =
            Intent(
              Intent.ACTION_VIEW,
              "https://myaccount.google.com/connections?utm_source=3p".toUri(),
            )
          context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
      )
      DropdownMenuItem(text = { Text("Google Sign-In") }, onClick = onSignInWithGoogle)
    }
  }
}

/**
 * Composable for displaying the Devices view, which shows a list of structures and devices.
 *
 * @param homeAppVM The [HomeAppViewModel] providing the data and logic.
 * @param onRequestCreateRoom Callback for requesting to create a new room.
 * @param onRequestRoomSettings Callback for requesting to view/edit room settings.
 * @param onRequestMoveDevice Callback for requesting to move a device to a different room.
 * @param onRequestAddHub Callback for requesting to add a new hub.
 */
@Composable
fun DevicesView(
  homeAppVM: HomeAppViewModel,
  onRequestPermissions: (Boolean) -> Unit = {},
  onSignInWithGoogle: () -> Unit = {},
  onRequestCreateRoom: () -> Unit = {},
  onRequestRoomSettings: (RoomViewModel) -> Unit = {},
  onRequestMoveDevice: (DeviceViewModel) -> Unit = {},
) {
  val scope: CoroutineScope = rememberCoroutineScope()

  val structureVMs: List<StructureViewModel> = homeAppVM.structureVMs.collectAsState().value
  val selectedStructureVM: StructureViewModel? =
    homeAppVM.selectedStructureVM.collectAsState().value
  val structureName: String =
    selectedStructureVM?.name ?: stringResource(R.string.devices_structure_loading)

  var structurePickerExpanded by remember { mutableStateOf(false) }
  var plusMenuExpanded by remember { mutableStateOf(false) }

  var isCommissioningMenuVisible by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxHeight()) {
    DevicesTopBar(
      title = "",
      leftButton = {
        IconButton(onClick = onRequestCreateRoom) {
          Icon(Icons.Default.Add, contentDescription = "Add Room")
        }
      },
      rightButtons =
        listOf({
          DevicesAccountButton(
            onSignInWithGoogle = onSignInWithGoogle,
            onRequestPermissions = onRequestPermissions,
          )
        }),
    )

    Box(modifier = Modifier.weight(1f)) {
      Column {
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
          if (structureVMs.size > 1) {
            TextButton(onClick = { structurePickerExpanded = true }) {
              Text(text = "$structureName ▾", fontSize = 32.sp)
            }
          } else {
            TextButton(onClick = { structurePickerExpanded = true }) {
              Text(text = structureName, fontSize = 32.sp)
            }
          }
        }

        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
          Box {
            DropdownMenu(
              expanded = structurePickerExpanded,
              onDismissRequest = { structurePickerExpanded = false },
            ) {
              for (structure in structureVMs) {
                DropdownMenuItem(
                  text = { Text(structure.name) },
                  onClick = {
                    if (structure.id != selectedStructureVM?.id) {
                      Log.d(TAG, "Selected structure changed to ${structure.name}")
                      homeAppVM.selectStructure(structure)
                    }
                    structurePickerExpanded = false
                  },
                )
              }
            }
          }
        }

        Column(
          modifier =
            Modifier.verticalScroll(rememberScrollState()).weight(weight = 1f, fill = false)
        ) {
          DeviceListComponent(
            homeAppVM = homeAppVM,
            onRoomClick = onRequestRoomSettings,
            onDeviceLongPress = onRequestMoveDevice,
          )
        }
      }
    }
  }
}

/**
 * Composable for displaying a single device item in a list.
 *
 * @param deviceVM The [DeviceViewModel] for the device.
 * @param homeAppVM The [HomeAppViewModel] for navigation.
 * @param onLongPress Callback for when the device item is long-pressed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeviceListItem(
  deviceVM: DeviceViewModel,
  homeAppVM: HomeAppViewModel,
  onLongPress: (DeviceViewModel) -> Unit,
) {
  val scope: CoroutineScope = rememberCoroutineScope()
  val deviceStatus: String = deviceVM.status.collectAsState().value
  val deviceName: String = deviceVM.name.collectAsState().value

  Column(
    Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
      .fillMaxWidth()
      .combinedClickable(
        onClick = { homeAppVM.selectDevice(deviceVM) },
        onLongClick = { onLongPress(deviceVM) },
      )
  ) {
    Text(deviceName, fontSize = 20.sp)
    Text(deviceStatus, fontSize = 16.sp)
  }
}

/**
 * Composable for displaying a single room item in a list.
 *
 * @param roomVM The [RoomViewModel] for the room.
 * @param onClick Callback for when the room item is clicked.
 */
@Composable
fun RoomListItem(roomVM: RoomViewModel, onClick: (RoomViewModel) -> Unit) {
  val roomName by roomVM.name.collectAsState()

  Column(
    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clickable {
      onClick(roomVM)
    }
  ) {
    Text(roomName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }
}

/**
 * Composable for displaying a list of devices, grouped by rooms.
 *
 * @param homeAppVM The [HomeAppViewModel] providing the data.
 * @param onRoomClick Callback for when a room is clicked.
 * @param onDeviceLongPress Callback for when a device is long-pressed.
 */
@Composable
fun DeviceListComponent(
  homeAppVM: HomeAppViewModel,
  onRoomClick: (RoomViewModel) -> Unit,
  onDeviceLongPress: (DeviceViewModel) -> Unit,
) {
  val selectedStructureVM: StructureViewModel =
    homeAppVM.selectedStructureVM.collectAsState().value ?: return

  val selectedRoomVMs: List<RoomViewModel> = selectedStructureVM.roomVMs.collectAsState().value

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

      Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        Text("Not in a room", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
      }

      for (deviceVM in selectedDeviceVMsWithoutRooms) {
        DeviceListItem(deviceVM, homeAppVM, onLongPress = onDeviceLongPress)
      }
    }
  }
}

/**
 * Composable for displaying the top bar of the Devices view.
 *
 * @param title The title to display in the top bar.
 * @param leftButton Optional Composable for a button on the left side of the top bar.
 * @param rightButtons List of Composable for buttons on the right side of the top bar.
 */
@Composable
fun DevicesTopBar(
  title: String,
  leftButton: (@Composable () -> Unit)? = null,
  rightButtons: List<@Composable () -> Unit>,
) {
  Box(Modifier.height(64.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
    if (leftButton != null) {
      Row(
        Modifier.height(64.dp).fillMaxWidth().background(Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
      ) {
        leftButton()
      }
    }

    Row(
      Modifier.height(64.dp).fillMaxWidth().background(Color.Transparent),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Text(title, fontSize = 24.sp)
    }

    Row(
      Modifier.height(64.dp).fillMaxWidth().background(Color.Transparent),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.End,
    ) {
      for (button in rightButtons) {
        button()
      }
    }
  }
}
