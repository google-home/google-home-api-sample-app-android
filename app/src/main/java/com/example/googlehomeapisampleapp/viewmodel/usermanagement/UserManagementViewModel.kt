package com.example.googlehomeapisampleapp.viewmodel.usermanagement

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.home.ConsentScreenOptions
import com.google.home.ForcePermissionFlow
import com.google.home.HomeClient
import com.google.home.PermissionsResultStatus
import com.google.home.Structure
import com.google.home.google.StructureUserManagement
import com.google.home.google.StructureUserManagementTrait
import com.google.home.google.StructureUserManagementTrait.InvitationDetails
import com.google.home.google.StructureUserManagementTrait.UserMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val TAG = "UserManagementVM"

class UserManagementViewModel : ViewModel() {

    // States for the UI
    private val _generatedToken = MutableStateFlow<String?>(null)
    val generatedToken: StateFlow<String?> = _generatedToken

    private val _activeInvitations = MutableStateFlow<List<InvitationDetails>>(emptyList())
    val activeInvitations: StateFlow<List<InvitationDetails>> = _activeInvitations

    private val _structureMembers = MutableStateFlow<List<UserMetadata>>(emptyList())
    val structureMembers: StateFlow<List<UserMetadata>> = _structureMembers

    // =========================================================================
    // Core Functions referencing the external partner documentation
    // =========================================================================

    suspend fun createAndShareInvitation(structure: Structure): String? {
        return try {
            val trait = structure.trait(StructureUserManagement).first()

            // TODO: In SDK 17.1.0, createInvitation() does not accept parameters.
            // The UserRole enum exists, but the CreateInvitationRequest is parameterless.
            // Replace with the following once the SDK is updated:
            // val response = trait.createInvitation(intendedUserRole = StructureUserManagementTrait.UserRole.Admin)
            val response = trait.createInvitation()

            response.token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create invitation: ${e.message}")
            null
        }
    }

    suspend fun getAllInvitations(structure: Structure): List<InvitationDetails> {
        return try {
            val trait = structure.trait(StructureUserManagement).first()
            val invitations = trait.listInvitations()

            // TODO: In SDK 17.1.0, InvitationDetails only exposes invitationId, invitationToken,
            // inviterUserId, and creationTimestamp. The 'status' field is defined in the backend
            // Protobuf schema but stripped from this SDK version.
            // Once the SDK is updated to expose 'status', you can filter this list as shown below:
            // invitations.filter { it.status == StructureUserManagementTrait.InvitationStatus.Pending }
            @Suppress("UNCHECKED_CAST")
            invitations as? List<InvitationDetails> ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list invitations: ${e.message}")
            emptyList()
        }
    }

    suspend fun revokeInvitation(structure: Structure, invitationId: String): Boolean {
        return try {
            val trait = structure.trait(StructureUserManagement).first()
            trait.revokeInvitation(invitationId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to revoke invitation: ${e.message}")
            false
        }
    }

    suspend fun removeUserFromStructure(structure: Structure, userId: String): Boolean {
        return try {
            val trait = structure.trait(StructureUserManagement).first()
            trait.removeUser(structure.id.id, userId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove user: ${e.message}")
            false
        }
    }

    suspend fun getConsentedStructureMembers(structure: Structure): List<UserMetadata>? {
        return try {
            val trait = structure.trait(StructureUserManagement).first()
            trait.listUsersInStructure(structureId = structure.id.id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list structure members: ${e.message}")
            null
        }
    }

    suspend fun redeemInvitation(client: HomeClient, token: String): Boolean {
        return try {
            val options = ConsentScreenOptions(invitationToken = token)
            val result = client.requestPermissions(
                forcePermissionFlow = ForcePermissionFlow.FORCE_LAUNCH,
                consentScreenOptions = options
            )
            when (result.status) {
                PermissionsResultStatus.SUCCESS -> {
                    Log.i(TAG, "Successfully accepted invitation and joined the structure!")
                    true
                }
                PermissionsResultStatus.CANCELLED -> {
                    Log.i(TAG, "Invitation authorization cancelled by user.")
                    false
                }
                PermissionsResultStatus.ERROR -> {
                    Log.e(TAG, "Invitation redemption failed: ${result.errorMessage}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to redeem invitation: ${e.message}")
            false
        }
    }

    // =========================================================================
    // UI Interaction Wrappers
    // =========================================================================

    fun generateInvitationToken(structure: Structure) {
        viewModelScope.launch {
            _generatedToken.value = createAndShareInvitation(structure)
        }
    }

    fun fetchAllInvitations(structure: Structure) {
        viewModelScope.launch {
            _activeInvitations.value = getAllInvitations(structure)
        }
    }

    fun cancelInvitation(structure: Structure, invitationId: String) {
        viewModelScope.launch {
            val success = revokeInvitation(structure, invitationId)
            if (success) {
                // Refresh list if successful
                fetchAllInvitations(structure)
            }
        }
    }

    fun fetchMembers(structure: Structure) {
        viewModelScope.launch {
            _structureMembers.value = getConsentedStructureMembers(structure) ?: emptyList()
        }
    }

    fun removeMember(structure: Structure, userId: String) {
        viewModelScope.launch {
            val success = removeUserFromStructure(structure, userId)
            if (success) {
                // Refresh list if successful
                fetchMembers(structure)
            }
        }
    }

    fun acceptInvitation(client: HomeClient, token: String) {
        viewModelScope.launch {
            redeemInvitation(client, token)
        }
    }
}