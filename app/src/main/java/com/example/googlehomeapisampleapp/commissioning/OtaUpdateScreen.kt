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

package com.example.googlehomeapisampleapp.commissioning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.googlehomeapisampleapp.viewmodel.ota.OtaUiState
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Extracts a clean version string from raw software version text.
 * Trims long build fingerprints like "capriicamera-user 1 OPENMASTER 6.3.2600.845616249 prod-keys"
 * into clean version numbers like "6.3.2600.845616249".
 */
fun formatVersionString(rawVersion: String?): String? {
    if (rawVersion.isNullOrBlank()) return null
    val match = Regex("""\b\d+\.\d+(\.\d+)*\b""").find(rawVersion)
    return match?.value ?: rawVersion
}

/**
 * Formats the current local device time in English format with seconds precision.
 */
private fun formatCurrentLocalTime(): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm:ss a", Locale.ENGLISH)
    return ZonedDateTime.now().format(formatter)
}

/**
 * OTA information screen shown after camera commissioning.
 * Renders live update status and software version.
 *
 * @param deviceName Name of the commissioned device
 * @param otaUiState Current live OTA state of the device
 * @param onComplete Callback when user dismisses the screen
 * @param paddingValues Extra padding values
 * @param modifier Custom modifier
 */
@Composable
fun OtaUpdateScreen(
    deviceName: String = "Camera",
    otaUiState: OtaUiState = OtaUiState.Loading,
    onComplete: () -> Unit,
    paddingValues: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    var currentTimeString by remember { mutableStateOf(formatCurrentLocalTime()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeString = formatCurrentLocalTime()
            delay(1000)
        }
    }

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Main content area
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Local current time display in English
                Text(
                    text = currentTimeString,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Device name
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Status Icon
                val iconVector = when (otaUiState) {
                    is OtaUiState.UpToDate -> Icons.Default.CheckCircle
                    is OtaUiState.Failed -> Icons.Default.Error
                    else -> Icons.Default.CloudDownload
                }
                val iconTint = when (otaUiState) {
                    is OtaUiState.UpToDate -> MaterialTheme.colorScheme.primary
                    is OtaUiState.Failed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }

                Icon(
                    imageVector = iconVector,
                    contentDescription = "Software Update Status",
                    modifier = Modifier.size(96.dp),
                    tint = iconTint
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Title
                Text(
                    text = "OTA Software Update",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Styled Status Surface / Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (otaUiState) {
                            is OtaUiState.Loading, is OtaUiState.Checking -> {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "Checking for updates...", style = MaterialTheme.typography.bodyMedium)
                            }
                            is OtaUiState.Downloading -> {
                                val version = formatVersionString(otaUiState.currentVersionString)
                                version?.let {
                                    Text(text = "Target Version: v$it", style = MaterialTheme.typography.labelMedium)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                if (otaUiState.progressPercent != null) {
                                    LinearProgressIndicator(
                                        progress = { otaUiState.progressPercent / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = "Downloading update (${otaUiState.progressPercent}%)", style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = "Downloading update...", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            is OtaUiState.Installing -> {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "Installing update & restarting device...", style = MaterialTheme.typography.bodyMedium)
                            }
                            is OtaUiState.Delayed -> {
                                Text(
                                    text = "Delayed: ${otaUiState.reason}",
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            is OtaUiState.Failed -> {
                                Text(
                                    text = "Failed. Device restored to previous version.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            is OtaUiState.UpToDate -> {
                                val rawVer = otaUiState.currentVersionString
                                val formattedVer = formatVersionString(rawVer)
                                Text(
                                    text = "Device is Up to Date",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!formattedVer.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Version $formattedVer",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Description
                Text(
                    text = "During setup, your device automatically receives software updates if available. Current update status is displayed above.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4f
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Info link
                Text(
                    text = "For more information, visit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextButton(
                    onClick = {
                        uriHandler.openUri("https://developers.home.google.com")
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("https://developers.home.google.com", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Done button at bottom
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}