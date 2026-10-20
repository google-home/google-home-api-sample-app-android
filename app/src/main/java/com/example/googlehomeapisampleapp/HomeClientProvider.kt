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

package com.example.googlehomeapisampleapp

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.google.home.FactoryRegistry
import com.google.home.Home
import com.google.home.HomeClient
import com.google.home.HomeConfig
import com.google.home.UserAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.measureTimedValue


/**
 * Provides a singleton instance of [HomeClient]. This class is responsible for creating and managing
 * the [HomeClient] instance, including handling account switching.
 *
 * @property applicationContext The application context.
 * @property homeConfig The configuration for the [HomeClient].
 */
@Singleton
class HomeClientProvider @Inject constructor(
  @ApplicationContext private val applicationContext: Context,
  private val homeConfig: HomeConfig,
  private val factoryRegistry: FactoryRegistry,
) {

  private var homeClient: HomeClient? = null

  companion object {
    private const val TAG = "HomeClientProvider"
  }

  /**
   * Returns the current [HomeClient] instance. If the client has not been initialized, it will be
   * created.
   *
   * @return The [HomeClient] instance.
   */
  fun getClient(): HomeClient {
    synchronized(this) {
      if (homeClient == null) {
        Log.d(TAG, "create a new HomeClient instance since homeClient is null")
        Log.d(TAG, "getClient: homeConfig.homePlatformScope: ${homeConfig.homePlatformScope}")
        homeClient = createHomeClient("", homeConfig)
      }
      return homeClient!!
    }
  }

  /**
   * Switches the current account and re-initializes the [HomeClient].
   *
   * @param userId The ID of the new user account.
   */
  fun switchAccount(userId: String, serverClientId: String) {
    Log.d(TAG, "applicationContext.packageName: ${applicationContext.packageName}")
    Log.d(TAG, "serverClientId: $serverClientId")

    val config = HomeConfig(
      coroutineContext = Dispatchers.IO,
      factoryRegistry = factoryRegistry,
      serverClientId = serverClientId,
      // If you are not using advanced camera features, you should continue to use the original
      // scope by changing this to HOME_PLATFORM_SCOPE_VERSION_1.
      homePlatformScope = HomeConfig.HomePlatformScope.HOME_PLATFORM_SCOPE_VERSION_2
    )
    Log.d(TAG, "switchAccount: config.homePlatformScope: ${config.homePlatformScope}")
    Log.i(TAG, "AccountManager switching account to $userId")
    homeClient = createHomeClient(userId, config)
  }

  private fun createHomeClient(userId: String, config: HomeConfig): HomeClient {
    val client = measureTimedValue {
      if (userId.isEmpty()) {
        Log.i(TAG, "createHomeClient without account")
        Home.getClient(applicationContext, homeConfig = config)
      } else {
        Log.i(TAG, "createHomeClient with account $userId")
        Home.getClient(
          applicationContext,
          account = lazy {
            UserAccount.GoogleAccount(
              account = Account(
                userId,
                "com.google"
              )
            )
          },
          homeConfig = config,
        )
      }
    }.also {
      Log.i(TAG, "HomeSDK construction took ${it.duration.inWholeMilliseconds} ms.")
    }
    return checkNotNull(client.value) { "HomeClient is null. Ensure createClient() succeeded." }  }
}
