
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

import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.googlehomeapisampleapp.HomeApp
import com.example.googlehomeapisampleapp.MainActivity
import com.example.googlehomeapisampleapp.viewmodel.automations.ActionViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.AutomationViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.CandidateViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.DraftViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.StructureViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.RoomViewModel
import com.google.home.Structure
import com.google.home.automation.CommandCandidate
import com.google.home.automation.DraftAutomation
import com.google.home.automation.NodeCandidate
import com.google.home.automation.UnknownDeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class HomeAppViewModel (val homeApp: HomeApp) : ViewModel() {

    // Tabs showing main capabilities of the app:
    enum class NavigationTab {
        DEVICES,
        AUTOMATIONS
    }

    // Container tracking the active navigation tab:
    var selectedTab : MutableStateFlow<NavigationTab>

    // Containers tracking the active object being edited:
    var selectedStructureVM: MutableStateFlow<StructureViewModel?>
    var selectedDeviceVM: MutableStateFlow<DeviceViewModel?>
    var selectedAutomationVM: MutableStateFlow<AutomationViewModel?>
    var selectedDraftVM: MutableStateFlow<DraftViewModel?>
    var selectedCandidateVMs: MutableStateFlow<List<CandidateViewModel>?>

    // Container to store returned structures from the app:
    var structureVMs: MutableStateFlow<List<StructureViewModel>>

    init {
        // Initialize the active tab to show the devices:
        selectedTab = MutableStateFlow(NavigationTab.DEVICES)

        // Initialize containers storing active objects:
        selectedStructureVM = MutableStateFlow(null)
        selectedDeviceVM = MutableStateFlow(null)
        selectedAutomationVM = MutableStateFlow(null)
        selectedDraftVM = MutableStateFlow(null)
        selectedCandidateVMs = MutableStateFlow(null)

        // Initialize the container to store structures:
        structureVMs = MutableStateFlow(mutableListOf())

        viewModelScope.launch {
            // If permissions flow is completed, subscribe to changes on structures:
            if (homeApp.permissionsManager.isSignedIn.value) {
                launch { subscribeToStructures() }
            }
            // If permissions flow is completed in the future, subscribe to changes on structures:
            homeApp.permissionsManager.isSignedIn.collect { isSignedIn ->
                if (isSignedIn) {
                    launch { subscribeToStructures() }
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
            if (selectedStructureVM.value == null && structureVMList.isNotEmpty())
                selectedStructureVM.emit(structureVMList.first())

        }
    }

    fun showCandidates() {
        viewModelScope.launch {
            val candidateVMList: MutableList<CandidateViewModel> = mutableListOf()

            // Retrieve automation candidates for every device present in the selected structure:
            for (deviceVM in selectedStructureVM.value!!.deviceVMs.value) {

                // Check whether the device has a known type:
                if(deviceVM.type.value is UnknownDeviceType)
                    continue

                // Retrieve a set of initial automation candidates from the device:
                val candidates: Set<NodeCandidate> = deviceVM.device.candidates().first()

                for (candidate in candidates) {
                    // Check whether the candidate trait is supported:
                    if(candidate.trait !in HomeApp.supportedTraits)
                        continue
                    // Check whether the candidate type is supported:
                    when (candidate) {
                        // Command candidate type:
                        is CommandCandidate -> {
                            // Check whether the command candidate has a supported command:
                            if (candidate.commandDescriptor !in ActionViewModel.commandMap)
                                continue
                        }
                        // Other candidate types are currently unsupported:
                        else -> { continue }
                    }

                    candidateVMList.add(CandidateViewModel(candidate, deviceVM))
                }
            }

            // Store the ViewModels:
            selectedCandidateVMs.emit(candidateVMList)
        }
    }

    fun createAutomation(isPending: MutableState<Boolean>) {
        viewModelScope.launch {
            val structure : Structure = selectedStructureVM.value?.structure!!
            val draft : DraftAutomation = selectedDraftVM.value?.getDraftAutomation()!!
            isPending.value = true

            // Call Automations API to create an automation from a draft:
            try {
                structure.createAutomation(draft)
            }
            catch (e: Exception) {
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

    /**
     * Move a device into the given (non-null) room for the selected structure.
     */
    fun moveDeviceToRoom(device: DeviceViewModel, room: RoomViewModel): Job = viewModelScope.launch {
        val vm = selectedStructureVM.value ?: return@launch
        vm.moveDeviceToRoom(device, room)
    }
}
