package com.example.googlehomeapisampleapp.repository

import com.example.googlehomeapisampleapp.viewmodel.automations.DraftViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.google.home.automation.DraftAutomation
import com.google.home.automation.automation
import com.google.home.automation.equals
import com.google.home.matter.standard.*
import com.google.home.matter.standard.OnOff.Companion.onOff

class AutomationsRepository {

    fun hasEnoughLights(deviceVMs: List<DeviceViewModel>): Boolean {
        val onOffLights = getOnOffCapableLights(deviceVMs)
        return onOffLights.size >= 2
    }

    /**
     * Creates an OnOff light automation draft
     * This should only be called when the user actually selects the predefined automation
     */
    fun createOnOffLightAutomationDraft(deviceVMs: List<DeviceViewModel>): DraftViewModel? {
        val onOffLights = getOnOffCapableLights(deviceVMs)
        if (onOffLights.size < 2) return null

        val deviceA = onOffLights[0]
        val deviceB = onOffLights[1]

        val presetDraft = createOnOffDraftAutomation(deviceA, deviceB)

        // Return a locked DraftViewModel that uses the preset
        return DraftViewModel(
            candidateVM = null,
            presetDraft = presetDraft,
            isLocked = true
        )
    }

    private fun createOnOffDraftAutomation(deviceA: DeviceViewModel, deviceB: DeviceViewModel): DraftAutomation {
        return automation {
            name = "On/Off Light Automation"
            description = "Turn off ${deviceB.name.value} when ${deviceA.name.value} turns off"
            isActive = true

            sequential {
                select {
                    sequential {
                        val onOffStarter = starter(deviceA.device, deviceA.type.value.factory, OnOff)
                        condition {
                            expression = onOffStarter.onOff equals false
                        }
                    }
                    manualStarter()
                }

                parallel {
                    action(deviceB.device, deviceB.type.value.factory) {
                        command(OnOff.off())
                    }
                }
            }
        }
    }

    private fun getOnOffCapableLights(deviceVMs: List<DeviceViewModel>): List<DeviceViewModel> {
        return deviceVMs.filter { vm ->
            val factory = vm.type.value.factory
            factory == OnOffLightDevice ||
                    factory == DimmableLightDevice ||
                    factory == ColorTemperatureLightDevice ||
                    factory == ExtendedColorLightDevice
        }
    }

}