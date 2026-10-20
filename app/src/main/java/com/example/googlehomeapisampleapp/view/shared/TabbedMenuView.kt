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

package com.example.googlehomeapisampleapp.view.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.example.googlehomeapisampleapp.R
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel

@Composable
fun TabbedMenuView(homeAppVM: HomeAppViewModel) {
  Column(Modifier.imePadding().background(Color.hsv(0f, 0f, 0.90f))) {
    // Navigation bar for the tabbed menu:
    Row(modifier = Modifier.fillMaxWidth()) {
      Column(
        content = { DevicesButtonContent(homeAppVM) },
        modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp).clickable {
          homeAppVM.selectedTab.value = HomeAppViewModel.NavigationTab.DEVICES
        })
      Column(
        content = { AutomationsButtonContent(homeAppVM) },
        modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp).clickable {
          homeAppVM.selectedTab.value = HomeAppViewModel.NavigationTab.AUTOMATIONS
        })
      Column(
        content = { ActivityButtonContent(homeAppVM) },
        modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp).clickable {
          homeAppVM.clearHistorySelection()
          homeAppVM.selectedTab.value = HomeAppViewModel.NavigationTab.HISTORY
        })
    }
    // Spacer to offset the system gesture bars for edge-to-edge applications:
    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
  }
}

@Composable
fun DevicesButtonContent(homeAppVM: HomeAppViewModel) {
  val selectedTab: HomeAppViewModel.NavigationTab = homeAppVM.selectedTab.collectAsState().value
  val isSelected: Boolean = (selectedTab == HomeAppViewModel.NavigationTab.DEVICES)
  val buttonColor: Color = if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray

  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.icon_hub), "",
      Modifier.size(36.dp).background(Color.Transparent), tint = buttonColor
    )
    Text(stringResource(R.string.tab_button_devices), color = buttonColor)
  }
}

@Composable
fun AutomationsButtonContent(homeAppVM: HomeAppViewModel) {
  val selectedTab: HomeAppViewModel.NavigationTab = homeAppVM.selectedTab.collectAsState().value
  val isSelected: Boolean = (selectedTab == HomeAppViewModel.NavigationTab.AUTOMATIONS)
  val buttonColor: Color = if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray

  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.icon_automations), "",
      Modifier.size(36.dp).background(Color.Transparent), tint = buttonColor
    )
    Text(stringResource(R.string.tab_button_automations), color = buttonColor)
  }
}

@Composable
fun ActivityButtonContent(homeAppVM: HomeAppViewModel) {
  val selectedTab: HomeAppViewModel.NavigationTab = homeAppVM.selectedTab.collectAsStateWithLifecycle().value
  val selectedHistoryDevice = homeAppVM.selectedHistoryDeviceVM.collectAsStateWithLifecycle().value
  val isSelected: Boolean = (selectedTab == HomeAppViewModel.NavigationTab.HISTORY && selectedHistoryDevice == null)
  val buttonColor: Color = if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray

  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
    Icon(
      imageVector = Icons.Outlined.History, contentDescription = stringResource(R.string.tab_button_activity),
      modifier = Modifier.size(36.dp).background(Color.Transparent), tint = buttonColor
    )
    Text(stringResource(R.string.tab_button_activity), color = buttonColor)
  }
}