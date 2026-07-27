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

package com.ioconnect2026.googlehomeapisampleapp.viewmodel

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
import com.ioconnect2026.googlehomeapisampleapp.BuildConfig
import com.ioconnect2026.googlehomeapisampleapp.HomeClientProvider
import com.ioconnect2026.googlehomeapisampleapp.MainActivity
import com.ioconnect2026.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.ioconnect2026.googlehomeapisampleapp.viewmodel.structures.RoomViewModel
import com.ioconnect2026.googlehomeapisampleapp.viewmodel.structures.StructureViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.home.HomeClient
import com.google.home.PermissionsState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
      val structureVMList: MutableList<StructureViewModel> = mutableListOf()
      // Store structures in container ViewModels:
      for (structure in structureSet) {
        structureVMList.add(StructureViewModel(structure, viewModelScope))
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
