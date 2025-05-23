
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

package com.example.googlehomeapisampleapp.viewmodel.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.googlehomeapisampleapp.HomeApp
import com.google.home.ConnectivityState
import com.google.home.DeviceType
import com.google.home.DeviceTypeFactory
import com.google.home.HomeDevice
import com.google.home.Trait
import com.google.home.TraitFactory
import com.google.home.automation.UnknownDeviceType
import com.google.home.matter.standard.BooleanState
import com.google.home.matter.standard.ColorTemperatureLightDevice
import com.google.home.matter.standard.ContactSensorDevice
import com.google.home.matter.standard.DimmableLightDevice
import com.google.home.matter.standard.ExtendedColorLightDevice
import com.google.home.matter.standard.GenericSwitchDevice
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.OccupancySensing
import com.google.home.matter.standard.OccupancySensorDevice
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOffLightDevice
import com.google.home.matter.standard.OnOffLightSwitchDevice
import com.google.home.matter.standard.OnOffPluginUnitDevice
import com.google.home.matter.standard.OnOffSensorDevice
import com.google.home.matter.standard.Thermostat
import com.google.home.matter.standard.ThermostatDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class DeviceViewModel (val device: HomeDevice) : ViewModel() {

    var id : String
    var name : String
    var connectivity: ConnectivityState

    val type : MutableStateFlow<DeviceType>
    val traits : MutableStateFlow<List<Trait>>
    val typeName : MutableStateFlow<String>
    val status : MutableStateFlow<String>

    init {
        // Initialize permanent values for a device:
        id = device.id.id
        name = device.name
        // Initialize the connectivity state:
        connectivity = device.sourceConnectivity.connectivityState

        // Initialize dynamic values for a structure:
        type = MutableStateFlow(UnknownDeviceType())
        traits = MutableStateFlow(mutableListOf())
        typeName = MutableStateFlow("--")
        status = MutableStateFlow("--")

        // Subscribe to changes on dynamic values:
        viewModelScope.launch { subscribeToType() }
    }

    private suspend fun subscribeToType() {
        // Subscribe to changes on device type, and the traits/attributes within:
        device.types().collect { typeSet ->
            // Container for the primary type for this device:
            var primaryType : DeviceType = UnknownDeviceType()

// TODO: 6.1.1 - Determine the primary type for this device
//             // Among all the types returned for this device, find the primary one:
//             for (typeInSet in typeSet)
//                 if (typeInSet.metadata.isPrimaryType)
//                     primaryType = typeInSet
//
//             // Optional: For devices with a single type that did not define a primary:
//             if (primaryType is UnknownDeviceType && typeSet.size == 1)
//                 primaryType = typeSet.first()

            // Set the connectivityState from the primary device type:
            connectivity = primaryType.metadata.sourceConnectivity.connectivityState

            // Container for list of supported traits present on the primary device type:
            val supportedTraits: List<Trait> = getSupportedTraits(primaryType.traits())

            // Store the primary type as the device type:
            type.emit(primaryType)

            // Determine the name for this type and store:
            typeName.emit(nameMap.get(primaryType.factory) ?: "Unsupported Device")

            // From the primary type, get the supported traits:
            traits.emit(supportedTraits)

            // Publish a device status based on connectivity, deviceType, and available traits:
            status.emit(getDeviceStatus(primaryType, supportedTraits, connectivity))
        }
    }

    fun getSupportedTraits(traits: Set<Trait>) : List<Trait> {
        val supportedTraits: MutableList<Trait> = mutableListOf()
// TODO: 6.1.2 - Get only the supported traits for this device
//        for (trait in traits)
//            if (trait.factory in HomeApp.supportedTraits)
//                supportedTraits.add(trait)

        return supportedTraits
    }

    companion object {
        // Map determining which trait value is going to be displayed as status for this device:
        val statusMap: Map <DeviceTypeFactory<out DeviceType>, TraitFactory<out Trait>> = mapOf(
            OnOffLightDevice to OnOff,
            DimmableLightDevice to OnOff,
            ColorTemperatureLightDevice to OnOff,
            ExtendedColorLightDevice to OnOff,
            GenericSwitchDevice to OnOff,
            OnOffLightSwitchDevice to OnOff,
            OnOffPluginUnitDevice to OnOff,
            OnOffSensorDevice to OnOff,
            ContactSensorDevice to BooleanState,
            OccupancySensorDevice to OccupancySensing,
            ThermostatDevice to Thermostat,
        )

        // Map determining the user readable value for this device:
        val nameMap: Map <DeviceTypeFactory<out DeviceType>, String> = mapOf(
            OnOffLightDevice to "Light",
            DimmableLightDevice to "Light",
            ColorTemperatureLightDevice to "Light",
            ExtendedColorLightDevice to "Light",
            GenericSwitchDevice to "Switch",
            OnOffLightSwitchDevice to "Switch",
            OnOffPluginUnitDevice to "Outlet",
            OnOffSensorDevice to "Sensor",
            ContactSensorDevice to "Sensor",
            OccupancySensorDevice to "Sensor",
            ThermostatDevice to "Thermostat",
        )

        fun <T : Trait?> getDeviceStatus(type: DeviceType, traits : List<T>, connectivity: ConnectivityState) : String {

            val targetTrait: TraitFactory<out Trait>? = statusMap.get(type.factory)

            if (type.metadata.sourceConnectivity.connectivityState != ConnectivityState.ONLINE)
                return "Offline"

            if (targetTrait == null)
                return "Unsupported"

            if (traits.isEmpty())
                return "Unsupported"

            if (traits.none{ it!!.factory == targetTrait })
                return "Unknown"

            return getTraitStatus(traits.first { it!!.factory == targetTrait }, type)
        }

        fun <T : Trait?> getTraitStatus(trait : T, type: DeviceType) : String {
            val status : String = when (trait) {
                is OnOff -> { if (trait.onOff == true) "On" else "Off" }
                is LevelControl -> { trait.currentLevel.toString() }
                is OccupancySensing -> { if (trait.occupancy?.occupied == true) "Occupied" else "Unoccupied" }
                is BooleanState -> {
                    // BooleanState is special, where the state gains meaning based on the device type:
                    when (type.factory) {
                        ContactSensorDevice -> {
                            if (trait.stateValue == true) "Closed"
                            else "Open"
                        }
                        else -> {
                            if (trait.stateValue == true) "True"
                            else "False"
                        }
                    }
                }
                is Thermostat -> { trait.systemMode.toString() }
                else -> ""
            }
            return status
        }
    }

}
