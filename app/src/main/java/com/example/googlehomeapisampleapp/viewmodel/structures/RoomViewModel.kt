
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
import com.google.home.Structure
import com.google.home.deleteRoom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RoomViewModel (val structure: Structure, val room: Room) : ViewModel() {

    var id : String
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    val deviceVMs : MutableStateFlow<List<DeviceViewModel>>

    init {
        // Initialize permanent values for a room:
        id = room.id.id
        _name.value = room.name

        // Initialize dynamic values for a structure:
        deviceVMs = MutableStateFlow(mutableListOf())

        // Subscribe to changes on dynamic values:
        viewModelScope.launch { subscribeToDevices() }
    }

    private suspend fun subscribeToDevices() {
        // Subscribe to changes on devices:
        room.devices().collect { deviceSet ->
            val deviceVMs = mutableListOf<DeviceViewModel>()
            // Store devices in container ViewModels:
            for (device in deviceSet) {
                deviceVMs.add(DeviceViewModel(device))
            }
            // Store the ViewModels:
            this.deviceVMs.emit(deviceVMs)
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
            // The room name should be updated automatically by the Room API
            // If not, we might need to manually update: _name.value = newRoomName
            Log.d(TAG, "Successfully renamed room to '$newRoomName'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename room from '${name.value}' to '$newRoomName': ${e.message}", e)
            throw e
        }
    }

    /**
     * Delete this room via the Structure API.
     * Note: If the room contains devices, they will be unassigned from the room
     * but will remain in the structure as devices without a room.
     * Exception if the delete operation fails
     */
    suspend fun delete() {
        try {
            Log.d(TAG, "Deleting room '${name.value}'")
            structure.deleteRoom(room)
            Log.d(TAG, "Successfully deleted room '${name.value}'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete room '${name.value}': ${e.message}", e)
            throw e
        }
    }
    companion object {
        private const val TAG = "RoomViewModel"
    }
}