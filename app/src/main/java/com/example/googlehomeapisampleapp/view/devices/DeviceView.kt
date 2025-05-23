
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

package com.example.googlehomeapisampleapp.view.devices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.googlehomeapisampleapp.MainActivity
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.google.home.ConnectivityState
import com.google.home.DeviceType
import com.google.home.Trait
import com.google.home.matter.standard.BooleanState
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.LevelControlTrait
import com.google.home.matter.standard.OccupancySensing
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.Thermostat
import com.google.home.matter.standard.ThermostatTrait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun DeviceView (homeAppVM: HomeAppViewModel) {
    val scope: CoroutineScope = rememberCoroutineScope()
    val deviceVM: DeviceViewModel? by homeAppVM.selectedDeviceVM.collectAsState()

    BackHandler {
        scope.launch { homeAppVM.selectedDeviceVM.emit(null) }
    }

    Column {
        Spacer(Modifier.height(64.dp))

        Box (modifier = Modifier.weight(1f)) {

            Column {
                Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Text(text = "" + deviceVM?.name, fontSize = 32.sp)
                }

                Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(weight = 1f, fill = false)) {
                    ControlListComponent(homeAppVM)
                }
            }
        }
    }
}

@Composable
fun ControlListComponent (homeAppVM: HomeAppViewModel) {

    val deviceVM: DeviceViewModel = homeAppVM.selectedDeviceVM.collectAsState().value ?: return
    val deviceType: DeviceType = deviceVM.type.collectAsState().value
    val deviceTypeName: String = deviceVM.typeName.collectAsState().value
    val deviceTraits: List<Trait> = deviceVM.traits.collectAsState().value

    Column (Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        Text(deviceTypeName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }

    for (trait in deviceTraits) {
        ControlListItem(trait, deviceType)
    }
}

@Composable
fun ControlListItem (trait: Trait, type: DeviceType) {
    val scope: CoroutineScope = rememberCoroutineScope()

    val isConnected : Boolean =
        type.metadata.sourceConnectivity.connectivityState == ConnectivityState.ONLINE

    Box (Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        when (trait) {
            is OnOff -> {
// TODO: 6.1.3 - Render controls based on the trait type
//                 Column (Modifier.fillMaxWidth()) {
//                     Text(trait.factory.toString(), fontSize = 20.sp)
//                     Text(DeviceViewModel.getTraitStatus(trait, type), fontSize = 16.sp)
//                 }

// TODO: 6.2.1 - Render controls based on the trait type
//                 Switch (checked = (trait.onOff == true), modifier = Modifier.align(Alignment.CenterEnd),
//                     onCheckedChange = { state ->
//                         scope.launch { if (state) trait.on() else trait.off() }
//                     },
//                     enabled = isConnected
//                 )
            }
            is LevelControl -> {
                val level = trait.currentLevel
                Text(trait.factory.toString(), fontSize = 20.sp)
                LevelSlider(value = level?.toFloat()!!, low = 0f, high = 254f, steps = 0,
                    modifier = Modifier.padding(top = 16.dp),
                    onValueChange = { value : Float ->
                        scope.launch {
                            trait.moveToLevelWithOnOff(
                                level = value.toInt().toUByte(),
                                transitionTime = null,
                                optionsMask = LevelControlTrait.OptionsBitmap(),
                                optionsOverride = LevelControlTrait.OptionsBitmap()
                            ) }
                    },
                    isEnabled = isConnected
                )
            }
            is BooleanState -> {
                Column (Modifier.fillMaxWidth()) {
                    Text(trait.factory.toString(), fontSize = 20.sp)
                    Text(DeviceViewModel.getTraitStatus(trait, type), fontSize = 16.sp)
                }
            }
            is OccupancySensing -> {
                Column (Modifier.fillMaxWidth()) {
                    Text(trait.factory.toString(), fontSize = 20.sp)
                    Text(DeviceViewModel.getTraitStatus(trait, type), fontSize = 16.sp)
                }
            }
            is Thermostat -> {
                val supportedModes = arrayOf(
                    ThermostatTrait.SystemModeEnum.Heat,
                    ThermostatTrait.SystemModeEnum.Cool,
                    ThermostatTrait.SystemModeEnum.Off
                )
                val workingModes = arrayOf(
                    ThermostatTrait.SystemModeEnum.Heat,
                    ThermostatTrait.SystemModeEnum.Cool
                )
                var expanded: Boolean by remember { mutableStateOf(false) }
                var vlow = 0f
                var vhigh = 0f
                var vset = 0f
                Column (Modifier.fillMaxWidth()) {
                    Box (Modifier.fillMaxWidth()) {
                        Text("Ambient", fontSize = 20.sp)
                        Text(trait.localTemperature?.div(100)?.toFloat().toString(), fontSize = 16.sp, modifier = Modifier.align(Alignment.CenterEnd))
                    }
                    Spacer (Modifier.height(16.dp))
                    Box (Modifier.fillMaxWidth()) {
                        Text("Mode", fontSize = 20.sp)
                        TextButton(onClick = { expanded = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
                            Text(text = trait.systemMode.toString() + " ▾", fontSize = 20.sp)
                        }

                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.align(Alignment.TopEnd)) {
                            for (mode in supportedModes) {
                                DropdownMenuItem(
                                    text = { Text(mode.toString()) },
                                    onClick = {
                                        scope.launch { trait.update { setSystemMode(mode) } }
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer (Modifier.height(16.dp))
                    Box (Modifier.fillMaxWidth()) {
                        Text("Setpoint", fontSize = 20.sp)
                        Text(trait.occupiedHeatingSetpoint?.div(100)?.toFloat()!!.toString(), fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd))
                        when (trait.systemMode) {
                            ThermostatTrait.SystemModeEnum.Heat -> {
                                vset = trait.occupiedHeatingSetpoint?.toFloat()!!
                                vlow = trait.absMinHeatSetpointLimit?.toFloat()!!
                                vhigh = trait.absMaxHeatSetpointLimit?.toFloat()!!
                            }
                            ThermostatTrait.SystemModeEnum.Cool -> {
                                vset = trait.occupiedCoolingSetpoint?.toFloat()!!
                                vlow = trait.absMinCoolSetpointLimit?.toFloat()!!
                                vhigh = trait.absMaxCoolSetpointLimit?.toFloat()!!
                            }
                            else -> {
                                vset = trait.occupiedHeatingSetpoint?.toFloat()!!
                                vlow = trait.absMinHeatSetpointLimit?.toFloat()!!
                                vhigh = trait.absMaxHeatSetpointLimit?.toFloat()!!
                            }
                        }

                        LevelSlider(value = vset, low = vlow, high = vhigh,
                            steps = (vhigh - vlow).div(100f).toInt().minus(1),
                            modifier = Modifier.padding(top = 16.dp),
                            onValueChange = { value : Float ->
                                scope.launch {
                                    try {
                                        trait.setpointRaiseLower(
                                            ThermostatTrait.SetpointRaiseLowerModeEnum.Both,
                                            (value - vset).div(10).toInt().toByte()
                                        )
                                    } catch (e:Exception) {
                                        MainActivity.showWarning(this, "Exception: " + e.message)
                                    }
                                }
                            },
                            isEnabled = isConnected && trait.systemMode in workingModes
                        )
                    }
                }
            }
            else -> return
        }
    }
}

@Composable
fun LevelSlider(value: Float, low: Float, high: Float, steps: Int, onValueChange: (Float) -> Unit, modifier: Modifier, isEnabled: Boolean) {
    var level: Float by remember { mutableStateOf(value) }
    var oldValue: Float by remember { mutableStateOf(value) }

    Slider(
        value = level,
        valueRange = low..high,
        steps = steps,
        modifier = modifier,
        onValueChange = { level = it },
        onValueChangeFinished = { onValueChange(level) },
        enabled = isEnabled
    )

    // Register external value change:
    if(value != oldValue) {
        oldValue = value
        level = value
    }
}
