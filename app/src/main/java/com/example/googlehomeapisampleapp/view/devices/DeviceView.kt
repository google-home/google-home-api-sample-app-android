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

package com.example.googlehomeapisampleapp.view.devices

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.example.googlehomeapisampleapp.camera.ActivityZoneStreamState
import com.example.googlehomeapisampleapp.camera.ActivityZoneView
import com.example.googlehomeapisampleapp.camera.CameraStreamActions
import com.example.googlehomeapisampleapp.camera.CameraStreamOptionsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.googlehomeapisampleapp.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.googlehomeapisampleapp.MainActivity
import com.example.googlehomeapisampleapp.camera.CameraStreamView
import com.example.googlehomeapisampleapp.camera.CameraStreamViewModel
import com.example.googlehomeapisampleapp.camera.familiarface.FamiliarFaceView
import com.example.googlehomeapisampleapp.extension.thermostat.getCoolingSetpoint
import com.example.googlehomeapisampleapp.extension.thermostat.getHeatingSetpoint
import com.example.googlehomeapisampleapp.extension.thermostat.getMaxCoolSetpointLimit
import com.example.googlehomeapisampleapp.extension.thermostat.getMaxHeatSetpointLimit
import com.example.googlehomeapisampleapp.extension.thermostat.getMinCoolSetpointLimit
import com.example.googlehomeapisampleapp.extension.thermostat.getMinHeatSetpointLimit
import com.example.googlehomeapisampleapp.extension.thermostat.getSystemMode
import com.example.googlehomeapisampleapp.extension.thermostat.isModeCoolingRelated
import com.example.googlehomeapisampleapp.extension.thermostat.isModeHeatingRelated
import com.example.googlehomeapisampleapp.extension.thermostat.isModeSupported
import com.example.googlehomeapisampleapp.extension.thermostat.isValidCoolingSetpointUpdate
import com.example.googlehomeapisampleapp.extension.thermostat.isValidHeatingSetpointUpdate
import com.example.googlehomeapisampleapp.extension.thermostat.setOccupiedCoolingPoint
import com.example.googlehomeapisampleapp.extension.thermostat.setOccupiedHeatingPoint
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.BasicInformationUiState
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.example.googlehomeapisampleapp.viewmodel.ota.OtaUiState
import com.google.home.ConnectivityState
import com.google.home.DeviceType
import com.google.home.HomeException
import com.google.home.Trait
import com.google.home.google.GoogleCameraDevice
import com.google.home.google.GoogleDoorbellDevice
import com.google.home.matter.standard.BooleanState
import com.google.home.matter.standard.Chime
import com.google.home.matter.standard.ColorTemperatureLightDevice
import com.google.home.matter.standard.DimmableLightDevice
import com.google.home.matter.standard.DoorLock
import com.google.home.matter.standard.DoorLockTrait
import com.google.home.matter.standard.ExtendedColorLightDevice
import com.google.home.matter.standard.FanControl
import com.google.home.matter.standard.FanControlTrait
import com.google.home.matter.standard.IlluminanceMeasurement
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.LevelControlTrait
import com.google.home.matter.standard.MediaPlayback
import com.google.home.matter.standard.MediaPlaybackTrait
import com.google.home.matter.standard.OccupancySensing
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOffLightDevice
import com.google.home.matter.standard.SpeakerDevice
import com.google.home.matter.standard.TemperatureMeasurement
import com.google.home.matter.standard.Thermostat
import com.google.home.matter.standard.ThermostatTrait
import com.google.home.matter.standard.WindowCovering
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun DeviceView(homeAppVM: HomeAppViewModel) {
  val scope: CoroutineScope = rememberCoroutineScope()
  val deviceVM: DeviceViewModel? by homeAppVM.selectedDeviceVM.collectAsState()

  BackHandler {
    scope.launch { homeAppVM.selectedDeviceVM.emit(null) }
  }

  deviceVM?.let { vm ->
    val context = LocalContext.current
    val deviceType by vm.type.collectAsStateWithLifecycle()
    val isCameraDevice = deviceType.factory == GoogleCameraDevice
    val isDoorbellDevice = deviceType.factory == GoogleDoorbellDevice

    val name by vm.name.collectAsStateWithLifecycle()
    val deviceTypeName by vm.typeName.collectAsStateWithLifecycle()
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showMetaDialog by rememberSaveable { mutableStateOf(false) }

    val basicInfoUiState by vm.basicInfoUiState.collectAsStateWithLifecycle(initialValue = BasicInformationUiState.Loading)

    LaunchedEffect(vm) {
      vm.uiEventFlow.collect { event ->
        when (event) {
          is DeviceViewModel.UiEvent.ShowToast -> {
            Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
          }
          is DeviceViewModel.UiEvent.NavigateBack -> {
            scope.launch { homeAppVM.selectedDeviceVM.emit(null) }
          }
        }
      }
    }

    Column(modifier = Modifier.fillMaxSize()) {
      Spacer(Modifier.height(64.dp))

      // Unified Header Row
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = name,
          fontSize = 28.sp,
          modifier = Modifier.weight(1f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        IconButton(onClick = { homeAppVM.openHistoryForDevice(vm) }) {
          Icon(Icons.Default.History, "History", tint = MaterialTheme.colorScheme.primary)
        }

        Box {
          IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.primary)
          }
          DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
          ) {
            DropdownMenuItem(
              text = { Text("Device Information") },
              onClick = {
                showMetaDialog = true
                showMenu = false
              }
            )
            DropdownMenuItem(
              text = { Text("Share Device") },
              onClick = {
                homeAppVM.homeApp.commissioningManager.requestShareDevice(vm.id)
                showMenu = false
              }
            )
            DropdownMenuItem(
              text = { Text("Rename Device") },
              onClick = {
                showRenameDialog = true
                showMenu = false
              }
            )
            DropdownMenuItem(
              text = { Text("Delete Device") },
              onClick = {
                showDeleteDialog = true
                showMenu = false
              }
            )
          }
        }
      }

      // Body Content
      if (isCameraDevice || isDoorbellDevice) {
        Surface(modifier = Modifier.fillMaxSize()) {
          key(vm.id) {
            var showFamiliarFaceScreen by remember { mutableStateOf(false) }
            var showActivityZoneScreen by remember { mutableStateOf(false) }

            val cameraVm: CameraStreamViewModel = hiltViewModel(key = vm.id.toString())

            val lifecycleOwner = LocalLifecycleOwner.current
            val currentCameraVm by rememberUpdatedState(cameraVm)
            DisposableEffect(lifecycleOwner) {
              val observer = LifecycleEventObserver { _, event ->
                when (event) {
                  Lifecycle.Event.ON_STOP -> currentCameraVm.stopPlayerExternally(isBackground = true)
                  Lifecycle.Event.ON_RESUME -> currentCameraVm.onAppForegrounded()
                  else -> {}
                }
              }
              lifecycleOwner.lifecycle.addObserver(observer)
              onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                currentCameraVm.stopPlayerExternally()
              }
            }

            LaunchedEffect(cameraVm.uiMessage) {
              cameraVm.uiMessage.collect { message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
              }
            }

            LaunchedEffect(vm.id) {
              Log.i("DeviceView", "Starting fresh stream session for: ${vm.id}")
              cameraVm.setDevice(vm.device)
            }

            if (showFamiliarFaceScreen) {
              val activeStructureVM by homeAppVM.selectedStructureVM.collectAsStateWithLifecycle()
              val structureIdStr = activeStructureVM?.structure?.id?.id ?: ""
              if (structureIdStr.isEmpty()) {
                Log.w("DeviceView", "Warning: Structure ID is empty, Familiar Face flow might fail.")
              }
              // Render the Familiar Face view and handle back navigation locally
              FamiliarFaceView(
                structureId = structureIdStr,
                onNavigateBack = { showFamiliarFaceScreen = false }
              )
            } else if (showActivityZoneScreen) {
              val snapshotUrl by cameraVm.snapshotUrl.collectAsStateWithLifecycle()
              val activityZones by cameraVm.activityZones.collectAsStateWithLifecycle()
              val twoDCartesianMax by cameraVm.twoDCartesianMax.collectAsStateWithLifecycle()
              val zoneUpdateStatus by cameraVm.zoneUpdateStatus.collectAsStateWithLifecycle()
              val isCameraOn by cameraVm.isRecording.collectAsStateWithLifecycle()
              val isFetchingLiveSnapshot by cameraVm.isFetchingLiveSnapshot.collectAsStateWithLifecycle()

              ActivityZoneView(
                snapshotUrl = snapshotUrl,
                authenticatedImageLoader = cameraVm.authenticatedImageLoader,
                activityZones = activityZones,
                twoDCartesianMax = twoDCartesianMax,
                zoneUpdateStatus = zoneUpdateStatus,
                isCameraOn = isCameraOn,
                isFetchingLiveSnapshot = isFetchingLiveSnapshot,
                onRefreshLiveSnapshot = { cameraVm.refreshLiveSnapshot() },
                onAddActivityZone = { cameraVm.addActivityZone() },
                onDeleteActivityZone = { zoneId -> cameraVm.deleteActivityZone(zoneId) },
                onNavigateBack = { showActivityZoneScreen = false }
              )
            } else {

            val playerState by cameraVm.state.collectAsStateWithLifecycle()
            val isTalkbackSupported by cameraVm.isTalkbackSupported.collectAsStateWithLifecycle()
            val isCameraOn by cameraVm.isRecording.collectAsStateWithLifecycle()
            val isTalkbackEnabled by cameraVm.isTalkbackEnabled.collectAsStateWithLifecycle()
            val isToggleRecordingInProgress by cameraVm.isToggleRecordingInProgress.collectAsStateWithLifecycle()

            val isAudioRecording by cameraVm.isAudioRecording.collectAsStateWithLifecycle()
            val isToggleAudioRecordingInProgress by cameraVm.isToggleAudioRecordingInProgress.collectAsStateWithLifecycle()

            val isDoorbell by cameraVm.isDoorbellDevice.collectAsStateWithLifecycle()
            val isChimeToggleSupported by cameraVm.isChimeToggleSupported.collectAsStateWithLifecycle()
            val chimeType by cameraVm.externalChimeType.collectAsStateWithLifecycle()
            val installedChimeSounds by cameraVm.installedChimeSounds.collectAsStateWithLifecycle()
            val selectedChimeId by cameraVm.selectedChimeId.collectAsStateWithLifecycle()

            val cameraTimelineUiState by cameraVm.cameraTimelineUiState.collectAsStateWithLifecycle()

            // Recording Mode
            val recordingModeOptions by cameraVm.recordingModeOptions.collectAsStateWithLifecycle()
            val selectedRecordingModeIndex by cameraVm.selectedRecordingModeIndex.collectAsStateWithLifecycle()

            // Activity Zones
            val activityZones by cameraVm.activityZones.collectAsStateWithLifecycle()
            val twoDCartesianMax by cameraVm.twoDCartesianMax.collectAsStateWithLifecycle()
            val zoneUpdateStatus by cameraVm.zoneUpdateStatus.collectAsStateWithLifecycle()

            // Camera Snapshot
            val snapshotUrl by cameraVm.snapshotUrl.collectAsStateWithLifecycle()
            val isFetchingLiveSnapshot by cameraVm.isFetchingLiveSnapshot.collectAsStateWithLifecycle()

            // AI Features
            val videoAnalysisControllers by cameraVm.videoAnalysisControllers.collectAsStateWithLifecycle()
            val isToggleAiFeaturesInProgress by cameraVm.isToggleAiFeaturesInProgress.collectAsStateWithLifecycle()

            // Floodlight (light facet on camera)
            val facets by vm.facets.collectAsStateWithLifecycle()
            val floodlightFacet = facets.firstOrNull {
              it.deviceType.factory == OnOffLightDevice ||
                it.deviceType.factory == DimmableLightDevice ||
                it.deviceType.factory == ColorTemperatureLightDevice ||
                it.deviceType.factory == ExtendedColorLightDevice
            }
            val floodlightOnOff = floodlightFacet?.traits?.filterIsInstance<OnOff>()?.firstOrNull()

            CameraStreamView(
              playerState = playerState,
              optionsState = CameraStreamOptionsState(
                isCameraOn = isCameraOn,
                isTalkbackSupported = isTalkbackSupported,
                isTalkbackEnabled = isTalkbackEnabled,
                isFloodlightSupported = floodlightOnOff != null,
                isFloodlightOn = floodlightOnOff?.onOff == true,
                isAudioRecording = isAudioRecording,
                isToggleRecordingInProgress = isToggleRecordingInProgress,
                isToggleAudioRecordingInProgress = isToggleAudioRecordingInProgress,
                isDoorbell = isDoorbell,
                isChimeToggleSupported = isChimeToggleSupported,
                chimeType = chimeType,
                installedChimeSounds = installedChimeSounds,
                selectedChimeId = selectedChimeId,
                cameraTimelineUiState = cameraTimelineUiState,
                recordingModeOptions = recordingModeOptions,
                selectedRecordingModeIndex = selectedRecordingModeIndex,
                videoAnalysisControllers = videoAnalysisControllers,
                isToggleAiFeaturesInProgress = isToggleAiFeaturesInProgress,
              ),
              activityZoneState = ActivityZoneStreamState(
                activityZones = activityZones,
                twoDCartesianMax = twoDCartesianMax,
                zoneUpdateStatus = zoneUpdateStatus,
                snapshotUrl = snapshotUrl,
                authenticatedImageLoader = cameraVm.authenticatedImageLoader,
                isFetchingLiveSnapshot = isFetchingLiveSnapshot,
              ),
              actions = CameraStreamActions(
                onSetRecordingMode = { index -> cameraVm.setRecordingMode(index) },
                onRefreshLiveSnapshot = { cameraVm.refreshLiveSnapshot() },
                onAddActivityZone = { cameraVm.addActivityZone() },
                onDeleteActivityZone = { zoneId -> cameraVm.deleteActivityZone(zoneId) },
                onNavigateToActivityZone = { showActivityZoneScreen = true },
                onNavigateToFamiliarFace = { showFamiliarFaceScreen = true },
                onSetAiFeaturesEnabled = cameraVm::onSetAiFeaturesEnabled,
                onTurnCameraOn = { cameraVm.setRecording(it) },
                onSetTalkback = { cameraVm.setTalkback(it) },
                onSetFloodlight = { enabled ->
                  scope.launch {
                    try {
                      if (enabled) floodlightOnOff?.on() else floodlightOnOff?.off()
                    } catch (e: Exception) {
                      Toast.makeText(context, "Floodlight toggle failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                  }
                },
                onSetAudioRecording = { cameraVm.setAudioRecording(it) },
                onToggleChime = { cameraVm.toggleIndoorChime() },
                onSetChimeType = { selectedType -> cameraVm.setExternalChimeType(selectedType) },
                onSetSelectedChimeSound = { chimeId -> cameraVm.setSelectedChimeSound(chimeId) },
                onRetry = { cameraVm.restartInitialization() },
                onSurfaceCreated = { cameraVm.onSurfaceCreated(it) },
                onSurfaceDestroyed = { cameraVm.onSurfaceDestroyed() },
                onShowSnackbar = { message -> Log.d("CameraStream", message) },
              ),
              paddingValues = PaddingValues(0.dp)
            )
            }
          }
        }
      } else {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          ControlListComponent(homeAppVM)
        }
      }
    }

    // Dialogs
    if (showRenameDialog) {
      var newName by remember { mutableStateOf(name) }
      AlertDialog(
        onDismissRequest = { showRenameDialog = false },
        title = { Text("Rename Device") },
        text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { vm.rename(newName); showRenameDialog = false }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
      )
    }

    if (showDeleteDialog) {
      AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("Delete Device") },
        text = { Text("Are you sure you want to delete $name?") },
        confirmButton = { TextButton(onClick = { showDeleteDialog = false; vm.deleteDevice() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
      )
    }

    if (showMetaDialog) {
      Dialog(
        onDismissRequest = { showMetaDialog = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
      ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(16.dp))
            // App Bar / Navigation Header
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(onClick = { showMetaDialog = false }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Navigate back")
              }
              Text(
                text = "Device Information",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 12.dp),
                fontWeight = FontWeight.Bold,
              )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Metadata entries
            Column(
              modifier =
                Modifier
                  .fillMaxSize()
                  .verticalScroll(rememberScrollState())
                  .padding(horizontal = 24.dp, vertical = 20.dp),
              verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
              MetaRow(label = "Device ID", value = vm.id)
              MetaRow(label = "Device Name", value = name)
              MetaRow(label = "Device Type", value = deviceTypeName)
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              val infoState = basicInfoUiState
              val notAvailable = stringResource(R.string.not_available_text)
              val loadingText = stringResource(R.string.loading_text)
              val errorText = stringResource(R.string.error_text)

              fun getDisplayValue(selector: (BasicInformationUiState.Success) -> String?): String {
                return when (infoState) {
                  is BasicInformationUiState.Success -> selector(infoState) ?: notAvailable
                  BasicInformationUiState.Error -> errorText
                  BasicInformationUiState.Loading, null -> loadingText
                }
              }

              MetaRow(label = "Serial Number", value = getDisplayValue { it.serialNumber })
              MetaRow(label = "Vendor Name", value = getDisplayValue { it.vendorName })
              MetaRow(label = "Product Name", value = getDisplayValue { it.productName })
              MetaRow(label = "Matter Vendor ID", value = getDisplayValue { it.vendorId })
              MetaRow(label = "Matter Product ID", value = getDisplayValue { it.productId })
              MetaRow(label = "Hardware Version", value = getDisplayValue { it.hardwareVersion })
              MetaRow(label = "Software Version", value = getDisplayValue { it.softwareVersion })
              MetaRow(label = "Node Label", value = getDisplayValue { it.nodeLabel })
              MetaRow(label = "Location", value = getDisplayValue { it.location })
              MetaRow(label = "Manufacturing Date", value = getDisplayValue { it.manufacturingDate })
              MetaRow(label = "Part Number", value = getDisplayValue { it.partNumber })
              MetaRow(label = "Product URL", value = getDisplayValue { it.productUrl })
              MetaRow(label = "Product Label", value = getDisplayValue { it.productLabel })
            }
          }
        }
      }
    }
  }
}

@Composable
fun DeviceOtaStatusCard(otaUiState: OtaUiState) {
  LaunchedEffect(otaUiState) {
    if (otaUiState !is OtaUiState.Loading) {
      Log.d("DeviceView", "DeviceOtaStatusCard state changed: $otaUiState")
    }
  }

  val versionString = when (otaUiState) {
    is OtaUiState.UpToDate -> otaUiState.currentVersionString
    is OtaUiState.Downloading -> otaUiState.currentVersionString
    is OtaUiState.Installing -> otaUiState.currentVersionString
    is OtaUiState.Delayed -> otaUiState.currentVersionString
    is OtaUiState.Failed -> otaUiState.currentVersionString
    else -> null
  }

  val isNonIdleState = otaUiState !is OtaUiState.UpToDate && otaUiState !is OtaUiState.Loading
  val hasVersionInfo = !versionString.isNullOrBlank()

  // Hide card for devices with no active update and no software version info available
  if (!isNonIdleState && !hasVersionInfo) {
    return
  }

  val statusText = when (otaUiState) {
    is OtaUiState.UpToDate -> if (hasVersionInfo) "Up to Date ($versionString)" else "Up to Date"
    is OtaUiState.Downloading -> {
      val percentText = if (otaUiState.progressPercent != null) " (${otaUiState.progressPercent}%)" else ""
      "Downloading$percentText"
    }
    is OtaUiState.Installing -> "Installing..."
    is OtaUiState.Delayed -> "Delayed: ${otaUiState.reason}"
    is OtaUiState.Failed -> "Failed (Restored)"
    is OtaUiState.Checking -> "Checking..."
    is OtaUiState.Loading -> "Loading..."
  }

  Text(
    text = "Software Update: $statusText",
    fontSize = 14.sp,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}

@Composable
fun ControlListComponent(homeAppVM: HomeAppViewModel) {

  val deviceVM: DeviceViewModel = homeAppVM.selectedDeviceVM.collectAsState().value ?: return
  val deviceType: DeviceType by deviceVM.type.collectAsStateWithLifecycle()
  val deviceTypeName: String by deviceVM.typeName.collectAsStateWithLifecycle()
  val deviceTraits: List<Trait> = deviceVM.traits.collectAsState().value
  val facets by deviceVM.facets.collectAsStateWithLifecycle()
  val deviceOtaState by deviceVM.deviceOtaUiState.collectAsStateWithLifecycle()
  val currentOtaUiState by homeAppVM.otaUiState.collectAsStateWithLifecycle(OtaUiState.Loading)
  val activeOtaDeviceId by homeAppVM.otaDeviceId.collectAsStateWithLifecycle()
  val activeOtaDeviceIds by homeAppVM.otaDeviceIds.collectAsStateWithLifecycle()

  val isThisDeviceUpdating = deviceVM.id == activeOtaDeviceId || activeOtaDeviceIds.contains(deviceVM.id)
  val otaUiState = if (isThisDeviceUpdating) currentOtaUiState else deviceOtaState
  val isControlEnabled = otaUiState !is OtaUiState.Downloading && otaUiState !is OtaUiState.Installing

  Column(
    Modifier
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .fillMaxWidth()
  ) {
    Text(deviceTypeName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    DeviceOtaStatusCard(otaUiState = otaUiState)
    if (!isControlEnabled) {
      Text(
        text = "Device controls paused during software update",
        color = MaterialTheme.colorScheme.error,
        fontSize = 14.sp
      )
    }
  }

  if (facets.size > 1) {
    for (facet in facets) {
      HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
      Text(
        text = "${facet.title} (${facet.status})",
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
      )
      for (trait in facet.traits) {
        ControlListItem(trait, facet.deviceType, enabled = isControlEnabled)
      }
    }
  } else {
    for (trait in deviceTraits) {
      ControlListItem(trait, deviceType, enabled = isControlEnabled)
    }
  }
}

@Composable
fun ControlListItem(trait: Trait, type: DeviceType, enabled: Boolean = true) {
  val scope: CoroutineScope = rememberCoroutineScope()

  // Make connectivity reactive by keying off the type changes
  // When deviceType updates (which happens in subscribeToType), this will recompose
  val isConnected = remember(type) {
    type.metadata.sourceConnectivity.connectivityState == ConnectivityState.ONLINE ||
            type.metadata.sourceConnectivity.connectivityState == ConnectivityState.PARTIALLY_ONLINE
  }
  val isInteractive = enabled && isConnected

  Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
    when (trait) {
      is OnOff -> {
        Column(Modifier.fillMaxWidth()) {
          Text(trait.factory.toString(), fontSize = 20.sp)
          Text(DeviceViewModel.getTraitStatus(trait, type), fontSize = 16.sp)
        }

        Switch(
          checked = (trait.onOff == true), modifier = Modifier.align(Alignment.CenterEnd),
          onCheckedChange = { state ->
            scope.launch {
              try {
                if (state) trait.on() else trait.off()
              } catch (e: HomeException) {
                MainActivity.showWarning(this, "Toggling device on/off failed: ${e.message}")
              }
            }
          },
          enabled = isInteractive
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
                  modifier = Modifier.align(Alignment.CenterEnd)
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
                        optionsOverride = LevelControlTrait.OptionsBitmap()
                      )
                    } catch (e: HomeException) {
                      MainActivity.showWarning(this, "Volume control failed: ${e.message}")
                    }
                  }
                },
                isEnabled = isInteractive
              )
            }
          } else {
            // For other devices (lights), use 0-254 scale
            Text(trait.factory.toString(), fontSize = 20.sp)
            LevelSlider(
              value = level.toFloat(),
              low = 0f, high = 254f, steps = 0,
              modifier = Modifier.padding(top = 16.dp),
              onValueChangeFinished = { value: Float ->
                scope.launch {
                  try {
                    trait.moveToLevelWithOnOff(
                      level = value.toInt().toUByte(),
                      transitionTime = null,
                      optionsMask = LevelControlTrait.OptionsBitmap(),
                      optionsOverride = LevelControlTrait.OptionsBitmap()
                    )
                  } catch (e: HomeException) {
                    MainActivity.showWarning(this, "Level control command failed: ${e.message}")
                  }
                }
              },
              isEnabled = isInteractive
            )
          }
        }
      }

      is BooleanState -> {
        Column(Modifier.fillMaxWidth()) {
          Text(trait.factory.toString(), fontSize = 20.sp)
          Text(DeviceViewModel.getTraitStatus(trait, type), fontSize = 16.sp)
        }
      }

      is OccupancySensing -> {
        Column(Modifier.fillMaxWidth()) {
          Text(trait.factory.toString(), fontSize = 20.sp)
          Text(DeviceViewModel.getTraitStatus(trait, type), fontSize = 16.sp)
        }
      }

      is Thermostat -> {
        ThermostatControl(
          trait = trait,
          isConnected = isInteractive,
          scope = scope
        )
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
                  if (it.all { c -> c.isDigit() } && it.length <= 8)
                    pinInput = it
                },
                label = { Text("PIN (4-8 digits)") },
                singleLine = true,
                enabled = !isProcessing
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
                      MainActivity.showWarning(this, "Wrong PIN or operation failed: ${e.message}")
                    } finally {
                      isProcessing = false
                    }
                  }
                },
                enabled = pinInput.length >= 4 && !isProcessing
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
                enabled = !isProcessing
              ) {
                Text("Cancel")
              }
            }
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
                  val needsCredential = e.message?.contains("19") == true ||
                          e.message?.contains("credential", ignoreCase = true) == true ||
                          e.message?.contains("authentication", ignoreCase = true) == true

                  if (needsCredential) {
                    isUnlocking = !shouldLock
                    showPinDialog = true
                  } else {
                    MainActivity.showWarning(this, "Operation failed: ${e.message}")
                  }
                } finally {
                  isProcessing = false
                }
              }
            }
          },
          enabled = isInteractive && !isProcessing
        )
      }

      is FanControl -> {
        FanControlComponent(
          trait = trait,
          isConnected = isInteractive
        )
      }

      is MediaPlayback -> {
        MediaPlaybackControlComponent(
          trait = trait,
          isConnected = isInteractive
        )
      }

      is WindowCovering -> {
        WindowCoveringControlComponent(
          trait = trait,
          isConnected = isInteractive
        )
      }

      is TemperatureMeasurement -> {
        Column(Modifier.fillMaxWidth()) {
          Text("Temperature", fontSize = 20.sp)
          val measuredValue = trait.measuredValue
          val tempText = if (measuredValue != null) {
            "%.1f°C".format(measuredValue / 100.0)
          } else {
            "--"
          }
          Text(tempText, fontSize = 16.sp)
        }
      }

      is IlluminanceMeasurement -> {
        Column(Modifier.fillMaxWidth()) {
          Text(trait.factory.toString(), fontSize = 20.sp)
          Text(DeviceViewModel.getTraitStatus(trait, type), fontSize = 16.sp)
        }
      }

      is Chime -> {
        val sounds = trait.installedChimeSounds.orEmpty()
        var showSoundMenu by remember { mutableStateOf(false) }
        val currentSoundName = sounds.firstOrNull { it.chimeId == trait.selectedChime }?.name

        Column(Modifier.fillMaxWidth()) {
          Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
              Text(trait.factory.toString(), fontSize = 20.sp)
              Text(DeviceViewModel.getTraitStatus(trait, type), fontSize = 16.sp)
            }

            Switch(
              checked = (trait.enabled != false),
              modifier = Modifier.align(Alignment.CenterEnd),
              onCheckedChange = { state ->
                scope.launch {
                  try {
                    trait.update { setEnabled(state) }
                  } catch (e: HomeException) {
                    MainActivity.showWarning(this, "Updating chime failed: ${e.message}")
                  }
                }
              },
              enabled = isInteractive
            )
          }

          if (sounds.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth()) {
              OutlinedButton(
                onClick = { showSoundMenu = true },
                enabled = isInteractive,
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("Chime Sound: ${currentSoundName ?: "Default"}")
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select Chime Sound")
              }

              DropdownMenu(
                expanded = showSoundMenu,
                onDismissRequest = { showSoundMenu = false }
              ) {
                sounds.forEach { sound ->
                  DropdownMenuItem(
                    text = { Text(sound.name) },
                    onClick = {
                      showSoundMenu = false
                      scope.launch {
                        try {
                          trait.update { setSelectedChime(sound.chimeId) }
                        } catch (e: HomeException) {
                          MainActivity.showWarning(this, "Updating chime sound failed: ${e.message}")
                        }
                      }
                    }
                  )
                }
              }
            }
          }
        }
      }

      else -> return
    }
  }
}

@Composable
fun FanControlComponent(
  trait: FanControl,
  isConnected: Boolean,
) {
  val scope = rememberCoroutineScope()
  val fanMode = trait.fanMode
  val percentSetting = trait.percentSetting
  // Determine if this device supports fanMode or only percentSetting
  val supportsFanMode = fanMode != null
  val supportsPercent = percentSetting != null

  var sliderPosition by remember(fanMode, percentSetting) {
    mutableFloatStateOf(
      when {
        supportsPercent -> percentSetting!!.toFloat()
        supportsFanMode -> when (fanMode) {
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
          TextButton(
            onClick = { if (isConnected) expanded = true },
            enabled = isConnected
          ) {
            Text(
              text = when (fanMode) {
                FanControlTrait.FanModeEnum.Off -> "Off"
                FanControlTrait.FanModeEnum.Low -> "Low"
                FanControlTrait.FanModeEnum.Medium -> "Medium"
                FanControlTrait.FanModeEnum.High -> "High"
                else -> "Unknown"
              },
              color = if (isConnected)
                MaterialTheme.colorScheme.primary
              else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Icon(
              imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
              contentDescription = null
            )
          }

          DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
              FanControlTrait.FanModeEnum.Off to "Off",
              FanControlTrait.FanModeEnum.Low to "Low",
              FanControlTrait.FanModeEnum.Medium to "Medium",
              FanControlTrait.FanModeEnum.High to "High"
            ).forEach { (mode, label) ->
              DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                  expanded = false
                  scope.launch {
                    try {
                      trait.update { setFanMode(mode) }
                      sliderPosition = when (mode) {
                        FanControlTrait.FanModeEnum.Off -> 0f
                        FanControlTrait.FanModeEnum.Low -> 25f
                        FanControlTrait.FanModeEnum.Medium -> 50f
                        FanControlTrait.FanModeEnum.High -> 75f
                        else -> 0f
                      }
                    } catch (e: Exception) {
                      MainActivity.showWarning(scope, "Failed to set fan mode: ${e.message}")
                    }
                  }
                }
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
        modifier = Modifier.align(Alignment.CenterEnd)
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
            MainActivity.showWarning(scope, "Failed to set fan speed: ${e.message}")
          }
        }
      },
      isEnabled = isConnected
    )
  }
}

/**
 * Media playback control component for TV and speaker devices.
 * Note: TV playback state updates depend on cloud sync between the device and Google Home API.
 * State changes may take a few seconds to reflect in the UI.
 */
@Composable
fun MediaPlaybackControlComponent(
  trait: MediaPlayback,
  isConnected: Boolean,
) {
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
        modifier = Modifier.align(Alignment.CenterEnd)
      )
    }

    Spacer(Modifier.height(12.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly
    ) {
      IconButton(
        onClick = {
          scope.launch {
            try {
              if (currentState != null && currentState == MediaPlaybackTrait.PlaybackStateEnum.Playing) {
                trait.pause()
              } else {
                trait.play()
              }
            } catch (e: Exception) {
              MainActivity.showWarning(scope, "Playback control failed: ${e.message}")
            }
          }
        },
        enabled = isConnected
      ) {
        Icon(
          imageVector = if (currentState == MediaPlaybackTrait.PlaybackStateEnum.Playing) {
            Icons.Default.Pause
          } else {
            Icons.Default.PlayArrow
          },
          contentDescription = if (currentState == MediaPlaybackTrait.PlaybackStateEnum.Playing) {
            "Pause"
          } else {
            "Play"
          },
          tint = if (isConnected) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
          }
        )
      }

      IconButton(
        onClick = {
          scope.launch {
            try {
              trait.stop()
            } catch (e: Exception) {
              MainActivity.showWarning(scope, "Stop failed: ${e.message}")
            }
          }
        },
        enabled = isConnected
      ) {
        Icon(
          imageVector = Icons.Default.Stop,
          contentDescription = "Stop",
          tint = if (isConnected) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
          }
        )
      }

      IconButton(
        onClick = {
          scope.launch {
            try {
              trait.next()
            } catch (e: Exception) {
              MainActivity.showWarning(scope, "Next failed: ${e.message}")
            }
          }
        },
        enabled = isConnected
      ) {
        Icon(
          imageVector = Icons.Default.SkipNext,
          contentDescription = "Next",
          tint = if (isConnected) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
          }
        )
      }
    }
  }
}

@Composable
fun WindowCoveringControlComponent(
  trait: WindowCovering,
  isConnected: Boolean,
) {
  val scope = rememberCoroutineScope()

  // 1. Resolve current physical lift position: prioritize current position, fallback to 1% resolution
  val currentLiftPercent100ths: UShort? = trait.currentPositionLiftPercent100ths
    ?: trait.currentPositionLiftPercentage?.let { (it.toInt() * 100).toUShort() }

  // In Matter, 0 = 0.00% closed (100% open), 10000 = 100.00% closed (0% open)
  val openLiftPercentage: Float = if (currentLiftPercent100ths != null) {
    (100f - (currentLiftPercent100ths.toFloat() / 100f)).coerceIn(0f, 100f)
  } else {
    0f
  }

  // 2. Resolve target lift position: prioritize target position, fallback to current position (when at rest)
  val effectiveTargetLiftPercent100ths: UShort? = trait.targetPositionLiftPercent100ths
    ?: currentLiftPercent100ths

  val targetLiftPercentage: Float = if (effectiveTargetLiftPercent100ths != null) {
    (100f - (effectiveTargetLiftPercent100ths.toFloat() / 100f)).coerceIn(0f, 100f)
  } else {
    0f
  }

  val isMovingToTarget = trait.targetPositionLiftPercent100ths != null &&
    (targetLiftPercentage.roundToInt() != openLiftPercentage.roundToInt())

  // 3. Resolve tilt position: prioritize target position for slider, fallback to current position
  val currentTiltPercent100ths: UShort? = trait.currentPositionTiltPercent100ths
    ?: trait.currentPositionTiltPercentage?.let { (it.toInt() * 100).toUShort() }

  val openTiltDegrees: Float = if (currentTiltPercent100ths != null) {
    ((currentTiltPercent100ths.toFloat() / 10000f) * 180f).coerceIn(0f, 180f)
  } else {
    0f
  }

  val effectiveTargetTiltPercent100ths: UShort? = trait.targetPositionTiltPercent100ths
    ?: currentTiltPercent100ths

  val targetTiltDegrees: Float = if (effectiveTargetTiltPercent100ths != null) {
    ((effectiveTargetTiltPercent100ths.toFloat() / 10000f) * 180f).coerceIn(0f, 180f)
  } else {
    0f
  }

  val supportsTilt = trait.currentPositionTiltPercent100ths != null ||
    trait.currentPositionTiltPercentage != null ||
    trait.targetPositionTiltPercent100ths != null

  val isOpen = targetLiftPercentage > 0f

  Column(
    modifier = Modifier.fillMaxWidth()
  ) {
    Spacer(Modifier.height(8.dp))

    // Open/Close Toggle
    Box(Modifier.fillMaxWidth()) {
      Text(
        text = "Position",
        fontSize = 16.sp
      )

      Row(
        modifier = Modifier.align(Alignment.CenterEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = if (isOpen) "Open" else "Closed",
          fontSize = 16.sp
        )
        Switch(
          checked = isOpen,
          onCheckedChange = { shouldOpen ->
            scope.launch {
              try {
                if (shouldOpen) {
                  trait.upOrOpen()
                } else {
                  trait.downOrClose()
                }
              } catch (e: Exception) {
                MainActivity.showWarning(scope, "Operation failed: ${e.message}")
              }
            }
          },
          enabled = isConnected
        )
      }
    }

    Spacer(Modifier.height(20.dp))

    // Current Physical Position (Read-Only Indicator)
    Box(Modifier.fillMaxWidth()) {
      Text(
        text = "Current Position",
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        text = "${openLiftPercentage.roundToInt()}% Open",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.align(Alignment.CenterEnd)
      )
    }

    LinearProgressIndicator(
      progress = { (openLiftPercentage / 100f).coerceIn(0f, 1f) },
      modifier = Modifier
        .fillMaxWidth()
        .height(8.dp)
        .padding(top = 8.dp),
      trackColor = MaterialTheme.colorScheme.surfaceVariant,
      color = MaterialTheme.colorScheme.primary,
    )

    if (isMovingToTarget) {
      Text(
        text = "Moving to target (${targetLiftPercentage.roundToInt()}%)...",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(top = 4.dp)
      )
    }

    Spacer(Modifier.height(24.dp))

    // Target Lift Position (Interactive Slider)
    Box(Modifier.fillMaxWidth()) {
      Text(
        text = "Target Lift Position",
        fontSize = 16.sp
      )
      Text(
        text = "${targetLiftPercentage.roundToInt()}%",
        fontSize = 16.sp,
        modifier = Modifier.align(Alignment.CenterEnd)
      )
    }

    LevelSlider(
      value = targetLiftPercentage,
      low = 0f,
      high = 100f,
      steps = 0,
      modifier = Modifier.padding(top = 8.dp),
      onValueChangeFinished = { value ->
        scope.launch {
          try {
            val invertedPercent = (100f - value).coerceIn(0f, 100f)
            val percent100ths = (invertedPercent * 100f).roundToInt().coerceIn(0, 10000).toUShort()
            trait.goToLiftPercentage(percent100ths)
          } catch (e: Exception) {
            MainActivity.showWarning(scope, "Lift position change failed: ${e.message}")
          }
        }
      },
      isEnabled = isConnected
    )

    // Tilt position control (if supported)
    if (supportsTilt) {
      Spacer(Modifier.height(24.dp))

      Box(Modifier.fillMaxWidth()) {
        Text(
          text = "Tilt Angle",
          fontSize = 16.sp
        )
        Text(
          text = "${targetTiltDegrees.roundToInt()}°",
          fontSize = 16.sp,
          modifier = Modifier.align(Alignment.CenterEnd)
        )
      }

      LevelSlider(
        value = targetTiltDegrees,
        low = 0f,
        high = 180f,
        steps = 0,
        modifier = Modifier.padding(top = 8.dp),
        onValueChangeFinished = { value ->
          scope.launch {
            try {
              val percent100ths = ((value.coerceIn(0f, 180f) / 180f) * 10000f).roundToInt().coerceIn(0, 10000).toUShort()
              trait.goToTiltPercentage(percent100ths)
            } catch (e: Exception) {
              MainActivity.showWarning(scope, "Tilt position change failed: ${e.message}")
            }
          }
        },
        isEnabled = isConnected
      )
    }
  }
}

/**
 * Thermostat control for the Thermostat trait, with heat, cool, auto support, both
 * relative and absolute.
 *
 * Arguments are straight from parent scope, see [ControlListItem].
 */
@Composable
fun ThermostatControl(
  trait: Thermostat,
  isConnected: Boolean,
  scope: CoroutineScope,
) {
  val allModes: List<ThermostatTrait.SystemModeEnum> =
    ThermostatTrait.SystemModeEnum.entries.filter { mode -> mode != ThermostatTrait.SystemModeEnum.UnknownValue }
  var expanded: Boolean by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth()) {
    // Ambient Temperature
    Box(Modifier.fillMaxWidth()) {
      Text("Ambient", fontSize = 20.sp)
      val temperatureString = trait.localTemperature?.div(100)?.toFloat().toString() + "℃"
      Text(temperatureString, fontSize = 16.sp, modifier = Modifier.align(Alignment.CenterEnd))
    }
    Spacer(Modifier.height(16.dp))

    // System Mode
    Box(Modifier.fillMaxWidth()) {
      Text("SystemMode", fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterStart))
      Box(modifier = Modifier.align(Alignment.CenterEnd)) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
          Text(text = trait.systemMode.toString() + " ▾", fontSize = 20.sp)
        }
        DropdownMenu(
          expanded = expanded,
          onDismissRequest = { expanded = false },
          modifier = Modifier.align(Alignment.CenterEnd)
        ) {
          for (mode in allModes) {
            DropdownMenuItem(
              text = { Text(mode.toString()) },
              onClick = {
                scope.launch {
                  try {
                    trait.update { setSystemMode(mode) }
                  } catch (e: HomeException) {
                    MainActivity.showWarning(this, "Exception: " + e.message)
                  }
                }
                expanded = false
              },
              enabled = trait.isModeSupported(mode)
            )
          }
        }
      }
    }
    val isCoolingSetpointEnabled = isConnected &&
            (trait.systemMode?.isModeCoolingRelated() == true)
    if (isCoolingSetpointEnabled) {
      val lowCoolSetpoint = trait.getMinCoolSetpointLimit()
      val highCoolSetpoint = trait.getMaxCoolSetpointLimit()
      val coolSetpoint = trait.getCoolingSetpoint()

      // Cooling Setpoint slider
      Spacer(Modifier.height(16.dp))
      LabeledSlider(
        title = "Cooling Setpoint",
        isEnabled = isCoolingSetpointEnabled,
        currentValue = coolSetpoint,
        low = lowCoolSetpoint,
        high = highCoolSetpoint,
        roundingValue = 50f,
        unitSuffix = "℃",
        onValueChange = { newCoolSetPointCentiDegrees ->
          trait.isValidCoolingSetpointUpdate(
            newCoolSetPointCentiDegrees.toInt().toShort()
          )
        },
        onValueChangeFinished = { newValueCentiDegrees ->
          if (newValueCentiDegrees.toInt().toShort() != coolSetpoint)
            trait.setOccupiedCoolingPoint(newValueCentiDegrees.toInt())
        }
      )

      // Cool Adjustment Buttons
      Spacer(Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Button(onClick = {
          scope.launch {
            try {
              trait.setpointRaiseLower(
                mode = ThermostatTrait.SetpointRaiseLowerModeEnum.Cool,
                amount = -5
              )
              println("Decreased Cool by 0.5℃")
            } catch (e: Exception) {
              println("Error changing setpoint: ${e.message}")
            }
          }
        }) {
          Text("-0.5℃ Cool")
        }

        Button(onClick = {
          scope.launch {
            try {
              trait.setpointRaiseLower(
                mode = ThermostatTrait.SetpointRaiseLowerModeEnum.Cool,
                amount = 5
              )
              println("Increased Cool by 0.5℃")
            } catch (e: Exception) {
              println("Error changing setpoint: ${e.message}")
            }
          }
        }) {
          Text("+0.5℃ Cool")
        }
      }

      Spacer(Modifier.height(8.dp))
    }

    val isHeatingSetpointEnabled = isConnected &&
            (trait.systemMode?.isModeHeatingRelated() == true)
    if (isHeatingSetpointEnabled) {
      val lowHeatSetpoint = trait.getMinHeatSetpointLimit()
      val highHeatSetpoint = trait.getMaxHeatSetpointLimit()
      val heatSetpoint = trait.getHeatingSetpoint()

      // Heating Setpoint slider
      Spacer(Modifier.height(16.dp))
      LabeledSlider(
        title = "Heating Setpoint",
        isEnabled = isHeatingSetpointEnabled,
        currentValue = heatSetpoint,
        low = lowHeatSetpoint,
        high = highHeatSetpoint,
        roundingValue = 50f,
        unitSuffix = "℃",
        onValueChange = { newHeatSetPointCentiDegrees ->
          trait.isValidHeatingSetpointUpdate(
            newHeatSetPointCentiDegrees.toInt().toShort()
          )
        },
        onValueChangeFinished = { newValueCentiDegrees -> // The lambda receives the new absolute value
          if (newValueCentiDegrees.toInt().toShort() != heatSetpoint)
            trait.setOccupiedHeatingPoint(newValueCentiDegrees.toInt())
        }
      )

      // Heat Adjustment Buttons
      Spacer(Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Button(onClick = {
          scope.launch {
            try {
              trait.setpointRaiseLower(
                mode = ThermostatTrait.SetpointRaiseLowerModeEnum.Heat,
                amount = -5
              )
              println("Decreased Heat by 0.5℃")
            } catch (e: Exception) {
              println("Error changing setpoint: ${e.message}")
            }
          }
        }) {
          Text("-0.5℃ Heat")
        }
        Button(onClick = {
          println("Increase Heat by 0.5℃")
          scope.launch {
            try {
              trait.setpointRaiseLower(
                mode = ThermostatTrait.SetpointRaiseLowerModeEnum.Heat,
                amount = 5
              )
              println("Increased Heat by 0.5℃")
            } catch (e: Exception) {
              println("Error changing setpoint: ${e.message}")
            }
          }
        }) {
          Text("+0.5℃ Heat")
        }
      }

      Spacer(Modifier.height(8.dp))

    }

    // Both Adjustment Buttons
    if (trait.getSystemMode() == ThermostatTrait.SystemModeEnum.Auto) {
      HorizontalDivider(thickness = 2.dp)

      Text("Auto mode only:")
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Button(onClick = {
          scope.launch {
            try {
              trait.setpointRaiseLower(
                mode = ThermostatTrait.SetpointRaiseLowerModeEnum.Both,
                amount = -5
              )
              println("Decreased Both by 0.5℃")
            } catch (e: Exception) {
              println("Error changing setpoint: ${e.message}")
            }
          }
        }) {
          Text("-0.5℃ Both")
        }
        Button(onClick = {
          scope.launch {
            try {
              trait.setpointRaiseLower(
                mode = ThermostatTrait.SetpointRaiseLowerModeEnum.Both,
                amount = 5
              )
              println("Increased Both by 0.5℃")
            } catch (e: Exception) {
              println("Error changing setpoint: ${e.message}")
            }
          }
        }) {
          Text("+0.5℃ Both")
        }
      }
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
 * @param roundingValue An optional value to round the slider's output to the nearest
 * multiple of. If null, no rounding is performed.
 * @param onValueChange An optional validation lambda that is invoked as the slider
 * value changes. Return `true` to accept the change, `false` to reject it.
 * @param onValueChangeFinished A lambda that is invoked when the user has
 * finished interacting with the slider.
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
      val newValue = if (roundingValue != null) {
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
    enabled = isEnabled
  )
}

/**
 * A composable that wraps a [LevelSlider] with a title and a current value display.
 *
 * This component provides a complete UI element for a labeled slider, handling null
 * states and wiring up the necessary callbacks.
 *
 * @param title The text label to display above the slider.
 * @param isEnabled Whether the slider is enabled and can be interacted with.
 * @param currentValue The current value of the slider. The component will display
 * 'Not available' if this is null.
 * @param low The minimum value of the slider range. Required for the slider to be displayed.
 * @param high The maximum value of the slider range. Required for the slider to be displayed.
 * @param roundingValue An optional value to round the slider's output to the nearest
 * multiple of. Passed to the underlying [LevelSlider].
 * @param unitSuffix A string added as a suffix to the currentValue if a value is present,
 *                   to indicate units of measurement.
 * @param onValueChange An optional validation lambda that is invoked as the slider value
 * changes. Passed to the underlying [LevelSlider].
 * @param onValueChangeFinished A suspend lambda that is invoked with the new value when
 * the user has finished interacting with the slider.
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
      modifier = Modifier.align(Alignment.TopEnd)
    )

    LevelSlider(
      value = vset,
      low = vlow,
      high = vhigh,
      // Calculate steps safely, ensuring it's not negative
      steps = ((vhigh - vlow) / 10f).toInt().minus(1).coerceAtLeast(0),
      roundingValue = roundingValue,
      modifier = Modifier.padding(top = 16.dp),
      onValueChange = { value ->
        if (onValueChange == null) true
        else onValueChange(value)
      },
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
      isEnabled = isEnabled
    )
  }
}

@Composable
private fun MetaRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 14.sp,
      modifier = Modifier.padding(end = 16.dp)
    )
    SelectionContainer(modifier = Modifier.weight(1f, fill = false)) {
      Text(
        text = value,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp,
        textAlign = TextAlign.End,
      )
    }
  }
}