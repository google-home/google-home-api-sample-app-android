/* Copyright 2026 Google LLC

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

package com.example.googlehomeapisampleapp.doorbell

import android.util.Log
import com.google.home.HomeDevice
import com.google.home.google.Chime
import com.google.home.google.ChimeThemes
import com.google.home.google.ChimeTrait
import com.google.home.google.GoogleDoorbellDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform

interface DoorbellChimeController {
    val isChimeEnabled: Flow<Boolean>
    val isChimeToggleSupported: Flow<Boolean>
    val externalChimeType: Flow<ChimeTrait.ExternalChimeType>
    val installedChimeSounds: Flow<List<ChimeTrait.ChimeSoundStruct>>
    val selectedChimeId: Flow<UByte?>
    suspend fun setChimeEnabled(enabled: Boolean): Boolean
    suspend fun setExternalChimeType(type: ChimeTrait.ExternalChimeType): Boolean
    suspend fun setSelectedChimeSound(chimeId: UByte): Boolean
    suspend fun getAvailableThemes(): List<String>
}

/**
 * Chime Trait Attribute IDs (ChimeTrait.kt Reference):
 * * 0u -> installedChimeSounds: A read-only list of audio files stored on the device.
 * * 1u -> selectedChime: The index of the sound played by the doorbell's internal speaker.
 * * 2u -> enabled: The "Master Software Mute." Controls the doorbell's internal logic
 * to suppress all chime events.
 * NOTE: This may be unsupported on hardwired devices because the software
 * cannot physically break the electrical circuit to the chime box.
 * * 3u -> externalChime: Controls the physical relay for the house chime.
 * 0 = None (Off/Disabled)
 * 1 = Mechanical (Physical striker/bell)
 * 2 = Electronic (Digital melody/speaker)
 * * 4u -> externalChimeDurationSeconds: Only used for Electronic chimes (ID 3u = 2).
 * Determines how many seconds the voltage is applied to allow the
 * electronic melody to finish playing.
 */
class DoorbellChimeControllerImpl(private val device: HomeDevice) : DoorbellChimeController {
    private val TAG = "DoorbellChimeCtrl"

    override val isChimeEnabled: Flow<Boolean> = device.type(GoogleDoorbellDevice)
        .transform { doorbell ->
            val trait = doorbell.trait(Chime)
            emit(trait?.enabled ?: true)
        }
        .distinctUntilChanged()

    override val isChimeToggleSupported: Flow<Boolean> = device.type(GoogleDoorbellDevice)
        .transform { doorbell ->
            val trait = doorbell.trait(Chime)
            // Check if attribute index 2u (enabled) is supported by the hardware
            emit(trait?.attributeList?.contains(2u) == true)
        }
        .distinctUntilChanged()

    override val externalChimeType: Flow<ChimeTrait.ExternalChimeType> = device.type(GoogleDoorbellDevice)
        .transform { doorbell ->
            doorbell.trait(Chime)?.let { emit(it.externalChime ?: ChimeTrait.ExternalChimeType.Electronic) }
        }
        .distinctUntilChanged()

    override val installedChimeSounds: Flow<List<ChimeTrait.ChimeSoundStruct>> = device.type(GoogleDoorbellDevice)
        .transform { doorbell ->
            val trait = doorbell.trait(Chime)
            emit(trait?.installedChimeSounds ?: emptyList())
        }
        .distinctUntilChanged()

    override val selectedChimeId: Flow<UByte?> = device.type(GoogleDoorbellDevice)
        .transform { doorbell ->
            val trait = doorbell.trait(Chime)
            emit(trait?.selectedChime)
        }
        .distinctUntilChanged()

    override suspend fun setChimeEnabled(enabled: Boolean): Boolean {
        return try {
            val doorbell = device.type(GoogleDoorbellDevice).first()
            val trait = doorbell.trait(Chime) ?: return false

            if (trait.attributeList.contains(2u)) {
                trait.update {
                    setEnabled(enabled)
                }
                true
            } else {
                Log.w(TAG, "Device does not support the 'enabled' attribute.")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set chime enabled", e)
            false
        }
    }

    override suspend fun setExternalChimeType(type: ChimeTrait.ExternalChimeType): Boolean {
        return try {
            val doorbell = device.type(GoogleDoorbellDevice).first()
            val trait = doorbell.trait(Chime) ?: return false

            if (trait.attributeList.contains(3u)) {
                trait.update {
                    setExternalChime(type)
                }
                true
            } else {
                Log.w(TAG, "Hardware does not support changing chime type (ID 3u).")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting chime type", e)
            false
        }
    }

    override suspend fun setSelectedChimeSound(chimeId: UByte): Boolean {
        return try {
            val doorbell = device.type(GoogleDoorbellDevice).first()
            val trait = doorbell.trait(Chime) ?: return false

            // Check if attribute index 1u (selectedChime) is supported by the hardware
            if (trait.attributeList.contains(Chime.Attribute.selectedChime.tag)) {
                trait.update {
                    setSelectedChime(chimeId)
                }
                true
            } else {
                Log.w(TAG, "Hardware does not support changing selected chime (ID 1u).")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting selected chime", e)
            false
        }
    }

    override suspend fun getAvailableThemes(): List<String> {
        return try {
            val doorbell = device.type(GoogleDoorbellDevice).first()
            val trait = doorbell.trait(ChimeThemes)
            trait?.getAvailableThemes()?.themes?.map { it.name } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch themes", e)
            emptyList()
        }
    }
}