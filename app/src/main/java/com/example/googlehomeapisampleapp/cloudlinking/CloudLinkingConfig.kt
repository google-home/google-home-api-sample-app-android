package com.example.googlehomeapisampleapp.cloudlinking

/**
 * Configuration parameters for the Cloud Linking (PICToCAL) flow.
 *
 * @property authEndpoint The authorization endpoint URL for OAuth. Defaults to the GHP EAP
 *   playground.
 * @property clientId The OAuth client ID.
 * @property redirectUri The redirect URI registered for the app.
 * @property scopes The requested OAuth scopes.
 */
data class CloudLinkingConfig(
  // TODO: Revert authEndpoint to production/EAP endpoint
  // (https://home-playground-eap.withgoogle.com/auth) in the future.
  val authEndpoint: String = "https://oauth-dot-smarthome-playground-staging.appspot.com",
  val clientId: String,
  // TODO: Revert redirectUri back to "googlehomeapisampleapp://oauth-callback" in the future.
  val redirectUri: String =
    "https://oauth-redirect.googleusercontent.com/r/smarthome-playground-staging",
  val scopes: String = "smarthome:control offline_access",
)
