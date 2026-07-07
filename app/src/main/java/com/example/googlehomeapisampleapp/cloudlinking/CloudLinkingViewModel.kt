package com.example.googlehomeapisampleapp.cloudlinking

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.googlehomeapisampleapp.viewmodel.structures.StructureViewModel
import com.google.home.HomeClient
import com.google.home.HomeException
import com.google.home.Structure
import com.google.home.annotation.HomeExperimentalApi
import com.google.home.initiateCloudLink
import com.google.home.setAsCloudLinkDefault
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface CloudLinkingEvent {
  data class LaunchBrowser(val uri: Uri) : CloudLinkingEvent
}

/**
 * ViewModel for managing the Cloud Linking (PICToCAL) flow.
 *
 * It coordinates the OAuth sign-in flow via Chrome Custom Tabs, processes the returning deep link
 * redirect, executes the cloud linking handshake with the Google Home SDK, and triggers device
 * synchronization.
 */
@HiltViewModel
class CloudLinkingViewModel
@Inject
constructor(
  private val config: CloudLinkingConfig,
  private val authCoordinator: CloudLinkingAuthCoordinator,
  private val currentStructureRepository: CurrentStructureRepository,
  private val homeClient: HomeClient,
  private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val _uiState = MutableStateFlow<CloudLinkingState>(CloudLinkingState.Idle)
  val uiState: StateFlow<CloudLinkingState> = _uiState.asStateFlow()

  private val _targetStructure = MutableStateFlow<StructureViewModel?>(null)
  val targetStructure: StateFlow<StructureViewModel?> = _targetStructure.asStateFlow()

  private val _structures = MutableStateFlow<List<StructureViewModel>>(emptyList())
  val structures: StateFlow<List<StructureViewModel>> = _structures.asStateFlow()

  private val _events = MutableSharedFlow<CloudLinkingEvent>()
  val events: SharedFlow<CloudLinkingEvent> = _events.asSharedFlow()

  init {
    // Subscribe to auth redirect flow
    viewModelScope.launch {
      authCoordinator.oauthRedirectFlow.collect { intent -> handleOAuthRedirect(intent) }
    }

    // Subscribe to active structure from repository
    viewModelScope.launch {
      currentStructureRepository.selectedStructureVM.collect { repoStructure ->
        // Only override target if it's not been explicitly changed by user
        if (_targetStructure.value == null) {
          _targetStructure.value = repoStructure
        }
      }
    }

    // Fetch all structures for override dropdown
    viewModelScope.launch {
      homeClient.structures().collectLatest { structureSet ->
        _structures.value = structureSet.map { StructureViewModel(it) }
      }
    }
  }

  fun setTargetStructure(structure: StructureViewModel) {
    _targetStructure.value = structure
  }

  /**
   * Initiates the OAuth flow by generating the authorization URL and emitting a
   * [CloudLinkingEvent.LaunchBrowser] event.
   *
   * Note: This browser-based flow is primarily for the Google Home Playground simulation. In a
   * production partner app, you would typically retrieve the authorization code silently from your
   * own backend and pass it directly to [linkAccount], bypassing this browser flow.
   *
   * Generates a random CSRF state token, stores it in [SavedStateHandle] for validation upon
   * return, and emits the launch event.
   */
  fun startOAuthFlow() {
    if (_targetStructure.value == null) {
      Log.e(TAG, "Cannot start OAuth flow: No target structure selected.")
      return
    }
    _uiState.value = CloudLinkingState.AuthCodeRequested
    // Generate secure random state token to prevent CSRF attacks
    val stateToken = UUID.randomUUID().toString()
    savedStateHandle[KEY_STATE_TOKEN] = stateToken

    val uri =
      Uri.parse(config.authEndpoint)
        .buildUpon()
        .appendQueryParameter("client_id", config.clientId)
        .appendQueryParameter("redirect_uri", config.redirectUri)
        .appendQueryParameter("scope", config.scopes)
        .appendQueryParameter("state", stateToken)
        .appendQueryParameter("response_type", "code")
        .build()

    viewModelScope.launch {
      _events.emit(CloudLinkingEvent.LaunchBrowser(uri))
    }
  }

  /** Resets the UI state back to [CloudLinkingState.Idle]. */
  fun resetState() {
    _uiState.value = CloudLinkingState.Idle
  }

  /**
   * Submits the manually entered auth code (or URL containing it) to initiate linking.
   *
   * TODO: Revert/remove manual auth code submission method in the future.
   */
  fun submitManualAuthCode(input: String) {
    linkAccount(extractAuthCode(input))
  }

  /**
   * Processes the returning deep link intent from the OAuth flow.
   *
   * Note: This is only needed to handle the redirect from the browser-based flow used in the
   * playground setup.
   *
   * Validates the CSRF state token against the saved token, extracts the authorization code, and
   * initiates the account linking process.
   */
  private fun handleOAuthRedirect(intent: Intent) {
    val uri = intent.data ?: return
    if (!uri.toString().startsWith(config.redirectUri)) return

    val state = uri.getQueryParameter("state")
    val savedState: String? = savedStateHandle[KEY_STATE_TOKEN]

    // Verify state token to prevent CSRF
    if (state == null || state != savedState) {
      Log.e(TAG, "CSRF verification failed or OAuth Canceled. State mismatch.")
      _uiState.value = CloudLinkingState.OAuthCanceled
      return
    }

    val authCode = uri.getQueryParameter("code")
    if (authCode == null) {
      Log.e(TAG, "OAuth callback did not contain an authorization code.")
      _uiState.value = CloudLinkingState.OAuthCanceled
      return
    }

    linkAccount(authCode)
  }

  /** Exchanges the authorization code for tokens and links the account to the structure. */
  private fun linkAccount(authCode: String) {
    val structureVM =
      _targetStructure.value
        ?: run {
          Log.e(TAG, "No target structure available to initiate link.")
          _uiState.value = CloudLinkingState.LinkingFailed
          return
        }

    viewModelScope.launch {
      _uiState.value = CloudLinkingState.LinkingInProgress
      try {
        // Exchange code and link via SDK
        structureVM.structure.initiateCloudLink(authCode)
        finalizeLink(structureVM.structure)
      } catch (e: HomeException) {
        // Handle specific SDK errors to provide better user feedback
        when (e.error.code) {
          HomeException.Codes.ALREADY_EXISTS -> {
            // Safe recovery: if already linked, just ensure it is set as default
            Log.d(TAG, "Account is already linked. Adopting as default.")
            finalizeLink(structureVM.structure)
          }
          HomeException.Codes.DEADLINE_EXCEEDED,
          HomeException.Codes.UNAVAILABLE -> {
            Log.e(TAG, "Network error during linking handshake", e)
            _uiState.value = CloudLinkingState.LinkingFailed
          }
          HomeException.Codes.UNAUTHENTICATED,
          HomeException.Codes.PERMISSION_DENIED -> {
            Log.e(TAG, "Auth code rejected or expired", e)
            _uiState.value = CloudLinkingState.OAuthCanceled
          }
          else -> {
            Log.e(TAG, "Unexpected error during linking handshake", e)
            _uiState.value = CloudLinkingState.LinkingFailed
          }
        }
      } catch (e: Exception) {
        // Catch-all for unexpected failures (e.g. coroutine cancellation, runtime issues)
        Log.e(TAG, "Fatal error during linking handshake", e)
        _uiState.value = CloudLinkingState.LinkingFailed
      }
    }
  }

  /** Finalizes the link by setting this cloud link as default and updating UI state. */
  private suspend fun finalizeLink(structure: Structure) {
    structure.setAsCloudLinkDefault()
    _uiState.value = CloudLinkingState.Success
  }

  /**
   * Triggers a sync of linked devices from the cloud.
   *
   * This imports any new devices associated with the linked account into the structure.
   */
  @OptIn(HomeExperimentalApi::class)
  fun syncLinkedDevices() {
    viewModelScope.launch {
      _uiState.value = CloudLinkingState.LinkingInProgress
      try {
        homeClient.syncLinkedDevices()
        _uiState.value = CloudLinkingState.Success
      } catch (e: Exception) {
        // Catch-all for unexpected failures during sync API call
        Log.e(TAG, "Failed to sync linked devices", e)
        _uiState.value = CloudLinkingState.SyncFailed
      }
    }
  }

  companion object {
    const val TAG = "CloudLinkingViewModel"
    const val KEY_STATE_TOKEN = "state_token"

    /**
     * Extracts the authorization code from the input string.
     *
     * The input can be the raw authorization code or the full redirect URL containing the code as a
     * query parameter.
     *
     * @param input The raw input string.
     * @return The extracted authorization code, or the original trimmed input if "code=" is not
     *   present.
     */
    fun extractAuthCode(input: String): String {
      val cleanedInput = input.trim()
      if (cleanedInput.isEmpty()) return ""
      return if (cleanedInput.contains("code=")) {
        try {
          Uri.parse(cleanedInput).getQueryParameter("code")
        } catch (e: Exception) {
          null
        } ?: cleanedInput.substringAfter("code=").substringBefore("&")
      } else {
        cleanedInput
      }
    }
  }
}
