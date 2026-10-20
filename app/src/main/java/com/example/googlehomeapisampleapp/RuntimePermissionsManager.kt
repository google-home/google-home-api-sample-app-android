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

package com.example.googlehomeapisampleapp

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat


class RuntimePermissionsManager(
  private val activity: ComponentActivity,
  private val requestPermissionLauncher: ActivityResultLauncher<String>,
  private val onPermissionResult: (Boolean) -> Unit,
) {
  fun hasMicrophonePermission(): Boolean {
    return ContextCompat.checkSelfPermission(
      activity,
      Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
  }

  fun checkMicrophonePermission() {
    val isGranted = ContextCompat.checkSelfPermission(
      activity,
      Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    onPermissionResult(isGranted)
  }

  fun requestMicrophonePermission() {
    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
  }
}