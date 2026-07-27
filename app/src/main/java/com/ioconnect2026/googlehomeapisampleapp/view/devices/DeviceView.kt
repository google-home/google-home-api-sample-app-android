/* Copyright 2026 Google LLC

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

package com.ioconnect2026.googlehomeapisampleapp.view.devices

import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioconnect2026.googlehomeapisampleapp.MainActivity
import com.ioconnect2026.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.ioconnect2026.googlehomeapisampleapp.viewmodel.devices.DeviceStatusFormatter
import com.ioconnect2026.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.google.home.ConnectivityState
import com.google.home.DeviceType
import com.google.home.HomeException
import com.google.home.Trait
import com.google.home.matter.standard.BooleanState
import com.google.home.matter.standard.DoorLock
import com.google.home.matter.standard.DoorLockTrait
import com.google.home.matter.standard.FanControl
import com.google.home.matter.standard.FanControlTrait
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.LevelControlTrait
import com.google.home.matter.standard.MediaPlayback
import com.google.home.matter.standard.MediaPlaybackTrait
import com.google.home.matter.standard.OccupancySensing
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.SpeakerDevice
import com.google.home.matter.standard.TemperatureMeasurement
import com.google.home.matter.standard.WindowCovering
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun DeviceView(homeAppVM: HomeAppViewModel) {
  val scope: CoroutineScope = rememberCoroutineScope()
  val deviceVM: DeviceViewModel? by homeAppVM.selectedDeviceVM.collectAsState()

  BackHandler { homeAppVM.selectDevice(null) }

  deviceVM?.let { vm ->
    val context = LocalContext.current
    val name by vm.name.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
      vm.uiEventFlow.collect { event ->
        when (event) {
          is DeviceViewModel.UiEvent.ShowToast -> {
            Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
          }
          is DeviceViewModel.UiEvent.NavigateBack -> {
            homeAppVM.selectDevice(null)
          }
        }
      }
    }

    Column(modifier = Modifier.fillMaxSize()) {
      Spacer(Modifier.height(64.dp))

      // Unified Header Row
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = name,
          fontSize = 28.sp,
          modifier = Modifier.weight(1f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        IconButton(onClick = { showRenameDialog = true }) {
          Icon(Icons.Default.Edit, "Rename", tint = MaterialTheme.colorScheme.primary)
        }

        IconButton(onClick = { showDeleteDialog = true }) {
          Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.primary)
        }
      }

      // Body Content
      Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ControlListComponent(homeAppVM)
      }
    }

    // Dialogs
    if (showRenameDialog) {
      var newName by remember { mutableStateOf(name) }
      AlertDialog(
        onDismissRequest = { showRenameDialog = false },
        title = { Text("Rename Device") },
        text = {
          OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Name") },
          )
        },
        confirmButton = {
          TextButton(
            onClick = {
              vm.rename(newName)
              showRenameDialog = false
            }
          ) {
            Text("Save")
          }
        },
        dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } },
      )
    }

    if (showDeleteDialog) {
      AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("Delete Device") },
        text = { Text("Are you sure you want to delete $name?") },
        confirmButton = {
          TextButton(
            onClick = {
              showDeleteDialog = false
              vm.deleteDevice()
            }
          ) {
            Text("Delete")
          }
        },
        dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
      )
    }
  }
}

@Composable
fun ControlListComponent(homeAppVM: HomeAppViewModel) {

  val deviceVM: DeviceViewModel = homeAppVM.selectedDeviceVM.collectAsState().value ?: return
  val deviceType: DeviceType = deviceVM.type.collectAsState().value
  val deviceTypeName: String = deviceVM.typeName.collectAsState().value
  val deviceTraits: List<Trait> = deviceVM.traits.collectAsState().value

  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
    Text(deviceTypeName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }

  for (trait in deviceTraits) {
    ControlListItem(trait, deviceType)
  }
}

@Composable
fun ControlListItem(trait: Trait, type: DeviceType) {
  val scope: CoroutineScope = rememberCoroutineScope()

  // Make connectivity reactive by keying off the type changes
  // When deviceType updates (which happens in subscribeToType), this will recompose
  val isConnected =
    remember(type) {
      type.metadata.sourceConnectivity.connectivityState == ConnectivityState.ONLINE ||
        type.metadata.sourceConnectivity.connectivityState == ConnectivityState.PARTIALLY_ONLINE
    }

  Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
    when (trait) {
      is OnOff -> {
        Column(Modifier.fillMaxWidth()) {
          Text(trait.factory.toString(), fontSize = 20.sp)
          Text(DeviceStatusFormatter.getTraitStatus(trait, type), fontSize = 16.sp)
        }

        Switch(
          checked = (trait.onOff == true),
          modifier = Modifier.align(Alignment.CenterEnd),
          onCheckedChange = { state ->
            scope.launch {
              try {
                if (state) trait.on() else trait.off()
              } catch (e: HomeException) {
                MainActivity.showWarning(this, e, "Toggling device on/off failed")
              }
            }
          },
          enabled = isConnected,
        )
      }

      is LevelControl -> {
        val level = trait.currentLevel
        if (level != null) {
          val isSpeakerDevice = type.factory == SpeakerDevice

          if (isSpeakerDevice) {
            // For speakers, use 0-100 directly
            val currentVolumePercent = level.toInt().coerceIn(0, 100)

            Column {
              Spacer(Modifier.height(8.dp))
              Text("Volume Control", fontSize = 20.sp, fontWeight = FontWeight.Bold)
              Spacer(Modifier.height(16.dp))

              Box(Modifier.fillMaxWidth()) {
                Text("Volume", fontSize = 16.sp)
                Text(
                  text = "$currentVolumePercent%",
                  fontSize = 16.sp,
                  modifier = Modifier.align(Alignment.CenterEnd),
                )
              }

              LevelSlider(
                value = currentVolumePercent.toFloat(),
                low = 0f,
                high = 100f,
                steps = 0,
                modifier = Modifier.padding(top = 8.dp),
                onValueChange = { newValue ->
                  // Validate that value is in range
                  newValue in 0f..100f
                },
                onValueChangeFinished = { volumePercent ->
                  scope.launch {
                    try {
                      trait.moveToLevelWithOnOff(
                        level = volumePercent.toInt().toUByte(),
                        transitionTime = null,
                        optionsMask = LevelControlTrait.OptionsBitmap(),
                        optionsOverride = LevelControlTrait.OptionsBitmap(),
                      )
                    } catch (e: HomeException) {
                      MainActivity.showWarning(this, e, "Volume control failed")
                    }
                  }
                },
                isEnabled = isConnected,
              )
            }
          } else {
            // For other devices (lights), use 0-254 scale
            Text(trait.factory.toString(), fontSize = 20.sp)
            LevelSlider(
              value = level.toFloat(),
              low = 0f,
              high = 254f,
              steps = 0,
              modifier = Modifier.padding(top = 16.dp),
              onValueChangeFinished = { value: Float ->
                scope.launch {
                  try {
                    trait.moveToLevelWithOnOff(
                      level = value.toInt().toUByte(),
                      transitionTime = null,
                      optionsMask = LevelControlTrait.OptionsBitmap(),
                      optionsOverride = LevelControlTrait.OptionsBitmap(),
                    )
                  } catch (e: HomeException) {
                    MainActivity.showWarning(this, e, "Level control command failed")
                  }
                }
              },
              isEnabled = isConnected,
            )
          }
        }
      }

      is BooleanState -> {
        Column(Modifier.fillMaxWidth()) {
          Text(trait.factory.toString(), fontSize = 20.sp)
          Text(DeviceStatusFormatter.getTraitStatus(trait, type), fontSize = 16.sp)
        }
      }

      is OccupancySensing -> {
        Column(Modifier.fillMaxWidth()) {
          Text(trait.factory.toString(), fontSize = 20.sp)
          Text(DeviceStatusFormatter.getTraitStatus(trait, type), fontSize = 16.sp)
        }
      }

      is DoorLock -> {
        val lockState: DoorLockTrait.DlLockState? = trait.lockState
        val isLocked: Boolean = lockState == DoorLockTrait.DlLockState.Locked
        val requiresPin: Boolean? = trait.requirePinforRemoteOperation

        var showPinDialog by remember { mutableStateOf(false) }
        var pinInput by remember { mutableStateOf("") }
        var isUnlocking by remember { mutableStateOf(false) }
        var isProcessing by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxWidth()) {
          Text("Door lock", fontSize = 20.sp)
          Text(if (isLocked) "Locked" else "Unlocked", fontSize = 16.sp)
        }

        // PIN dialog
        if (showPinDialog) {
          AlertDialog(
            onDismissRequest = {
              if (!isProcessing) {
                showPinDialog = false
                pinInput = ""
              }
            },
            title = { Text(if (isUnlocking) "Enter PIN to Unlock" else "Enter PIN to Lock") },
            text = {
              OutlinedTextField(
                value = pinInput,
                onValueChange = {
                  if (it.all { c -> c.isDigit() } && it.length <= 8) pinInput = it
                },
                label = { Text("PIN (4-8 digits)") },
                singleLine = true,
                enabled = !isProcessing,
              )
            },
            confirmButton = {
              TextButton(
                onClick = {
                  isProcessing = true
                  scope.launch {
                    try {
                      val pin = pinInput.toByteArray()
                      if (isUnlocking) {
                        trait.unlockDoor { pinCode = pin }
                      } else {
                        trait.lockDoor { pinCode = pin }
                      }
                      showPinDialog = false
                      pinInput = ""
                    } catch (e: HomeException) {
                      MainActivity.showWarning(this, e, "Wrong PIN or operation failed")
                    } finally {
                      isProcessing = false
                    }
                  }
                },
                enabled = pinInput.length >= 4 && !isProcessing,
              ) {
                Text("OK")
              }
            },
            dismissButton = {
              TextButton(
                onClick = {
                  showPinDialog = false
                  pinInput = ""
                },
                enabled = !isProcessing,
              ) {
                Text("Cancel")
              }
            },
          )
        }

        Switch(
          checked = isLocked,
          modifier = Modifier.align(Alignment.CenterEnd),
          onCheckedChange = { shouldLock ->
            if (requiresPin == true) {
              // Device requires PIN - show dialog immediately
              isUnlocking = !shouldLock
              showPinDialog = true
            } else {
              // Device doesn't require PIN (or unknown) - attempt operation directly
              isProcessing = true
              scope.launch {
                try {
                  if (shouldLock) {
                    trait.lockDoor()
                  } else {
                    trait.unlockDoor()
                  }
                } catch (e: HomeException) {
                  // If operation fails due to credential requirement, show PIN dialog
                  val needsCredential =
                    e.message?.contains("19") == true ||
                      e.message?.contains("credential", ignoreCase = true) == true ||
                      e.message?.contains("authentication", ignoreCase = true) == true

                  if (needsCredential) {
                    isUnlocking = !shouldLock
                    showPinDialog = true
                  } else {
                    MainActivity.showWarning(this, e, "Operation failed")
                  }
                } finally {
                  isProcessing = false
                }
              }
            }
          },
          enabled = isConnected && !isProcessing,
        )
      }

      is FanControl -> {
        FanControlComponent(trait = trait, isConnected = isConnected)
      }

      is MediaPlayback -> {
        MediaPlaybackControlComponent(trait = trait, isConnected = isConnected)
      }

      is WindowCovering -> {
        WindowCoveringControlComponent(trait = trait, isConnected = isConnected)
      }

      is TemperatureMeasurement -> {
        Column(Modifier.fillMaxWidth()) {
          Text("Temperature", fontSize = 20.sp)
          val measuredValue = trait.measuredValue
          val tempText =
            if (measuredValue != null) {
              "%.1f°C".format(measuredValue / 100.0)
            } else {
              "--"
            }
          Text(tempText, fontSize = 16.sp)
        }
      }

      else -> return
    }
  }
}

@Composable
fun FanControlComponent(trait: FanControl, isConnected: Boolean) {
  val scope = rememberCoroutineScope()
  val fanMode = trait.fanMode
  val percentSetting = trait.percentSetting
  // Determine if this device supports fanMode or only percentSetting
  val supportsFanMode = fanMode != null
  val supportsPercent = percentSetting != null

  var sliderPosition by
    remember(fanMode, percentSetting) {
      mutableFloatStateOf(
        when {
          supportsPercent -> percentSetting!!.toFloat()
          supportsFanMode ->
            when (fanMode) {
              FanControlTrait.FanModeEnum.Off -> 0f
              FanControlTrait.FanModeEnum.Low -> 25f
              FanControlTrait.FanModeEnum.Medium -> 50f
              FanControlTrait.FanModeEnum.High -> 75f
              else -> 0f
            }
          else -> 0f
        }
      )
    }

  fun percentageToFanMode(percentage: Float): FanControlTrait.FanModeEnum {
    return when {
      percentage == 0f -> FanControlTrait.FanModeEnum.Off
      percentage <= 33f -> FanControlTrait.FanModeEnum.Low
      percentage <= 66f -> FanControlTrait.FanModeEnum.Medium
      else -> FanControlTrait.FanModeEnum.High
    }
  }

  Column {
    Spacer(Modifier.height(8.dp))

    // Only show Fan Mode dropdown if the device supports it
    if (supportsFanMode) {
      Box(Modifier.fillMaxWidth()) {
        Text("Fan Mode", fontSize = 16.sp)
        var expanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
          TextButton(onClick = { if (isConnected) expanded = true }, enabled = isConnected) {
            Text(
              text =
                when (fanMode) {
                  FanControlTrait.FanModeEnum.Off -> "Off"
                  FanControlTrait.FanModeEnum.Low -> "Low"
                  FanControlTrait.FanModeEnum.Medium -> "Medium"
                  FanControlTrait.FanModeEnum.High -> "High"
                  else -> "Unknown"
                },
              color =
                if (isConnected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Icon(
              imageVector =
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
              contentDescription = null,
            )
          }

          DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                FanControlTrait.FanModeEnum.Off to "Off",
                FanControlTrait.FanModeEnum.Low to "Low",
                FanControlTrait.FanModeEnum.Medium to "Medium",
                FanControlTrait.FanModeEnum.High to "High",
              )
              .forEach { (mode, label) ->
                DropdownMenuItem(
                  text = { Text(label) },
                  onClick = {
                    expanded = false
                    scope.launch {
                      try {
                        trait.update { setFanMode(mode) }
                        sliderPosition =
                          when (mode) {
                            FanControlTrait.FanModeEnum.Off -> 0f
                            FanControlTrait.FanModeEnum.Low -> 25f
                            FanControlTrait.FanModeEnum.Medium -> 50f
                            FanControlTrait.FanModeEnum.High -> 75f
                            else -> 0f
                          }
                      } catch (e: Exception) {
                        MainActivity.showWarning(scope, e, "Failed to set fan mode")
                      }
                    }
                  },
                )
              }
          }
        }
      }
      Spacer(Modifier.height(16.dp))
    }

    Box(Modifier.fillMaxWidth()) {
      Text("Fan Speed", fontSize = 16.sp)
      Text(
        text = "${sliderPosition.toInt()}%",
        fontSize = 16.sp,
        modifier = Modifier.align(Alignment.CenterEnd),
      )
    }

    LevelSlider(
      value = sliderPosition,
      low = 0f,
      high = 100f,
      steps = 0,
      modifier = Modifier.padding(top = 8.dp),
      onValueChange = { value ->
        sliderPosition = value
        true
      },
      onValueChangeFinished = { value ->
        scope.launch {
          try {
            when {
              // Prefer percentSetting if supported (playground fan)
              supportsPercent -> {
                trait.update { setPercentSetting(value.toInt().toUByte()) }
              }
              // Fall back to fanMode mapping
              supportsFanMode -> {
                val newMode = percentageToFanMode(value)
                trait.update { setFanMode(newMode) }
              }
            }
          } catch (e: Exception) {
            MainActivity.showWarning(scope, e, "Failed to set fan speed")
          }
        }
      },
      isEnabled = isConnected,
    )
  }
}

/**
 * Media playback control component for TV and speaker devices. Note: TV playback state updates
 * depend on cloud sync between the device and Google Home API. State changes may take a few seconds
 * to reflect in the UI.
 */
@Composable
fun MediaPlaybackControlComponent(trait: MediaPlayback, isConnected: Boolean) {
  val scope = rememberCoroutineScope()
  val currentState = trait.currentState

  Column {
    Spacer(Modifier.height(8.dp))

    Box(Modifier.fillMaxWidth()) {
      Text("Playback", fontSize = 16.sp)
      Text(
        text = currentState?.name ?: "Unknown",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.align(Alignment.CenterEnd),
      )
    }

    Spacer(Modifier.height(12.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
      IconButton(
        onClick = {
          scope.launch {
            try {
              if (
                currentState != null && currentState == MediaPlaybackTrait.PlaybackStateEnum.Playing
              ) {
                trait.pause()
              } else {
                trait.play()
              }
            } catch (e: Exception) {
              MainActivity.showWarning(scope, e, "Playback control failed")
            }
          }
        },
        enabled = isConnected,
      ) {
        Icon(
          imageVector =
            if (currentState == MediaPlaybackTrait.PlaybackStateEnum.Playing) {
              Icons.Default.Pause
            } else {
              Icons.Default.PlayArrow
            },
          contentDescription =
            if (currentState == MediaPlaybackTrait.PlaybackStateEnum.Playing) {
              "Pause"
            } else {
              "Play"
            },
          tint =
            if (isConnected) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
      }

      IconButton(
        onClick = {
          scope.launch {
            try {
              trait.stop()
            } catch (e: Exception) {
              MainActivity.showWarning(scope, e, "Stop failed")
            }
          }
        },
        enabled = isConnected,
      ) {
        Icon(
          imageVector = Icons.Default.Stop,
          contentDescription = "Stop",
          tint =
            if (isConnected) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
      }

      IconButton(
        onClick = {
          scope.launch {
            try {
              trait.next()
            } catch (e: Exception) {
              MainActivity.showWarning(scope, e, "Next failed")
            }
          }
        },
        enabled = isConnected,
      ) {
        Icon(
          imageVector = Icons.Default.SkipNext,
          contentDescription = "Next",
          tint =
            if (isConnected) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
      }
    }
  }
}

@Composable
fun WindowCoveringControlComponent(trait: WindowCovering, isConnected: Boolean) {
  val scope = rememberCoroutineScope()
  val targetLiftPercent100ths = trait.targetPositionLiftPercent100ths ?: 0u
  val targetTiltPercent100ths = trait.targetPositionTiltPercent100ths ?: 0u
  val targetLiftPercentage =
    remember(targetLiftPercent100ths) { 100f - (targetLiftPercent100ths.toInt() / 100).toFloat() }
  val targetTiltDegrees =
    remember(targetTiltPercent100ths) { (targetTiltPercent100ths.toInt() / 10000f) * 180f }
  var displayLiftPercentage by remember { mutableFloatStateOf(targetLiftPercentage) }
  var displayTiltDegrees by remember { mutableFloatStateOf(targetTiltDegrees) }

  LaunchedEffect(targetLiftPercentage) { displayLiftPercentage = targetLiftPercentage }

  LaunchedEffect(targetTiltDegrees) { displayTiltDegrees = targetTiltDegrees }
  val isOpen = remember(displayLiftPercentage) { displayLiftPercentage > 0f }

  Column(modifier = Modifier.fillMaxWidth()) {
    Spacer(Modifier.height(8.dp))

    // Open/Close Toggle
    Box(Modifier.fillMaxWidth()) {
      Text(text = "Position", fontSize = 16.sp)

      Row(
        modifier = Modifier.align(Alignment.CenterEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(text = if (isOpen) "Open" else "Closed", fontSize = 16.sp)
        Switch(
          checked = isOpen,
          onCheckedChange = { shouldOpen ->
            scope.launch {
              try {
                if (shouldOpen) {
                  // Open to 99%
                  val percent100ths = 100.toUShort()
                  trait.goToLiftPercentage(percent100ths)
                  displayLiftPercentage = 99f
                } else {
                  // Close to 0%
                  trait.downOrClose()
                  displayLiftPercentage = 0f
                }
              } catch (e: Exception) {
                MainActivity.showWarning(scope, e, "Operation failed")
              }
            }
          },
          enabled = isConnected,
        )
      }
    }

    Spacer(Modifier.height(24.dp))

    // Lift position display and slider
    Box(Modifier.fillMaxWidth()) {
      Text(text = "Lift Position", fontSize = 16.sp)
      Text(
        text = "${displayLiftPercentage.toInt()}%",
        fontSize = 16.sp,
        modifier = Modifier.align(Alignment.CenterEnd),
      )
    }

    LevelSlider(
      value = displayLiftPercentage,
      low = 0f,
      high = 100f,
      steps = 0,
      modifier = Modifier.padding(top = 8.dp),
      onValueChange = { value ->
        displayLiftPercentage = value
        true
      },
      onValueChangeFinished = { value ->
        scope.launch {
          try {
            val invertedPercent = 100f - value
            val percent100ths = (invertedPercent.toInt() * 100).toUShort()
            trait.goToLiftPercentage(percent100ths)
          } catch (e: Exception) {
            MainActivity.showWarning(scope, e, "Lift position change failed")
          }
        }
      },
      isEnabled = isConnected,
    )

    Spacer(Modifier.height(24.dp))

    // Tilt position control (if supported)
    if (trait.targetPositionTiltPercent100ths != null) {
      Box(Modifier.fillMaxWidth()) {
        Text(text = "Tilt Angle", fontSize = 16.sp)
        Text(
          text = "${displayTiltDegrees.toInt()}°",
          fontSize = 16.sp,
          modifier = Modifier.align(Alignment.CenterEnd),
        )
      }

      LevelSlider(
        value = displayTiltDegrees,
        low = 0f,
        high = 180f,
        steps = 0,
        modifier = Modifier.padding(top = 8.dp),
        onValueChange = { value ->
          displayTiltDegrees = value
          true
        },
        onValueChangeFinished = { value ->
          scope.launch {
            try {
              val percent100ths = ((value / 180f) * 10000f).toInt().toUShort()
              trait.goToTiltPercentage(percent100ths)
            } catch (e: Exception) {
              MainActivity.showWarning(scope, e, "Tilt position change failed")
            }
          }
        },
        isEnabled = isConnected,
      )
    }
  }
}

/**
 * A custom slider composable with support for value rounding and validation.
 *
 * @param value The current value of the slider.
 * @param low The minimum value the slider can be set to.
 * @param high The maximum value the slider can be set to.
 * @param steps The number of discrete intervals between [low] and [high].
 * @param roundingValue An optional value to round the slider's output to the nearest multiple of.
 *   If null, no rounding is performed.
 * @param onValueChange An optional validation lambda that is invoked as the slider value changes.
 *   Return `true` to accept the change, `false` to reject it.
 * @param onValueChangeFinished A lambda that is invoked when the user has finished interacting with
 *   the slider.
 * @param modifier The [Modifier] to be applied to the slider.
 * @param isEnabled Whether the slider is enabled and can be interacted with.
 */
@Composable
fun LevelSlider(
  value: Float,
  low: Float,
  high: Float,
  steps: Int,
  modifier: Modifier = Modifier,
  roundingValue: Float? = null,
  onValueChange: ((Float) -> Boolean)? = null,
  onValueChangeFinished: (Float) -> Unit,
  isEnabled: Boolean = true,
) {
  // This state now correctly resets if the external `value` changes.
  var level: Float by remember(value) { mutableFloatStateOf(value) }

  Slider(
    value = level,
    valueRange = low..high,
    steps = steps,
    modifier = modifier,
    onValueChange = { newRawValue ->
      // Apply rounding if roundingValue is provided
      val newValue =
        if (roundingValue != null) {
          (newRawValue / roundingValue).roundToInt() * roundingValue
        } else {
          newRawValue
        }
      // Update state only if validation passes or there's no validator
      if (onValueChange == null || onValueChange(newValue)) {
        level = newValue
      }
    },
    onValueChangeFinished = { onValueChangeFinished(level) },
    enabled = isEnabled,
  )
}

/**
 * A composable that wraps a [LevelSlider] with a title and a current value display.
 *
 * This component provides a complete UI element for a labeled slider, handling null states and
 * wiring up the necessary callbacks.
 *
 * @param title The text label to display above the slider.
 * @param isEnabled Whether the slider is enabled and can be interacted with.
 * @param currentValue The current value of the slider. The component will display 'Not available'
 *   if this is null.
 * @param low The minimum value of the slider range. Required for the slider to be displayed.
 * @param high The maximum value of the slider range. Required for the slider to be displayed.
 * @param roundingValue An optional value to round the slider's output to the nearest multiple of.
 *   Passed to the underlying [LevelSlider].
 * @param unitSuffix A string added as a suffix to the currentValue if a value is present, to
 *   indicate units of measurement.
 * @param onValueChange An optional validation lambda that is invoked as the slider value changes.
 *   Passed to the underlying [LevelSlider].
 * @param onValueChangeFinished A suspend lambda that is invoked with the new value when the user
 *   has finished interacting with the slider.
 */
@Composable
private fun LabeledSlider(
  title: String,
  isEnabled: Boolean,
  currentValue: Short?,
  low: Short?,
  high: Short?,
  roundingValue: Float? = null,
  unitSuffix: String = "",
  onValueChange: ((Float) -> Boolean)? = null,
  onValueChangeFinished: suspend (newValue: Float) -> Unit,
) {
  // Return early if the necessary values are null
  if (currentValue == null || low == null || high == null) {
    Text("$title: Not available")
    return
  }

  val scope = rememberCoroutineScope()
  val vset = currentValue.toFloat()
  val vlow = low.toFloat()
  val vhigh = high.toFloat()

  Box(Modifier.fillMaxWidth()) {
    Text(title, fontSize = 20.sp)
    Text(
      text = (vset / 100f).toString() + unitSuffix,
      fontSize = 16.sp,
      modifier = Modifier.align(Alignment.TopEnd),
    )

    LevelSlider(
      value = vset,
      low = vlow,
      high = vhigh,
      // Calculate steps safely, ensuring it's not negative
      steps = ((vhigh - vlow) / 10f).toInt().minus(1).coerceAtLeast(0),
      roundingValue = roundingValue,
      modifier = Modifier.padding(top = 16.dp),
      onValueChange = { value -> if (onValueChange == null) true else onValueChange(value) },
      onValueChangeFinished = { value: Float ->
        scope.launch {
          try {
            // Call the lambda with the new slider value directly
            onValueChangeFinished(value)
          } catch (e: HomeException) {
            MainActivity.showWarning(this, "Exception: " + e.message)
          }
        }
      },
      isEnabled = isEnabled,
    )
  }
}
