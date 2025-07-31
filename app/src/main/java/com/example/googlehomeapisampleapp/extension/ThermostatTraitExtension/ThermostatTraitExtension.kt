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

package com.example.googlehomeapisampleapp.extension.ThermostatTraitExtension

import com.google.home.matter.standard.Thermostat
import com.google.home.matter.standard.ThermostatTrait

// Default values for Thermostat traits, based on the Matter specification.
// These are used as fallbacks when the device does not report a value.
internal object CommonMatterSpecDefaults {
  const val MIN_SETPOINT_DEAD_BAND_CENTIDEGREES: Short = 200
  const val MIN_COOL_SETPOINT_LIMIT_CENTIDEGREES: Short = 1600
  const val MIN_HEAT_SETPOINT_LIMIT_CENTIDEGREES: Short = 700
  const val MAX_COOL_SETPOINT_LIMIT_CENTIDEGREES: Short = 3200
  const val MAX_HEAT_SETPOINT_LIMIT_CENTIDEGREES: Short = 3000
}

internal fun deciDegreesToCentiDegrees(deciDegrees: Int): Int {
  return (deciDegrees * 10)
}

/**
 * Validates a potential new cooling setpoint against the thermostat's current constraints.
 *
 * This function checks for several conditions:
 * 1.  The new setpoint must be within the thermostat's overall min/max cooling limits.
 * 2.  The thermostat must be in a cooling-related system mode (e.g., Cool, Auto).
 * 3.  If in Auto mode, the new cooling setpoint must maintain the minimum dead band distance
 * from the current heating setpoint.
 *
 * @param coolSetPointCentiDegrees The proposed new cooling setpoint in centi-degrees Celsius.
 * @return `true` if the update is valid, `false` otherwise.
 */
fun Thermostat.isValidCoolingSetpointUpdate(coolSetPointCentiDegrees: Short): Boolean {
  if (coolSetPointCentiDegrees < this.getMinCoolSetpointLimit()) return false
  if (coolSetPointCentiDegrees > this.getMaxCoolSetpointLimit()) return false

  if (!this.getSystemMode().isModeCoolingRelated()) return false

  if (this.getSystemMode() != ThermostatTrait.SystemModeEnum.Auto) {
    // We got a cooling mode, but not heat/cool, so the necessary checks have passed.
    return true
  }

  // Validate basic dead band distance.
  return this.getHeatingSetpoint()?.let { existingHeatSetPoint ->
    coolSetPointCentiDegrees >= (existingHeatSetPoint + this.getMinSetpointDeadBand())
  } ?: true /* Fallthrough to valid if not in auto mode. */
}

/**
 * Validates a potential new heating setpoint against the thermostat's current constraints.
 *
 * This function checks for several conditions:
 * 1.  The new setpoint must be within the thermostat's overall min/max heating limits.
 * 2.  The thermostat must be in a heating-related system mode (e.g., Heat, Auto).
 * 3.  If in Auto mode, the new heating setpoint must maintain the minimum dead band distance
 * from the current cooling setpoint.
 *
 * @param heatSetPointCentiDegrees The proposed new heating setpoint in centi-degrees Celsius.
 * @return `true` if the update is valid, `false` otherwise.
 */
fun Thermostat.isValidHeatingSetpointUpdate(heatSetPointCentiDegrees: Short): Boolean {
  if (heatSetPointCentiDegrees < this.getMinHeatSetpointLimit()) return false
  if (heatSetPointCentiDegrees > this.getMaxHeatSetpointLimit()) return false

  if (!this.getSystemMode().isModeHeatingRelated()) return false;

  if (this.getSystemMode() != ThermostatTrait.SystemModeEnum.Auto) {
    // We got a heating mode, but not heat/cool, so the necessary checks have passed.
    return true
  }

  // Validate basic dead band distance.
  return this.getCoolingSetpoint()?.let { existingCoolSetPoint ->
    heatSetPointCentiDegrees <= (existingCoolSetPoint - this.getMinSetpointDeadBand())
  } ?: true /* Fallthrough to valid if not in auto mode. */
}

/**
 * Gets the current cooling setpoint for the thermostat in centidegrees (0.01C).
 *
 * This only considers the occupiedCoolingSetpoint, which is mandatory on all
 * devices.
 
 * @return The cooling setpoint value in centidegrees as a [Short].
 */
fun Thermostat.getCoolingSetpoint(): Short? {
  return this.occupiedCoolingSetpoint
}

/**
 * Gets the current heating setpoint for the thermostat in centidegrees (0.01C).
 *
 * This only considers the occupiedCoolingSetpoint, which is mandatory on all
 * devices.
 *
 * @return The heating setpoint value in centidegrees as a [Short].
 */
fun Thermostat.getHeatingSetpoint(): Short? {
  return this.occupiedHeatingSetpoint
}

/**
 * Gets the minimum setpoint dead band value for the thermostat in centidegrees (0.01C).
 * The dead band is the minimum legal distance or range between heating and cooling
 * setpoints when in HeatCool mode.
 *
 * If the attribute is not available, it provides a default value from the Matter specification
 * that also matches Google cloud-based thermostat behavior.
 *
 * @return The minimum dead band value in centidegrees as a [Short].
 */
fun Thermostat.getMinSetpointDeadBand(): Short {

  return (this.minSetpointDeadBand?.let { deciDegreesToCentiDegrees(it.toInt()) }
    ?: CommonMatterSpecDefaults.MIN_SETPOINT_DEAD_BAND_CENTIDEGREES).toShort()
}

/**
 * Gets the minimum cooling setpoint limit for the thermostat in centidegrees (0.01C).
 *
 * It prioritizes `minCoolSetpointLimit`, falls back to `absMinCoolSetpointLimit`,
 * and finally returns a default of `1600` as per the Matter specification if both are absent.
 *
 * @return The minimum cooling setpoint limit as a [Short].
 */
fun Thermostat.getMinCoolSetpointLimit(): Short {
  return this.minCoolSetpointLimit ?: this.absMinCoolSetpointLimit
  ?: CommonMatterSpecDefaults.MIN_COOL_SETPOINT_LIMIT_CENTIDEGREES // If all attributes are absent, follow the Matter spec
}

/**
 * Gets the minimum heating setpoint limit for the thermostat in centidegrees (0.01C).
 *
 * It prioritizes `minHeatSetpointLimit`, falls back to `absMinHeatSetpointLimit`,
 * and finally returns a default of `700` as per the Matter specification if both are absent.
 *
 * @return The minimum heating setpoint limit as a [Short].
 */
fun Thermostat.getMinHeatSetpointLimit(): Short {
  return this.minHeatSetpointLimit ?: this.absMinHeatSetpointLimit
  ?: CommonMatterSpecDefaults.MIN_HEAT_SETPOINT_LIMIT_CENTIDEGREES // If all attributes are absent, follow the Matter spec
}

/**
 * Gets the maximum cooling setpoint limit for the thermostat in centidegrees (0.01C).
 *
 * It prioritizes `maxCoolSetpointLimit`, falls back to `absMaxCoolSetpointLimit`,
 * and finally returns a default of `3200` as per the Matter specification if both are absent.
 *
 * @return The maximum cooling setpoint limit as a [Short].
 */
fun Thermostat.getMaxCoolSetpointLimit(): Short {
  return this.maxCoolSetpointLimit ?: this.absMaxCoolSetpointLimit
  ?: CommonMatterSpecDefaults.MAX_COOL_SETPOINT_LIMIT_CENTIDEGREES // If all attributes are absent, follow the Matter spec
}

/**
 * Gets the maximum heating setpoint limit for the thermostat in centidegrees (0.01C).
 *
 * It prioritizes `maxHeatSetpointLimit`, falls back to `absMaxHeatSetpointLimit`,
 * and finally returns a default of `3000` as per the Matter specification if both are absent.
 *
 * @return The maximum heating setpoint limit as a [Short].
 */
fun Thermostat.getMaxHeatSetpointLimit(): Short {
  return this.maxHeatSetpointLimit ?: this.absMaxHeatSetpointLimit
  ?: CommonMatterSpecDefaults.MAX_HEAT_SETPOINT_LIMIT_CENTIDEGREES // If all attributes are absent, follow the Matter spec
}

/**
 * Gets the current running mode of the thermostat.
 *
 * This retrieves the `thermostatRunningMode` trait, which represents what the thermostat is
 * actively doing (e.g., heating, cooling) when in HeatCool mode. If the running mode is not available,
 * it safely returns [ThermostatTrait.ThermostatRunningModeEnum.UnknownValue].
 *
 * @return The current [ThermostatTrait.ThermostatRunningModeEnum], or `UnknownValue` if unavailable.
 */
fun Thermostat.getRunningMode(): ThermostatTrait.ThermostatRunningModeEnum =
  this.thermostatRunningMode ?: ThermostatTrait.ThermostatRunningModeEnum.UnknownValue

/**
 * Gets the set of supported system modes for this thermostat based on its features.
 *
 * This function builds a [Set] of [ThermostatTrait.SystemModeEnum] values that are
 * available on the thermostat. The `Off` mode is always included. Other modes like `Auto`,
 * `Heat`, and `Cool` are conditionally added based on the thermostat's capabilities
 * reporting (the feature map and other attributes).
 *
 * Some modes like `Precooling`, `EmergencyHeat, `FanOnly`, `Dry` and `Sleep` are not reported
 * as supported, but either appear due to internal workings of the thermostat, or
 * can be attempted on user request, but could fail since they cannot be pre-validated.
 * They can still be added and enabled with caution. Future releases may offer better
 * underlying reporting of actual support for these modes.
 *
 * @return A [Set] of [ThermostatTrait.SystemModeEnum] representing the modes this thermostat supports.
 * Returns a set containing only `Off` if `featureMap` is null.
 */
fun Thermostat.getSupportedSystemModes(): Set<ThermostatTrait.SystemModeEnum> {
  // Use buildSet for creating an efficient, immutable set.
  return buildSet {
    // The 'Off' mode is always considered a supported base mode.
    add(ThermostatTrait.SystemModeEnum.Off)

    // Use a safe call to execute the following logic only if 'featureMap' is not null.
    featureMap.let { features ->
      // 'Auto' mode is only supported if the thermostat explicitly supports auto mode
      // and can both heat and cool.
      if (features.autoMode && features.heating && features.cooling) {
        add(ThermostatTrait.SystemModeEnum.Auto)
      }
      // 'Heat' mode is added if the thermostat supports heating.
      if (features.heating) {
        add(ThermostatTrait.SystemModeEnum.Heat)
      }
      // 'Cool' mode is added if the thermostat supports cooling.
      if (features.cooling) {
        add(ThermostatTrait.SystemModeEnum.Cool)
      }
    }
  }
}

/**
 * Gets the target system mode of the thermostat.
 *
 * This retrieves the `systemMode` trait, which represents what the thermostat is set to
 * (e.g., Heat, Cool, Off). If the trait is not available, it safely returns
 * [ThermostatTrait.SystemModeEnum.UnknownValue].
 *
 * @return The current [ThermostatTrait.SystemModeEnum], or `UnknownValue` if unavailable.
 */
fun Thermostat.getSystemMode(): ThermostatTrait.SystemModeEnum =
  this.systemMode ?: ThermostatTrait.SystemModeEnum.UnknownValue

/**
 * Checks if a given system mode is supported by this thermostat.
 *
 * This function determines support by checking if the specified `mode` is present
 * in the set of modes returned by [getSupportedSystemModes].
 *
 * @param mode The [ThermostatTrait.SystemModeEnum] to check for support.
 * @return `true` if the mode is supported, `false` otherwise.
 */
fun Thermostat.isModeSupported(mode: ThermostatTrait.SystemModeEnum): Boolean {
  return mode in getSupportedSystemModes()
}

/**
 * Sets the occupied cooling setpoint for the thermostat.
 *
 * @param newValueCentiDegrees The new temperature setting in centidegrees (0.01C) as an [Int].
 */
suspend fun Thermostat.setOccupiedCoolingPoint(newValueCentiDegrees: Int) {
  this.update {
    setOccupiedCoolingSetpoint(newValueCentiDegrees.toShort())
  }
}

/**
 * Sets the occupied heating setpoint for the thermostat.
 *
 * @param newValueCentiDegrees The new temperature setting in centidegrees (0.01C) as an [Int].
 */
suspend fun Thermostat.setOccupiedHeatingPoint(newValueCentiDegrees: Int) {
  this.update {
    setOccupiedHeatingSetpoint(newValueCentiDegrees.toShort())
  }
}

/**
 * Checks if this system mode is related to cooling.
 *
 * This is true for modes where the cooling system can be active, such as
 * [Auto], [Cool], or [Precooling].
 *
 * @return `true` if the mode is a cooling-related mode, `false` otherwise.
 */
fun ThermostatTrait.SystemModeEnum.isModeCoolingRelated(): Boolean {
  return this == ThermostatTrait.SystemModeEnum.Auto ||
    this == ThermostatTrait.SystemModeEnum.Cool ||
    this == ThermostatTrait.SystemModeEnum.Precooling
}

/**
 * Checks if this system mode is related to heating.
 *
 * This is true for modes where the heating system can be active, such as
 * [Auto], [Heat], or [EmergencyHeat].
 *
 * @return `true` if the mode is a heating-related mode, `false` otherwise.
 */
fun ThermostatTrait.SystemModeEnum.isModeHeatingRelated(): Boolean {
  return this == ThermostatTrait.SystemModeEnum.Auto ||
    this == ThermostatTrait.SystemModeEnum.Heat ||
    this == ThermostatTrait.SystemModeEnum.EmergencyHeat
}
