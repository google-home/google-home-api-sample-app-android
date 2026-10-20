package com.example.googlehomeapisampleapp.view.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.google.home.google.AreaPresenceStateTrait
import com.google.home.google.AreaAttendanceStateTrait

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresenceSettingsView(
  homeAppVM: HomeAppViewModel,
  onBack: () -> Unit
) {
  val userSettings by homeAppVM.selectedStructureUserPresenceSettings.collectAsState()
  val featureConsentStatus by homeAppVM.selectedStructureFeatureConsentStatus.collectAsState()

  val isConsented = featureConsentStatus == com.google.home.ConsentStatus.CONSENTED
  val optIn = userSettings?.presenceOptIn ?: true
  val isToggleOn = isConsented && optIn

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        homeAppVM.refreshPresenceSettings()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  val presenceState by homeAppVM.selectedStructureAreaPresenceState.collectAsState()
  val attendanceState by homeAppVM.selectedStructureAreaAttendanceState.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Presence Settings") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = "Area Presence Detection", style = MaterialTheme.typography.titleMedium)
          Text(
            text = "Allow this app to detect if you are home or away.",
            style = MaterialTheme.typography.bodySmall
          )
        }
        Switch(
          checked = isToggleOn,
          onCheckedChange = { targetState ->
            homeAppVM.setPresenceOptIn(targetState)
          },
          enabled = true
        )
      }

      if (userSettings == null && !isConsented) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Presence settings not supported for this structure or consent not granted.",
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall
        )
      }

      if (isToggleOn) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "Current Status", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        val presenceText = when (presenceState?.presenceState) {
          AreaPresenceStateTrait.PresenceState.PresenceStateOccupied -> "Home"
          AreaPresenceStateTrait.PresenceState.PresenceStateVacant -> "Away"
          AreaPresenceStateTrait.PresenceState.PresenceStateUnspecified -> "Unspecified"
          else -> "Unknown / Not Supported"
        }
        Text(text = "AreaPresenceState: $presenceText")

        val attendanceText = when (attendanceState?.attendanceState) {
          AreaAttendanceStateTrait.AttendanceState.AttendanceStateAllHouseholdMembers -> "All Members Home"
          AreaAttendanceStateTrait.AttendanceState.AttendanceStateSomeHouseholdMembers -> "Some Members Home"
          AreaAttendanceStateTrait.AttendanceState.AttendanceStateNoHouseholdMembers -> "No Members Home"
          AreaAttendanceStateTrait.AttendanceState.AttendanceStateUnknown -> "Unknown"
          AreaAttendanceStateTrait.AttendanceState.AttendanceStateUnspecified -> "Unspecified"
          else -> "Unknown / Not Supported"
        }
        Text(text = "AreaAttendanceState: $attendanceText")
      }

      Spacer(modifier = Modifier.height(32.dp))
      Text(text = "Privacy", style = MaterialTheme.typography.titleMedium)
      Spacer(modifier = Modifier.height(8.dp))
      Button(
        onClick = { homeAppVM.deleteSelectedStructureHistory() },
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Delete Presence History")
      }
    }
  }
}
