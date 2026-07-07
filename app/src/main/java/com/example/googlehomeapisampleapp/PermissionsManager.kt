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

package com.example.googlehomeapisampleapp

import android.util.Log
import androidx.activity.ComponentActivity
import com.google.home.ConsentScreenOptions
import com.google.home.ForcePermissionFlow
import com.google.home.HomeClient
import com.google.home.HomeException
import com.google.home.PermissionsResult
import com.google.home.PermissionsResultStatus
import com.google.home.PermissionsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PermissionsManager(
  val scope: CoroutineScope,
  val activity: ComponentActivity,
  val client: HomeClient,
) {

  companion object {
    const val TAG = "PermissionsManager"
  }

  // Provider for active structure ID
  var currentStructureIdProvider: (() -> String?)? = null

  var isSignedIn: MutableStateFlow<Boolean> = MutableStateFlow(false)
  private val _isPermissionUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  val permissionUpdatedEvent = _isPermissionUpdated.asSharedFlow()
  var isInitialized: MutableStateFlow<Boolean> = MutableStateFlow(false)

  init {
    // Register permission caller callback on HomeClient:
    client.registerActivityResultCallerForPermissions(activity)
    // Check the current permission state:
    checkPermissions()
  }

  private fun checkPermissions() {
    scope.launch {
      // Block here to subscribe permission state changes
      client.hasPermissions().collectLatest { state ->
        if (state == PermissionsState.PERMISSIONS_STATE_UNINITIALIZED) {
          return@collectLatest
        }
        // Report the permission state:
        reportPermissionState(state)
        // Adjust the sign-in status according to permission state:
        val isPermissionStateGranted = state == PermissionsState.GRANTED
        // Emit the Sign-In state
        isSignedIn.emit(isPermissionStateGranted)
        // Emit every Permission Updated event
        _isPermissionUpdated.emit(Unit)
        Log.d(TAG, "Emit new isSignedIn=${isSignedIn.value}, state=$state")
        // Set to true when initialization
        if (!isInitialized.value)
          isInitialized.emit(true)
      }
    }
  }

  /**
   * Public wrapper for checkPermissions().
   *
   *
   * This function should be used by external components (e.g., UI layers) to refresh
   * the permission state. Directly calling checkPermissions() is discouraged to maintain
   * encapsulation and allow future flexibility.
   */
  fun refreshPermissions() {
    checkPermissions()
  }

  fun requestPermissions(
    isForceRefresh: Boolean = false,
    consentScreenOptions: ConsentScreenOptions? = null,
  ) {
    scope.launch {
      try {
        // Use passed options or fallback to active structure ID
        val optionsToUse = consentScreenOptions
          ?: run {
            val currentStructureId = currentStructureIdProvider?.invoke()
            ConsentScreenOptions(
              structureId = currentStructureId,
              allowedStructureIds = emptyList(),
              isAllowStructureChange = !currentStructureId.isNullOrEmpty()
            )
          }
        // Request permissions using ForcePermissionFlow:
        val result: PermissionsResult = client.requestPermissions(
          ForcePermissionFlow.FORCE_LAUNCH,
          consentScreenOptions = optionsToUse
        )
        // Adjust the sign-in status according to permission result:
        if (result.status == PermissionsResultStatus.SUCCESS) {
          Log.d(TAG, "PermissionsResultStatus.SUCCESS")
        }
        if (isForceRefresh) {
          // When user request permission to change structure, the permission
          // state won't change. So it is required to force emit a permission update event.
          Log.i(TAG, "forceRefresh after requestPermissions")
          _isPermissionUpdated.emit(Unit)
        }
        // Report the permission result:
        reportPermissionResult(result)
      } catch (e: HomeException) {
        MainActivity.showError(this, e.message.toString())
      }
    }
  }

  private fun reportPermissionState(permissionState: PermissionsState) {
    val message: String = "Permissions State: " + permissionState.name
    // Report the permission state:
    when (permissionState) {
      PermissionsState.GRANTED ->
        MainActivity.showDebug(this, message)

      PermissionsState.NOT_GRANTED ->
        MainActivity.showWarning(this, message)

      PermissionsState.PERMISSIONS_STATE_UNAVAILABLE ->
        MainActivity.showWarning(this, message)

      PermissionsState.PERMISSIONS_STATE_UNINITIALIZED ->
        MainActivity.showError(this, message)
    }
  }

  private fun reportPermissionResult(permissionResult: PermissionsResult) {
    var message: String = "Permissions Result: " + permissionResult.status.name
    // Include any error messages in the permission result:
    if (permissionResult.errorMessage != null)
      message += " | " + permissionResult.errorMessage
    // Report the permission result:
    when (permissionResult.status) {
      PermissionsResultStatus.SUCCESS ->
        MainActivity.showDebug(this, message)

      PermissionsResultStatus.CANCELLED ->
        MainActivity.showWarning(this, message)

      PermissionsResultStatus.ERROR ->
        MainActivity.showError(this, message)
    }
  }

}
