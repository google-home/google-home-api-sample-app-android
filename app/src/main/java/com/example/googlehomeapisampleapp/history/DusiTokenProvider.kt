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
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import com.google.home.HomeConfig

import com.example.googlehomeapisampleapp.scope

import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

interface DusiTokenProvider {
    fun getDusiToken(): String?
    fun setCachedToken(token: String)
    fun clearCachedToken()
}

@Singleton
class GhpCameraDusiTokenProvider @Inject internal constructor(
    @param:ApplicationContext private val context: Context,
    private val homeConfig: HomeConfig,
) : DusiTokenProvider {

    @Volatile
    private var cachedToken: String? = null

    @Synchronized
    override fun setCachedToken(token: String) {
        Log.i(TAG, "setCachedToken called with token (length: ${token.length})")
        cachedToken = token
    }

    @Synchronized
    override fun clearCachedToken() {
        Log.i(TAG, "clearCachedToken called")
        cachedToken = null
    }

    override fun getDusiToken(): String? {
        val currentToken = cachedToken
        if (currentToken != null) {
            Log.i(TAG, "getDusiToken returning cached token (length: ${currentToken.length})")
            return currentToken
        }

        synchronized(this) {
            val tokenInSync = cachedToken
            if (tokenInSync != null) {
                Log.i(TAG, "getDusiToken returning cached token (length: ${tokenInSync.length})")
                return tokenInSync
            }

            Log.i(TAG, "getDusiToken: No cached token, calling Identity authorizationClient.authorize...")
            val authorizationClient = Identity.getAuthorizationClient(context)
            // Use the configured scope from HomeConfig
            val scope = homeConfig.homePlatformScope.scope
            val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(scope))).build()

            // Note: Tasks.await() is a blocking call. This is safe here because getDusiToken()
            // is called from an OkHttp interceptor which runs on a background thread.
            // Our double-checked locking ensures that concurrent requests don't all block at once.
            // If the token requires user consent/resolution, result.accessToken will be null
            // and this background request will simply fail. This is expected and correctly
            // handled by the UI check in HistoryPlayerViewModel.
            return try {
                val result = Tasks.await(authorizationClient.authorize(request), 5, TimeUnit.SECONDS)
                if (result.accessToken == null) {
                    Log.w(TAG, "DUSI token is null. hasResolution=${result.hasResolution()}")
                } else {
                    cachedToken = result.accessToken
                    // NOTE: sensitive token printing should be disabled unless necessary debug
                    // Log.i(TAG, "Successfully retrieved DUSI token: ${result.accessToken}")
                }
                result.accessToken
            } catch (e: Exception) {
                Log.e(TAG, "Exception when requesting DUSI token", e)
                null
            }
        }
    }

    companion object {
        private const val TAG = "GhpCameraDusiTokenProvider"
    }
}
