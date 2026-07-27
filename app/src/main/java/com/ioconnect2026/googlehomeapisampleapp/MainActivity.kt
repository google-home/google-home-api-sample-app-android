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

package com.ioconnect2026.googlehomeapisampleapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.ioconnect2026.googlehomeapisampleapp.view.HomeAppView
import com.ioconnect2026.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.google.home.HomeException
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The main activity of the Google Home API Sample App. This activity is responsible for
 * initializing the [HomeApp] and displaying the [HomeAppView].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject lateinit var homeClientProvider: HomeClientProvider
  private val homeAppVM: HomeAppViewModel by viewModels()
  private lateinit var permissionsManager: PermissionsManager

  /**
   * Called when the activity is first created.
   *
   * @param savedInstanceState If the activity is being re-initialized after previously being shut
   *   down then this Bundle contains the data it most recently supplied in [onSaveInstanceState].
   *   Otherwise it is null.
   */
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate of MainActivity instance of MainActivity ${this@MainActivity}")
    // Initialize logger for logging and displaying messages:
    logger = Logger(applicationContext)

    val homeClient = homeClientProvider.getClient()
    permissionsManager = PermissionsManager(lifecycleScope, this, homeClient)

    // Call to make the app allocate the entire screen:
    enableEdgeToEdge()
    // Set the content of the screen to display the app:
    setContent {
      HomeAppView(
        homeAppVM = homeAppVM,
        onRequestPermissions = { force -> permissionsManager.requestPermissions(force) },
        onRefreshPermissions = { permissionsManager.refreshPermissions() },
      )
    }

    // Receive the intent extra data to see if it is from AccountSwitchActivity.kt
    val isFromAccountSwitch = intent.getBooleanExtra(EXTRA_FROM_ACCOUNT_SWITCH, false)
    Log.i(TAG, "Launched from account switch: $isFromAccountSwitch")

    if (savedInstanceState != null) return
    // Activity is fresh and newly created
    if (isFromAccountSwitch) {
      // After new account signed-in, it still needs to request the permission.
      // When switching to an account gotten permissions before, it needs to wait
      // until the permissions are fully loaded.
      lifecycleScope.launch {
        // Block here until permissionManager is initialized
        permissionsManager.isInitialized.first { it }
        if (!permissionsManager.isSignedIn.value) {
          // Try to request the permission
          Log.d(TAG, "Permissions not granted, requesting permissions")
          permissionsManager.requestPermissions()
        }
      }
    }
  }

  companion object {
    const val TAG = "MainActivity"
    const val EXTRA_FROM_ACCOUNT_SWITCH = "fromAccountSwitch"
    private lateinit var logger: Logger

    /**
     * Shows an error message to the user and logs it.
     *
     * @param caller The object calling this function.
     * @param message The error message to display.
     */
    fun showError(caller: Any, message: String) {
      logger.log(caller, message, Logger.LogLevel.ERROR)
    }

    /** Shows an error message for an exception. */
    fun showError(caller: Any, exception: Throwable, prefix: String = "") {
      showError(caller, formatException(exception, prefix))
    }

    /**
     * Shows a warning message to the user and logs it.
     *
     * @param caller The object calling this function.
     * @param message The warning message to display.
     */
    fun showWarning(caller: Any, message: String) {
      logger.log(caller, message, Logger.LogLevel.WARNING)
    }

    /** Shows a warning message for an exception. */
    fun showWarning(caller: Any, exception: Throwable, prefix: String = "") {
      showWarning(caller, formatException(exception, prefix))
    }

    private fun formatException(e: Throwable, prefix: String): String {
      val baseMessage =
        if (e is HomeException && e.subErrors.isNotEmpty()) {
          val subErrorsStr =
            e.subErrors
              .map { "${it.key}: ${it.value.code} (${it.value.message})" }
              .joinToString(", ")
          "${e.error.code}: ${e.error.message}, subErrors: [$subErrorsStr]"
        } else {
          e.message ?: e.toString()
        }
      return if (prefix.isEmpty()) baseMessage else "$prefix: $baseMessage"
    }

    /**
     * Shows an info message to the user and logs it.
     *
     * @param caller The object calling this function.
     * @param message The info message to display.
     */
    fun showInfo(caller: Any, message: String) {
      logger.log(caller, message, Logger.LogLevel.INFO)
    }

    /**
     * Logs a debug message.
     *
     * @param caller The object calling this function.
     * @param message The debug message to log.
     */
    fun showDebug(caller: Any, message: String) {
      logger.log(caller, message, Logger.LogLevel.DEBUG)
    }
  }
}

/*  Logger - Utility class for logging and displaying messages
 *   This helps us to communicate unexpected states on screen, as well as to record them appropriately
 *   so when it comes you to report an issue we can make sure the states are captured in adb logs.
 *  */
class Logger(val context: Context) {

  enum class LogLevel {
    ERROR,
    WARNING,
    INFO,
    DEBUG,
  }

  fun log(caller: Any, message: String, level: LogLevel) {
    // Log the message in accordance to its level:
    when (level) {
      LogLevel.ERROR -> Log.e(caller.javaClass.name, message)
      LogLevel.WARNING -> Log.w(caller.javaClass.name, message)
      LogLevel.INFO -> Log.i(caller.javaClass.name, message)
      LogLevel.DEBUG -> Log.d(caller.javaClass.name, message)
    }
    // For levels above debug, Also show the message on screen:
    if (level != LogLevel.DEBUG) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
  }
}
