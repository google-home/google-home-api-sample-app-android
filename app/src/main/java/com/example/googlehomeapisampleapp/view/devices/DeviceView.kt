
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

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.googlehomeapisampleapp.MainActivity
import com.example.googlehomeapisampleapp.camera.CameraStreamView
import com.example.googlehomeapisampleapp.extension.ThermostatTraitExtension.getCoolingSetpoint
import com.example.googlehomeapisampleapp.extension.ThermostatTraitExtension.getHeatingSetpoint
import com.example.googlehomeapisampleapp.extension.ThermostatTraitExtension.getMaxCoolSetpointLimit
import com.example.googlehomeapisampleapp.extension.ThermostatTraitExtension.getMaxHeatSetpointLimit
import com.example.googlehomeapisampleapp.extension.ThermostatTraitExtension.getMinCoolSetpointLimit
import com.example.googlehomeapisampleapp.extension.ThermostatTraitExtension.getMinHeatSetpointLimit
import com.example.googlehomeapisampleapp.extension.ThermostatTraitExtension.getSystemMode
import com.example.googlehomeapisampleapp.extension.ThermostatTraitExtension.isModeCoolingRelated
import com.example.googlehomeapisampleapp.extension.ThermostatTraitExtension.isModeHeatingRelated
import com.example.googlehomeapisampleapp.extension.ThermostatTraitExtension.isModeSupported
import com.example.googlehomeapisampleapp.extension.ThermostatTraitExtension.isValidCoolingSetpointUpdate
import com.example.googlehomeapisampleapp.extension.ThermostatTraitExtension.isValidHeatingSetpointUpdate
import com.example.googlehomeapisampleapp.extension.ThermostatTraitExtension.setOccupiedCoolingPoint
import com.example.googlehomeapisampleapp.extension.ThermostatTraitExtension.setOccupiedHeatingPoint
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.google.home.ConnectivityState
import com.google.home.DeviceType
import com.google.home.HomeException
import com.google.home.Trait
import com.google.home.google.GoogleCameraDevice
import com.google.home.matter.standard.BooleanState
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.LevelControlTrait
import com.google.home.matter.standard.OccupancySensing
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.Thermostat
import com.google.home.matter.standard.ThermostatTrait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DeviceView (homeAppVM: HomeAppViewModel) {
    val scope: CoroutineScope = rememberCoroutineScope()
    val deviceVM: DeviceViewModel? by homeAppVM.selectedDeviceVM.collectAsState()

    BackHandler {
        scope.launch { homeAppVM.selectedDeviceVM.emit(null) }
    }

    deviceVM?.let { vm ->
        val context = LocalContext.current

        // Check if the selected device is a camera
        val isCameraDevice = vm.type.collectAsState().value.factory == GoogleCameraDevice

        // Placeholder for the onShowSnackbar lambda required by CameraStreamView
        val onShowSnackbar: (String) -> Unit = { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        LaunchedEffect(vm) {
            vm.checkDecommissionEligibility()
            vm.uiEventFlow.collect { event ->
                when (event) {
                    is DeviceViewModel.UiEvent.ShowToast -> {
                        Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    }

                    is DeviceViewModel.UiEvent.NavigateBack -> {
                        scope.launch { homeAppVM.selectedDeviceVM.emit(null) }
                    }
                }
            }
        }

        val name by vm.name.collectAsState()
        var showRenameDialog by remember { mutableStateOf(false) }
        var showDeleteDialog by remember { mutableStateOf(false) }

        Column {
            Spacer(Modifier.height(64.dp))
            if (isCameraDevice) {
                BackHandler {
                    scope.launch { homeAppVM.selectedDeviceVM.emit(null) }
                }
                deviceVM?.let {vm ->
                    // For a camera, render the dedicated CameraStreamView immediately
                    CameraStreamView(
                        deviceId = vm.id,
                        paddingValues = PaddingValues(0.dp),
                        onShowSnackbar = onShowSnackbar
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                fontSize = 32.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Share button shown for all devices
                                IconButton(onClick = {
                                    homeAppVM.homeApp.commissioningManager.requestShareDevice(vm.id)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share Device",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Rename shown for all devices
                                IconButton(onClick = { showRenameDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rename Device",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(onClick = { showDeleteDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Device",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Rename dialog that allows the user to input a new device name
                        if (showRenameDialog) {
                            var newName by remember { mutableStateOf(name) }

                            AlertDialog(
                                onDismissRequest = { showRenameDialog = false },
                                title = { Text("Rename Device") },
                                text = {
                                    OutlinedTextField(
                                        value = newName,
                                        onValueChange = { newName = it },
                                        label = { Text("Device Name") }
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        vm.rename(newName)
                                        showRenameDialog = false
                                    }) {
                                        Text("Save")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showRenameDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        // Delete Confirmation Dialog
                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { Text("Delete Device") },
                                text = { Text("Are you sure you want to delete this device?") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showDeleteDialog = false
                                            vm.deleteDevice()
                                        }
                                    ) {
                                        Text("Delete")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .weight(1f, fill = false)
                        ) {
                            ControlListComponent(homeAppVM)
                        }
                    }
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

    Column (Modifier
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .fillMaxWidth()) {
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
        type.metadata.sourceConnectivity.connectivityState == ConnectivityState.ONLINE ||
                type.metadata.sourceConnectivity.connectivityState == ConnectivityState.PARTIALLY_ONLINE

    Box (Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        when (trait) {
            is OnOff -> {
                Column (Modifier.fillMaxWidth()) {
                    Text(trait.factory.toString(), fontSize = 20.sp)
                    Text(DeviceViewModel.getTraitStatus(trait, type), fontSize = 16.sp)
                }

                Switch (checked = (trait.onOff == true), modifier = Modifier.align(Alignment.CenterEnd),
                    onCheckedChange = { state ->
                        scope.launch {
                            try {
                                if (state) trait.on() else trait.off()
                            } catch (e: HomeException) {
                                MainActivity.showWarning(this, "Toggling device on/off failed")
                            }
                        }
                    },
                    enabled = isConnected
                )
            }
            is LevelControl -> {
                val level = trait.currentLevel
                //Hide LevelControl completely when offline or level unavailable
                if (isConnected && level != null) {
                    Text(trait.factory.toString(), fontSize = 20.sp)
                    LevelSlider(
                        value = level.toFloat(),
                        low = 0f, high = 254f, steps = 0,
                        modifier = Modifier.padding(top = 16.dp),
                        onValueChangeFinished = { value: Float ->
                            scope.launch {
                                try {
                                    trait.moveToLevelWithOnOff(
                                        level = value.toInt().toUByte(),
                                        transitionTime = null,
                                        optionsMask = LevelControlTrait.OptionsBitmap(),
                                        optionsOverride = LevelControlTrait.OptionsBitmap()
                                    )
                                } catch (e: HomeException) {
                                    MainActivity.showWarning(this, "Level control command failed")
                                }
                            }
                        },
                        isEnabled = isConnected
                    )
                }
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
                ThermostatControl(
                    trait = trait,
                    isConnected = isConnected,
                    scope = scope
                )
            }
            else -> return
        }
    }
}

/**
 * Thermostat control for the Thermostat trait, with heat, cool, auto support, both
 * relative and absolute.
 *
 * Arguments are straight from parent scope, see [ControlListItem].
 */
@Composable
fun ThermostatControl(
    trait: Thermostat,
    isConnected: Boolean,
    scope: CoroutineScope
) {
    val allModes: List<ThermostatTrait.SystemModeEnum> =
        ThermostatTrait.SystemModeEnum.entries.filter { mode -> mode != ThermostatTrait.SystemModeEnum.UnknownValue }
    var expanded: Boolean by remember { mutableStateOf(false) }
    Column (Modifier.fillMaxWidth()) {
        // Ambient Temperature
        Box (Modifier.fillMaxWidth()) {
            Text("Ambient", fontSize = 20.sp)
            val temperatureString = trait.localTemperature?.div(100)?.toFloat().toString() + "℃"
            Text(temperatureString, fontSize = 16.sp, modifier = Modifier.align(Alignment.CenterEnd))
        }
        Spacer (Modifier.height(16.dp))

        // System Mode
        Box (Modifier.fillMaxWidth()) {
            Text("SystemMode", fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterStart))
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                TextButton(onClick = { expanded = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text(text = trait.systemMode.toString() + " ▾", fontSize = 20.sp)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.align(Alignment.CenterEnd)) {
                    for (mode in allModes) {
                        DropdownMenuItem(
                            text = { Text(mode.toString()) },
                            onClick = {
                                scope.launch {
                                    try {
                                        trait.update { setSystemMode(mode) }
                                    } catch (e: HomeException) {
                                        MainActivity.showWarning(this, "Exception: " + e.message)
                                    }
                                }
                                expanded = false
                            },
                            enabled = trait.isModeSupported(mode)
                        )
                    }
                }
            }
        }
        val isCoolingSetpointEnabled = isConnected &&
                (trait.systemMode?.isModeCoolingRelated() == true)
        if (isCoolingSetpointEnabled) {
            val lowCoolSetpoint = trait.getMinCoolSetpointLimit()
            val highCoolSetpoint = trait.getMaxCoolSetpointLimit()
            val coolSetpoint = trait.getCoolingSetpoint()

            // Cooling Setpoint slider
            Spacer(Modifier.height(16.dp))
            LabeledSlider(
                title = "Cooling Setpoint",
                isEnabled = isCoolingSetpointEnabled,
                currentValue = coolSetpoint,
                low = lowCoolSetpoint,
                high = highCoolSetpoint,
                roundingValue = 50f,
                unitSuffix = "℃",
                onValueChange = { newCoolSetPointCentiDegrees ->
                    trait.isValidCoolingSetpointUpdate(
                        newCoolSetPointCentiDegrees.toInt().toShort()
                    )
                },
                onValueChangeFinished = { newValueCentiDegrees ->
                    if (newValueCentiDegrees.toInt().toShort() != coolSetpoint)
                        trait.setOccupiedCoolingPoint(newValueCentiDegrees.toInt())
                }
            )

            // Cool Adjustment Buttons
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    scope.launch {
                        try {
                            trait.setpointRaiseLower(
                                mode = ThermostatTrait.SetpointRaiseLowerModeEnum.Cool,
                                amount = -5
                            )
                            println("Decreased Cool by 0.5℃")
                        } catch (e: Exception) {
                            println("Error changing setpoint: ${e.message}")
                        }
                    }
                }) {
                    Text("-0.5℃ Cool")
                }

                Button(onClick = {
                    scope.launch {
                        try {
                            trait.setpointRaiseLower(
                                mode = ThermostatTrait.SetpointRaiseLowerModeEnum.Cool,
                                amount = 5
                            )
                            println("Increased Cool by 0.5℃")
                        } catch (e: Exception) {
                            println("Error changing setpoint: ${e.message}")
                        }
                    }
                }) {
                    Text("+0.5℃ Cool")
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        val isHeatingSetpointEnabled = isConnected &&
                (trait.systemMode?.isModeHeatingRelated() == true)
        if (isHeatingSetpointEnabled) {
            val lowHeatSetpoint = trait.getMinHeatSetpointLimit()
            val highHeatSetpoint = trait.getMaxHeatSetpointLimit()
            val heatSetpoint = trait.getHeatingSetpoint()

            // Heating Setpoint slider
            Spacer(Modifier.height(16.dp))
            LabeledSlider(
                title = "Heating Setpoint",
                isEnabled = isHeatingSetpointEnabled,
                currentValue = heatSetpoint,
                low = lowHeatSetpoint,
                high = highHeatSetpoint,
                roundingValue = 50f,
                unitSuffix = "℃",
                onValueChange = { newHeatSetPointCentiDegrees ->
                    trait.isValidHeatingSetpointUpdate(
                        newHeatSetPointCentiDegrees.toInt().toShort()
                    )
                },
                onValueChangeFinished = { newValueCentiDegrees -> // The lambda receives the new absolute value
                    if (newValueCentiDegrees.toInt().toShort() != heatSetpoint)
                        trait.setOccupiedHeatingPoint(newValueCentiDegrees.toInt())
                }
            )

            // Heat Adjustment Buttons
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    scope.launch {
                        try {
                            trait.setpointRaiseLower(
                                mode = ThermostatTrait.SetpointRaiseLowerModeEnum.Heat,
                                amount = -5
                            )
                            println("Decreased Heat by 0.5℃")
                        } catch (e: Exception) {
                            println("Error changing setpoint: ${e.message}")
                        }
                    }
                }) {
                    Text("-0.5℃ Heat")
                }
                Button(onClick = {
                    println("Increase Heat by 0.5℃")
                    scope.launch {
                        try {
                            trait.setpointRaiseLower(
                                mode = ThermostatTrait.SetpointRaiseLowerModeEnum.Heat,
                                amount = 5
                            )
                            println("Increased Heat by 0.5℃")
                        } catch (e: Exception) {
                            println("Error changing setpoint: ${e.message}")
                        }
                    }
                }) {
                    Text("+0.5℃ Heat")
                }
            }

            Spacer(Modifier.height(8.dp))

        }

        // Both Adjustment Buttons
        if (trait.getSystemMode() == ThermostatTrait.SystemModeEnum.Auto) {
            HorizontalDivider(thickness = 2.dp)

            Text ("Auto mode only:")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    scope.launch {
                        try {
                            trait.setpointRaiseLower(
                                mode = ThermostatTrait.SetpointRaiseLowerModeEnum.Both,
                                amount = -5
                            )
                            println("Decreased Both by 0.5℃")
                        } catch (e: Exception) {
                            println("Error changing setpoint: ${e.message}")
                        }
                    }
                }) {
                    Text("-0.5℃ Both")
                }
                Button(onClick = {
                    scope.launch {
                        try {
                            trait.setpointRaiseLower(
                                mode = ThermostatTrait.SetpointRaiseLowerModeEnum.Both,
                                amount = 5
                            )
                            println("Increased Both by 0.5℃")
                        } catch (e: Exception) {
                            println("Error changing setpoint: ${e.message}")
                        }
                    }
                }) {
                    Text("+0.5℃ Both")
                }
            }
        }
    }
}
/**
 * A custom slider composable with support for value rounding and validation.
 *
 * @param value The current value of the slider.
 * @param low The minimum value the slider can be set to.
 * @param high The maximum value the slider can be set to.
 * @param steps The number of discrete intervals between [low] and [high].
 * @param roundingValue An optional value to round the slider's output to the nearest
 * multiple of. If null, no rounding is performed.
 * @param onValueChange An optional validation lambda that is invoked as the slider
 * value changes. Return `true` to accept the change, `false` to reject it.
 * @param onValueChangeFinished A lambda that is invoked when the user has
 * finished interacting with the slider.
 * @param modifier The [Modifier] to be applied to the slider.
 * @param isEnabled Whether the slider is enabled and can be interacted with.
 */
@Composable
fun LevelSlider(
    value: Float,
    low: Float,
    high: Float,
    steps: Int,
    modifier: Modifier = Modifier,
    roundingValue: Float? = null,
    onValueChange: ((Float) -> Boolean)? = null,
    onValueChangeFinished: (Float) -> Unit,
    isEnabled: Boolean = true,
) {
    // This state now correctly resets if the external `value` changes.
    var level: Float by remember(value) { mutableFloatStateOf(value) }

    Slider(
        value = level,
        valueRange = low..high,
        steps = steps,
        modifier = modifier,
        onValueChange = { newRawValue ->
            // Apply rounding if roundingValue is provided
            val newValue = if (roundingValue != null) {
                (newRawValue / roundingValue).roundToInt() * roundingValue
            } else {
                newRawValue
            }
            // Update state only if validation passes or there's no validator
            if (onValueChange == null || onValueChange(newValue)) {
                level = newValue
            }
        },
        onValueChangeFinished = { onValueChangeFinished(level) },
        enabled = isEnabled
    )
}

/**
 * A composable that wraps a [LevelSlider] with a title and a current value display.
 *
 * This component provides a complete UI element for a labeled slider, handling null
 * states and wiring up the necessary callbacks.
 *
 * @param title The text label to display above the slider.
 * @param isEnabled Whether the slider is enabled and can be interacted with.
 * @param currentValue The current value of the slider. The component will display
 * 'Not available' if this is null.
 * @param low The minimum value of the slider range. Required for the slider to be displayed.
 * @param high The maximum value of the slider range. Required for the slider to be displayed.
 * @param roundingValue An optional value to round the slider's output to the nearest
 * multiple of. Passed to the underlying [LevelSlider].
 * @param unitSuffix A string added as a suffix to the currentValue if a value is present,
 *                   to indicate units of measurement.
 * @param onValueChange An optional validation lambda that is invoked as the slider value
 * changes. Passed to the underlying [LevelSlider].
 * @param onValueChangeFinished A suspend lambda that is invoked with the new value when
 * the user has finished interacting with the slider.
 */
@Composable
private fun LabeledSlider(
    title: String,
    isEnabled: Boolean,
    currentValue: Short?,
    low: Short?,
    high: Short?,
    roundingValue: Float? = null,
    unitSuffix: String = "",
    onValueChange: ((Float) -> Boolean)? = null,
    onValueChangeFinished: suspend (newValue: Float) -> Unit,
) {
    // Return early if the necessary values are null
    if (currentValue == null || low == null || high == null) {
        Text("$title: Not available")
        return
    }

    val scope = rememberCoroutineScope()
    val vset = currentValue.toFloat()
    val vlow = low.toFloat()
    val vhigh = high.toFloat()

    Box(Modifier.fillMaxWidth()) {
        Text(title, fontSize = 20.sp)
        Text(
            text = (vset / 100f).toString() + unitSuffix,
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.TopEnd)
        )

        LevelSlider(
            value = vset,
            low = vlow,
            high = vhigh,
            // Calculate steps safely, ensuring it's not negative
            steps = ((vhigh - vlow) / 10f).toInt().minus(1).coerceAtLeast(0),
            roundingValue = roundingValue,
            modifier = Modifier.padding(top = 16.dp),
            onValueChange = { value ->
                if (onValueChange == null) true
                else onValueChange(value)
            },
            onValueChangeFinished = { value: Float ->
                scope.launch {
                    try {
                        // Call the lambda with the new slider value directly
                        onValueChangeFinished(value)
                    } catch (e: HomeException) {
                        MainActivity.showWarning(this, "Exception: " + e.message)
                    }
                }
            },
            isEnabled = isEnabled
        )
    }
}