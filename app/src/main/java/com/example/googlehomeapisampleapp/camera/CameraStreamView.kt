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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.googlehomeapisampleapp.R
import com.example.googlehomeapisampleapp.RuntimePermissionsManager
import com.example.googlehomeapisampleapp.camera.timeline.CameraTimeline
import com.example.googlehomeapisampleapp.camera.timeline.CameraTimelineUiState
import com.google.home.google.ChimeTrait
import com.google.home.google.ZoneManagementTrait

import coil3.ImageLoader

/**
 * Holds general camera stream settings, chime traits, timeline, and AI perception state.
 */
data class CameraStreamOptionsState(
  val isCameraOn: Boolean = false,
  val isTalkbackSupported: Boolean = false,
  val isTalkbackEnabled: Boolean = false,
  val isAudioRecording: Boolean = false,
  val isToggleRecordingInProgress: Boolean = false,
  val isToggleAudioRecordingInProgress: Boolean = false,
  val isDoorbell: Boolean = false,
  val isChimeToggleSupported: Boolean = false,
  val isChimeEnabled: Boolean = false,
  val chimeType: ChimeTrait.ExternalChimeType = ChimeTrait.ExternalChimeType.Electronic,
  val cameraTimelineUiState: CameraTimelineUiState? = null,
  val recordingModeOptions: List<RecordingModeOption> = emptyList(),
  val selectedRecordingModeIndex: Int? = null,
  val videoAnalysisControllers: List<VideoAnalysisController> = emptyList(),
  val isToggleAiFeaturesInProgress: Boolean = false,
)

/**
 * Holds Activity Zone canvas, snapshot URLs, and zone list state for the stream view.
 */
data class ActivityZoneStreamState(
  val activityZones: List<ActivityZone> = emptyList(),
  val twoDCartesianMax: ZoneManagementTrait.TwoDCartesianVertexStruct? = null,
  val zoneUpdateStatus: ZoneUpdateStatus = ZoneUpdateStatus.Idle,
  val snapshotUrl: String? = null,
  val authenticatedImageLoader: ImageLoader? = null,
  val isFetchingLiveSnapshot: Boolean = false,
)

/**
 * Encapsulates all action callbacks for stream controls and activity zone operations.
 */
data class CameraStreamActions(
  val onSetRecordingMode: (Int) -> Unit = {},
  val onRefreshLiveSnapshot: () -> Unit = {},
  val onAddActivityZone: () -> Unit = {},
  val onDeleteActivityZone: (Int) -> Unit = {},
  val onNavigateToActivityZone: () -> Unit = {},
  val onNavigateToFamiliarFace: () -> Unit = {},
  val onSetAiFeaturesEnabled: (VideoAnalysisController, Boolean) -> Unit = { _, _ -> },
  val onTurnCameraOn: (Boolean) -> Unit = {},
  val onSetTalkback: (Boolean) -> Unit = {},
  val onSetAudioRecording: (Boolean) -> Unit = {},
  val onToggleChime: () -> Unit = {},
  val onSetChimeType: (ChimeTrait.ExternalChimeType) -> Unit = {},
  val onRetry: () -> Unit = {},
  val onSurfaceCreated: (Surface) -> Unit = {},
  val onSurfaceDestroyed: () -> Unit = {},
  val onShowSnackbar: (String) -> Unit = {},
)

/**
 * Primary stream screen composable accepting structured state holder and action callback objects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraStreamView(
  playerState: CameraStreamState,
  optionsState: CameraStreamOptionsState = CameraStreamOptionsState(),
  activityZoneState: ActivityZoneStreamState = ActivityZoneStreamState(),
  actions: CameraStreamActions = CameraStreamActions(),
  paddingValues: PaddingValues = PaddingValues(),
  modifier: Modifier = Modifier,
) {
  val canToggleAudio by remember(optionsState.isCameraOn, optionsState.isToggleAudioRecordingInProgress) {
    derivedStateOf { optionsState.isCameraOn && !optionsState.isToggleAudioRecordingInProgress }
  }

  val sheetState = rememberModalBottomSheetState()
  var showBottomSheet by rememberSaveable { mutableStateOf(false) }

  val isCurrentlyStreaming = (playerState == CameraStreamState.STREAMING_WITH_TALKBACK ||
          playerState == CameraStreamState.STREAMING_WITHOUT_TALKBACK) && !optionsState.isToggleRecordingInProgress

  // Permission Logic
  var microphonePermissionGranted by rememberSaveable { mutableStateOf(false) }
  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { isGranted ->
      microphonePermissionGranted = isGranted
      if (!isGranted) {
        actions.onShowSnackbar("Microphone permission denied. Talkback will not be available.")
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
    modifier = modifier.padding(paddingValues).fillMaxSize().testTag("CameraStreamScreen"),
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
              if (optionsState.cameraTimelineUiState != null) {
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
            onSurfaceCreated = actions.onSurfaceCreated,
            onSurfaceDestroyed = actions.onSurfaceDestroyed,
            modifier = Modifier.fillMaxSize()
          )
          LiveStreamOverlay(playerState, isCurrentlyStreaming, actions.onRetry)
        }
      }
      if (optionsState.cameraTimelineUiState != null) {
        CameraTimeline(
          uiState = optionsState.cameraTimelineUiState,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        )
      }

      if (optionsState.isTalkbackSupported && isCurrentlyStreaming) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center
        ) {
          MicrophoneOverlay(
            isEnabled = optionsState.isTalkbackEnabled,
            onToggle = { requestedEnabled ->
              if (requestedEnabled && permissionsManager?.hasMicrophonePermission() != true) {
                permissionsManager?.requestMicrophonePermission()
              } else {
                actions.onSetTalkback(requestedEnabled)
              }
            }
          )
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .run {
            if (optionsState.cameraTimelineUiState == null) {
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
          Icon(Icons.Filled.Menu, "Menu")
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
          supportingContent = { Text(if (optionsState.isCameraOn) "On" else "Off") },
          trailingContent = {
            Switch(
              checked = optionsState.isCameraOn,
              onCheckedChange = null,
              enabled = !optionsState.isToggleRecordingInProgress,
              thumbContent = if (optionsState.isToggleRecordingInProgress) {
                {
                  CircularProgressIndicator(
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                    strokeWidth = 2.dp
                  )
                }
              } else null
            )
          },
          modifier = Modifier.toggleable(
            value = optionsState.isCameraOn,
            enabled = !optionsState.isToggleRecordingInProgress,
            role = Role.Switch,
            onValueChange = actions.onTurnCameraOn
          )
        )

        // --- AUDIO RECORDING ---
        ListItem(
          headlineContent = { Text("Audio Recording") },
          supportingContent = { Text(if (optionsState.isAudioRecording) "Saving audio" else "Not saving") },
          trailingContent = {
            Switch(
              checked = optionsState.isAudioRecording,
              onCheckedChange = null,
              enabled = canToggleAudio && !optionsState.isToggleAudioRecordingInProgress,
              thumbContent = if (optionsState.isToggleAudioRecordingInProgress) {
                {
                  CircularProgressIndicator(
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                    strokeWidth = 2.dp
                  )
                }
              } else null
            )
          },
          modifier = Modifier.toggleable(
            value = optionsState.isAudioRecording,
            enabled = canToggleAudio && !optionsState.isToggleAudioRecordingInProgress,
            role = Role.Switch,
            onValueChange = actions.onSetAudioRecording
          )
        )

        // --- FAMILIAR FACE ---
        // Entry point to manage familiar face library and consent
        ListItem(
          headlineContent = { Text("Familiar Face") },
          supportingContent = { Text("Manage face library and consent settings") },
          trailingContent = {
            Icon(Icons.Default.ChevronRight,
            contentDescription = "Navigate to Familiar Face settings")
          },
          modifier = Modifier.clickable {
            // Dismiss the bottom sheet before navigating
            showBottomSheet = false
            // Trigger the navigation callback
            actions.onNavigateToFamiliarFace()
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

        val availableModes = optionsState.recordingModeOptions.filter { it.available }
        val currentMode = optionsState.recordingModeOptions.firstOrNull { it.index == optionsState.selectedRecordingModeIndex }
        var showRecordingModeMenu by rememberSaveable { mutableStateOf(false) }

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
                      actions.onSetRecordingMode(option.index)
                      showRecordingModeMenu = false
                    }
                  )
                }
              }
            }
          }
        )

        // --- ACTIVITY ZONES ---
        // Section header and controls for viewing, editing, and adding Activity Zones via bottom sheet
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
          "Activity Zones",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val modifiableZones = activityZoneState.activityZones.filter { it.modifiable }

        // ListItem option for navigating to the full Activity Zone canvas setup screen
        ListItem(
          headlineContent = { Text("Configure Activity Zones") },
          supportingContent = { Text("${modifiableZones.size} zones configured · Edit on camera snapshot canvas") },
          trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = "Open Activity Zone Configuration")
          },
          modifier = Modifier.clickable {
            showBottomSheet = false
            actions.onNavigateToActivityZone()
          }
        )

        val canAddZone = modifiableZones.size < MAX_ACTIVITY_ZONES &&
                activityZoneState.zoneUpdateStatus != ZoneUpdateStatus.InProgress

        // ListItem option for directly adding a new Activity Zone
        ListItem(
          headlineContent = {
            Text(
              text = "Add a zone",
              color = if (canAddZone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
          },
          supportingContent = if (modifiableZones.size >= MAX_ACTIVITY_ZONES) {
            { Text("Four zones max.", color = MaterialTheme.colorScheme.error) }
          } else null,
          modifier = Modifier.clickable(enabled = canAddZone) {
            showBottomSheet = false
            actions.onAddActivityZone()
            actions.onNavigateToActivityZone()
          },
          leadingContent = {
            Icon(
              Icons.Default.Add,
              contentDescription = null,
              tint = if (canAddZone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
          }
        )

        // --- GEMINI AI FEATURES ---
        if (optionsState.videoAnalysisControllers.isNotEmpty()) {
          // Visual divider for camera AI Perception settings section
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
          Text(
            stringResource(R.string.ai_perception_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
          )

          optionsState.videoAnalysisControllers.forEach { controller ->
            val isAiFeaturesSupported by controller.isAiFeaturesSupported.collectAsState(false)
            val isAiFeaturesEnabled by controller.isAiFeaturesEnabled.collectAsState(false)
            val isToggleInteractive = isAiFeaturesSupported && !optionsState.isToggleAiFeaturesInProgress

            // ListItem control for toggling Gemini AI features for this endpoint
            ListItem(
              headlineContent = {
                Text(
                  controller.label,
                  color = if (isAiFeaturesSupported) Color.Unspecified else MaterialTheme.colorScheme.outline
                )
              },
              supportingContent = {
                Text(
                  if (isAiFeaturesSupported) {
                    if (isAiFeaturesEnabled) {
                      stringResource(R.string.ai_features_active_description)
                    } else {
                      stringResource(R.string.ai_features_disabled_description)
                    }
                  } else {
                    stringResource(R.string.ai_features_unsupported_description)
                  }
                )
              },
              trailingContent = {
                Switch(
                  checked = isAiFeaturesEnabled,
                  onCheckedChange = null,
                  enabled = isToggleInteractive,
                  thumbContent = if (optionsState.isToggleAiFeaturesInProgress) {
                    {
                      CircularProgressIndicator(
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                        strokeWidth = 2.dp
                      )
                    }
                  } else null
                )
              },
              modifier = Modifier.toggleable(
                value = isAiFeaturesEnabled,
                enabled = isToggleInteractive,
                role = Role.Switch,
                onValueChange = { enabled -> actions.onSetAiFeaturesEnabled(controller, enabled) }
              )
            )
          }
        }

        // --- DOORBELL CHIME ---
        if (optionsState.isDoorbell) {
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
                color = if (optionsState.isChimeToggleSupported) Color.Unspecified else MaterialTheme.colorScheme.outline
              )
            },
            supportingContent = {
              Text(
                if (optionsState.isChimeToggleSupported) {
                  if (optionsState.isChimeEnabled) "On" else "Off"
                } else {
                  "Not supported by this hardware"
                }
              )
            },
            trailingContent = {
              Switch(
                checked = optionsState.isChimeEnabled,
                onCheckedChange = { actions.onToggleChime() },
                enabled = optionsState.isChimeToggleSupported
              )
            },
            modifier = Modifier.clickable(enabled = optionsState.isChimeToggleSupported) { actions.onToggleChime() }
          )

          var showTypeMenu by rememberSaveable { mutableStateOf(false) }

          ListItem(
            headlineContent = { Text("Physical Chime Type") },
            supportingContent = {
              val label = when (optionsState.chimeType) {
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
                      actions.onSetChimeType(ChimeTrait.ExternalChimeType.None)
                      showTypeMenu = false
                    }
                  )
                  DropdownMenuItem(
                    text = { Text("Mechanical") },
                    onClick = {
                      actions.onSetChimeType(ChimeTrait.ExternalChimeType.Mechanical)
                      showTypeMenu = false
                    }
                  )
                  DropdownMenuItem(
                    text = { Text("Electronic") },
                    onClick = {
                      actions.onSetChimeType(ChimeTrait.ExternalChimeType.Electronic)
                      showTypeMenu = false
                    }
                  )
                }
              }
            }
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