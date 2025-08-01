
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
fun DraftView (homeAppVM: HomeAppViewModel) {
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

    Box (modifier = Modifier.fillMaxSize()) {

        Column {
            Spacer(Modifier.height(64.dp))

            // Title Text:
            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Text(text = stringResource(R.string.draft_title), fontSize = 32.sp)
            }
            // Name Input:
            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                TextField(value = draftName, onValueChange = { scope.launch { draftVM.name.emit(it) } }, label = {
                    Text(text = stringResource(R.string.draft_label_name)) })
            }
            // Description Input:
            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                TextField(value = draftDescription, onValueChange = { scope.launch { draftVM.description.emit(it) } }, label = {
                    Text(text = stringResource(R.string.draft_label_description)) })
            }
            // Spacer:
            Spacer(modifier = Modifier)
            // Expanding Container:
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(weight = 1f, fill = false)) {
                // Draft Starters:
                DraftStarterList(draftVM)
                // Draft Actions:
                DraftActionList(draftVM)
            }
        }

        // Button to save the draft automation:
        Row (modifier = Modifier.padding(16.dp).align(Alignment.BottomCenter)) {
            // Check on whether at least a starter and an action are selected:
            val isOptionsSelected: Boolean = starterVMs.isNotEmpty() && actionVMs.isNotEmpty()
            // Check on whether a name and description are provided:
            val isValueProvided: Boolean = draftName.isNotBlank() || draftDescription.isNotBlank()
            Button (
                enabled = isOptionsSelected && isValueProvided && !isPending.value,
                onClick = { homeAppVM.createAutomation(isPending) })
            { Text(if(!isPending.value) stringResource(R.string.draft_button_create) else stringResource(R.string.draft_text_creating)) }
        }
    }
}

@Composable
fun DraftStarterList (draftVM: DraftViewModel) {
    val scope: CoroutineScope = rememberCoroutineScope()
    // List of existing starters in this automation draft:
    val starterVMs: List<StarterViewModel> = draftVM.starterVMs.collectAsState().value

    // Starters title:
    Column (Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        Text(text = stringResource(R.string.draft_text_starters),
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
    // For each starter, creating a StarterItem view:
    for (starterVM in starterVMs)
        DraftStarterItem(starterVM, draftVM)
    // Button to add a new starter:
    Box (Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Column (Modifier.fillMaxWidth().clickable {
            scope.launch { draftVM.selectedStarterVM.emit(StarterViewModel(null)) }
        }) {
            Text(text = stringResource(R.string.draft_new_starter_name), fontSize = 20.sp)
            Text(text = stringResource(R.string.draft_new_starter_description), fontSize = 16.sp)
        }
    }
}

@Composable
fun DraftStarterItem (starterVM: StarterViewModel, draftVM: DraftViewModel) {
    val scope = rememberCoroutineScope()
    // Attributes of the starter item:
    val starterDeviceVM: DeviceViewModel = starterVM.deviceVM.collectAsState().value!!
    val starterTrait: TraitFactory<out Trait>? = starterVM.trait.collectAsState().value

    // Item to view and select the starter:
    Box (Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Column (Modifier.fillMaxWidth().clickable {
            scope.launch { draftVM.selectedStarterVM.emit(starterVM) }
        }) {
            val starterName by starterDeviceVM.name.collectAsState()
            Text(starterName, fontSize = 20.sp)
            Text(starterTrait.toString(), fontSize = 16.sp)
        }
    }
}

@Composable
fun DraftActionList (draftVM: DraftViewModel) {
    val scope: CoroutineScope = rememberCoroutineScope()
    // List of existing starters in this automation draft:
    val actionVMs: List<ActionViewModel> = draftVM.actionVMs.collectAsState().value

    // Actions title:
    Column (Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        Text(text = stringResource(R.string.draft_text_actions),
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
    // For each action, creating an ActionItem view:
    for (actionVM in actionVMs)
        DraftActionItem(actionVM, draftVM)
    // Button to add a new action:
    Box (Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Column (Modifier.fillMaxWidth().clickable {
            scope.launch { draftVM.selectedActionVM.emit(ActionViewModel(null)) }
        }) {
            Text(text = stringResource(R.string.draft_new_action_name), fontSize = 20.sp)
            Text(text = stringResource(R.string.draft_new_action_description), fontSize = 16.sp)
        }
    }
}

@Composable
fun DraftActionItem (actionVM: ActionViewModel, draftVM: DraftViewModel) {
    val scope: CoroutineScope = rememberCoroutineScope()
    // Attributes of the action item:
    val actionDeviceVM: DeviceViewModel = actionVM.deviceVM.collectAsState().value!!
    val actionTrait: Trait? = actionVM.trait.collectAsState().value

    // Item to view and select the action:
    Box (Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Column (Modifier.fillMaxWidth().clickable {
            scope.launch { draftVM.selectedActionVM.emit(actionVM) }
        }) {val actionName by actionDeviceVM.name.collectAsState()
            Text(actionName, fontSize = 20.sp)
            Text(actionTrait?.factory.toString(), fontSize = 16.sp)
        }
    }
}
