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
package com.example.googlehomeapisampleapp.camera

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.googlehomeapisampleapp.RuntimePermissionsManager
import com.example.googlehomeapisampleapp.camera.timeline.CameraTimeline
import com.example.googlehomeapisampleapp.camera.timeline.CameraTimelineUiState
import com.google.home.google.ChimeTrait
import com.google.home.google.ZoneManagementTrait

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraStreamView(
  playerState: CameraStreamState,
  paddingValues: PaddingValues = PaddingValues(),
  isCameraOn: Boolean = false,
  isTalkbackSupported: Boolean = false,
  isTalkbackEnabled: Boolean = false,
  isAudioRecording: Boolean = false,
  deviceInfo: DeviceInfo? = null,
  isToggleRecordingInProgress: Boolean = false,
  isToggleAudioRecordingInProgress: Boolean = false,
  isDoorbell: Boolean = false,
  isChimeToggleSupported: Boolean = false,
  isChimeEnabled: Boolean = false,
  chimeType: ChimeTrait.ExternalChimeType = ChimeTrait.ExternalChimeType.Electronic,
  cameraTimelineUiState: CameraTimelineUiState? = null,
  recordingModeOptions: List<RecordingModeOption> = emptyList(),
  selectedRecordingModeIndex: Int? = null,
  onSetRecordingMode: (Int) -> Unit = {},
  activityZones: List<ActivityZone> = emptyList(),
  twoDCartesianMax: ZoneManagementTrait.TwoDCartesianVertexStruct? = null,
  zoneUpdateStatus: ZoneUpdateStatus = ZoneUpdateStatus.Idle,
  onAddActivityZone: () -> Unit = {},
  onDeleteActivityZone: (Int) -> Unit = {},
  onTurnCameraOn: (Boolean) -> Unit = {},
  onSetTalkback: (Boolean) -> Unit = {},
  onSetAudioRecording: (Boolean) -> Unit = {},
  onToggleChime: () -> Unit = {},
  onSetChimeType: (ChimeTrait.ExternalChimeType) -> Unit = {},
  onRetry: () -> Unit = {},
  onSurfaceCreated: (Surface) -> Unit = {},
  onSurfaceDestroyed: () -> Unit = {},
  onShowSnackbar: (String) -> Unit = {},
) {
  val canToggleAudio by remember(isCameraOn, isToggleAudioRecordingInProgress) {
    derivedStateOf { isCameraOn && !isToggleAudioRecordingInProgress }
  }

  val sheetState = rememberModalBottomSheetState()
  var showBottomSheet by remember { mutableStateOf(false) }

  val isCurrentlyStreaming = (playerState == CameraStreamState.STREAMING_WITH_TALKBACK ||
          playerState == CameraStreamState.STREAMING_WITHOUT_TALKBACK) && !isToggleRecordingInProgress

  // Permission Logic
  var microphonePermissionGranted by remember { mutableStateOf(false) }
  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { isGranted ->
      microphonePermissionGranted = isGranted
      if (!isGranted) {
        onShowSnackbar("Microphone permission denied. Talkback will not be available.")
      }
    }
  )

  val activity = LocalActivity.current as? ComponentActivity
  val permissionsManager = remember(activity) {
    activity?.let {
      RuntimePermissionsManager(it, launcher) { isGranted ->
        microphonePermissionGranted = isGranted
      }
    }
  }

  Scaffold(
    modifier = Modifier.padding(paddingValues).fillMaxSize().testTag("CameraStreamScreen"),
    containerColor = Color.Black
  ) { _ ->
    Column(modifier = Modifier.fillMaxSize()) {
      BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
      ) {
        val screenMaxHeight = maxHeight

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .run {
              if (cameraTimelineUiState != null) {
                // When timeline is present, cap video at 50% of screen height
                heightIn(max = screenMaxHeight * 0.5f)
              } else {
                // When no timeline, use original 4:3 aspect ratio
                aspectRatio(4f / 3f)
              }
            },
          contentAlignment = Alignment.Center
        ) {
          PunchThroughSurface(
            isVisible = isCurrentlyStreaming,
            onSurfaceCreated = onSurfaceCreated,
            onSurfaceDestroyed = onSurfaceDestroyed,
            modifier = Modifier.fillMaxSize()
          )
          LiveStreamOverlay(playerState, isCurrentlyStreaming, onRetry)
        }
      }
      if (cameraTimelineUiState != null) {
        CameraTimeline(
          uiState = cameraTimelineUiState,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        )
      }

      if (isTalkbackSupported && isCurrentlyStreaming) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center
        ) {
          MicrophoneOverlay(
            isEnabled = isTalkbackEnabled,
            onToggle = { requestedEnabled ->
              if (requestedEnabled && !permissionsManager?.hasMicrophonePermission()!!) {
                permissionsManager.requestMicrophonePermission()
              } else {
                onSetTalkback(requestedEnabled)
              }
            }
          )
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .run {
            if (cameraTimelineUiState == null) {
              // When no timeline, give FAB its own weighted space
              weight(1f).padding(26.dp)
            } else {
              // When timeline is present, just add padding
              padding(26.dp)
            }
          },
        contentAlignment = Alignment.BottomEnd
      ) {
        FloatingActionButton(onClick = { showBottomSheet = true }) {
          Icon(Icons.Filled.Settings, "Settings")
        }
      }
    }
  }

  if (showBottomSheet) {
    ModalBottomSheet(onDismissRequest = { showBottomSheet = false }, sheetState = sheetState) {
      Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        // --- CAMERA POWER ---
        ListItem(
          headlineContent = { Text("Camera Power") },
          supportingContent = { Text(if (isCameraOn) "On" else "Off") },
          trailingContent = {
            if (isToggleRecordingInProgress) {
              CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
              Switch(checked = isCameraOn, onCheckedChange = onTurnCameraOn)
            }
          }
        )

        // --- AUDIO RECORDING ---
        ListItem(
          headlineContent = { Text("Audio Recording") },
          supportingContent = { Text(if (isAudioRecording) "Saving audio" else "Not saving") },
          trailingContent = {
            if (isToggleAudioRecordingInProgress) {
              CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
              Switch(checked = isAudioRecording, onCheckedChange = onSetAudioRecording, enabled = canToggleAudio)
            }
          }
        )

        // RECORDING MODE
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
          "Recording Settings",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val availableModes = recordingModeOptions.filter { it.available }
        val currentMode = recordingModeOptions.firstOrNull { it.index == selectedRecordingModeIndex }
        var showRecordingModeMenu by remember { mutableStateOf(false) }

        ListItem(
          headlineContent = { Text("Recording Mode") },
          supportingContent = {
            Text("Current: ${currentMode?.readableString ?: "Unknown"}")
          },
          modifier = Modifier.clickable(enabled = availableModes.isNotEmpty()) {
            showRecordingModeMenu = true
          },
          trailingContent = {
            Box {
              Icon(Icons.Default.ChevronRight, null)
              DropdownMenu(
                expanded = showRecordingModeMenu,
                onDismissRequest = { showRecordingModeMenu = false }
              ) {
                availableModes.forEach { option ->
                  DropdownMenuItem(
                    text = { Text(option.readableString) },
                    onClick = {
                      onSetRecordingMode(option.index)
                      showRecordingModeMenu = false
                    }
                  )
                }
              }
            }
          }
        )

        // --- ACTIVITY ZONES ---
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
          "Activity Zones",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val modifiableZones = activityZones.filter { it.modifiable }

        if (modifiableZones.isEmpty()) {
          ListItem(
            headlineContent = { Text("No zones configured") },
            supportingContent = { Text("Add a zone to filter events by area") }
          )
        } else {
          modifiableZones.forEach { zone ->
            ListItem(
              headlineContent = { Text(zone.zoneName.ifBlank { "Zone ${zone.zoneId}" }) },
              supportingContent = { Text("${zone.vertices.size} vertices · ${zone.color.displayName}") },
              leadingContent = {
                Box(
                  modifier = Modifier
                    .size(16.dp)
                    .background(
                      color = try {
                        Color(android.graphics.Color.parseColor(zone.color.hexString))
                      } catch (e: Exception) {
                        Color.Gray
                      },
                      shape = CircleShape
                    )
                )
              },
              trailingContent = {
                if (zoneUpdateStatus == ZoneUpdateStatus.InProgress) {
                  CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                  IconButton(onClick = { zone.zoneId?.let { onDeleteActivityZone(it) } }) {
                    Icon(
                      Icons.Default.Delete,
                      contentDescription = "Delete zone",
                      tint = MaterialTheme.colorScheme.error
                    )
                  }
                }
              }
            )
          }
        }

        // There is currently a limit of 4 activity zones
        val canAddZone = modifiableZones.size < 4 &&
                zoneUpdateStatus != ZoneUpdateStatus.InProgress

        ListItem(
          headlineContent = {
            Text(
              text = if (modifiableZones.size >= 4) "Maximum 4 zones reached" else "Add Activity Zone",
              color = if (canAddZone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
          },
          modifier = Modifier.clickable(enabled = canAddZone) { onAddActivityZone() },
          leadingContent = {
            Icon(
              Icons.Default.Add,
              contentDescription = null,
              tint = if (canAddZone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
          }
        )

        // --- DOORBELL CHIME ---
        if (isDoorbell) {
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
          Text(
            "Indoor Chime Settings",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
          )

          ListItem(
            headlineContent = {
              Text(
                text = "Indoor Chime Toggle (Software)",
                color = if (isChimeToggleSupported) Color.Unspecified else MaterialTheme.colorScheme.outline
              )
            },
            supportingContent = {
              Text(
                if (isChimeToggleSupported) {
                  if (isChimeEnabled) "On" else "Off"
                } else {
                  "Not supported by this hardware"
                }
              )
            },
            trailingContent = {
              Switch(
                checked = isChimeEnabled,
                onCheckedChange = { onToggleChime() },
                enabled = isChimeToggleSupported
              )
            },
            modifier = Modifier.clickable(enabled = isChimeToggleSupported) { onToggleChime() }
          )

          var showTypeMenu by remember { mutableStateOf(false) }

          ListItem(
            headlineContent = { Text("Physical Chime Type") },
            supportingContent = {
              val label = when (chimeType) {
                ChimeTrait.ExternalChimeType.None -> "None (Chime Disabled)"
                ChimeTrait.ExternalChimeType.Mechanical -> "Mechanical"
                ChimeTrait.ExternalChimeType.Electronic -> "Electronic"
                else -> "Unknown"
              }
              Text("Current: $label")
            },
            modifier = Modifier.clickable { showTypeMenu = true },
            trailingContent = {
              Box {
                Icon(Icons.Default.ChevronRight, null)

                DropdownMenu(
                  expanded = showTypeMenu,
                  onDismissRequest = { showTypeMenu = false }
                ) {
                  DropdownMenuItem(
                    text = { Text("None (Disabled)") },
                    onClick = {
                      onSetChimeType(ChimeTrait.ExternalChimeType.None)
                      showTypeMenu = false
                    }
                  )
                  DropdownMenuItem(
                    text = { Text("Mechanical") },
                    onClick = {
                      onSetChimeType(ChimeTrait.ExternalChimeType.Mechanical)
                      showTypeMenu = false
                    }
                  )
                  DropdownMenuItem(
                    text = { Text("Electronic") },
                    onClick = {
                      onSetChimeType(ChimeTrait.ExternalChimeType.Electronic)
                      showTypeMenu = false
                    }
                  )
                }
              }
            }
          )
        }
          // DEVICE INFORMATION
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

          deviceInfo?.let { info ->
              ListItem(
                  headlineContent = {
                      Text(
                          "Model",
                          fontWeight = FontWeight.Bold
                      )
                  },
                  supportingContent = { Text(info.model) }
              )

              ListItem(
                  headlineContent = {
                      Text(
                          "Software Version",
                          fontWeight = FontWeight.Bold
                      )
                  },
                  supportingContent = { Text(info.softwareVersion) }
              )
          } ?: run {
              ListItem(
                  headlineContent = {
                      Text(
                          "Device Information",
                          fontWeight = FontWeight.Bold
                      )
                  },
                  supportingContent = { Text("Not available") }
              )
          }
      }
    }
  }
}

@Composable
fun MicrophoneOverlay(isEnabled: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
  FloatingActionButton(onClick = { onToggle(!isEnabled) }, shape = CircleShape, modifier = modifier) {
    Icon(if (isEnabled) Icons.Default.Mic else Icons.Default.MicOff, null)
  }
}

@Composable
fun LiveStreamOverlay(state: CameraStreamState, isStreaming: Boolean, onRetry: () -> Unit) {
  val isTransitioning = state in listOf(
    CameraStreamState.STOPPING,
    CameraStreamState.INITIALIZED,
    CameraStreamState.NOT_STARTED
  )
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    when {
      state == CameraStreamState.ERROR -> {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("Connection Failed", color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
          Button(onClick = onRetry) { Text("Retry Connection") }
        }
      }
      state == CameraStreamState.READY_OFF -> {
        Text("Camera is Off", color = Color.White)
      }
      isTransitioning -> {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator(color = Color.White)
          Text("Reconnecting...", color = Color.White)
        }
      }
      state == CameraStreamState.READY_ON || state == CameraStreamState.STARTING || !isStreaming -> {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator(color = Color.White)
          Text("Loading...", color = Color.White, modifier = Modifier.padding(top = 8.dp))
        }
      }
    }
  }
}

@Composable
private fun PunchThroughSurface(
  isVisible: Boolean,
  onSurfaceCreated: (Surface) -> Unit,
  onSurfaceDestroyed: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentOnSurfaceCreated by rememberUpdatedState(onSurfaceCreated)
  val currentOnSurfaceDestroyed by rememberUpdatedState(onSurfaceDestroyed)
  AndroidView(
    factory = { context ->
      SurfaceView(context).apply {
        setZOrderMediaOverlay(true)
        holder.addCallback(object : SurfaceHolder.Callback {
          override fun surfaceCreated(h: SurfaceHolder) { currentOnSurfaceCreated(h.surface) }
          override fun surfaceChanged(d: SurfaceHolder, f: Int, w: Int, h: Int) {}
          override fun surfaceDestroyed(h: SurfaceHolder) { currentOnSurfaceDestroyed() }
        })
      }
    },
    update = { it.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE },
    modifier = modifier.fillMaxSize()
  )
}