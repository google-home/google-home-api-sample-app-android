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

package com.example.googlehomeapisampleapp.history

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.google.home.HomeConfig
import com.example.googlehomeapisampleapp.scope

@HiltViewModel
class HistoryPlayerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    val cameraMediaAuth: CameraMediaAuth,
    val homeConfig: HomeConfig
) : ViewModel() {

    private val _isAuthGranted = MutableStateFlow(false)
    val isAuthGranted: StateFlow<Boolean> = _isAuthGranted

    private val _pendingAuthIntent = MutableStateFlow<android.app.PendingIntent?>(null)
    val pendingAuthIntent: StateFlow<android.app.PendingIntent?> = _pendingAuthIntent

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError

    fun checkAndRequestAuth() {
        _authError.value = null
        viewModelScope.launch {
            try {
                Log.i(TAG, "Starting OAuth authorization check...")
                val authorizationClient = Identity.getAuthorizationClient(context)
                val scope = homeConfig.homePlatformScope.scope
                val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(scope))).build()
                val result = authorizationClient.authorize(request).await()
                Log.i(TAG, "Authorize result: hasResolution=${result.hasResolution()}, hasToken=${result.accessToken != null}")
                
                if (result.accessToken != null) {
                    Log.i(TAG, "OAuth scope already granted or no resolution required.")
                    cameraMediaAuth.dusiTokenProvider.setCachedToken(result.accessToken!!)
                    _isAuthGranted.value = true
                } else if (result.hasResolution() && result.pendingIntent != null) {
                    Log.i(TAG, "Requesting OAuth resolution...")
                    _pendingAuthIntent.value = result.pendingIntent
                } else {
                    Log.e(TAG, "OAuth failed, no resolution and no token.")
                    _authError.value = "Failed to obtain authorization token."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request OAuth scope", e)
                _authError.value = "Authentication error: ${e.localizedMessage ?: "Unknown"}"
            }
        }
    }

    fun handleAuthResult(data: Intent?) {
        try {
            if (data != null) {
                val authResult = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
                Log.i(TAG, "OAuth scope granted successfully. Token present: ${authResult.accessToken != null}")
                if (authResult.accessToken != null) {
                    Log.i(TAG, "Caching OAuth token in DusiTokenProvider")
                    cameraMediaAuth.dusiTokenProvider.setCachedToken(authResult.accessToken!!)
                } else {
                    Log.w(TAG, "authResult.accessToken was null!")
                    _authError.value = "Access token was unexpectedly null."
                }
            } else {
                Log.w(TAG, "Intent result.data was null! Cannot extract auth result.")
                _authError.value = "Authorization result data was missing."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get auth result from intent", e)
            _authError.value = "Failed to process authorization result."
        }
        
        if (authError.value == null) {
            _isAuthGranted.value = true
        }
        _pendingAuthIntent.value = null
    }

    fun onAuthFailed() {
        Log.e(TAG, "OAuth scope grant failed or was cancelled.")
        _authError.value = "Authentication was cancelled or failed."
        _pendingAuthIntent.value = null
    }

    companion object {
        private const val TAG = "HistoryPlayerViewModel"
    }
}
