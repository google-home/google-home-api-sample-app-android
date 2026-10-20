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
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.google.home.Room
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class RoomViewModel(
  val room: Room,
  val isMultifacetEnabled: StateFlow<Boolean> = MutableStateFlow(false),
  private val structureDeviceVMs: StateFlow<List<DeviceViewModel>>? = null,
) : ViewModel() {

  var id: String = room.id.id
  private val _name = MutableStateFlow("")
  val name: StateFlow<String> = _name.asStateFlow()

  val deviceVMs: MutableStateFlow<List<DeviceViewModel>>

  init {
    _name.value = room.name

    // Initialize dynamic values for a structure:
    deviceVMs = MutableStateFlow(mutableListOf())

    // Subscribe to changes on dynamic values:
    viewModelScope.launch { subscribeToDevices() }
  }

  fun updateName(newName: String) {
    _name.value = newName
  }

  fun clear() {
    if (structureDeviceVMs == null) {
      deviceVMs.value.forEach { it.clear() }
    }
    viewModelScope.cancel()
  }

  override fun onCleared() {
    super.onCleared()
    clear()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun subscribeToDevices() {
    if (structureDeviceVMs != null) {
      structureDeviceVMs.collect { allDevices ->
        this.deviceVMs.emit(allDevices.filter { it.device.roomId == room.id })
      }
      return
    }

    // Fallback if RoomViewModel is constructed standalone without StructureViewModel:
    isMultifacetEnabled.flatMapLatest { enabled ->
      this.deviceVMs.value.forEach { it.clear() }
      this.deviceVMs.value = emptyList()
      room.devices(enableMultipartDevices = enabled)
    }.collect { deviceSet ->
      val previousById = this.deviceVMs.value.associateBy { it.id }
      val newDeviceVMs = mutableListOf<DeviceViewModel>()
      val keptIds = mutableSetOf<String>()
      for (device in deviceSet) {
        val existingVM = previousById[device.id.id]
        val deviceVM = if (existingVM != null && existingVM.device.roomId == device.roomId) {
          keptIds.add(device.id.id)
          existingVM
        } else {
          DeviceViewModel(device = device)
        }
        newDeviceVMs.add(deviceVM)
      }
      previousById.forEach { (id, vm) -> if (id !in keptIds) vm.clear() }
      this.deviceVMs.emit(newDeviceVMs)
    }
  }

  /**
   * Rename the room to the specified name.
   * newName The new name for the room
   * IllegalArgumentException if the new name is empty or unchanged
   * Exception if the rename operation fails
   */
  suspend fun renameRoom(newName: String) {
    val newRoomName = newName.trim()
    if (newRoomName.isEmpty()) {
      Log.w(TAG, "Attempted to rename room to empty name")
      throw IllegalArgumentException("The room name cannot be empty.")
    }
    if (newRoomName == name.value) {
      Log.d(TAG, "Room name unchanged, skipping rename operation")
      return
    }

    try {
      Log.d(TAG, "Renaming room from '${name.value}' to '$newRoomName'")
      room.setName(newRoomName)
      _name.value = newRoomName
      Log.d(TAG, "Successfully renamed room to '$newRoomName'")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to rename room from '${name.value}' to '$newRoomName': ${e.message}", e)
      throw e
    }
  }

  companion object {
    private const val TAG = "RoomViewModel"
  }
}