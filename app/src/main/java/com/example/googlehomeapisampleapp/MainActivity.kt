
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

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.googlehomeapisampleapp.view.HomeAppView
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel

class MainActivity : ComponentActivity() {

    private lateinit var homeApp: HomeApp
    private lateinit var homeAppVM : HomeAppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize logger for logging and displaying messages:
        logger = Logger(this)

        // Initialize the main app class to interact with the APIs:
        homeApp = HomeApp(baseContext, lifecycleScope, this)
        // Initialize the viewmodel representing the main app:
        homeAppVM = HomeAppViewModel(homeApp)

        // Call to make the app allocate the entire screen:
        enableEdgeToEdge()
        // Set the content of the screen to display the app:
        setContent { HomeAppView(homeAppVM) }
    }

    companion object {
        private lateinit var logger: Logger
        // Exposed utility functions for logging and displaying messages:
        fun showError(caller: Any, message: String) { logger.log(caller, message, Logger.LogLevel.ERROR) }
        fun showWarning(caller: Any, message: String) { logger.log(caller, message, Logger.LogLevel.WARNING) }
        fun showInfo(caller: Any, message: String) { logger.log(caller, message, Logger.LogLevel.INFO) }
        fun showDebug(caller: Any, message: String) { logger.log(caller, message, Logger.LogLevel.DEBUG) }
    }
}

/*  Logger - Utility class for logging and displaying messages
*   This helps us to communicate unexpected states on screen, as well as to record them appropriately
*   so when it comes you to report an issue we can make sure the states are captured in adb logs.
*  */
class Logger (val activity: ComponentActivity) {

    enum class LogLevel {
        ERROR,
        WARNING,
        INFO,
        DEBUG
    }

    fun log (caller: Any, message: String, level: LogLevel) {
        // Log the message in accordance to its level:
        when (level) {
            LogLevel.ERROR -> Log.e(caller.javaClass.name, message)
            LogLevel.WARNING -> Log.w(caller.javaClass.name, message)
            LogLevel.INFO -> Log.i(caller.javaClass.name, message)
            LogLevel.DEBUG -> Log.d(caller.javaClass.name, message)
        }
        // For levels above debug, Also show the message on screen:
        if (level != LogLevel.DEBUG)
            Toast.makeText(activity.baseContext, message, Toast.LENGTH_LONG).show()
    }
}

