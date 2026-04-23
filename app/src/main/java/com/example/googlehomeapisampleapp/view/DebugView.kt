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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.example.googlehomeapisampleapp.view.shared.TabbedMenuView
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.settings.Debugger
import kotlinx.coroutines.launch

@Composable
fun DebugView(homeAppVM: HomeAppViewModel) {
  val scope = rememberCoroutineScope()
  val debugger = Debugger.getInstance(homeAppVM.homeApp.homeClient)
  val selectedStructureVM = homeAppVM.selectedStructureVM.collectAsState().value
  var showSummaryDialog by remember { mutableStateOf(false) }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = "Debug Utilities",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        Button(
          onClick = {
            scope.launch {
              selectedStructureVM?.structure?.let { structure ->
                debugger.dumpStructure(structure)
              }
            }
          },
          modifier = Modifier.fillMaxWidth(),
          enabled = selectedStructureVM != null
        ) {
          Text("Dump Structure")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
          onClick = {
            scope.launch {
              selectedStructureVM?.structure?.let { structure ->
                debugger.dumpAutomationsInStructure(structure)
              }
            }
          },
          modifier = Modifier.fillMaxWidth(),
          enabled = selectedStructureVM != null
        ) {
          Text("Dump Automation")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
          onClick = { showSummaryDialog = true },
          modifier = Modifier.fillMaxWidth(),
          enabled = selectedStructureVM != null
        ) {
          Text("Summarize Home")
        }
      }

      TabbedMenuView(homeAppVM)
    }
  }

  if (showSummaryDialog && selectedStructureVM != null) {
    val rooms by selectedStructureVM.roomVMs.collectAsState()
    val devicesWithoutRooms by selectedStructureVM.deviceVMsWithoutRooms.collectAsState()

    AlertDialog(
      onDismissRequest = { showSummaryDialog = false },
      title = { Text("Home Summary") },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
        ) {
          Text(
            text = "Structure: ${selectedStructureVM.name}",
            style = MaterialTheme.typography.titleMedium
          )
          Spacer(Modifier.height(8.dp))
          
          if (rooms.isNotEmpty()) {
            Text(text = "Rooms:", style = MaterialTheme.typography.titleSmall)
            rooms.forEach { roomVM ->
              val roomName by roomVM.name.collectAsState()
              val devicesInRoom by roomVM.deviceVMs.collectAsState()
              Text(
                text = "• $roomName (${devicesInRoom.size} devices)",
                modifier = Modifier.padding(start = 8.dp)
              )
              devicesInRoom.forEach { deviceVM ->
                val deviceName by deviceVM.name.collectAsState()
                Text(
                  text = "  - $deviceName",
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(start = 16.dp)
                )
              }
            }
          } else {
            Text(text = "No rooms found.")
          }

          if (devicesWithoutRooms.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(text = "Devices not in any room:", style = MaterialTheme.typography.titleSmall)
            devicesWithoutRooms.forEach { deviceVM ->
              val deviceName by deviceVM.name.collectAsState()
              Text(
                text = "• $deviceName",
                modifier = Modifier.padding(start = 8.dp)
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showSummaryDialog = false }) {
          Text("Close")
        }
      }
    )
  }
}
