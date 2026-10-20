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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.googlehomeapisampleapp.R
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.ActionViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.DraftViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.StarterViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.google.home.Trait
import com.google.home.TraitFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun DraftView(homeAppVM: HomeAppViewModel) {
  val scope: CoroutineScope = rememberCoroutineScope()
  // Selected DraftViewModel on screen to create a new automation:
  val draftVM: DraftViewModel = homeAppVM.selectedDraftVM.collectAsState().value!!
  val starterVMs: List<StarterViewModel> = draftVM.starterVMs.collectAsState().value
  val actionVMs: List<ActionViewModel> = draftVM.actionVMs.collectAsState().value
  // Editable text fields for the automation draft:
  val draftName: String = draftVM.name.collectAsState().value
  val draftDescription: String = draftVM.description.collectAsState().value

  val isPending: MutableState<Boolean> = remember { mutableStateOf(false) }

  // Back action for closing view:
  BackHandler {
    scope.launch { homeAppVM.selectedDraftVM.emit(null) }
  }

  Box(modifier = Modifier.fillMaxSize()) {

    Column {
      Spacer(Modifier.height(64.dp))

      // Title Text:
      Row(
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
      ) {
        Text(text = stringResource(R.string.draft_title), fontSize = 32.sp)
      }

      // Name Input - make read-only for locked drafts
      Row(
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
      ) {
        TextField(
          value = draftName,
          onValueChange = if (draftVM.isLocked) {
            {}
          } else {
            { scope.launch { draftVM.name.emit(it) } }
          },
          label = { Text(text = stringResource(R.string.draft_label_name)) },
          readOnly = draftVM.isLocked,
          modifier = Modifier.fillMaxWidth()
        )
      }

      // Description Input - make read-only for locked drafts
      Row(
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
      ) {
        TextField(
          value = draftDescription,
          onValueChange = if (draftVM.isLocked) {
            {}
          } else {
            { scope.launch { draftVM.description.emit(it) } }
          },
          label = { Text(text = stringResource(R.string.draft_label_description)) },
          readOnly = draftVM.isLocked,
          modifier = Modifier.fillMaxWidth()
        )
      }
      // Spacer:
      Spacer(modifier = Modifier)

      // Expanding Container - show read-only preview for locked drafts
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).weight(weight = 1f, fill = false)
      ) {
        if (!draftVM.isLocked) {
          // Draft Starters:
          DraftStarterList(draftVM)
          // Draft Actions:
          DraftActionList(draftVM)
        } else {
          // For locked predefined automations, show read-only preview
          LockedDraftPreview(draftVM)
        }
      }
    }

    // Button to save the draft automation:
    Row(modifier = Modifier.padding(16.dp).align(Alignment.BottomCenter)) {
      // Check on whether at least a starter and an action are selected:
      val isOptionsSelected: Boolean = if (draftVM.isLocked) true
      else starterVMs.isNotEmpty() && actionVMs.isNotEmpty()
      // Check on whether a name and description are provided:
      val isValueProvided: Boolean = draftName.isNotBlank() || draftDescription.isNotBlank()
      Button(
        enabled = isOptionsSelected && isValueProvided && !isPending.value,
        onClick = { homeAppVM.createAutomation(isPending) })
      {
        Text(
          if (!isPending.value) stringResource(R.string.draft_button_create) else stringResource(
            R.string.draft_text_creating
          )
        )
      }
    }
  }
}

@Composable
fun DraftStarterList(draftVM: DraftViewModel) {
  val scope: CoroutineScope = rememberCoroutineScope()
  // List of existing starters in this automation draft:
  val starterVMs: List<StarterViewModel> = draftVM.starterVMs.collectAsState().value

  // Starters title:
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text(
      text = stringResource(R.string.draft_text_starters),
      fontSize = 16.sp, fontWeight = FontWeight.SemiBold
    )
  }
  // For each starter, creating a StarterItem view:
  for (starterVM in starterVMs)
    DraftStarterItem(starterVM, draftVM)
  // Button to add a new starter:
  if (!draftVM.isLocked) {
    // Button to add a new starter: (original block unchanged)
    Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
      Column(Modifier.fillMaxWidth().clickable {
        scope.launch { draftVM.selectedStarterVM.emit(StarterViewModel()) }
      }) {
        Text(text = stringResource(R.string.draft_new_starter_name), fontSize = 20.sp)
        Text(text = stringResource(R.string.draft_new_starter_description), fontSize = 16.sp)
      }
    }
  }
}

@Composable
fun DraftStarterItem(starterVM: StarterViewModel, draftVM: DraftViewModel) {
  val scope = rememberCoroutineScope()
  // Attributes of the starter item:
  val starterDeviceVM: DeviceViewModel = starterVM.deviceVM.collectAsState().value!!
  val starterTrait: TraitFactory<out Trait>? = starterVM.trait.collectAsState().value

  // Item to view and select the starter:
  Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
    Column(Modifier.fillMaxWidth().clickable {
      scope.launch { draftVM.selectedStarterVM.emit(starterVM) }
    }) {
      val starterName by starterDeviceVM.name.collectAsState()
      Text(starterName, fontSize = 20.sp)
      Text(starterTrait.toString(), fontSize = 16.sp)
    }
  }
}

@Composable
fun DraftActionList(draftVM: DraftViewModel) {
  val scope: CoroutineScope = rememberCoroutineScope()
  // List of existing starters in this automation draft:
  val actionVMs: List<ActionViewModel> = draftVM.actionVMs.collectAsState().value

  // Actions title:
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text(
      text = stringResource(R.string.draft_text_actions),
      fontSize = 16.sp, fontWeight = FontWeight.SemiBold
    )
  }
  // For each action, creating an ActionItem view:
  for (actionVM in actionVMs)
    DraftActionItem(actionVM, draftVM)
  // Button to add a new action:
  if (!draftVM.isLocked) {
    // Button to add a new action
    Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
      Column(Modifier.fillMaxWidth().clickable {
        scope.launch { draftVM.selectedActionVM.emit(ActionViewModel(null)) }
      }) {
        Text(text = stringResource(R.string.draft_new_action_name), fontSize = 20.sp)
        Text(text = stringResource(R.string.draft_new_action_description), fontSize = 16.sp)
      }
    }
  }
}

@Composable
fun DraftActionItem(actionVM: ActionViewModel, draftVM: DraftViewModel) {
  val scope: CoroutineScope = rememberCoroutineScope()
  // Attributes of the action item:
  val actionDeviceVM: DeviceViewModel = actionVM.deviceVM.collectAsState().value!!
  val actionTrait: Trait? = actionVM.trait.collectAsState().value

  // Item to view and select the action:
  Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
    Column(Modifier.fillMaxWidth().clickable {
      scope.launch { draftVM.selectedActionVM.emit(actionVM) }
    }) {
      val actionName by actionDeviceVM.name.collectAsState()
      Text(actionName, fontSize = 20.sp)
      Text(actionTrait?.factory.toString(), fontSize = 16.sp)
    }
  }
}

@Composable
fun LockedDraftPreview(draftVM: DraftViewModel) {
  val name = draftVM.name.collectAsState().value
  val description = draftVM.description.collectAsState().value

  // Use the automation type enum instead of string matching
  when (draftVM.automationType) {
    DraftViewModel.AutomationType.ON_OFF -> {
      OnOffAutomationPreview(description)
    }

    DraftViewModel.AutomationType.SPEAKER_AND_FAN -> {
      SpeakerAndFanAutomationPreview(description)
    }

    DraftViewModel.AutomationType.LIGHT_AND_THERMOSTAT -> {
      LightAndThermostatAutomationPreview()
    }

    DraftViewModel.AutomationType.WINDOW_COVERING -> {
      WindowCoveringAutomationPreview(description)
    }

    DraftViewModel.AutomationType.LIGHT_AND_TV_PERIODIC -> {
      LightAndTVPeriodicAutomationPreview(description)
    }

    DraftViewModel.AutomationType.CUSTOM -> {
      // Generic preview for custom automations
      GenericAutomationPreview(name, description)
    }
  }
}

@Composable
private fun OnOffAutomationPreview(description: String) {
  val deviceNames = extractOnOffDeviceNames(description)
  val starterDeviceName = deviceNames.first
  val actionDeviceName = deviceNames.second

  // Starters Section
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text(text = "Starters", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }

  // Starter preview card
  Card(
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "$starterDeviceName turns OFF",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = "OnOffTrait",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }

  // Actions Section
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text(text = "Actions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }
  // Action preview card
  Card(
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Turn OFF $actionDeviceName",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = "OffCommand",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun SpeakerAndFanAutomationPreview(description: String) {
  // Extract device names from description
  val deviceNames = extractSpeakerAndFanDeviceNames(description)
  val speakerName = deviceNames.first
  val fanName = deviceNames.second
  val plugName = deviceNames.third

  // Starters Section
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text(text = "Starters", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }

  // Voice starter preview card
  Card(
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "\"Hey Google, I can't sleep\"",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = "VoiceStarter",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }

  // Actions Section
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text(text = "Actions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }

  // Speaker action preview card
  Card(
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Play ocean sounds on $speakerName",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = "AssistantFulfillment",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }

  // Fan action preview card
  Card(
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Turn ON $fanName",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = "OnCommand",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }

  // Plug action preview card
  Card(
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Turn ON $plugName",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = "OnCommand",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun LightAndThermostatAutomationPreview() {
  // Starters Section
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text(text = "Starters", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }

  Card(
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Door unlocked",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = "DoorLockTrait - Unlocked state",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }

  // Actions Section
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text(text = "Actions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }

  // Turn on lights action
  Card(
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Turn ON all lights",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = "OnOffTrait - OnCommand",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }

  // Set thermostat action
  Card(
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Set thermostat to Auto mode",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = "SimplifiedThermostatTrait - Auto mode",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
@Composable
private fun WindowCoveringAutomationPreview(description: String) {
  // Extract window covering name from description
  val windowCoveringName = extractWindowCoveringDeviceName(description)

  // Starters Section
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text(text = "Starters", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }

  // Temperature starter preview card
  Card(
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Temperature < 15°C (59°F) AND it's dark",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = "TemperatureMeasurement + Time condition",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }

  // Actions Section
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text(text = "Actions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }

  // Close window covering action
  Card(
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Close $windowCoveringName",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = "WindowCovering - DownOrClose command",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
@Composable
private fun LightAndTVPeriodicAutomationPreview(description: String) {
  // Starters Section
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text(text = "Starters", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }

  Card(
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Motion detected",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = "OccupancySensing trait",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }

  // Actions Section
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text(text = "Actions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }

  // Broadcast alarm action
  Card(
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Broadcast ALARM",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = "AssistantBroadcast on all speakers",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }

  // Lights and TV action
  Card(
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Turn ON all lights and TV",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = "OnOff trait - OnCommand (sequential)",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun GenericAutomationPreview(name: String, description: String) {
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text(text = "Preview", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }

  Card(
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = name,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = description,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

// Helper function to extract device names from OnOff automation description
private fun extractOnOffDeviceNames(description: String): Pair<String, String> {
  val regex = "Turn off (.+) when (.+) turns off".toRegex()
  val matchResult = regex.find(description)

  return if (matchResult != null) {
    val actionDevice = matchResult.groupValues[1].trim()
    val starterDevice = matchResult.groupValues[2].trim()
    Pair(starterDevice, actionDevice)
  } else {
    // Fallback to generic names if parsing fails
    Pair("First light", "Second light")
  }
}

// Helper function to extract device names from Speaker and Fan automation description
private fun extractSpeakerAndFanDeviceNames(description: String): Triple<String, String, String> {
  // Pattern: "Play ocean sounds on [speaker], turn on [fan] and [plug]"
  val regex = "Play ocean sounds on (.+?), turn on (.+?) and (.+)".toRegex()
  val matchResult = regex.find(description)

  return if (matchResult != null) {
    val speaker = matchResult.groupValues[1].trim()
    val fan = matchResult.groupValues[2].trim()
    val plug = matchResult.groupValues[3].trim()
    Triple(speaker, fan, plug)
  } else {
    // Fallback to generic names if parsing fails
    Triple("Speaker", "Fan", "Outlet")
  }
}

// Helper function to extract window covering device name from description
private fun extractWindowCoveringDeviceName(description: String): String {
  // Pattern: "Close [device name] when temperature..."
  val regex = "Close (.+?) when".toRegex()
  val matchResult = regex.find(description)

  return if (matchResult != null) {
    matchResult.groupValues[1].trim()
  } else {
    "Window Covering"
  }
}