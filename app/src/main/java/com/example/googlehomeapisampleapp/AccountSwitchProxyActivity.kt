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

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint(ComponentActivity::class)
class AccountSwitchProxyActivity : Hilt_AccountSwitchProxyActivity() {

  @Inject
  lateinit var homeClientProvider: HomeClientProvider

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.i(TAG, "AccountSwitchProxyActivity: onCreate")
    lifecycleScope.launch {
      try {
        // Wait for the FIRST HomeClient emitted by the factory.
        // Since this Activity is launched *after* the account switch trigger,
        // this 'first()' will correspond to the HomeClient for the NEW account.
        val homeClient = homeClientProvider.getClient()
        Log.i(TAG, "AccountSwitchProxyActivity: Got new HomeClient. Registering permissions.")
        // Call the registration function within this Activity's onCreate lifecycle.
        homeClient.registerActivityResultCallerForPermissions(
          this@AccountSwitchProxyActivity
        )
        Log.i(TAG, "AccountSwitchProxyActivity: Permissions registered for the new HomeClient.")
        // Launch MainActivity, clearing the task to ensure a clean state.
        val intent = Intent(this@AccountSwitchProxyActivity, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
          // Optionally pass any necessary data to MainActivity
          putExtra(MainActivity.EXTRA_FROM_ACCOUNT_SWITCH, true)
        }
        startActivity(intent)
        finish() // Finish the proxy activity so it's not on the back stack.
        Log.i(TAG, "AccountSwitchProxyActivity: Launched MainActivity and finishing.")
      } catch (e: Exception) {
        Log.e(TAG, "AccountSwitchProxyActivity: Failed to get/register HomeClient", e)
        Toast.makeText(
          this@AccountSwitchProxyActivity,
          "Account switch failed: ${e.message}",
          Toast.LENGTH_LONG,
        )
          .show()
        // If an error occurs, still return to MainActivity to avoid a blank screen.
        val intent = Intent(this@AccountSwitchProxyActivity, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
      }
    }
  }

  companion object {
    private const val TAG = "AccountSwitchProxyActivity"
  }
}
