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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.googlehomeapisampleapp.view.shared.TabbedMenuView
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel

@Composable
fun DebugView(homeAppVM: HomeAppViewModel) {
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
          onClick = { /* No functionality yet */ },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Dump Structure")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
          onClick = { /* No functionality yet */ },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Dump Automation")
        }
      }

      TabbedMenuView(homeAppVM)
    }
  }
}
