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

package com.example.googlehomeapisampleapp.viewmodel

import android.accounts.Account
import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.example.googlehomeapisampleapp.BuildConfig
import com.example.googlehomeapisampleapp.FabricType
import com.example.googlehomeapisampleapp.HomeApp
import com.example.googlehomeapisampleapp.HomeModule_ProvideSupportedTraitsFactory
import com.example.googlehomeapisampleapp.MainActivity
import com.example.googlehomeapisampleapp.cloudlinking.CurrentStructureRepository
import com.example.googlehomeapisampleapp.history.HistoryEventUi
import com.example.googlehomeapisampleapp.history.HistoryUiDataModel
import com.example.googlehomeapisampleapp.history.HomeBriefCameraEvent
import com.example.googlehomeapisampleapp.history.HomeHistoryPagingSource
import com.example.googlehomeapisampleapp.history.toUiDataModel
import com.example.googlehomeapisampleapp.repository.AutomationsRepository
import com.example.googlehomeapisampleapp.viewmodel.automations.ActionViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.AutomationViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.CandidateViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.DraftViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.example.googlehomeapisampleapp.viewmodel.hubs.HubDiscoveryViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.RoomViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.StructureViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.home.HomeBriefsPage
import com.google.home.Structure
import com.google.home.annotation.HomeExperimentalApi
import com.google.home.automation.CommandCandidate
import com.google.home.automation.DraftAutomation
import com.google.home.automation.NodeCandidate
import com.google.home.automation.UnknownDeviceType
import com.google.home.getHistoryManager
import com.google.home.getHomeBriefsManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class HomeAppViewModel(
  val homeApp: HomeApp,
  val currentStructureRepository: CurrentStructureRepository,
) : ViewModel() {

  // Tabs showing main capabilities of the app:
  enum class NavigationTab {
    DEVICES,
    AUTOMATIONS,
    HISTORY,
  }

  companion object {
    const val TAG = "HomeAppViewModel"
  }

  // Container tracking the active navigation tab:
  var selectedTab: MutableStateFlow<NavigationTab> = MutableStateFlow(NavigationTab.DEVICES)

  private val _showQrCodeScanner = MutableStateFlow(false)
  val showQrCodeScanner = _showQrCodeScanner.asStateFlow()

  private val _showCloudLinkingSheet = MutableStateFlow(false)
  val showCloudLinkingSheet = _showCloudLinkingSheet.asStateFlow()

  fun openCloudLinkingSheet() {
    _showCloudLinkingSheet.value = true
  }

  fun closeCloudLinkingSheet() {
    _showCloudLinkingSheet.value = false
  }

  // OTA Screen State Management
  /**
   * State flow for showing the OTA information screen after camera commissioning. True when OTA
   * screen should be shown, false otherwise.
   */
  private val _showOtaScreen = MutableStateFlow(false)
  val showOtaScreen: StateFlow<Boolean> = _showOtaScreen

  // Containers tracking the active object being edited:
  val selectedStructureVM: StateFlow<StructureViewModel?> =
    currentStructureRepository.selectedStructureVM

  fun setSelectedStructure(structure: StructureViewModel?) {
    currentStructureRepository.setSelectedStructure(structure)
  }

  var selectedDeviceVM: MutableStateFlow<DeviceViewModel?> = MutableStateFlow(null)
  var selectedAutomationVM: MutableStateFlow<AutomationViewModel?> = MutableStateFlow(null)
  var selectedDraftVM: MutableStateFlow<DraftViewModel?> = MutableStateFlow(null)
  var selectedCandidateVMs: MutableStateFlow<List<CandidateViewModel>?> = MutableStateFlow(null)

  // Container to store returned structures from the app:
  var structureVMs: MutableStateFlow<List<StructureViewModel>> = MutableStateFlow(mutableListOf())

  private var hubDiscoveryVM: HubDiscoveryViewModel? = null
  val hubDiscoveryViewModel: HubDiscoveryViewModel
    get() = hubDiscoveryVM!!

  private val _selectedHistoryDeviceVM = MutableStateFlow<DeviceViewModel?>(null)
  val selectedHistoryDeviceVM = _selectedHistoryDeviceVM.asStateFlow()
  private val _selectedVideoEvent = MutableStateFlow<HistoryUiDataModel.CameraEvent?>(null)
  val selectedVideoEvent = _selectedVideoEvent.asStateFlow()

  // HomeBriefs state
  /** The latest fetched list of Home Briefs for the selected structure. */
  private val _homeBriefs = MutableStateFlow<List<HistoryUiDataModel.HomeBriefEvent>>(emptyList())
  val homeBriefs: StateFlow<List<HistoryUiDataModel.HomeBriefEvent>> = _homeBriefs.asStateFlow()

  /** True while a [loadHomeBriefs] fetch is in progress. */
  private val _homeBriefsLoading = MutableStateFlow(false)
  val homeBriefsLoading: StateFlow<Boolean> = _homeBriefsLoading.asStateFlow()

  @OptIn(ExperimentalCoroutinesApi::class, HomeExperimentalApi::class)
  val historyFlow: Flow<PagingData<HistoryEventUi>> =
    combine(
        selectedStructureVM.filterNotNull().map { it.id }.distinctUntilChanged(),
        selectedHistoryDeviceVM.map { it?.device?.id?.id }.distinctUntilChanged(),
      ) { structureId, deviceId ->
        structureId to deviceId
      }
      .flatMapLatest { (structureId, deviceId) ->
        // 1. Get the Structure object from the list of available structures
        val structure =
          structureVMs.value.firstOrNull { it.id == structureId }?.structure
            ?: return@flatMapLatest kotlinx.coroutines.flow.flowOf(PagingData.empty())

        Pager(PagingConfig(pageSize = 20)) {
            // 2. Obtain historyManager from the specific structure
            val historyManager = structure.getHistoryManager()
            val builder = HomeHistoryPagingSource.Builder(historyManager)

            // 3. Apply the device filter to stop global history fetching
            deviceId?.let { builder.addHistoryFilters(com.google.home.HistoryFilter.id(it)) }
            builder.build()
          }
          .flow
          .map { pagingData ->
            pagingData
              .map { historyItem ->
                // 4. Map raw SDK HistoryItem to your app's UI model
                historyItem.toUiDataModel()
              }
              .insertSeparators { before, after ->
                // 5. Add date separators (Today, Yesterday, etc.)
                shouldAddDateSeparator(before, after)?.let { HistoryEventUi.DateSeparatorModel(it) }
              }
          }
      }
      .cachedIn(viewModelScope)

  private val _navigateToProxyActivity = Channel<Unit>(Channel.CONFLATED)
  val navigateToProxyActivity = _navigateToProxyActivity.receiveAsFlow()

  fun signInWithGoogleAccount(context: Context) {
    viewModelScope.launch {
      try {
        Log.d(TAG, "Initiating Google Sign-In flow...")
        // CredentialManager is responsible for interacting with various credential providers on the
        // device
        val credentialManager = CredentialManager.create(context)
        // Your GCP console Web Client ID for Google Sign-In
        val serverClientId = BuildConfig.DEFAULT_WEB_CLIENT_ID
        // Build the request for Google ID token
        val googleIdOption =
          GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) // Show all Google accounts on the device
            .setServerClientId(serverClientId) // embed WebClientID in token
            .build()
        // Build the GetCredentialRequest
        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

        // Credential returns when user has selected an account and the getCredential call completes
        val result = credentialManager.getCredential(context = context, request = request)
        val credential = result.credential
        Log.d(TAG, "get credential type: ${credential::class.java.simpleName}")

        if (
          credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
          try {
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            googleCredential.id.let { email ->
              Log.i(TAG, "Email found in Google ID Token: $email")
              /*
               Why "com.google"?
               The string "com.google" is a standard identifier used in Android's android.accounts.
               Account system to represent accounts managed by Google. This is often used when
               interacting with Android's Account Manager or when using Google-specific APIs. So,
               even if the email ends in "@gmail.com", the underlying account type or provider is
               still considered "com.google" within the Android system.
              */
              val account = Account(email, "com.google")
              homeApp.homeClientProvider.switchAccount(account.name, serverClientId)
              Log.d(TAG, "Switched to account to : $account")
            }
            Log.i(TAG, "Account switch complete. Emitting navigation event.")
            // Send an event to the channel to signal the UI to navigate.
            _navigateToProxyActivity.send(Unit)
          } catch (e: Exception) {
            Log.e(TAG, "Could not convert CustomCredential to Google ID Token", e)
            MainActivity.showError(
              this@HomeAppViewModel,
              "Could not convert CustomCredential to Google ID Token" + e.message,
            )
          }
        } else {
          Log.e(
            TAG,
            "Google Sign-In failed: Unexpected result type ${credential::class.java.simpleName}",
          )
          MainActivity.showError(
            this@HomeAppViewModel,
            "Google Sign-In failed: Unexpected result type ${credential::class.java.simpleName}",
          )
        }
      } catch (e: NoCredentialException) {
        Log.e(TAG, "No credentials available", e)
        MainActivity.showError(
          this@HomeAppViewModel,
          "No accounts found. Please add a Google Account to your device settings.",
        )
      } catch (e: GetCredentialException) {
        Log.e(TAG, "Credential retrieval failed", e)
        // You might not want to show an error if the user simply cancelled the dialog
        if (!e.message.orEmpty().contains("User cancelled")) {
          MainActivity.showError(this@HomeAppViewModel, "Sign in failed: ${e.message}")
        }
      } catch (e: Exception) {
        Log.e(TAG, "Google Sign-In failed with unexpected error", e)
        MainActivity.showError(
          this@HomeAppViewModel,
          "Google Sign-In failed with unexpected error" + e.message,
        )
      }
    }
  }

  private val selectedStructureFlow: Flow<Structure> =
    selectedStructureVM
      .filterNotNull()
      .map { it.structure }
      .shareIn(scope = viewModelScope, started = SharingStarted.Eagerly, replay = 1)

  init {
    Log.i(TAG, "HomeAppViewModel init")
    // Assign active structure ID provider
    homeApp.permissionsManager.currentStructureIdProvider = {
      selectedStructureVM.value?.structure?.id?.id
    }
    val errorsEmitter: MutableSharedFlow<Exception> =
      MutableSharedFlow(replay = 0, extraBufferCapacity = 0)
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    // HubDiscoveryViewModel now consumes the derived selectedStructureFlow
    hubDiscoveryVM =
      HubDiscoveryViewModel(
        structureFlow = selectedStructureFlow,
        viewModelScope = viewModelScope,
        errorsEmitter = errorsEmitter,
        ioDispatcher = ioDispatcher,
      )

    viewModelScope.launch {
      var structuresJob: Job? = null
      // Resubscribe or cancel subscription when permission is updated
      homeApp.permissionsManager.permissionUpdatedEvent
        .map { homeApp.permissionsManager.isSignedIn.value }
        .distinctUntilChanged()
        .collect { isSignedIn ->
          Log.i(TAG, "Sign-in state changed: $isSignedIn")
          structuresJob?.cancel()
          if (isSignedIn) {
            structuresJob = viewModelScope.launch { subscribeToStructures() }
          } else {
            Log.d(TAG, "Cancel the job to subscribe to structure")
          }
        }
    }
  }

  private suspend fun subscribeToStructures() {
    // Subscribe to structures returned by the Structures API:
    homeApp.homeClient.structures().collect { structureSet ->
      val structureVMList: MutableList<StructureViewModel> = mutableListOf()
      // Store structures in container ViewModels:
      for (structure in structureSet) {
        structureVMList.add(StructureViewModel(structure))
      }
      // Store the ViewModels:
      structureVMs.emit(structureVMList)

      // If a structure isn't selected yet, select the first structure from the list:
      if (selectedStructureVM.value == null && structureVMList.isNotEmpty()) {
        currentStructureRepository.setSelectedStructure(structureVMList.first())
        // Load HomeBriefs once the first structure is available
        loadHomeBriefs()
      }
    }
  }

  /**
   * Reports an error message to the UI layer via the main logger. This is used when an error occurs
   * outside of the standard flow emission (e.g., in onActivityResult).
   *
   * @param resultCode The result code of the failed activation.
   */
  fun handleActivationFailure(resultCode: Int) {
    val errorMessage = "Hub activation failed with result code: $resultCode"
    MainActivity.showError(this, errorMessage)
  }

  /** Starts the hub discovery process. */
  fun startHubDiscovery() {
    hubDiscoveryVM?.startDiscovery()
  }

  fun openQrCodeScanner() {
    _showQrCodeScanner.value = true
  }

  fun closeQrCodeScanner() {
    _showQrCodeScanner.value = false
  }

  // OTA Screen Functions
  /**
   * Shows the OTA information screen for a camera device. Called after a camera device is
   * successfully commissioned.
   */
  fun showOtaScreen() {
    viewModelScope.launch {
      try {
        _showOtaScreen.emit(true)
      } catch (e: Exception) {
        Log.e(TAG, "Error emitting OTA screen state", e)
      }
    }
  }

  fun closeOtaScreen() {
    viewModelScope.launch {
      try {
        _showOtaScreen.emit(false)
      } catch (e: Exception) {
        Log.e(TAG, "Error closing OTA screen state", e)
      }
    }
  }

  /**
   * Starts the commissioning flow with the selected fabric type and optional payload. This function
   * manages the view transitions (scanner -> commissioning client).
   */
  fun onCommissionCamera(payload: String? = null) {

    // 1. OPEN SCANNER: If payload is null (initial click), open the scanner UI.
    if (payload == null) {
      openQrCodeScanner()
      return // Stop execution here, wait for the scanner result
    }

    // 2. START API: If payload exists (result from MatterQrCodeScanner), close the UI and call the
    // API.
    closeQrCodeScanner()
    homeApp.commissioningManager.requestCommissioning(FabricType.GOOGLE_CAMERA, payload)
  }

  /** Shows automation candidates for the selected structure. */
  @OptIn(HomeExperimentalApi::class)
  fun showCandidates() {
    viewModelScope.launch {
      val candidateVMList: MutableList<CandidateViewModel> = mutableListOf()

      // Retrieve automation candidates for every device present in the selected structure:
      for (deviceVM in selectedStructureVM.value!!.deviceVMs.value) {

        // Check whether the device has a known type:
        if (deviceVM.type.value is UnknownDeviceType) continue
        // Retrieve a set of initial automation candidates from the device:
        val candidates: Set<NodeCandidate> = deviceVM.device.candidates().first()

        for (candidate in candidates) {
          // Check whether the candidate trait is supported:
          if (candidate.trait !in HomeModule_ProvideSupportedTraitsFactory().get()) continue
          // Check whether the candidate type is supported:
          when (candidate) {
            // Command candidate type:
            is CommandCandidate -> {
              // Check whether the command candidate has a supported command:
              if (candidate.commandDescriptor !in ActionViewModel.commandMap) continue
            }
            // Other candidate types are currently unsupported:
            else -> {
              continue
            }
          }
          candidateVMList.add(CandidateViewModel(candidate, deviceVM))
        }
      }

      // Store the ViewModels:
      selectedCandidateVMs.emit(candidateVMList)
    }
  }

  /**
   * Creates an automation from the currently selected draft.
   *
   * @param isPending A [MutableState] to track if the automation creation is in progress.
   */
  fun createAutomation(isPending: MutableState<Boolean>) {
    viewModelScope.launch {
      val structure: Structure = selectedStructureVM.value?.structure!!
      val draft: DraftAutomation = selectedDraftVM.value?.getDraftAutomation()!!
      isPending.value = true

      // Call Automations API to create an automation from a draft:
      try {
        structure.createAutomation(draft)
      } catch (e: Exception) {
        MainActivity.showError(this, e.toString())
        isPending.value = false
        return@launch
      }

      // Scrap the draft and automation candidates used in the process:
      selectedCandidateVMs.emit(null)
      selectedDraftVM.emit(null)
      isPending.value = false
    }
  }

  /** Create a room on the currently selected structure. */
  fun createRoomInSelectedStructure(name: String): Job = viewModelScope.launch {
    val vm = selectedStructureVM.value ?: return@launch
    vm.createRoom(name)
  }

  /** Delete a room from the currently selected structure. */
  fun deleteRoomFromSelectedStructure(roomVM: RoomViewModel): Job = viewModelScope.launch {
    val structureVM = selectedStructureVM.value ?: return@launch
    structureVM.deleteRoom(roomVM)
  }

  /**
   * Move a device into the given (non-null) room for the selected structure.
   *
   * @param device The [DeviceViewModel] of the device to move.
   * @param room The [RoomViewModel] of the room to move the device to.
   */
  fun moveDeviceToRoom(device: DeviceViewModel, room: RoomViewModel): Job = viewModelScope.launch {
    val vm = selectedStructureVM.value ?: return@launch
    vm.moveDeviceToRoom(device, room)
  }

  /**
   * Creates and shows a predefined draft for an On/Off light automation.
   *
   * This draft requires at least two OnOff-capable lights in the selected structure. If fewer than
   * two are available, an error message is shown instead.
   */
  fun showPredefinedOnOffDraft() {
    viewModelScope.launch {
      val structureVM = selectedStructureVM.value ?: return@launch
      val repository = AutomationsRepository()

      val draftVM = repository.createOnOffLightAutomationDraft(structureVM.deviceVMs.value)

      if (draftVM == null) {
        MainActivity.showError(this, "Need at least two OnOff-capable lights in this structure.")
        return@launch
      }

      selectedDraftVM.emit(draftVM)
    }
  }

  /**
   * Creates and shows a predefined draft for the "Speaker and Fan" automation.
   *
   * This draft requires a speaker, fan, and plug in the selected structure. If any required device
   * is missing, an error message is shown instead.
   */
  fun showPredefinedSpeakerAndFanDraft() {
    viewModelScope.launch {
      val structureVM = selectedStructureVM.value ?: return@launch
      val repository = AutomationsRepository()

      // Pass the structure from selectedStructureVM
      val draftVM =
        repository.createSpeakerAndFanAutomationDraft(
          structureVM.deviceVMs.value,
          structureVM.structure,
        )

      if (draftVM == null) {
        MainActivity.showError(
          this,
          "This automation requires:\n• 1 Speaker\n• 1 Fan\n• 1 Smart Outlet\n\nPlease add these devices and try again.",
        )
        return@launch
      }

      selectedDraftVM.emit(draftVM)
    }
  }

  /**
   * Shows the predefined light and thermostat automation draft This creates a draft that turns on
   * lights and sets thermostat to auto when door is unlocked
   */
  suspend fun showPredefinedLightAndThermostatDraft() {
    val structureVM = selectedStructureVM.value ?: return
    val repository = AutomationsRepository()

    val draftVM = repository.createLightAndThermostatAutomationDraft(structureVM.deviceVMs.value)
    if (draftVM != null) {
      selectedDraftVM.emit(draftVM)
    }
  }

  /**
   * Creates and shows a predefined draft for the Window Covering automation.
   *
   * This draft requires a temperature sensor (or thermostat) and a window covering device. The
   * automation closes the window covering when temperature drops below 15°C and it's dark outside.
   */
  fun showPredefinedWindowCoveringDraft() {
    viewModelScope.launch {
      val structureVM = selectedStructureVM.value ?: return@launch
      val repository = AutomationsRepository()

      val draftVM =
        repository.createWindowCoveringAutomationDraft(
          structureVM.deviceVMs.value,
          structureVM.structure,
        )

      if (draftVM == null) {
        MainActivity.showError(
          this,
          "This automation requires:\n• 1 Temperature Sensor (or Thermostat)\n• 1 Window Covering\n\nPlease add these devices and try again.",
        )
        return@launch
      }

      selectedDraftVM.emit(draftVM)
    }
  }

  fun showPredefinedLightAndTVPeriodicDraft() {
    viewModelScope.launch {
      val structureVM = selectedStructureVM.value ?: return@launch
      val repository = AutomationsRepository()

      val draftVM =
        repository.createLightAndTVPeriodicAutomationDraft(
          structureVM.deviceVMs.value,
          structureVM.structure,
        )

      if (draftVM == null) {
        MainActivity.showError(
          this,
          "This automation requires:\n• At least 1 Light\n• 1 Occupancy Sensor\n• 1 Google TV\n\nPlease add these devices and try again.",
        )
        return@launch
      }

      selectedDraftVM.emit(draftVM)
    }
  }
  /**
   * Creates and shows a predefined draft for the "Camera Scene Detected" Natural
   * Language camera starter automation. Uses a generic, location-independent query
   * ("a person is detected") so it's reliably testable on any camera.
   *
   * This draft requires a camera and an OnOff-capable light in the selected structure.
   * If either required device is missing, an error message is shown instead.
   */
  fun showPredefinedCameraSceneDetectedLightDraft() {
    viewModelScope.launch {
      val structureVM = selectedStructureVM.value ?: return@launch
      val repository = AutomationsRepository()

      val draftVM = repository.createCameraSceneDetectedLightAutomationDraft(
        structureVM.deviceVMs.value
      )

      if (draftVM == null) {
        MainActivity.showError(
          this@HomeAppViewModel,
          "This automation requires:\n• 1 Camera\n• 1 OnOff-capable Light\n\nPlease add these devices and try again."
        )
        return@launch
      }

      selectedDraftVM.emit(draftVM)
    }
  }

  fun openHistoryForDevice(deviceVM: DeviceViewModel) {
    _selectedHistoryDeviceVM.value = deviceVM
  }

  fun clearHistorySelection() {
    _selectedHistoryDeviceVM.value = null
  }

  /** Opens the video player screen for the given camera history event. */
  fun openVideoPlayer(event: HistoryUiDataModel.CameraEvent) {
    _selectedVideoEvent.value = event
  }

  /** Closes the video player screen and returns to the history list. */
  fun closeVideoPlayer() {
    _selectedVideoEvent.value = null
  }

  /**
   * Opens the video player for a HomeBrief camera event clip. Maps [HomeBriefCameraEvent] to
   * [HistoryUiDataModel.CameraEvent] and reuses the existing [_selectedVideoEvent] state so the
   * View only manages one player.
   */
  fun openHomeBriefVideo(event: HomeBriefCameraEvent) {
    _selectedVideoEvent.value =
      HistoryUiDataModel.CameraEvent(
        eventId = event.sessionId,
        timestamp = event.startTime ?: java.time.Instant.EPOCH,
        entityName = "Camera Clip",
        eventType = com.example.googlehomeapisampleapp.history.HistoryUiEventType.Unknown,
        mediaUrl =
          com.example.googlehomeapisampleapp.history.MediaUrl(
            previewUrl = event.previewUrl,
            mp4DownloadUrl = event.previewUrl,
          ),
        deviceId = event.entityObjectId.id,
      )
  }

  /**
   * Fetches the latest page of Home Briefs for the currently selected structure and exposes them
   * via [homeBriefs]. Briefs are displayed pinned at the top of the activity feed, above camera
   * history items.
   *
   * Non-fatal on error: the history feed still shows camera events if briefs are unavailable (e.g.
   * feature not enabled for this account, trait offline). Guarded by [_homeBriefsLoading] to
   * prevent concurrent fetches.
   */
  @OptIn(HomeExperimentalApi::class)
  fun loadHomeBriefs() {
    val structure =
      selectedStructureVM.value?.structure
        ?: run {
          Log.w(TAG, "loadHomeBriefs: no structure selected, skipping")
          return
        }
    if (_homeBriefsLoading.value) {
      Log.d(TAG, "loadHomeBriefs: already loading, skipping")
      return
    }

    viewModelScope.launch {
      _homeBriefsLoading.value = true
      try {
        Log.d(TAG, "loadHomeBriefs: calling getHomeBriefsManager()")
        val manager = structure.getHomeBriefsManager()
        Log.d(TAG, "loadHomeBriefs: manager obtained, calling getHomeBriefs()")
        when (val page = manager.getHomeBriefs()) {
          is HomeBriefsPage.Success -> {
            _homeBriefs.value = page.result.map { it.toUiDataModel() }
            Log.d(TAG, "loadHomeBriefs: loaded ${_homeBriefs.value.size} briefs")
          }
          is HomeBriefsPage.Error -> {
            // Non-fatal: HomeBriefs is an enhancement on top of camera history.
            // Expected when the feature is not enabled for this account.
            Log.w(TAG, "loadHomeBriefs: API returned error — ${page.exception.message}")
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "loadHomeBriefs: unexpected exception", e)
      } finally {
        _homeBriefsLoading.value = false
      }
    }
  }

  private fun shouldAddDateSeparator(
    before: HistoryUiDataModel?,
    after: HistoryUiDataModel?,
  ): java.time.LocalDate? {
    if (after == null) return null
    val zone = java.time.ZoneId.systemDefault()
    val afterDate = after.timestamp.atZone(zone).toLocalDate()
    if (before == null) return afterDate
    val beforeDate = before.timestamp.atZone(zone).toLocalDate()
    return if (beforeDate != afterDate) afterDate else null
  }
}
