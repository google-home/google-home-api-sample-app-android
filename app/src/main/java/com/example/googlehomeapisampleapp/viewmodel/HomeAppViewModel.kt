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

package com.example.googlehomeapisampleapp.viewmodel

import android.accounts.Account
import android.content.Context
import android.util.Log
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
import androidx.paging.filter
import androidx.paging.insertSeparators
import androidx.paging.map
import com.example.googlehomeapisampleapp.BuildConfig
import com.example.googlehomeapisampleapp.HomeClientProvider
import com.example.googlehomeapisampleapp.MainActivity
import com.example.googlehomeapisampleapp.history.HistoryDeviceTypeFilter
import com.example.googlehomeapisampleapp.history.HistoryEventUi
import com.example.googlehomeapisampleapp.history.HistoryUiDataModel
import com.example.googlehomeapisampleapp.history.HomeHistoryPagingSource
import com.example.googlehomeapisampleapp.history.toApiFilter
import com.example.googlehomeapisampleapp.history.toUiDataModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.RoomViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.StructureViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.home.HistoryFilter
import com.google.home.HomeClient
import com.google.home.PermissionsState
import com.google.home.annotation.HomeExperimentalApi
import com.google.home.getHistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class HistoryQueryState(
  val structureId: String,
  val deviceId: String?,
  val deviceTypeFilter: HistoryDeviceTypeFilter,
)

@HiltViewModel
class HomeAppViewModel
@Inject
constructor(
  private val homeClient: HomeClient,
  private val homeClientProvider: HomeClientProvider,
) : ViewModel() {

  companion object {
    const val TAG = "HomeAppViewModel"
  }

  val isSignedIn: StateFlow<Boolean> =
    homeClient
      .hasPermissions()
      .map { it == PermissionsState.GRANTED }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
      )

  // Containers tracking the active object being edited:
  private val _selectedStructureVM = MutableStateFlow<StructureViewModel?>(null)
  val selectedStructureVM: StateFlow<StructureViewModel?> = _selectedStructureVM.asStateFlow()

  private val _selectedDeviceVM = MutableStateFlow<DeviceViewModel?>(null)
  val selectedDeviceVM: StateFlow<DeviceViewModel?> = _selectedDeviceVM.asStateFlow()

  // Container to store returned structures from the app:
  private val _structureVMs = MutableStateFlow<List<StructureViewModel>>(mutableListOf())
  val structureVMs: StateFlow<List<StructureViewModel>> = _structureVMs.asStateFlow()

  private val _selectedHistoryDeviceVM = MutableStateFlow<DeviceViewModel?>(null)
  val selectedHistoryDeviceVM: StateFlow<DeviceViewModel?> = _selectedHistoryDeviceVM.asStateFlow()

  private val _selectedVideoEvent = MutableStateFlow<HistoryUiDataModel.CameraEvent?>(null)
  val selectedVideoEvent: StateFlow<HistoryUiDataModel.CameraEvent?> = _selectedVideoEvent.asStateFlow()

  private val _selectedDeviceTypeFilter = MutableStateFlow(HistoryDeviceTypeFilter.All)
  val selectedDeviceTypeFilter: StateFlow<HistoryDeviceTypeFilter> = _selectedDeviceTypeFilter.asStateFlow()

  fun selectDeviceTypeFilter(filter: HistoryDeviceTypeFilter) {
    _selectedDeviceTypeFilter.value = filter
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

  private val _navigateToProxyActivity = Channel<Unit>(Channel.CONFLATED)
  val navigateToProxyActivity = _navigateToProxyActivity.receiveAsFlow()

  @OptIn(ExperimentalCoroutinesApi::class, HomeExperimentalApi::class)
  val historyFlow: Flow<PagingData<HistoryEventUi>> =
    combine(
        selectedStructureVM.filterNotNull().map { it.id }.distinctUntilChanged(),
        selectedHistoryDeviceVM.map { it?.device?.id?.id }.distinctUntilChanged(),
        selectedDeviceTypeFilter,
      ) { structureId, deviceId, deviceTypeFilter ->
        HistoryQueryState(structureId, deviceId, deviceTypeFilter)
      }
      .flatMapLatest { queryState ->
        val structureId = queryState.structureId
        val deviceId = queryState.deviceId
        val deviceTypeFilter = queryState.deviceTypeFilter

        // 1. Get the Structure object from the list of available structures
        val structure =
          structureVMs.value.firstOrNull { it.id == structureId }?.structure
            ?: return@flatMapLatest kotlinx.coroutines.flow.flowOf(PagingData.empty())

        // When deviceId is present (device-scoped history), ignore structure category chip filter
        val activeFilter = if (deviceId != null) HistoryDeviceTypeFilter.All else deviceTypeFilter

        Pager(PagingConfig(pageSize = 50)) {
            // 2. Obtain historyManager from the specific structure
            val historyManager = structure.getHistoryManager()
            val builder = HomeHistoryPagingSource.Builder(historyManager)

            // 3. Apply API-side filters (by device ID or category filter)
            deviceId?.let { builder.addHistoryFilters(HistoryFilter.id(it)) }
            activeFilter.toApiFilter()?.let { builder.addHistoryFilters(it) }
            builder.build()
          }
          .flow
          .map { pagingData ->
            pagingData
              .map { historyItem ->
                // 4. Map raw SDK HistoryItem to your app's UI model
                historyItem.toUiDataModel()
              }
              .filter { uiModel ->
                when (activeFilter) {
                  HistoryDeviceTypeFilter.All -> true
                  HistoryDeviceTypeFilter.Camera -> uiModel is HistoryUiDataModel.CameraEvent
                  HistoryDeviceTypeFilter.DoorLock -> uiModel is HistoryUiDataModel.DoorLockEvent
                  HistoryDeviceTypeFilter.Thermostat ->
                    uiModel is HistoryUiDataModel.ThermostatStateChange ||
                    uiModel is HistoryUiDataModel.ExtendedThermostatStateChange
                }
              }
              .insertSeparators { before, after ->
                // 5. Add date separators (Today, Yesterday, etc.)
                shouldAddDateSeparator(before, after)?.let { HistoryEventUi.DateSeparatorModel(it) }
              }
          }
      }
      .cachedIn(viewModelScope)

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
              homeClientProvider.switchAccount(account.name, serverClientId)
              Log.d(TAG, "Switched to account to : $account")
            }
            Log.i(TAG, "Account switch complete. Emitting navigation event.")
            // Send an event to the channel to signal the UI to navigate.
            _navigateToProxyActivity.send(Unit)
          } catch (e: Exception) {
            Log.e(TAG, "Could not convert CustomCredential to Google ID Token", e)
            MainActivity.showError(
              this@HomeAppViewModel,
              e,
              "Could not convert CustomCredential to Google ID Token",
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
          e,
          "No accounts found. Please add a Google Account to your device settings.",
        )
      } catch (e: GetCredentialException) {
        Log.e(TAG, "Credential retrieval failed", e)
        // You might not want to show an error if the user simply cancelled the dialog
        if (!e.message.orEmpty().contains("User cancelled")) {
          MainActivity.showError(this@HomeAppViewModel, e, "Sign in failed")
        }
      } catch (e: Exception) {
        Log.e(TAG, "Google Sign-In failed with unexpected error", e)
        MainActivity.showError(
          this@HomeAppViewModel,
          e,
          "Google Sign-In failed with unexpected error",
        )
      }
    }
  }

  init {
    Log.i(TAG, "HomeAppViewModel init")
    viewModelScope.launch {
      var structuresJob: Job? = null
      isSignedIn.collect { signedIn ->
        Log.i(TAG, "Sign-in state changed: $signedIn")
        structuresJob?.cancel()
        if (signedIn) {
          structuresJob = viewModelScope.launch { subscribeToStructures() }
        } else {
          Log.d(TAG, "Cancel the job to subscribe to structure")
        }
      }
    }
  }

  private suspend fun subscribeToStructures() {
    // Subscribe to structures returned by the Structures API:
    homeClient.structures().collect { structureSet ->
      val existingById = _structureVMs.value.associateBy { it.id }
      val structureVMList: MutableList<StructureViewModel> = mutableListOf()
      // Store structures in container ViewModels:
      for (structure in structureSet) {
        structureVMList.add(existingById[structure.id.id] ?: StructureViewModel(structure, viewModelScope))
      }
      // Store the ViewModels:
      _structureVMs.emit(structureVMList)

      // If a structure isn't selected yet, select the first structure from the list:
      if (_selectedStructureVM.value == null && structureVMList.isNotEmpty()) {
        _selectedStructureVM.emit(structureVMList.first())
      }
    }
  }

  /** Shows automation candidates for the selected structure. */
  fun selectStructure(structureVM: StructureViewModel) {
    viewModelScope.launch { _selectedStructureVM.emit(structureVM) }
  }

  fun selectDevice(deviceVM: DeviceViewModel?) {
    viewModelScope.launch { _selectedDeviceVM.emit(deviceVM) }
  }

  /** Create a room on the currently selected structure. */
  fun createRoomInSelectedStructure(name: String): Job = viewModelScope.launch {
    val vm = _selectedStructureVM.value ?: return@launch
    vm.createRoom(name)
  }

  /** Delete a room from the currently selected structure. */
  fun deleteRoomFromSelectedStructure(roomVM: RoomViewModel): Job = viewModelScope.launch {
    val structureVM = _selectedStructureVM.value ?: return@launch
    structureVM.deleteRoom(roomVM)
  }

  /**
   * Move a device into the given (non-null) room for the selected structure.
   *
   * @param device The [DeviceViewModel] of the device to move.
   * @param room The [RoomViewModel] of the room to move the device to.
   */
  fun moveDeviceToRoom(device: DeviceViewModel, room: RoomViewModel): Job = viewModelScope.launch {
    val vm = _selectedStructureVM.value ?: return@launch
    vm.moveDeviceToRoom(device, room)
  }
}
