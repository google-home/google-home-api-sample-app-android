package com.example.googlehomeapisampleapp.cloudlinking

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Composable representing the Cloud Linking Bottom Sheet.
 *
 * This sheet guides the user through the PICToCAL integration flow, showing different UI states
 * based on the current [CloudLinkingState]:
 * - [CloudLinkingState.Idle]: Initial consent and structure selection screen.
 * - [CloudLinkingState.AuthCodeRequested]: Waiting for the user to complete OAuth in browser.
 * - [CloudLinkingState.LinkingInProgress]: Handshake with Google Cloud.
 * - [CloudLinkingState.Success]: Final success screen prompting device import.
 * - [CloudLinkingState.OAuthCanceled]: Error screen if OAuth failed or was canceled.
 * - [CloudLinkingState.SyncFailed]: Error screen if device import failed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudLinkingBottomSheet(viewModel: CloudLinkingViewModel, onDismissRequest: () -> Unit) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val targetStructure by viewModel.targetStructure.collectAsStateWithLifecycle()
  val structures by viewModel.structures.collectAsStateWithLifecycle()
  val context = LocalContext.current

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is CloudLinkingEvent.LaunchBrowser -> {
          try {
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(context, event.uri)
          } catch (e: Exception) {
            Log.d(
              "CloudLinkingBottomSheet",
              "Chrome Custom Tabs unavailable, falling back to Intent.ACTION_VIEW"
            )
            val browserIntent =
              Intent(Intent.ACTION_VIEW, event.uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
            context.startActivity(browserIntent)
          }
        }
      }
    }
  }

  ModalBottomSheet(
    onDismissRequest = {
      viewModel.resetState()
      onDismissRequest()
    }
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      when (uiState) {
        // State: Idle - Consent and Target Home selection
        is CloudLinkingState.Idle -> {
          Text(
            text = "Connect to Google Home",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { contentDescription = "Connect to Google Home Title" },
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text =
              "Link your account to seamlessly integrate your smart devices into your Google Home ecosystem.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier =
              Modifier.semantics { contentDescription = "Connect to Google Home Description" },
          )
          Spacer(modifier = Modifier.height(24.dp))

          // Target Structure Selector
          if (structures.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
              expanded = expanded,
              onExpandedChange = { expanded = !expanded },
            ) {
              OutlinedTextField(
                readOnly = true,
                value = targetStructure?.name ?: "Select Home",
                onValueChange = {},
                label = { Text("Target Home") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier =
                  Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .semantics { contentDescription = "Target Structure Selector" },
              )
              ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                structures.forEach { structure ->
                  DropdownMenuItem(
                    text = { Text(structure.name) },
                    onClick = {
                      viewModel.setTargetStructure(structure)
                      expanded = false
                    },
                  )
                }
              }
            }
            Spacer(modifier = Modifier.height(24.dp))
          }

          Button(
            onClick = { viewModel.startOAuthFlow() },
            modifier =
              Modifier.fillMaxWidth().semantics { contentDescription = "Agree and Connect Button" },
          ) {
            Text("Agree & Connect")
          }
        }

        // State: AuthCodeRequested - Waiting for browser OAuth redirection
        CloudLinkingState.AuthCodeRequested -> {
          CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = "Waiting for Authentication" }
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text =
              "Complete the sign-in flow in your browser, or paste the auth code from the URL bar below:",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = "Auth in progress description" },
          )
          Spacer(modifier = Modifier.height(16.dp))
          // TODO: Revert/remove manual staging code input field in the future.
          var manualCode by remember { mutableStateOf("") }
          OutlinedTextField(
            value = manualCode,
            onValueChange = { manualCode = it },
            label = { Text("Auth Code (?code=...)") },
            singleLine = true,
            modifier =
              Modifier.fillMaxWidth().semantics { contentDescription = "Manual Auth Code Input" },
          )
          val parsedToken =
            remember(manualCode) { CloudLinkingViewModel.extractAuthCode(manualCode) }
          if (parsedToken.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
              colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(
                  "Parsed OAuth Token:",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(parsedToken, style = MaterialTheme.typography.bodySmall)
              }
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = { viewModel.submitManualAuthCode(manualCode) },
            enabled = manualCode.isNotBlank(),
            modifier =
              Modifier.fillMaxWidth().semantics { contentDescription = "Submit Auth Code Button" },
          ) {
            Text("Link with Auth Code")
          }
        }

        // State: LinkingInProgress - Actively calling initiateCloudLink SDK API
        CloudLinkingState.LinkingInProgress -> {
          CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = "Linking in progress" }
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "Connecting to Google Cloud...",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { contentDescription = "Connecting description" },
          )
        }

        // State: Success - Account linked successfully, prompt device import
        is CloudLinkingState.Success -> {
          Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Success Icon",
            tint = Color.Green,
            modifier = Modifier.size(64.dp),
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "Google Home is Connected!",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { contentDescription = "Success Title" },
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "Your accounts are successfully linked.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = "Success Description" },
          )
          Spacer(modifier = Modifier.height(24.dp))

          Button(
            onClick = { viewModel.syncLinkedDevices() },
            modifier =
              Modifier.fillMaxWidth().semantics { contentDescription = "Import Devices Button" },
          ) {
            Text("Import Devices")
          }
        }

        // State: OAuthCanceled - Auth failed or user canceled
        CloudLinkingState.OAuthCanceled -> {
          Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = "Error Icon",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp),
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "Sign-In Canceled",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { contentDescription = "Canceled Title" },
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "The connection flow was canceled or expired. Please try again.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = "Canceled Description" },
          )
          Spacer(modifier = Modifier.height(24.dp))
          Button(
            onClick = { viewModel.startOAuthFlow() },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Try Again Button" },
          ) {
            Text("Try Again")
          }
        }

        // State: LinkingFailed - Handshake failed due to network or server issues
        CloudLinkingState.LinkingFailed -> {
          Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = "Error Icon",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp),
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "Linking Failed",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { contentDescription = "Linking Failed Title" },
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "A network or system error occurred during linking. Please try again.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = "Linking Failed Description" },
          )
          Spacer(modifier = Modifier.height(24.dp))
          Button(
            onClick = { viewModel.startOAuthFlow() },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Try Again Button" },
          ) {
            Text("Try Again")
          }
        }

        // State: SyncFailed - Linked successfully, but device synchronization failed
        CloudLinkingState.SyncFailed -> {
          Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = "Error Icon",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp),
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "Device Import Failed",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { contentDescription = "Sync Failed Title" },
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "Your accounts are successfully linked, but pulling your latest devices failed.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = "Sync Failed Description" },
          )
          Spacer(modifier = Modifier.height(24.dp))

          Button(
            onClick = { viewModel.syncLinkedDevices() },
            modifier =
              Modifier.fillMaxWidth().semantics { contentDescription = "Retry Sync Button" },
          ) {
            Text("Retry Import")
          }
        }
      }
      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}
