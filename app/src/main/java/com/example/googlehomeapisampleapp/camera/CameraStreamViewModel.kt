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

import android.util.Log
import android.view.Surface
import androidx.lifecycle.SavedStateHandle
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
import com.example.googlehomeapisampleapp.camera.livestreamplayer.LiveStreamPlayer
import com.example.googlehomeapisampleapp.camera.livestreamplayer.LiveStreamPlayerFactory
import com.example.googlehomeapisampleapp.camera.livestreamplayer.LiveStreamPlayerState
import com.example.googlehomeapisampleapp.camera.livestreamplayer.OnOffController
import com.example.googlehomeapisampleapp.camera.livestreamplayer.OnOffControllerFactory
import com.google.home.HomeClient
import com.google.home.HomeDevice
import com.google.home.Id
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** ViewModel for the camera stream view. */
@HiltViewModel
class CameraStreamViewModel
@Inject
internal constructor(
    private val savedStateHandle: SavedStateHandle,
    private val homeClient: HomeClient,
    private val liveStreamPlayerFactory: LiveStreamPlayerFactory,
    private val onOffControllerFactory: OnOffControllerFactory,
) : ViewModel() {

    private val _targetDeviceId = MutableStateFlow<String?>(null)

    fun initialize(id: String) { _targetDeviceId.value = id }

    /** The surface that is used to render the camera stream. */
    private var surface: Surface? = null

    private val _errorMessage = MutableStateFlow<String?>(null)
    /** An error message to display to the user. */
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _liveStreamPlayer = MutableStateFlow<LiveStreamPlayer?>(null)
    /** The [LiveStreamPlayer] for the camera stream. */
    private val liveStreamPlayer: StateFlow<LiveStreamPlayer?> = _liveStreamPlayer

    private val _onOffController = MutableStateFlow<OnOffController?>(null)
    /** Controller for camera on/off state. */
    private val onOffController: StateFlow<OnOffController?> = _onOffController

    /** Whether the camera is recording. This is the on/off state of the camera. */
    val isRecording: StateFlow<Boolean> =
        onOffController
            .flatMapLatest { it?.isRecording ?: flowOf(false) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    /** Whether the camera supports talkback. */
    val supportsTalkback: StateFlow<Boolean> =
        liveStreamPlayer
            .map { it?.supportsTalkback == true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    private val isTalkbackEnabled: Flow<Boolean> =
        liveStreamPlayer.flatMapLatest { it?.isTalkbackEnabled ?: flowOf(false) }
    private val _isToggleRecordingInProgress = MutableStateFlow(false)
    /** Whether a toggle recording operation is in progress. */
    val isToggleRecordingInProgress: StateFlow<Boolean> = _isToggleRecordingInProgress

    private val _isToggleTalkbackInProgress = MutableStateFlow(false)
    /** Whether a toggle talkback operation is in progress. */
    val isToggleTalkbackInProgress: StateFlow<Boolean> = _isToggleTalkbackInProgress

    /** Whether the camera stream view is paused when the app is in background. */
    private val isPaused = MutableStateFlow(false)

    private val isBackgroundPaused: Flow<Boolean> =
        isPaused
            .map {
                if (it) {
                    backgroundStop()
                }
                it
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _state = MutableStateFlow(CameraStreamState.NOT_STARTED)
    val state: StateFlow<CameraStreamState> = _state

    private val livestreamPlayerState: Flow<LiveStreamPlayerState> =
        liveStreamPlayer.flatMapLatest { it?.state ?: flowOf(LiveStreamPlayerState.NOT_STARTED) }
    private var playerJob: Job? = null
    private var startJob: Job? = null

    init {
        viewModelScope.launch {
            _targetDeviceId
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest { id ->
                    Log.d(TAG, "New targetDeviceId received: $id. Initializing resources.")

                    // Run the device-specific resource initialization
                    val success = setupDeviceResources(id)
                    if (success) {
                        // If resources are set up, launch the state machine flow
                        // This nested collector runs as long as the outer collectLatest is active (i.e., until a new ID is emitted or the scope is cancelled).
                        _state
                            .map { state -> handleCameraStreamState(state, id) }
                            .collect { newState ->
                                // Update the state based on the handler's output
                                _state.value = newState
                            }
                    }
                }
        }
    }

    /**
     * Centralized resource setup that runs whenever a new unique device ID is set.
     *
     * @return true if setup was successful, false otherwise.
     */
    private suspend fun setupDeviceResources(deviceId: String): Boolean {
        // Cleanup old resources and reset state before starting setup for a new ID
        _state.value = CameraStreamState.NOT_STARTED
        stopPlayer()
        _onOffController.value = null

        val device = getCameraDevice(deviceId)
        if (device == null) {
            _errorMessage.value = "Device not found for ID: $deviceId"
            _state.value = CameraStreamState.ERROR
            return false
        }

        // Player setup (Create but don't start yet)
        val player = liveStreamPlayerFactory.createPlayerFromDevice(device, viewModelScope)
        if (player == null) {
            _errorMessage.value = "Failed to create player for device"
            _state.value = CameraStreamState.ERROR
            return false
        }
        _liveStreamPlayer.value = player

        // Controller setup
        val controller = onOffControllerFactory.create(device)
        if (controller == null) {
            _errorMessage.value = "Failed to create on/off controller for device"
            _state.value = CameraStreamState.ERROR
            return false
        }
        _onOffController.value = controller

        // Start listening for isRecording changes (once per device setup)
        // Note: The isRecording flow relies on _onOffController being set.
        viewModelScope.launch { controller.isRecording.collect { handleIsRecordingChange(it) } }

        // Success: Transition to the INITIALIZED state, which triggers the state machine
        _state.value = CameraStreamState.INITIALIZED
        return true
    }

    /**
     * Centralized resource setup that runs whenever a new unique device ID is set.
     */
    private suspend fun initializeForDevice(deviceId: String) {
        // Cleanup old resources and reset state before starting setup for a new ID
        _state.value = CameraStreamState.NOT_STARTED
        stopPlayer()
        _onOffController.value = null

        val device = getCameraDevice(deviceId)
        if (device == null) {
            _errorMessage.value = "Device not found for ID: $deviceId"
            _state.value = CameraStreamState.ERROR
            return
        }

        // 1. Player setup
        val player = liveStreamPlayerFactory.createPlayerFromDevice(device, viewModelScope)
        if (player == null) {
            _errorMessage.value = "Failed to create player for device"
            _state.value = CameraStreamState.ERROR
            return
        }
        _liveStreamPlayer.value = player

        // 2. Controller setup
        val controller = onOffControllerFactory.create(device)
        if (controller == null) {
            _errorMessage.value = "Failed to create on/off controller for device"
            _state.value = CameraStreamState.ERROR
            return
        }
        _onOffController.value = controller

        // 3. Start listening for isRecording changes (once per device setup)
        viewModelScope.launch { controller.isRecording.collect { handleIsRecordingChange(it) } }

        // 4. Success: Transition to the INITIALIZED state, which triggers the state machine
        _state.value = CameraStreamState.INITIALIZED
    }

    private suspend fun handleLiveStreamPlayerState(state: LiveStreamPlayerState) {
        Log.d(TAG, "liveStreamPlayer state: $state")
        when (state) {
            LiveStreamPlayerState.STREAMING -> {
                val viewModelState = _state.value
                val isValidState =
                    viewModelState == CameraStreamState.STREAMING_WITHOUT_TALKBACK ||
                            viewModelState == CameraStreamState.STREAMING_WITH_TALKBACK ||
                            viewModelState == CameraStreamState.STARTING
                if (isValidState) {
                    if (isTalkbackEnabled.first()) {
                        _state.value = CameraStreamState.STREAMING_WITH_TALKBACK
                    } else {
                        _state.value = CameraStreamState.STREAMING_WITHOUT_TALKBACK
                    }
                }
            }
            LiveStreamPlayerState.DISPOSED -> {
                if (_state.value != CameraStreamState.ERROR && _state.value != CameraStreamState.STOPPING) {
                    _state.value = CameraStreamState.STOPPING
                }
            }
            else -> {}
        }
    }

    /**
     * Handles the camera stream state.
     *
     * @param state The camera stream state.
     * @param deviceId The ID of the currently targeted device.
     * @return The next camera stream state.
     */
    private suspend fun handleCameraStreamState(state: CameraStreamState, deviceId: String): CameraStreamState {
        Log.d(TAG, "handleCameraStreamState: $state")
        return when (state) {
            // Initialize the on/off controller since streaming depends on its output
            CameraStreamState.NOT_STARTED -> {
                if (!initializeOnOffController(deviceId)) {
                    CameraStreamState.ERROR
                } else {
                    CameraStreamState.INITIALIZED
                }
            }
            CameraStreamState.INITIALIZED -> {
                // Wait for screen to be in foreground
                isBackgroundPaused.first({ !it })

                if (isRecording.first()) {
                    CameraStreamState.READY_ON
                } else {
                    CameraStreamState.READY_OFF
                }
            }
            CameraStreamState.READY_OFF -> {
                if (isRecording.first()) {
                    CameraStreamState.READY_ON
                } else {
                    CameraStreamState.READY_OFF
                }
            }
            // No players have been created yet at this point
            CameraStreamState.READY_ON -> {
                if (startPlayer(deviceId)) {
                    CameraStreamState.STARTING
                } else {
                    CameraStreamState.ERROR
                }
            }
            // The livestream player is stopping
            CameraStreamState.STOPPING -> {
                stopPlayer()
                CameraStreamState.INITIALIZED
            }
            CameraStreamState.ERROR -> {
                stopPlayer()
                CameraStreamState.ERROR
            }
            else -> {
                state
            }
        }
    }

    private suspend fun initializeOnOffController(deviceId:String): Boolean {
        val device = getCameraDevice(deviceId)
        val controller = onOffControllerFactory.create(device)
        if (controller == null) {
            _errorMessage.value = "Failed to create on/off controller for device"
            _state.value = CameraStreamState.ERROR
            return false
        }
        viewModelScope.launch { isRecording.collect { handleIsRecordingChange(it) } }
        _onOffController.value = controller
        return true
    }

    private fun handleIsRecordingChange(isRecording: Boolean) {
        if (isRecording) {
            if (_state.value == CameraStreamState.READY_OFF) {
                _state.value = CameraStreamState.READY_ON
            }
        } else {
            if (
                _state.value != CameraStreamState.NOT_STARTED &&
                _state.value != CameraStreamState.ERROR &&
                _state.value != CameraStreamState.READY_OFF &&
                _state.value != CameraStreamState.STOPPING
            ) {
                _state.value = CameraStreamState.STOPPING
            }
        }
    }

    private suspend fun startPlayer(deviceId: String): Boolean { // <-- Takes deviceId now
        stopPlayer()
        Log.d(TAG, "startPlayer for ID: $deviceId")
        val device = getCameraDevice(deviceId)

        val player = liveStreamPlayerFactory.createPlayerFromDevice(device, viewModelScope)
        if (player == null) {
            _errorMessage.value = "Failed to create player for device"
            _state.value = CameraStreamState.ERROR
            return false
        }
        _liveStreamPlayer.value = player
        surface?.let { player.attachRenderer(it) }

        playerJob =
            viewModelScope.launch { livestreamPlayerState.collect { handleLiveStreamPlayerState(it) } }

        startJob = viewModelScope.launch { player.start() }
        return true
    }

    private suspend fun stopPlayer() {
        Log.d(TAG, "stopPlayer")
        startJob?.cancelAndJoin()
        playerJob?.cancelAndJoin()
        liveStreamPlayer.value?.dispose()
        _liveStreamPlayer.value = null
        playerJob = null
    }

    private suspend fun backgroundStop() {
        if (
            _state.value == CameraStreamState.READY_ON ||
            _state.value == CameraStreamState.STARTING ||
            _state.value == CameraStreamState.STREAMING_WITHOUT_TALKBACK ||
            _state.value == CameraStreamState.STREAMING_WITH_TALKBACK
        ) {
            Log.d(TAG, "Background: stopping player")
            _state.value = CameraStreamState.STOPPING
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up resources if necessary
        viewModelScope.launch(NonCancellable) { stopPlayer() }
    }

    /** Called on the lifecycle event onPause. */
    fun onPause() {
        isPaused.value = true
        if (_state.value == CameraStreamState.READY_OFF) {
            _state.value = CameraStreamState.INITIALIZED
        }
    }

    /** Called when the camera stream view is in foreground. Either rotation or from background. */
    fun onResume() {
        isPaused.value = false
    }

    /**
     * Called when the surface is created.
     *
     * @param surface The surface that was created.
     */
    fun onSurfaceCreated(surface: Surface) {
        if (this.surface != null) {
            _liveStreamPlayer.value?.detachRenderer()
        }
        this.surface = surface
        viewModelScope.launch { _liveStreamPlayer.filterNotNull().first().attachRenderer(surface) }
    }

    /** Called when the surface is destroyed. */
    fun onSurfaceDestroyed() {
        _liveStreamPlayer.value?.detachRenderer()
        this.surface = null
    }

    /**
     * Get the camera device from the home client.
     *
     * @param deviceId The ID of the device to fetch.
     * @return The camera device, or null if not found.
     */
    private suspend fun getCameraDevice(deviceId: String): HomeDevice {
        return requireNotNull(homeClient.devices().get(Id(deviceId)))
    }

    /**
     * Toggle the talkback on or off.
     *
     * @param enabled Whether to enable or disable the microphone.
     */
    fun setTalkback(enabled: Boolean) {
        val player = liveStreamPlayer.value ?: return
        if (
            _state.value == CameraStreamState.STREAMING_WITHOUT_TALKBACK ||
            _state.value == CameraStreamState.STREAMING_WITH_TALKBACK
        ) {
            viewModelScope.launch {
                _isToggleTalkbackInProgress.value = true
                player.toggleTalkback(enabled)
                if (enabled) {
                    _state.value = CameraStreamState.STREAMING_WITH_TALKBACK
                } else {
                    _state.value = CameraStreamState.STREAMING_WITHOUT_TALKBACK
                }
                _isToggleTalkbackInProgress.value = false
            }
        }
    }

    /**
     * Set recording on or off.
     *
     * @param enabled Whether to enable or disable recording.
     */
    fun setRecording(enabled: Boolean) {
        if (_state.value == CameraStreamState.NOT_STARTED || _state.value == CameraStreamState.ERROR) {
            return
        }
        val onOffController = onOffController.value ?: return
        viewModelScope.launch {
            _isToggleRecordingInProgress.value = true
            withTimeoutOrNull(TOGGLE_RECORDING_WAIT_TIME_MILLISECONDS) {
                // Player should be started only after the recording is enabled.
                val toggleSuccess = onOffController.setRecording(enabled)
                if (!toggleSuccess) {
                    _errorMessage.value = "Failed to toggle recording"
                } else if (!enabled) {
                    // Stop the player optimistically before waiting for the recording state to be
                    // updated.
                    stopPlayer()
                }
                // Wait for the recording state to be updated to the desired state for UI to reflect the
                // change.
                isRecording.first { it == enabled }
            }
            _isToggleRecordingInProgress.value = false
        }
    }

    /** To be called when the error message has been shown to the user. */
    fun errorShown() {
        _errorMessage.value = null
    }

    companion object {
        private const val TAG = "CameraStreamViewModel"
        private const val TOGGLE_RECORDING_WAIT_TIME_MILLISECONDS = 4000L
    }
}

/**
 * Enum class for the camera stream state.
 *
 * @property NOT_STARTED The camera stream has not been initialized yet.
 * @property INITIALIZED The camera stream has been initialized.
 * @property READY_OFF The player is ready to be initialized and the camera is off.
 * @property READY_ON The player is ready to be initialized and the camera is on.
 * @property STARTING The player is starting.
 * @property STREAMING_WITHOUT_TALKBACK The player is streaming without talkback.
 * @property STREAMING_WITH_TALKBACK The player is streaming with talkback.
 * @property STOPPING The camera stream is stopping.
 * @property ERROR The camera stream has encountered an error. Can happen at any state.
 */
enum class CameraStreamState {
    NOT_STARTED,
    INITIALIZED,
    READY_OFF, // Camera is off
    READY_ON, // Player is ready to be initialized and camera is on
    STARTING, // Starting the player
    STREAMING_WITHOUT_TALKBACK,
    STREAMING_WITH_TALKBACK,
    STOPPING,
    ERROR,
}