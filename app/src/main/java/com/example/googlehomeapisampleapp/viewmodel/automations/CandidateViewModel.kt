
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

package com.example.googlehomeapisampleapp.viewmodel.automations

import androidx.lifecycle.ViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.google.home.automation.CommandCandidate
import com.google.home.automation.NodeCandidate

class CandidateViewModel (val candidate: NodeCandidate, val deviceVM: DeviceViewModel? = null) : ViewModel() {

    enum class CandidateType {
        CommandCandidate,
        Unknown
    }

    var id : String
    var name : String
    var description : String
    var type: CandidateType

    init {
        id = candidate.entity.id.id

        if (candidate is CommandCandidate) {
            name = deviceVM!!.name.value + " - " + ActionViewModel.commandMap.get(candidate.commandDescriptor).toString()
            description = "CommandCandidate"
            type = CandidateType.CommandCandidate
        } else {
            name = "Unsupported Candidate"
            description = "-"
            type = CandidateType.Unknown
        }
    }
}
