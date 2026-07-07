package com.example.googlehomeapisampleapp.view.usermanagement

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel
import com.example.googlehomeapisampleapp.viewmodel.usermanagement.UserManagementViewModel

/**
 * Composable screen demonstrating User Management flows (Invitations & Members).
 *
 * @param viewModel The view model handling the logic.
 * @param homeAppVM The global app view model.
 * @param onBack Callback triggered when the user wants to return to the previous screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementView(
    viewModel: UserManagementViewModel,
    homeAppVM: HomeAppViewModel,
    onBack: () -> Unit
) {
    val selectedStructureVM = homeAppVM.selectedStructureVM.collectAsState().value
    val structure = selectedStructureVM?.structure

    val generatedToken by viewModel.generatedToken.collectAsState()
    val activeInvitations by viewModel.activeInvitations.collectAsState()
    val structureMembers by viewModel.structureMembers.collectAsState()

    var inputToken by remember { mutableStateOf("") }

    // Tools for Copy-to-Clipboard functionality
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Intercepts the system back button or swipe-to-go-back gesture.
    BackHandler {
        onBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // --- Header with UI Back Button ---
        TopAppBar(
            title = { Text("User Management") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (structure == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Loading structure...")
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- 1. Accept Invitation Flow ---
            item {
                Text("Accept Invitation", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = inputToken,
                    onValueChange = { inputToken = it },
                    label = { Text("Invitation Token") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        homeAppVM.homeApp.homeClient?.let { client ->
                            if (inputToken.isNotBlank()) {
                                viewModel.acceptInvitation(client, inputToken)
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Redeem Invitation")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            // --- 2. Structure Management (Create) ---
            item {
                Text("Structure Management", style = MaterialTheme.typography.titleLarge)
                Button(onClick = { viewModel.generateInvitationToken(structure) }) {
                    Text("Create Invitation Token")
                }

                // Display Generated Token with Copy Functionality
                if (!generatedToken.isNullOrBlank()) {
                    SelectionContainer {
                        Text(
                            text = "Generated Token:\n$generatedToken\n\n(Tap here to copy)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary, // Highlight to indicate interactability
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable {
                                    generatedToken?.let { token ->
                                        clipboardManager.setText(AnnotatedString(token))
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            // --- 3. List and Revoke Invitations ---
            item {
                Button(onClick = { viewModel.fetchAllInvitations(structure) }) {
                    Text("List All Invitations")
                }
            }
            items(activeInvitations) { invitation ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Invite ID: ${invitation.invitationId?.take(8)}...")

                        // TODO: In SDK 17.1.0, the 'status' field is unavailable.
                        // Once updated, display the actual status enum instead of creationTimestamp.
                        val timestampText = invitation.creationTimestamp?.toString() ?: "Unknown time"
                        Text(
                            text = "Created: $timestampText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Button(onClick = {
                        invitation.invitationId?.let { id ->
                            viewModel.cancelInvitation(structure, id)
                        }
                    }) {
                        Text("Revoke")
                    }
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }

            // --- 4. List and Remove Members ---
            item {
                Button(onClick = { viewModel.fetchMembers(structure) }) {
                    Text("List Structure Members")
                }
            }
            items(structureMembers) { member ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = member.Name ?: "Unknown Name")
                        Text(text = member.email ?: "No Email", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = {
                        member.userId?.let { id ->
                            viewModel.removeMember(structure, id)
                        }
                    }) {
                        Text("Remove")
                    }
                }
            }

            item { Spacer(modifier = Modifier.padding(16.dp)) }
        }
    }
}