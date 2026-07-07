package com.example.googlehomeapisampleapp.cloudlinking

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Coordinator to relay OAuth redirect intents from the Activity to the ViewModel.
 *
 * This class acts as a bridge, allowing the [MainActivity] to pass the returning deep link intent
 * containing the authorization code to the active [CloudLinkingViewModel] without tightly coupling
 * them.
 */
@Singleton
class CloudLinkingAuthCoordinator @Inject constructor() {
  // Flow emitting the redirect intents received from OAuth flow.
  private val _oauthRedirectFlow = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
  val oauthRedirectFlow = _oauthRedirectFlow.asSharedFlow()

  /** Called when an OAuth redirect intent is received by the activity. */
  fun onOAuthRedirect(intent: Intent) {
    _oauthRedirectFlow.tryEmit(intent)
  }
}
