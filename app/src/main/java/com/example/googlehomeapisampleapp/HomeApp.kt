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

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import com.google.home.HomeClient
import kotlinx.coroutines.CoroutineScope

/**
 * Main application class for interacting with the Google Home APIs.
 *
 * @property context The application context.
 * @property scope The [CoroutineScope] for launching coroutines.
 * @property activity The [ComponentActivity] for handling activity results.
 * @property homeClientProvider The provider for obtaining a [HomeClient] instance.
 */
class HomeApp(
  val context: Context,
  val scope: CoroutineScope,
  val activity: ComponentActivity,
  val homeClientProvider: HomeClientProvider,
) {

  /**
   * The primary object to use all Home APIs.
   */
  var homeClient: HomeClient

  /**
   * Manages runtime permissions for the application.
   */
  val permissionsManager: PermissionsManager

  /**
   * Manages commissioning of Matter devices.
   */
  val commissioningManager: CommissioningManager

  init {
    Log.i(TAG, "HomeApp init")
    // Initialize the HomeClient, which is the primary object to use all Home APIs:
    homeClient = homeClientProvider.getClient()

    // Initialize supporting classes for Permissions and Commissioning APIs:
    Log.d(TAG, "create PermissionsManager")
    permissionsManager = PermissionsManager(scope, activity, homeClient)
    Log.d(TAG, "create CommissioningManager")
    commissioningManager = CommissioningManager(context, scope, activity)
  }

  companion object {
    const val TAG = "HomeApp"
  }
}