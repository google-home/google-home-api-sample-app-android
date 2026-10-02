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

package com.example.googlehomeapisampleapp.viewmodel.structures

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.googlehomeapisampleapp.viewmodel.automations.AutomationViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.google.home.Structure
import com.google.home.google.AreaPresenceState
import com.google.home.google.AreaPresenceStateTrait
import com.google.home.createRoom
import com.google.home.deleteRoom
import com.google.home.moveDevicesToRoom
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class StructureViewModel(
  val structure: Structure,
  val isMultifacetEnabled: StateFlow<Boolean> = MutableStateFlow(false),
) : ViewModel() {

  var id: String = structure.id.id
  var name: String = structure.name

  val roomVMs: MutableStateFlow<List<RoomViewModel>> = MutableStateFlow(mutableListOf())
  val deviceVMs: MutableStateFlow<List<DeviceViewModel>> = MutableStateFlow(mutableListOf())
  val deviceVMsWithoutRooms: MutableStateFlow<List<DeviceViewModel>> =
    MutableStateFlow(mutableListOf())
  val automationVMs: MutableStateFlow<List<AutomationViewModel>> = MutableStateFlow(mutableListOf())
  val presenceState: MutableStateFlow<String> = MutableStateFlow("--")

  init {

    // Subscribe to changes on dynamic values:
    viewModelScope.launch { subscribeToRooms() }
    viewModelScope.launch { subscribeToDevices() }
    viewModelScope.launch { subscribeToAutomations() }
    viewModelScope.launch { subscribeToPresence() }
  }

  fun clear() {
    roomVMs.value.forEach { it.clear() }
    deviceVMs.value.forEach { it.clear() }
    viewModelScope.cancel()
  }

  override fun onCleared() {
    super.onCleared()
    clear()
  }

  private suspend fun subscribeToRooms() {
    // Subscribe to changes on rooms:
    structure.rooms().collect { roomSet ->
      val previousById = this.roomVMs.value.associateBy { it.id }
      val newRoomVMs = mutableListOf<RoomViewModel>()
      val keptIds = mutableSetOf<String>()
      // Store rooms in container ViewModels (reusing existing instances by room ID):
      for (room in roomSet) {
        keptIds.add(room.id.id)
        val roomVM = previousById[room.id.id]?.also { it.updateName(room.name) } ?: RoomViewModel(
          room = room,
          isMultifacetEnabled = isMultifacetEnabled,
          structureDeviceVMs = this.deviceVMs,
        )
        newRoomVMs.add(roomVM)
      }
      previousById.forEach { (id, vm) -> if (id !in keptIds) vm.clear() }
      // Store the ViewModels:
      this.roomVMs.emit(newRoomVMs)
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun subscribeToDevices() {
    // Subscribe to changes on devices based on Multifacet mode toggle:
    isMultifacetEnabled.flatMapLatest { enabled ->
      this.deviceVMs.value.forEach { it.clear() }
      this.deviceVMs.value = emptyList()
      structure.devices(enableMultipartDevices = enabled)
    }.collect { deviceSet ->
      val previousById = this.deviceVMs.value.associateBy { it.id }
      val newDeviceVMs = mutableListOf<DeviceViewModel>()
      val newDeviceWithoutRoomVMs = mutableListOf<DeviceViewModel>()
      val keptIds = mutableSetOf<String>()
      // Store devices in container ViewModels (reusing existing instances by device ID):
      for (device in deviceSet) {
        val existingVM = previousById[device.id.id]
        val deviceVM = if (existingVM != null && existingVM.device.roomId == device.roomId) {
          keptIds.add(device.id.id)
          existingVM
        } else {
          DeviceViewModel(device = device)
        }
        newDeviceVMs.add(deviceVM)
        // For any device that's not in a room, additionally keep track of a separate list:
        if (device.roomId == null)
          newDeviceWithoutRoomVMs.add(deviceVM)
      }
      previousById.forEach { (id, vm) -> if (id !in keptIds) vm.clear() }
      // Store the ViewModels:
      this.deviceVMs.emit(newDeviceVMs)
      deviceVMsWithoutRooms.emit(newDeviceWithoutRoomVMs)
    }
  }

  private suspend fun subscribeToAutomations() {
    // Subscribe to changes on automations:
    structure.automations().collect { automationSet ->
      val automationVMs = mutableListOf<AutomationViewModel>()
      // Store automations in container ViewModels:
      for (automation in automationSet) {
        automationVMs.add(AutomationViewModel(automation))
      }
      // Store the ViewModels:
      this.automationVMs.emit(automationVMs)
    }
  }

  private suspend fun subscribeToPresence() {
    if (structure.has(AreaPresenceState)) {
      try {
        structure.trait(AreaPresenceState).collect { trait ->
          presenceState.emit(getPresenceStatusString(trait.presenceState))
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error subscribing to presence: ${e.message}", e)
        presenceState.emit("Error")
      }
    } else {
      presenceState.emit("Unsupported")
    }
  }

  private fun getPresenceStatusString(state: AreaPresenceStateTrait.PresenceState?): String {
    return when (state) {
      AreaPresenceStateTrait.PresenceState.PresenceStateOccupied -> "Home"
      AreaPresenceStateTrait.PresenceState.PresenceStateVacant -> "Away"
      AreaPresenceStateTrait.PresenceState.PresenceStateUnspecified -> "Unspecified"
      else -> "Unknown"
    }
  }

  /**
   * Create a new room with the given name.
   * name The name for the new room
   * IllegalArgumentException if the room name is empty after trimming
   * Exception if the room creation fails
   */
  suspend fun createRoom(name: String) {
    val roomName = name.trim()
    if (roomName.isEmpty()) {
      Log.w(TAG, "Attempted to create room with empty name")
      throw IllegalArgumentException("The room name cannot be empty.")
    }
    try {
      Log.d(TAG, "Creating room with name: $roomName")
      structure.createRoom(roomName)
      Log.d(TAG, "Successfully created room: $roomName")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to create room '$roomName': ${e.message}", e)
      throw e
    }
  }

  /**
   * Delete a room from this structure.
   * Note: If the room contains devices, they will be unassigned from the room
   * but will remain in the structure as devices without a room.
   * roomVM The room to delete
   * Exception if the delete operation fails
   */
  suspend fun deleteRoom(roomVM: RoomViewModel) {
    try {
      Log.d(TAG, "Deleting room '${roomVM.name.value}' from structure '$name'")
      structure.deleteRoom(roomVM.room)
      Log.d(TAG, "Successfully deleted room '${roomVM.name.value}' from structure '$name'")
    } catch (e: Exception) {
      Log.e(
        TAG,
        "Failed to delete room '${roomVM.name.value}' from structure '$name': ${e.message}",
        e
      )
      throw e
    }
  }

  /**
   * Move a device to the specified room.
   * deviceVM The device to move
   * roomVM The target room
   * Exception if the move operation fails
   */
  suspend fun moveDeviceToRoom(deviceVM: DeviceViewModel, roomVM: RoomViewModel) {
    try {
      Log.d(TAG, "Moving device '${deviceVM.device.name}' to room '${roomVM.name.value}'")
      structure.moveDevicesToRoom(roomVM.room, listOf(deviceVM.device))
      Log.d(
        TAG,
        "Successfully moved device '${deviceVM.device.name}' to room '${roomVM.name.value}'"
      )
    } catch (e: Exception) {
      Log.e(
        TAG,
        "Failed to move device '${deviceVM.device.name}' to room '${roomVM.name.value}': ${e.message}",
        e
      )
      throw e
    }
  }

  companion object {
    private const val TAG = "StructureViewModel"
  }
}

