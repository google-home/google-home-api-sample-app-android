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

package com.example.googlehomeapisampleapp.view.hubs

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.googlehomeapisampleapp.MainActivity
import com.example.googlehomeapisampleapp.viewmodel.hubs.HubDiscoveryViewModel
import com.google.home.google.HubManagementTrait
import java.net.InetAddress

const val TAG = "HubDiscoveryView"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubDiscoveryView(
  hubDiscoveryViewModel: HubDiscoveryViewModel,
  modifier: Modifier = Modifier,
) {
  // Collect state from the ViewModel using lifecycle-aware collection
  val discoveryStatus by hubDiscoveryViewModel.discoveryStatus.collectAsStateWithLifecycle()
  val discoveredHubs by hubDiscoveryViewModel.discoveredHubs.collectAsStateWithLifecycle()

  var ipPort by rememberSaveable { mutableStateOf("") }
  var code by rememberSaveable { mutableStateOf("") }

  fun parseHubFromManualEntry(ipPort: String, code: String): HubManagementTrait.Hub {
    val (ip, port) =
      try {
        val lastColonIndex = ipPort.lastIndexOf(':')
        if (lastColonIndex == -1) {
          throw IllegalArgumentException("Invalid IP:Port format: $ipPort. Expected IP:Port")
        }
        InetAddress.getByName(ipPort.take(lastColonIndex)).hostAddress!! to
          ipPort.substring(lastColonIndex + 1).toInt()
      } catch (e: Exception) {
        throw IllegalArgumentException("Invalid port number in $ipPort", e)
      }
    Log.d(TAG, "Parsed IP: $ip, port: $port")

    return HubManagementTrait.Hub(
      deviceName = null,
      productCode = null,
      ipAddressesList = listOf(ip),
      discoveryCode = code,
      runtimeState = null,
      port = port,
      mdnsHostName = null,
      setupServerPort = port
    )
  }

  // Use Card as the main container, matching the provided structure
  Card(
    modifier = modifier.width(400.dp).height(400.dp),
    shape = RoundedCornerShape(16.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
  ) {
    Column {
      // Top section (Header/Status)
      Surface(
        color = MaterialTheme.colorScheme.secondary,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            // Discovery Status Indicator
            when (discoveryStatus) {
              HubManagementTrait.DiscoveryStatus.DiscoveryStatusInProgress -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
              }

              HubManagementTrait.DiscoveryStatus.DiscoveryStatusFailureInternalError,
                -> {
                Icon(
                  imageVector = Icons.Filled.Clear,
                  contentDescription = "Error",
                  tint = MaterialTheme.colorScheme.error,
                )
              }

              else -> {
                // Restart Discovery Button
                IconButton(
                  onClick = {
                    hubDiscoveryViewModel.startDiscovery()
                    // Clear state on restart
                    ipPort = "" // Clear state on restart
                    code = ""
                  },
                  modifier = Modifier.size(24.dp),
                ) {
                  Icon(Icons.Filled.Refresh, contentDescription = "Restart Discovery")
                }
              }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Hub Discovery",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSecondary,
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Please select from a list of discovered hubs below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondary,
          )
        }
      }

      // LazyList section for discovered hubs
      LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).weight(1f)
      ) {
        items(discoveredHubs) { hub ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.fillMaxWidth().padding(vertical = 12.dp).clickable {
                hubDiscoveryViewModel.activateHub(hub)
              },
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = hub.deviceName ?: "?",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )

            if (hub.ipAddressesList.isNotEmpty()) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
              ) {
                // Assuming 'hub' has the necessary properties (ipAddressesList, port)
                val ipAddressesWithPort = hub.ipAddressesList.map { "$it:${hub.port}" }
                Text(
                  text = ipAddressesWithPort.joinToString(","),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }
          }
          HorizontalDivider()
        }

        // "No hubs found" message
        if (
          discoveredHubs.isEmpty() &&
          discoveryStatus != HubManagementTrait.DiscoveryStatus.DiscoveryStatusInProgress
        ) {
          item {
            Text(
              text = "No hubs found.",
              modifier = Modifier.padding(vertical = 12.dp),
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
      // Manual entry section
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        OutlinedTextField(
          value = ipPort,
          onValueChange = { ipPort = it },
          label = { Text(text = "IP:Port", style = MaterialTheme.typography.bodySmall) },
          modifier = Modifier.weight(2f).padding(end = 8.dp).testTag("ip_port_input"),
          shape = RoundedCornerShape(8.dp),
          textStyle = MaterialTheme.typography.bodySmall,
          colors =
            TextFieldDefaults.colors(
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
          singleLine = true,
        )

        OutlinedTextField(
          value = code,
          onValueChange = { code = it },
          label = { Text(text = "Code", style = MaterialTheme.typography.bodySmall) },
          modifier = Modifier.weight(1f).padding(end = 8.dp).testTag("code_input"),
          shape = RoundedCornerShape(8.dp),
          textStyle = MaterialTheme.typography.bodySmall,
          colors =
            TextFieldDefaults.colors(
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
          singleLine = true,
        )

        Button(
          onClick = {
            try {
              val hub = parseHubFromManualEntry(ipPort, code)
              hubDiscoveryViewModel.activateHub(hub)
            } catch (e: Exception) {
              e.message?.let { MainActivity.showError(this, it) }
              Log.e(TAG, "Failed to parse hub details: ${e.message}")
            }
          },
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(0.dp),
          modifier = Modifier.size(48.dp),
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Submit")
        }
      }
    }
  }
}