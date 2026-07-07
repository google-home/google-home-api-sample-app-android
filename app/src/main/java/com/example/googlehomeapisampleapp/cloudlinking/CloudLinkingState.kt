package com.example.googlehomeapisampleapp.cloudlinking

/** Represents the states of the Cloud Linking (PICToCAL) flow. */
sealed interface CloudLinkingState {
  // Flow is not started.
  data object Idle : CloudLinkingState

  // User has initiated OAuth flow and we are waiting for redirect.
  data object AuthCodeRequested : CloudLinkingState

  // OAuth flow was canceled or failed validation (e.g. CSRF mismatch).
  data object OAuthCanceled : CloudLinkingState

  // Exchanging auth code for tokens and linking account.
  data object LinkingInProgress : CloudLinkingState

  /** Account linking was successful. */
  data object Success : CloudLinkingState

  // Linking handshake failed (network timeout, server error, etc.)
  data object LinkingFailed : CloudLinkingState

  // Failed to sync devices after linking.
  data object SyncFailed : CloudLinkingState
}
