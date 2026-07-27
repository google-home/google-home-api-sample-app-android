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

package com.ioconnect2026.googlehomeapisampleapp.view

import android.content.Intent
import android.util.Log
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
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ioconnect2026.googlehomeapisampleapp.AccountSwitchProxyActivity
import com.ioconnect2026.googlehomeapisampleapp.MainActivity
import com.ioconnect2026.googlehomeapisampleapp.ui.theme.GoogleHomeAPISampleAppTheme
import com.ioconnect2026.googlehomeapisampleapp.view.devices.DeviceView
import com.ioconnect2026.googlehomeapisampleapp.view.devices.DevicesView
import com.ioconnect2026.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.ioconnect2026.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.ioconnect2026.googlehomeapisampleapp.viewmodel.structures.RoomViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The main Composable function for the Google Home API Sample App. This function displays the
 * appropriate view based on the state of the [HomeAppViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAppView(
  homeAppVM: HomeAppViewModel,
  onRequestPermissions: (Boolean) -> Unit = {},
  onRefreshPermissions: () -> Unit = {},
) {
  /** Value tracking whether a user is signed-in on the app * */
  val isSignedIn: Boolean by homeAppVM.isSignedIn.collectAsStateWithLifecycle()

  /** Values tracking what is being selected on the app * */
  val selectedDeviceVM: DeviceViewModel? by homeAppVM.selectedDeviceVM.collectAsState()
  val showCreateRoom = remember { mutableStateOf(false) }
  val roomSettingsFor = remember { mutableStateOf<RoomViewModel?>(null) }
  val moveDeviceFor = remember { mutableStateOf<DeviceViewModel?>(null) }
  val selectedStructureVM by homeAppVM.selectedStructureVM.collectAsState()

  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current

  LaunchedEffect(Unit) {
    homeAppVM.navigateToProxyActivity.collect {
      Log.i(
        MainActivity.TAG,
        "HomeActivity: Received navigate event from ViewModel. Launching AccountSwitchProxyActivity.",
      )
      val intent =
        Intent(context, AccountSwitchProxyActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
      context.startActivity(intent)
    }
  }

  /**
   * Periodically refreshes permissions while the user is signed in.
   *
   * This loop helps ensure that permission state remains accurate in case it changes outside the
   * app(e.g., in Google Home or system settings).
   */
  LaunchedEffect(isSignedIn) {
    while (isSignedIn) {
      onRefreshPermissions()
      delay(2000)
    }
  }

  // Apply theme on the top-level view:
  GoogleHomeAPISampleAppTheme {
    // Top-level external frame for the views:
    Column(modifier = Modifier.fillMaxSize()) {
      Spacer(modifier = Modifier.height(48.dp).fillMaxWidth().background(Color.Transparent))

      Column(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Transparent)) {
        if (!isSignedIn) {
          WelcomeView(
            onSignInWithGoogle = { homeAppVM.signInWithGoogleAccount(context) },
            onRequestPermissions = { onRequestPermissions(false) },
          )
        }

        if (selectedDeviceVM != null) {
          DeviceView(homeAppVM)
        }

        DevicesView(
          homeAppVM = homeAppVM,
          onRequestPermissions = onRequestPermissions,
          onSignInWithGoogle = { homeAppVM.signInWithGoogleAccount(context) },
          onRequestCreateRoom = { showCreateRoom.value = true },
          onRequestRoomSettings = { room -> roomSettingsFor.value = room },
          onRequestMoveDevice = { device -> moveDeviceFor.value = device },
        )
      }

      // Room and Hub Dialogs
      if (showCreateRoom.value) {
        val roomName = remember { mutableStateOf("") }
        AlertDialog(
          onDismissRequest = { showCreateRoom.value = false },
          title = { Text("Create Room") },
          text = {
            OutlinedTextField(
              value = roomName.value,
              onValueChange = { roomName.value = it },
              label = { Text("Room name") },
              singleLine = true,
            )
          },
          confirmButton = {
            TextButton(
              onClick = {
                val name = roomName.value.trim()
                if (name.isNotEmpty()) {
                  homeAppVM.createRoomInSelectedStructure(name)
                }
                showCreateRoom.value = false
              }
            ) {
              Text("Create")
            }
          },
          dismissButton = {
            TextButton(onClick = { showCreateRoom.value = false }) { Text("Cancel") }
          },
        )
      }

      roomSettingsFor.value?.let { activeRoom ->
        ModalBottomSheet(onDismissRequest = { roomSettingsFor.value = null }) {
          Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
              Text("Room settings", modifier = Modifier.weight(1f))
              TextButton(
                onClick = {
                  homeAppVM.deleteRoomFromSelectedStructure(activeRoom)
                  roomSettingsFor.value = null
                }
              ) {
                Text("Delete")
              }
            }
            val renameText = remember(activeRoom.id) { mutableStateOf(activeRoom.name.value) }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
              OutlinedTextField(
                value = renameText.value,
                onValueChange = { renameText.value = it },
                label = { Text("Room name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              Spacer(Modifier.width(12.dp))
              TextButton(
                onClick = {
                  val newName = renameText.value.trim()
                  if (newName.isNotEmpty() && newName != activeRoom.name.value) {
                    homeAppVM.viewModelScope.launch { activeRoom.renameRoom(newName) }
                  }
                  roomSettingsFor.value = null
                }
              ) {
                Text("Save")
              }
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
              onExpandedChange = { expanded.value = !expanded.value },
            ) {
              TextField(
                readOnly = true,
                value = selectedRoom.value?.name?.value ?: "Select a room",
                onValueChange = {},
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                modifier =
                  Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
              )
              ExposedDropdownMenu(
                expanded = expanded.value,
                onDismissRequest = { expanded.value = false },
              ) {
                rooms.forEach { room ->
                  val roomName by room.name.collectAsState()
                  DropdownMenuItem(
                    text = { Text(roomName) },
                    onClick = {
                      selectedRoom.value = room
                      expanded.value = false
                    },
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
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Move")
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { moveDeviceFor.value = null }) { Text("Close") }
          }
        }
      }
    }
  }
}
