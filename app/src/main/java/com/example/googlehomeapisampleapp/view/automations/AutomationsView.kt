
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

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MoreVert
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
import com.example.googlehomeapisampleapp.BuildConfig
import com.example.googlehomeapisampleapp.R
import com.example.googlehomeapisampleapp.view.shared.TabbedMenuView
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.AutomationViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.StructureViewModel
import com.google.home.automation.Action
import com.google.home.automation.Starter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AutomationsAccountButton (homeAppVM: HomeAppViewModel) {
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

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Revoke Permissions") },
                onClick = {
                    expanded = false
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://myaccount.google.com/connections/link?project_number=${BuildConfig.GOOGLE_CLOUD_PROJECT_ID}")
                    )
                    homeAppVM.homeApp.context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            )
        }
    }
}

@Composable
fun AutomationsView (homeAppVM: HomeAppViewModel) {
    val scope: CoroutineScope = rememberCoroutineScope()
    var expanded: Boolean by remember { mutableStateOf(false) }

    val structureVMs: List<StructureViewModel> = homeAppVM.structureVMs.collectAsState().value
    val selectedStructureVM: StructureViewModel? = homeAppVM.selectedStructureVM.collectAsState().value
    val structureName: String = selectedStructureVM?.name ?: stringResource(R.string.automations_text_loading)
    
    Column {
        AutomationsTopBar("", listOf { AutomationsAccountButton(homeAppVM) })

        Box (modifier = Modifier.weight(1f)) {

            Column {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {

                    if (structureVMs.size > 1) {
                        TextButton(onClick = { expanded = true }) {
                            Text(text = structureName + " ▾", fontSize = 32.sp)
                        }
                    } else {
                        TextButton(onClick = { expanded = true }) {
                            Text(text = structureName, fontSize = 32.sp)
                        }
                    }
                }

                Row (horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Box {
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            for (structure in structureVMs) {
                                DropdownMenuItem(
                                    text = { Text(structure.name) },
                                    onClick = {
                                        scope.launch { homeAppVM.selectedStructureVM.emit(structure) }
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(weight = 1f, fill = false)) {
                    AutomationListComponent(homeAppVM)
                }
            }

            Button(onClick = { homeAppVM.showCandidates() } , modifier = Modifier.padding(16.dp).align(Alignment.BottomEnd)) { Text("+ Create") }
        }

        TabbedMenuView(homeAppVM)
    }
}

@Composable
fun AutomationListItem (automationVM: AutomationViewModel, homeAppVM: HomeAppViewModel) {
    val scope: CoroutineScope = rememberCoroutineScope()

    val automationName: String = automationVM.name.collectAsState().value
    val automationStarters: List<Starter> = automationVM.starters.collectAsState().value
    val automationActions: List<Action> = automationVM.actions.collectAsState().value

    val status: String = "" + automationStarters.size + " starters" +
            " ● " + automationActions.size + " actions"

    Column (Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth()
        .clickable { scope.launch { homeAppVM.selectedAutomationVM.emit(automationVM) } }) {
        Text(automationName, fontSize = 20.sp)
        Text(status, fontSize = 16.sp)
    }
}

@Composable
fun AutomationListComponent (homeAppVM: HomeAppViewModel) {

    val selectedStructureVM: StructureViewModel = homeAppVM.selectedStructureVM.collectAsState().value ?: return

    val selectedAutomationVMs: List<AutomationViewModel> =
        selectedStructureVM.automationVMs.collectAsState().value

    Column (Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        Text(stringResource(R.string.automations_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }

    for (automationVM in selectedAutomationVMs) {
        AutomationListItem(automationVM, homeAppVM)
    }
}

@Composable
fun AutomationsTopBar (title: String, buttons: List<@Composable () -> Unit>) {
    Box (Modifier.height(64.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
        Row (Modifier.height(64.dp).fillMaxWidth().background(Color.Transparent), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(title, fontSize = 24.sp)
        }

        Row (Modifier.height(64.dp).fillMaxWidth().background(Color.Transparent), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            for (button in buttons) {
                button()
            }
        }
    }
}
