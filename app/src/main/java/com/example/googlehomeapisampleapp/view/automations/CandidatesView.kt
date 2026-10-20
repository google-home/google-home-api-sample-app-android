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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.googlehomeapisampleapp.R
import com.example.googlehomeapisampleapp.view.automations.SuggestListSection
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.CandidateViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.DraftViewModel
import com.example.googlehomeapisampleapp.repository.DeviceCapabilities
import com.example.googlehomeapisampleapp.repository.computeDeviceCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun CandidatesView(homeAppVM: HomeAppViewModel) {
  val scope: CoroutineScope = rememberCoroutineScope()

  BackHandler {
    scope.launch { homeAppVM.selectedCandidateVMs.emit(null) }
  }

  Column {
    Spacer(Modifier.height(64.dp))

    Box(modifier = Modifier.weight(1f)) {

      Column {
        Row(
          horizontalArrangement = Arrangement.Start,
          modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
        ) {
          Text(text = stringResource(R.string.candidate_button_create), fontSize = 32.sp)
        }

        Column(
          modifier = Modifier.verticalScroll(rememberScrollState())
            .weight(weight = 1f, fill = false)
        ) {
          CandidateListComponent(homeAppVM)
        }
      }

    }

  }

}

@Composable
fun CandidateListComponent(homeAppVM: HomeAppViewModel) {
  val candidates: List<CandidateViewModel> =
    homeAppVM.selectedCandidateVMs.collectAsState().value ?: return

  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text("", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }

  BlankListItem(homeAppVM)
  val structureVM = homeAppVM.selectedStructureVM.collectAsState().value
  val deviceVMs = structureVM?.deviceVMs?.value ?: emptyList()
  val capabilities = remember(deviceVMs) { computeDeviceCapabilities(deviceVMs) }
  val availableAutomations = predefinedAutomations.filter { it.isAvailable(capabilities) }

  SuggestListSection(homeAppVM = homeAppVM)

  if (availableAutomations.isNotEmpty()) {
    PredefinedListSection(homeAppVM, availableAutomations)
  }

  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text("Candidates", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }

  for (candidate in candidates) {
    if (candidate.name != "[]")
      CandidateListItem(candidate, homeAppVM)
  }
}

@Composable
fun BlankListItem(homeAppVM: HomeAppViewModel) {
  val scope: CoroutineScope = rememberCoroutineScope()

  Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
    Column(Modifier.fillMaxWidth().clickable {
      scope.launch {
        homeAppVM.selectedDraftVM.emit(
          DraftViewModel(
            candidateVM = null,
            automationType = DraftViewModel.AutomationType.CUSTOM
          )
        )
      }
    }) {
      Text(stringResource(R.string.candidate_title_new), fontSize = 20.sp)
      Text(stringResource(R.string.candidate_description_new), fontSize = 16.sp)
    }
  }
}

private data class PredefinedAutomation(
  val title: String,
  val description: String,
  val automationType: DraftViewModel.AutomationType,
  val isAvailable: (DeviceCapabilities) -> Boolean,
  val onClick: suspend (CoroutineScope, HomeAppViewModel) -> Unit,
)

private val predefinedAutomations = listOf(
  PredefinedAutomation(
    title = "On/Off Automation",
    description = "Simple automation that turns off a light when another light is turned off.",
    automationType = DraftViewModel.AutomationType.ON_OFF,
    isAvailable = { it.hasEnoughLights }
  ) { scope, vm ->
    scope.launch { vm.showPredefinedOnOffDraft() }
  },
  PredefinedAutomation(
    title = "Speaker and Fan Automation",
    description = "Say 'Hey Google, I can't sleep' to play ocean sounds, turn on fan and outlet.",
    automationType = DraftViewModel.AutomationType.SPEAKER_AND_FAN,
    isAvailable = { it.hasRequiredDevicesForSleep }
  ) { scope, vm ->
    scope.launch { vm.showPredefinedSpeakerAndFanDraft() }
  },
  PredefinedAutomation(
    title = "Lights/Thermostat Automation",
    description = "Turn on lights and set thermostat to Auto mode when door is unlocked.",
    automationType = DraftViewModel.AutomationType.LIGHT_AND_THERMOSTAT,
    isAvailable = { it.hasLightsAndThermostat }
  ) { scope, vm ->
    scope.launch { vm.showPredefinedLightAndThermostatDraft() }
  },
  PredefinedAutomation(
    title = "Window Covering Automation",
    description = "Close blinds when temperature < 15°C (59°F) AND it's dark outside.",
    automationType = DraftViewModel.AutomationType.WINDOW_COVERING,
    isAvailable = { it.hasWindowCoveringDevices }
  ) { scope, vm ->
    scope.launch { vm.showPredefinedWindowCoveringDraft() }
  },
  PredefinedAutomation(
    title = "Light and TV Periodic Automation",
    description = "Turn on lights and TV when motion detected to deter intruders.",
    automationType = DraftViewModel.AutomationType.LIGHT_AND_TV_PERIODIC,
    isAvailable = { it.hasRequiredDevicesForLightAndTVPeriodic }
  ) { scope, vm ->
    scope.launch { vm.showPredefinedLightAndTVPeriodicDraft() }
  },
  PredefinedAutomation(
    title = "Camera Scene Detected Automation",
    description = "Turns on a light when a person is detected by the camera (natural language).",
    automationType = DraftViewModel.AutomationType.CUSTOM,
    isAvailable = { it.hasCameraAndLight }
  ) { scope, vm ->
    scope.launch { vm.showPredefinedCameraSceneDetectedLightDraft() }
  },
)

@Composable
private fun PredefinedListSection(
  homeAppVM: HomeAppViewModel,
  availableAutomations: List<PredefinedAutomation>,
) {
  val scope = rememberCoroutineScope()

  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text("Predefined", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

    availableAutomations.forEach { automation ->
      Box(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
        Column(
          Modifier
            .fillMaxWidth()
            .clickable {
              scope.launch {
                automation.onClick(scope, homeAppVM)
              }
            }
        ) {
          Text(automation.title, fontSize = 20.sp)
          Text(automation.description, fontSize = 16.sp)
        }
      }
    }
  }
}

@Composable
fun CandidateListItem(candidateVM: CandidateViewModel, homeAppVM: HomeAppViewModel) {
  val scope: CoroutineScope = rememberCoroutineScope()

  Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
    Column(Modifier.fillMaxWidth().clickable {
      scope.launch {
        homeAppVM.selectedDraftVM.emit(
          DraftViewModel(
            candidateVM = candidateVM,
            automationType = DraftViewModel.AutomationType.CUSTOM
          )
        )
      }
    }) {
      Text(candidateVM.name, fontSize = 20.sp)
      Text(candidateVM.description, fontSize = 16.sp)
    }
  }
}
