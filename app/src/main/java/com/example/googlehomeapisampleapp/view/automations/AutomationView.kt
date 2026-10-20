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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.googlehomeapisampleapp.MainActivity
import com.example.googlehomeapisampleapp.R
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.AutomationViewModel
import com.google.home.HomeDevice
import com.google.home.automation.Action
import com.google.home.automation.Command
import com.google.home.automation.Starter
import com.google.home.automation.UnknownCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AutomationView(homeAppVM: HomeAppViewModel) {
  val scope: CoroutineScope = rememberCoroutineScope()
  val automation: AutomationViewModel = homeAppVM.selectedAutomationVM.collectAsState().value!!

  val automationName: String = automation.name.collectAsState().value
  val automationDescription: String = automation.description.collectAsState().value
  val automationStarters: List<Starter> = automation.starters.collectAsState().value
  val automationActions: List<Action> = automation.actions.collectAsState().value
  val automationIsActive: Boolean = automation.isActive.collectAsState().value
  val automationIsValid: Boolean = automation.isValid.collectAsState().value
  val isManuallyExecutable: Boolean = automation.isManuallyExecutable.collectAsState().value

  BackHandler {
    scope.launch { homeAppVM.selectedAutomationVM.emit(null) }
  }

  Box(modifier = Modifier.fillMaxSize()) {

    Column {
      Spacer(Modifier.height(64.dp))

      Row(
        horizontalArrangement = Arrangement.Start, modifier = Modifier
          .padding(horizontal = 16.dp)
          .fillMaxWidth()
      ) {
        Text(text = "" + automationName, fontSize = 32.sp)
      }

      Column(
        modifier = Modifier
          .verticalScroll(rememberScrollState())
          .weight(weight = 1f, fill = false)
      ) {

        Column(
          Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
        ) {
          Text(
            stringResource(R.string.automation_title_description),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
          )
        }

        Column(
          Modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .fillMaxWidth()
        ) {
          Text(automationDescription, fontSize = 16.sp, fontWeight = FontWeight.Normal)
        }

        Column(
          Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
        ) {
          Text(
            stringResource(R.string.automation_title_starters),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
          )
        }

        for (starter in automationStarters) {
          Column(
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 24.dp, vertical = 8.dp)
          ) {
            val starterDevice: HomeDevice? = starter.entity as? HomeDevice
            Text(
              starterDevice?.name ?: stringResource(R.string.automation_text_unknown),
              fontSize = 20.sp
            )
            Text(starter.trait.toString(), fontSize = 16.sp)
          }
        }

        Column(
          Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
        ) {
          Text(
            stringResource(R.string.automation_title_actions),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
          )
        }

        for (action in automationActions) {
          Column(
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 24.dp, vertical = 8.dp)
          ) {

            val actionDevice: HomeDevice? = action.entity as? HomeDevice
            Text(
              actionDevice?.name ?: stringResource(R.string.automation_text_unknown),
              fontSize = 20.sp
            )

            if (action.behavior !is UnknownCommand) {
              val actionCommand: Command = action.behavior as Command
              Text(actionCommand.trait.toString(), fontSize = 16.sp)
            }
          }
        }

        Column(
          Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
        ) {
          Text(
            stringResource(R.string.automation_title_status),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
          )
        }

        Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
          Column(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.automation_text_is_valid), fontSize = 20.sp)
            Text(automationIsValid.toString(), fontSize = 16.sp)
          }
        }

        Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
          Column(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.automation_text_is_active), fontSize = 20.sp)
            Text(automationIsActive.toString(), fontSize = 16.sp)
          }

          Switch(
            checked = automationIsActive,
            enabled = automationIsValid,
            modifier = Modifier.align(Alignment.CenterEnd),
            onCheckedChange = { state ->
              scope.launch {
                automation.automation.update { this.isActive = state }
                automation.isActive.emit(state)
              }
            }
          )
        }
      }
    }

    Column(
      modifier = Modifier
        .padding(16.dp)
        .align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally
    ) {
      if (!isManuallyExecutable) {
        Text(
          text = stringResource(R.string.automation_not_executable_message),
          color = Color.Gray,
          fontSize = 12.sp,
          fontStyle = FontStyle.Italic,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(bottom = 8.dp)
        )
      }

      Button(
        enabled = automationIsValid && isManuallyExecutable,
        onClick = {
          scope.launch {
            try {
              homeAppVM.selectedAutomationVM.value?.automation?.execute()
            } catch (e: Exception) {
              MainActivity.showError(this, e.message.toString())
            }
          }
        })
      { Text(stringResource(R.string.automation_button_execute)) }

      Button(
        enabled = true,
        onClick = {
          scope.launch {
            homeAppVM.selectedStructureVM.value!!.structure.deleteAutomation(automation.automation)
            homeAppVM.selectedAutomationVM.emit(null)
          }
        })
      { Text(stringResource(R.string.automation_button_delete)) }
    }

  }

}
