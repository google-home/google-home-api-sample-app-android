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

import android.content.Context
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.googlehomeapisampleapp.camera.CameraStreamState.ERROR
import com.example.googlehomeapisampleapp.camera.CameraStreamState.INITIALIZED
import com.example.googlehomeapisampleapp.camera.CameraStreamState.NOT_STARTED
import com.example.googlehomeapisampleapp.camera.CameraStreamState.READY_OFF
import com.example.googlehomeapisampleapp.camera.CameraStreamState.READY_ON
import com.example.googlehomeapisampleapp.camera.CameraStreamState.STARTING
import com.example.googlehomeapisampleapp.camera.CameraStreamState.STOPPING
import com.example.googlehomeapisampleapp.camera.CameraStreamState.STREAMING_WITHOUT_TALKBACK
import com.example.googlehomeapisampleapp.camera.CameraStreamState.STREAMING_WITH_TALKBACK
import com.example.googlehomeapisampleapp.camera.livestreamplayer.CameraAvStreamManagementController
import com.example.googlehomeapisampleapp.camera.livestreamplayer.CameraAvStreamManagementControllerFactory
import com.example.googlehomeapisampleapp.camera.livestreamplayer.LiveStreamPlayer
import com.example.googlehomeapisampleapp.camera.livestreamplayer.LiveStreamPlayerFactory
import com.example.googlehomeapisampleapp.camera.livestreamplayer.OnOffController
import com.example.googlehomeapisampleapp.camera.livestreamplayer.OnOffControllerFactory
import com.example.googlehomeapisampleapp.camera.timeline.CameraTimelinePresenter
import com.example.googlehomeapisampleapp.camera.timeline.CameraTimelineUiState
import com.example.googlehomeapisampleapp.doorbell.DoorbellChimeController
import com.example.googlehomeapisampleapp.doorbell.DoorbellChimeControllerFactory
import com.google.home.HomeDevice
import com.google.home.google.ChimeTrait
import com.google.home.google.GoogleDoorbellDevice
import com.google.home.google.ZoneManagementTrait
import com.google.home.matter.standard.RootNodeDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import com.example.googlehomeapisampleapp.AuthenticatedImageLoader
import coil3.ImageLoader
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
open class CameraStreamViewModel @Inject internal constructor(
  @AuthenticatedImageLoader val authenticatedImageLoader: ImageLoader,
  private val liveStreamPlayerFactory: LiveStreamPlayerFactory,
  private val onOffControllerFactory: OnOffControllerFactory,
  private val cameraAvStreamManagementControllerFactory: CameraAvStreamManagementControllerFactory,
  private val doorbellChimeControllerFactory: DoorbellChimeControllerFactory,
  private val recordingModeControllerFactory: RecordingModeControllerFactory,
  private val activityZoneControllerFactory: ActivityZoneControllerFactory,
  private val videoAnalysisControllerFactory: VideoAnalysisControllerFactory,
  private val cameraTimelinePresenter: CameraTimelinePresenter,
) : ViewModel() {
  private var activeJobs = mutableListOf<Job>()
  private var recordingOffDebounceJob: Job? = null
  private var recordingOnDebounceJob: Job? = null
  private val deviceDeferred = CompletableDeferred<HomeDevice>()

  private val _uiMessage = MutableSharedFlow<String?>()
  val uiMessage: SharedFlow<String?> = _uiMessage.asSharedFlow()

  private val _liveStreamPlayer = MutableStateFlow<LiveStreamPlayer?>(null)
  private val _onOffController = MutableStateFlow<OnOffController?>(null)

  //Device info state
  private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
  val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo

  // Audio Controller Flow
  private val _cameraAvStreamManagementController = MutableStateFlow<CameraAvStreamManagementController?>(null)
  private val cameraAvStreamManagementController: StateFlow<CameraAvStreamManagementController?> = _cameraAvStreamManagementController

  private val _microphonePermissionGranted = MutableStateFlow(false)
  private val _recordingTruthKnown = MutableStateFlow(false)
  private var stoppedForBackground = false

  fun initialize(microphonePermissionGranted: Boolean) {
    // 1. Check if the permission status has actually changed
    if (_microphonePermissionGranted.value == microphonePermissionGranted) {
      return
    }

    // 2. Update the state
    _microphonePermissionGranted.value = microphonePermissionGranted
    Log.d(TAG, "Permission status changed to: $microphonePermissionGranted. Re-initializing.")

    // 3. Restart the stream to apply the hardware changes
    restartInitialization()
  }

  private val _isToggleAudioRecordingInProgress = MutableStateFlow(false)
  val isToggleAudioRecordingInProgress: StateFlow<Boolean> = _isToggleAudioRecordingInProgress

  // Doorbell Settings Controller
  private val _doorbellChimeController = MutableStateFlow<DoorbellChimeController?>(null)
  @OptIn(ExperimentalCoroutinesApi::class)
  val isDoorbellDevice: StateFlow<Boolean> = _onOffController
    .filterNotNull()
    .map {
      val device = deviceDeferred.await()
      val hasDoorbellTrait = device.has(GoogleDoorbellDevice)
      Log.d(TAG, "isDoorbellDevice Flow Logic -> hasDoorbellTrait: $hasDoorbellTrait")
      hasDoorbellTrait
    }.onEach { Log.d(TAG, "isDoorbellDevice Flow EMITTED: $it") }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = false
    )

  @OptIn(ExperimentalCoroutinesApi::class)
  val isIndoorChimeEnabled: StateFlow<Boolean> =
    _doorbellChimeController
      .flatMapLatest { it?.isChimeEnabled ?: flowOf(true) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), true)

  @OptIn(ExperimentalCoroutinesApi::class)
  val externalChimeType: StateFlow<ChimeTrait.ExternalChimeType> =
    _doorbellChimeController
      .flatMapLatest { it?.externalChimeType ?: flowOf(ChimeTrait.ExternalChimeType.Electronic) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), ChimeTrait.ExternalChimeType.Electronic)

  // Recording mode controller
  private val _recordingModeController = MutableStateFlow<RecordingModeController?>(null)

  /**
   * Emits the full list of recording mode options for this device, each
   * annotated with its availability and a human-readable label.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  val recordingModeOptions: StateFlow<List<RecordingModeOption>> =
    _recordingModeController
      .flatMapLatest { it?.recordingModeOptions ?: flowOf(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), emptyList())

  /**
   * Emits the index of the currently active recording mode, or null if unavailable.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  val selectedRecordingModeIndex: StateFlow<Int?> =
    _recordingModeController
      .flatMapLatest { it?.selectedRecordingModeIndex ?: flowOf(null) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), null)

  // Activity Zone Controller
  private val _activityZoneController = MutableStateFlow<ActivityZoneController?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  val activityZones: StateFlow<List<ActivityZone>> =
    _activityZoneController
      .flatMapLatest { it?.activityZones ?: flowOf(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), emptyList())

  @OptIn(ExperimentalCoroutinesApi::class)
  val twoDCartesianMax: StateFlow<ZoneManagementTrait.TwoDCartesianVertexStruct?> =
    _activityZoneController
      .flatMapLatest { it?.twoDCartesianMax ?: flowOf(null) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), null)

  private val _zoneUpdateStatus = MutableStateFlow<ZoneUpdateStatus>(ZoneUpdateStatus.Idle)
  val zoneUpdateStatus: StateFlow<ZoneUpdateStatus> = _zoneUpdateStatus

  // Camera Snapshot State
  private val _liveSnapshotUrl = MutableStateFlow<String?>(null)
  private val _isFetchingLiveSnapshot = MutableStateFlow(false)
  val isFetchingLiveSnapshot: StateFlow<Boolean> = _isFetchingLiveSnapshot.asStateFlow()

  /**
   * StateFlow emitting the camera's static preview image URL provided by the [CameraSnapshot] trait.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  val previewImageUrl: StateFlow<String?> =
    _activityZoneController
      .flatMapLatest { it?.previewImageUrl ?: flowOf(null) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), null)

  /**
   * StateFlow emitting the effective snapshot URL to display (prefers on-demand live snapshot URL over static preview image URL).
   */
  val snapshotUrl: StateFlow<String?> =
    combine(_liveSnapshotUrl, previewImageUrl) { liveUrl, previewUrl ->
      liveUrl ?: previewUrl
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), null)

  /**
   * Triggers an on-demand live snapshot fetch from the camera.
   * Updates [snapshotUrl] via [_liveSnapshotUrl] on success, or emits a UI message on failure.
   */
  fun refreshLiveSnapshot() {
    val controller = _activityZoneController.value ?: return
    if (!isRecording.value) {
      viewModelScope.launch {
        _uiMessage.emit("Cannot refresh snapshot when camera is off")
      }
      return
    }
    viewModelScope.launch {
      _isFetchingLiveSnapshot.value = true
      try {
        val url = controller.fetchLiveSnapshotUrl()
        if (url != null) {
          val timestamp = System.currentTimeMillis()
          // Append timestamp query parameter to URL to force UI image reload when fetching a new snapshot.
          // Note: Query parameter cache-busting is intentionally used here to bypass Coil caching without overriding image loader defaults.
          val cacheBustedUrl = if (url.contains("?")) "$url&t=$timestamp" else "$url?t=$timestamp"
          _liveSnapshotUrl.value = cacheBustedUrl
          _uiMessage.emit("Live snapshot updated")
        } else {
          _uiMessage.emit("Failed to refresh live snapshot")
        }
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Error refreshing live snapshot", e)
        _uiMessage.emit("Error fetching live snapshot: ${e.message}")
      } finally {
        _isFetchingLiveSnapshot.value = false
      }
    }
  }

  // Video Analysis (Gemini AI Features) Controllers state flow
  private val _videoAnalysisControllers = MutableStateFlow<List<VideoAnalysisController>>(emptyList())
  val videoAnalysisControllers: StateFlow<List<VideoAnalysisController>> = _videoAnalysisControllers

  // Tracks in-progress state of asynchronous toggle operations for UI progress indicators
  private val _isToggleAiFeaturesInProgress = MutableStateFlow(false)
  val isToggleAiFeaturesInProgress: StateFlow<Boolean> = _isToggleAiFeaturesInProgress

  // --- UI State Flows ---
  @OptIn(ExperimentalCoroutinesApi::class)
  val isRecording: StateFlow<Boolean> = _onOffController
    .filterNotNull() // Wait until the controller actually exists
    .flatMapLatest { it.isRecording }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS),
      initialValue = false // This will still be false until the first cloud sync
    )
  /** * Observe the Audio Recording state.
   * Hardware "Muted" = UI "Recording OFF", so we invert the boolean.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  val isAudioRecording: StateFlow<Boolean> = cameraAvStreamManagementController
    .flatMapLatest { controller ->
      // Reference mapping: Hardware Muted = UI OFF (Inverted)
      controller?.isRecordingMicrophoneMuted?.map { isMuted -> !isMuted } ?: flowOf(false)
    }
    .onEach { isEnabled ->
      Log.d("AUDIO_DEBUG", "OBSERVER: Cloud Trait Updated -> Recording Enabled = $isEnabled")
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), false)

  // Add this init block or update your existing one to "unlock" the state
  private val isHardwareReady = MutableStateFlow(false)

  init {
    viewModelScope.launch {
      Log.d(TAG, "Hardware Stabilizing...")
      delay(3000)

      _onOffController.filterNotNull().first()

      isHardwareReady.value = true
      Log.d(TAG, "Hardware UNLOCKED - Now evaluating truth")

      _state.collect { currentState ->
        val nextState = handleCameraStreamState(currentState)
        if (nextState != currentState) {
          _state.value = nextState
        }
      }
    }
  }

  private val _isToggleRecordingInProgress = MutableStateFlow(false)
  val isToggleRecordingInProgress: StateFlow<Boolean> = _isToggleRecordingInProgress

  private val _isToggleTalkbackInProgress = MutableStateFlow(false)
  val isToggleTalkbackInProgress: StateFlow<Boolean> = _isToggleTalkbackInProgress

  @OptIn(ExperimentalCoroutinesApi::class)
  val isTalkbackEnabled: StateFlow<Boolean> = _liveStreamPlayer
    .flatMapLatest { it?.isTalkbackEnabled ?: flowOf(false) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), false)

  val isTalkbackSupported: StateFlow<Boolean> = _liveStreamPlayer
    .map { it?.isTalkbackSupported == true }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), false)

  private val _state = MutableStateFlow(NOT_STARTED)
  val state: StateFlow<CameraStreamState> = _state

  private var surface: Surface? = null

  @OptIn(ExperimentalCoroutinesApi::class)
  val cameraTimelineUiState: StateFlow<CameraTimelineUiState?> =
    flow {
      emit(deviceDeferred.await())
    }
      .flatMapLatest { device ->
        Log.d(TAG, "Timeline: Starting presenter for device ${device.id.id}")
        cameraTimelinePresenter.present(
          deviceId = device.id.id,
          initialTimestamp = null,
        )
      }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS),
        null
      )

  // --- Commands ---

  fun setRecording(enabled: Boolean) {
    if (_state.value == NOT_STARTED) return
    val controller = _onOffController.value ?: return

    viewModelScope.launch {
      _isToggleRecordingInProgress.value = true

      if (enabled) {
        // Clear any lingering sessions before turning back on
        stopPlayer()
        _state.value = INITIALIZED
      }

      val result = withTimeoutOrNull(TOGGLE_WAIT_TIME) {
        controller.setRecording(enabled)

        if (!enabled) {
          stopPlayer(showReadyOff = true)
        }

        // Wait for hardware to confirm it actually changed
        isRecording.first { it == enabled }
      }

      if (result == null) {
        Log.e(TAG, "Toggle timed out. Hardware is out of sync.")
      }

      if (enabled && result != null) {
        Log.d(TAG, "Toggle Success: Forcing READY_ON to start video")
        _state.value = READY_ON
      }

      _isToggleRecordingInProgress.value = false
    }
  }

  fun setTalkback(enabled: Boolean) {
    Log.i(TAG, "setTalkback: Requesting Mic -> $enabled")
    val player = _liveStreamPlayer.value
    if (player == null) {
      Log.w(TAG, "setTalkback: Ignored, player is null (likely closing).")
      return
    }

    viewModelScope.launch {
      _isToggleTalkbackInProgress.value = true
      try {
        player.toggleTalkback(enabled)
        // Wait for WebRTC hardware to confirm state change
        withTimeoutOrNull(2000) {
          isTalkbackEnabled.first { it == enabled }
        }
        _uiMessage.emit(if (enabled) "Microphone ON" else "Microphone OFF")
      } catch (e: Exception) {
        Log.e(TAG, "Talkback toggle failed: ${e.message}")
        _uiMessage.emit("Microphone error")
      } finally {
        _isToggleTalkbackInProgress.value = false
      }
    }
  }

  /** Set audio recording on or off (microphone for clips). */
  /** * Writes the new Audio Recording state to the cloud.
   * Translates UI "Enabled" to Hardware "Not Muted".
   */
  fun setAudioRecording(enabled: Boolean) {
    val audioController = cameraAvStreamManagementController.value ?: return

    if (_state.value != STREAMING_WITH_TALKBACK && _state.value != STREAMING_WITHOUT_TALKBACK) {
      Log.w(TAG, "SDK Busy: Delaying audio write until stream is stable")
      // Optionally queue the action or show a 'Please wait' message
      return
    }
    viewModelScope.launch {
      _isToggleAudioRecordingInProgress.value = true
      try {
        val success = audioController.setRecordingMicrophoneMuted(!enabled)
        Log.d("USER_ACTION", "Write command sent. Success: $success")

        withTimeoutOrNull(TOGGLE_WAIT_TIME) {
          isAudioRecording.first { it == enabled }
        }
      } finally {
        _isToggleAudioRecordingInProgress.value = false
      }
    }
  }

  /**
   * Updates the camera's recording mode (CVR, EBR, ETR, Still Images, Live View, Disabled).
   *
   * @param index The index of the selected mode from [recordingModeOptions].
   */
  fun setRecordingMode(index: Int) {
    val controller = _recordingModeController.value ?: return
    viewModelScope.launch {
      controller.setRecordingMode(index)
    }
  }

  /**
   * Creates a new activity zone with default vertices covering ~60% of the camera frame,
   * scaled to the device's twoDCartesianMax bounds.
   */
  fun addActivityZone() {
    val controller = _activityZoneController.value ?: return
    val max = twoDCartesianMax.value
    // Use device max if available, otherwise fall back to common default (1920x1080)
    val maxX = max?.x?.toInt() ?: 1920
    val maxY = max?.y?.toInt() ?: 1080
    val modifiableCount = activityZones.value.count { it.modifiable }

    val existingNumbers = activityZones.value
      .filter { it.modifiable }
      .mapNotNull { zone ->
        zoneNameRegex.find(zone.zoneName.trim())?.let { it.groupValues[1].toIntOrNull() }
      }
      .toSet()
    val nextNumber = (1..MAX_ACTIVITY_ZONES).firstOrNull { it !in existingNumbers } ?: (modifiableCount + 1)
    val colorIndex = (nextNumber - 1).coerceAtLeast(0) % ActivityZoneColorCycle.size

    val zone = ActivityZone(
      zoneName = "Zone $nextNumber",
      color = ActivityZoneColorCycle[colorIndex],
      maxX = maxX,
      maxY = maxY,
      vertices = listOf(
        ActivityZoneVertex((maxX * 0.2).toInt(), (maxY * 0.2).toInt()),
        ActivityZoneVertex((maxX * 0.8).toInt(), (maxY * 0.2).toInt()),
        ActivityZoneVertex((maxX * 0.8).toInt(), (maxY * 0.8).toInt()),
        ActivityZoneVertex((maxX * 0.2).toInt(), (maxY * 0.8).toInt()),
      )
    )

    viewModelScope.launch {
      _zoneUpdateStatus.value = ZoneUpdateStatus.InProgress
      val initialCount = activityZones.value.count { it.modifiable }
      val result = controller.addZone(zone)
      if (result is ZoneUpdateStatus.Success) {
        // Wait for the GHP trait Flow to reflect the new zone before clearing the progress state
        withTimeoutOrNull(TOGGLE_WAIT_TIME) {
          activityZones.first { zones -> zones.count { it.modifiable } > initialCount }
        }
      }
      _zoneUpdateStatus.value = result
    }
  }

  /**
   * Deletes the activity zone with the given [zoneId].
   */
  fun deleteActivityZone(zoneId: Int) {
    val controller = _activityZoneController.value ?: return
    viewModelScope.launch {
      _zoneUpdateStatus.value = ZoneUpdateStatus.InProgress
      val result = controller.deleteZone(zoneId)
      if (result is ZoneUpdateStatus.Success) {
        // Wait for the GHP trait Flow to reflect the deletion before clearing the progress state
        withTimeoutOrNull(TOGGLE_WAIT_TIME) {
          activityZones.first { zones -> zones.none { it.zoneId == zoneId } }
        }
      }
      _zoneUpdateStatus.value = result
    }
  }

  /**
   * Toggles Gemini AI feature analysis on or off for a specific controller/endpoint.
   *
   * @param controller The target VideoAnalysisController endpoint.
   * @param enabled Desired enablement state for AI features.
   */
  fun onSetAiFeaturesEnabled(controller: VideoAnalysisController, enabled: Boolean) {
    // Launch coroutine to execute feature toggle asynchronously
    viewModelScope.launch {
      _isToggleAiFeaturesInProgress.value = true
      try {
        val success = controller.setAiFeaturesEnabled(enabled)
        if (!success) {
          Log.w(TAG, "Failed to toggle AI features enablement to $enabled for ${controller.label}")
          _uiMessage.emit("Failed to toggle ${controller.label}")
        }
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Exception toggling AI features enablement for ${controller.label}", e)
        _uiMessage.emit("Error toggling ${controller.label}: ${e.localizedMessage ?: "Unknown error"}")
      } finally {
        _isToggleAiFeaturesInProgress.value = false
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun setDevice(device: HomeDevice) {
    val currentDevice = if (deviceDeferred.isCompleted) deviceDeferred.getCompleted() else null
    if (currentDevice != null && currentDevice.id != device.id) {
      viewModelScope.launch {
        stopPlayer()
        setupDeviceResources(device, micGranted = _microphonePermissionGranted.value)
      }
      return
    }
    if (!deviceDeferred.isCompleted) {
      deviceDeferred.complete(device)
      viewModelScope.launch { setupDeviceResources(device, micGranted = _microphonePermissionGranted.value) }
      return
    }
    viewModelScope.launch {
      setupDeviceResources(device, micGranted = _microphonePermissionGranted.value)
    }
  }

  private suspend fun setupDeviceResources(device: HomeDevice, micGranted: Boolean): Boolean {
    stopExternalJob?.cancel()
    stopExternalJob = null
    recordingOffDebounceJob?.cancel()
    recordingOffDebounceJob = null
    recordingOnDebounceJob?.cancel()
    recordingOnDebounceJob = null

    _liveSnapshotUrl.value = null
    _isFetchingLiveSnapshot.value = false

    activeJobs.forEach { it.cancel() }
    activeJobs.clear()
    val stalePlayer = _liveStreamPlayer.value
    if (stalePlayer != null) {
      _liveStreamPlayer.value = null
      withContext(NonCancellable) {
        try { stalePlayer.toggleTalkback(false); stalePlayer.dispose() } catch (e: Exception) {
          Log.e(TAG, "setupDeviceResources: stale player dispose error: ${e.message}")
        }
      }
    }

    // Extract device information
    extractDeviceInfo(device)

    if (device.has(GoogleDoorbellDevice)) {
      _doorbellChimeController.value = doorbellChimeControllerFactory.create(device)
    }

    val controller = onOffControllerFactory.create(device)
    _onOffController.value = controller
    _cameraAvStreamManagementController.value = cameraAvStreamManagementControllerFactory.create(device)
    _recordingModeController.value = recordingModeControllerFactory.create(device)
    _activityZoneController.value = activityZoneControllerFactory.create(device)
    _videoAnalysisControllers.value = videoAnalysisControllerFactory.createAll(device)

    val player = liveStreamPlayerFactory.createPlayerFromDevice(device, viewModelScope, micGranted)
    _liveStreamPlayer.value = player

    surface?.let {
      Log.d(TAG, "setupDeviceResources: Attaching surface to new player")
      player?.attachRenderer(it)
    }

    viewModelScope.launch {
      player?.state?.collect { playerInternalState ->
        val stateStr = playerInternalState.toString()
        when {
          stateStr.contains("STREAMING", ignoreCase = true) -> {
            val isTalkbackOn = player.isTalkbackEnabled.first()
            _state.value = if (isTalkbackOn) STREAMING_WITH_TALKBACK else STREAMING_WITHOUT_TALKBACK
          }
          stateStr.contains("ERROR", ignoreCase = true) ||
                  stateStr.contains("FAILED", ignoreCase = true) ||
                  stateStr.contains("DISCONNECTED", ignoreCase = true) -> {
            if (_state.value == STARTING) {
              _state.value = ERROR
            }
          }
        }
      }
    }.also { activeJobs.add(it) }

    viewModelScope.launch {
      controller?.isRecording?.collect { rec ->
        if (!_recordingTruthKnown.value) _recordingTruthKnown.value = true
        handleIsRecordingChange(rec)
      }
    }.also { activeJobs.add(it) }

    _state.value = INITIALIZED
    return true
  }

  //Extract device information
  private fun extractDeviceInfo(device: HomeDevice) {
    viewModelScope.launch {
      try {
        val deviceTypes = device.types().first()
        val rootNode = deviceTypes.filterIsInstance<RootNodeDevice>().firstOrNull()

        if (rootNode != null) {
          val basicInfo = rootNode.standardTraits.basicInformation

          if (basicInfo != null) {
            val vendorId = basicInfo.vendorId?.toInt() ?: 0
            val productId = basicInfo.productId?.toInt() ?: 0
            val productName = basicInfo.productName
            val vendorName = basicInfo.vendorName

            val model = if (productName != null && productName != "Unknown") {
              if (vendorName != null && vendorName != "Unknown Vendor") {
                "$vendorName $productName"
              } else {
                productName
              }
            } else {
              "VID: $vendorId, PID: $productId"
            }

            val finalSoftwareVersion = basicInfo.softwareVersionString
              ?: basicInfo.softwareVersion?.toString()
              ?: "Unknown"

            val finalHardwareVersion = basicInfo.hardwareVersionString
              ?: basicInfo.hardwareVersion?.toString()
              ?: "Unknown"

            _deviceInfo.value = DeviceInfo(
              model = model,
              softwareVersion = finalSoftwareVersion,
              hardwareVersion = finalHardwareVersion
            )
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to extract device info: ${e.message}")
      }
    }
  }

  private suspend fun handleCameraStreamState(currentState: CameraStreamState): CameraStreamState {
    Log.d(TAG, "State Machine Eval: $currentState")
    return when (currentState) {
      INITIALIZED -> {
        if (_isToggleRecordingInProgress.value) return currentState
        if (!_recordingTruthKnown.value) return currentState
        if (isRecording.value) READY_ON else READY_OFF
      }
      READY_ON -> {
        Log.d(TAG, "State Machine: READY_ON -> STARTING")
        if (startPlayer()) {
          STARTING
        } else {
          ERROR
        }
      }
      STOPPING -> {
        stopPlayer()
        READY_OFF // Clean landing on the "Camera is Off" screen
      }
      else -> currentState
    }
  }

  private fun handleIsRecordingChange(isRecording: Boolean) {
    viewModelScope.launch {
      if (!isHardwareReady.value) return@launch

      if (isRecording) {
        recordingOffDebounceJob?.cancel()
        recordingOffDebounceJob = null

        recordingOnDebounceJob?.cancel()
        recordingOnDebounceJob = viewModelScope.launch {
          delay(1500)
          val currentState = _state.value
          if (currentState == STARTING || currentState == STREAMING_WITHOUT_TALKBACK || currentState == STREAMING_WITH_TALKBACK) {
            return@launch
          }
          if (_liveStreamPlayer.value == null) {
            val device = deviceDeferred.await()
            setupDeviceResources(device, micGranted = _microphonePermissionGranted.value)
          }
          _state.value = READY_ON
        }
      } else {
        val currentState = _state.value
        val isStreaming = currentState == STREAMING_WITH_TALKBACK ||
                currentState == STREAMING_WITHOUT_TALKBACK
        if (!isStreaming) {
          return@launch
        }

        recordingOffDebounceJob?.cancel()
        recordingOffDebounceJob = viewModelScope.launch {
          Log.w(TAG, "debounce: waiting 3s, current state=${_state.value}")
          // Some hardware is slow to report its final state and can emit a transient
          // false before settling back to true. Wait 3s then re-check before stopping.
          delay(3000)
          val controller = _onOffController.value
          val stillOff = controller?.isRecording?.first() == false
          Log.w(TAG, "debounce: stillOff=$stillOff, state=${_state.value}")
          if (stillOff) stopPlayer(showReadyOff = true)
        }
      }
    }
  }
  private fun startPlayer(): Boolean {
    val player = _liveStreamPlayer.value
    if (player == null) {
      Log.w(TAG, "startPlayer: Player null, attempting emergency setup")
      viewModelScope.launch {
        val device = deviceDeferred.await()
        setupDeviceResources(device, micGranted = _microphonePermissionGranted.value)
      }
      return false
    }

    surface?.let {
      Log.d(TAG, "startPlayer: Attaching surface")
      player.attachRenderer(it)
    }

    // Launch in a tracked job so it gets cancelled cleanly by setupDeviceResources
    // on the next navigation cycle.
    val startJob = viewModelScope.launch {
      try {
        player.start()
      } catch (e: Exception) {
        Log.e(TAG, "startPlayer: Handshake failed: ${e.message}")
        if (_state.value == STARTING) _state.value = ERROR
      }
    }.also { job ->
      job.invokeOnCompletion { activeJobs.remove(job) }
    }
    activeJobs.add(startJob)
    return true
  }

  private var stopExternalJob: Job? = null

  fun stopPlayerExternally(isBackground: Boolean = false) {
    stoppedForBackground = isBackground
    stopExternalJob = viewModelScope.launch {
      stopPlayer(showReadyOff = !isBackground)
    }
  }

  private suspend fun stopPlayer(showReadyOff: Boolean = false) {
    val player = _liveStreamPlayer.value ?: return

    // Immediately clear state before entering NonCancellable so that any concurrent
    // setupDeviceResources call sees a clean slate and can install a new player.
    _liveStreamPlayer.value = null
    if (showReadyOff) _state.value = READY_OFF
    activeJobs.forEach { it.cancel() }
    activeJobs.clear()

    Log.w(TAG, "stopPlayer: disposing player=$player")
    withContext(NonCancellable) {
      try {
        player.toggleTalkback(false)
        player.dispose()
      } catch (e: Exception) {
        Log.e(TAG, "stopPlayer error: ${e.message}")
      } finally {
        Log.w(TAG, "stopPlayer: player disposed")
      }
    }
  }

  fun onSurfaceCreated(surface: Surface) {
    this.surface = surface
    _liveStreamPlayer.value?.attachRenderer(surface)
  }

  // Only detach for true navigation away. During background/foreground cycles Android
  // destroys and recreates the SurfaceView surface — the player was already disposed on
  // ON_STOP, so calling detachRenderer here would hit the new player and null its
  // renderTarget before the video track arrives.
  fun onSurfaceDestroyed() {
    if (!stoppedForBackground) {
      _liveStreamPlayer.value?.detachRenderer()
    }
    this.surface = null
  }

  // Only restarts the stream if the app was actually backgrounded (ON_STOP).
  // ON_RESUME after navigation is handled by setDevice(), so no action needed there.
  fun onAppForegrounded() {
    if (!stoppedForBackground) return
    stoppedForBackground = false
    viewModelScope.launch {
      Log.i(TAG, "onAppForegrounded: restarting stream after background")
      val device = deviceDeferred.await()
      setupDeviceResources(device, micGranted = _microphonePermissionGranted.value)
    }
  }

  fun restartInitialization() {
    Log.i(TAG, "restartInitialization: Manual hard-reset triggered.")
    viewModelScope.launch {
      val device = deviceDeferred.await()
      // Use the actual permission state from the flow
      setupDeviceResources(device, micGranted = _microphonePermissionGranted.value)
    }
  }

  override fun onCleared() {
    Log.i(TAG, "ViewModel onCleared: Releasing all resources")
    stopExternalJob?.cancel()
    recordingOffDebounceJob?.cancel()
    recordingOnDebounceJob?.cancel()
    activeJobs.forEach { it.cancel() }
    activeJobs.clear()
    val player = _liveStreamPlayer.value
    _liveStreamPlayer.value = null
    if (player != null) {
      viewModelScope.launch(NonCancellable) {
        try { player.toggleTalkback(false); player.dispose() } catch (e: Exception) {
          Log.e(TAG, "onCleared: player dispose error: ${e.message}")
        }
      }
    }
    surface = null
    super.onCleared()
  }

  // Expose whether the "Software Enable" attribute exists on this hardware
  @OptIn(ExperimentalCoroutinesApi::class)
  val isChimeToggleSupported: StateFlow<Boolean> = _doorbellChimeController
    .flatMapLatest { it?.isChimeToggleSupported ?: flowOf(false) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), false)


  fun toggleIndoorChime() {
    val controller = _doorbellChimeController.value ?: return
    val currentState = isIndoorChimeEnabled.value // Current truth

    viewModelScope.launch {
      // We calculate the inverse here (!currentState)
      val success = controller.setChimeEnabled(!currentState)

      if (success) {
        withTimeoutOrNull(TOGGLE_WAIT_TIME) {
          // Wait for the flow to reflect the change from the cloud
          isIndoorChimeEnabled.first { it == !currentState }
        }
      }
    }
  }

  fun setExternalChimeType(type: ChimeTrait.ExternalChimeType) {
    val controller = _doorbellChimeController.value ?: return
    viewModelScope.launch {
      Log.d(TAG, "Setting Physical Chime Type to: $type")
      controller.setExternalChimeType(type)
    }
  }

  companion object {
    private const val TAG = "CameraStreamViewModel"
    private const val TOGGLE_WAIT_TIME = 4000L
    private const val FLOW_STOP_TIMEOUT_MS = 5000L
    private val zoneNameRegex = Regex("""^Zone\s+(\d+)$""", RegexOption.IGNORE_CASE)
  }
}

enum class CameraStreamState {
  NOT_STARTED,
  INITIALIZED,
  READY_OFF, // Camera is physically powered off
  READY_ON,  // Ready to start WebRTC session
  STARTING,  // Handshake in progress
  STREAMING_WITHOUT_TALKBACK,
  STREAMING_WITH_TALKBACK,
  STOPPING,
  ERROR,
}

data class DeviceInfo(
  val model: String,
  val softwareVersion: String,
  val hardwareVersion: String
)