
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.googlehomeapisampleapp.R
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.CandidateViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.DraftViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun CandidatesView (homeAppVM: HomeAppViewModel) {
    val scope: CoroutineScope = rememberCoroutineScope()

    BackHandler {
        scope.launch { homeAppVM.selectedCandidateVMs.emit(null) }
    }

    Column {
        Spacer(Modifier.height(64.dp))

        Box (modifier = Modifier.weight(1f)) {

            Column {
                Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Text(text = stringResource(R.string.candidate_button_create), fontSize = 32.sp)
                }

                Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(weight = 1f, fill = false)) {
                    CandidateListComponent(homeAppVM)
                }
            }

        }

    }

}

@Composable
fun CandidateListComponent (homeAppVM: HomeAppViewModel) {
    val candidates: List<CandidateViewModel> = homeAppVM.selectedCandidateVMs.collectAsState().value ?: return

    Column (Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        Text("", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }

    BlankListItem(homeAppVM)

    Column (Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        Text("Candidates", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }

    for (candidate in candidates) {
        if(candidate.name != "[]")
            CandidateListItem(candidate, homeAppVM)
    }
}

@Composable
fun BlankListItem (homeAppVM: HomeAppViewModel) {
    val scope: CoroutineScope = rememberCoroutineScope()

    Box (Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Column (Modifier.fillMaxWidth().clickable {
            scope.launch { homeAppVM.selectedDraftVM.emit(DraftViewModel(null)) }
        }) {
            Text(stringResource(R.string.candidate_title_new), fontSize = 20.sp)
            Text(stringResource(R.string.candidate_description_new), fontSize = 16.sp)
        }
    }
}

@Composable
fun CandidateListItem (candidateVM: CandidateViewModel, homeAppVM: HomeAppViewModel) {
    val scope: CoroutineScope = rememberCoroutineScope()

    Box (Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Column (Modifier.fillMaxWidth().clickable {
// TODO: 5.2.1 - Set the selectedDraftVM to the selected candidate
//             scope.launch { homeAppVM.selectedDraftVM.emit(DraftViewModel(candidateVM)) }
        }) {
            Text(candidateVM.name, fontSize = 20.sp)
            Text(candidateVM.description, fontSize = 16.sp)
        }
    }
}
