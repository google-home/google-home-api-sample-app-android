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
import android.util.Log
import androidx.media3.datasource.okhttp.OkHttpDataSource
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.example.googlehomeapisampleapp.DefaultOkHttpClient
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient

/**
 * Class for handling authentication for the historical player.
 *
 * It provides two DataSource factories: one for fetching the manifest (which attaches the DUSI
 * token and saves the returned cookies) and one for fetching media chunks (which only attaches the
 * cached cookies).
 */
@Singleton
class CameraMediaAuth @Inject internal constructor(
    val dusiTokenProvider: DusiTokenProvider,
    @param:DefaultOkHttpClient private val baseOkHttpClient: OkHttpClient,
    @param:ApplicationContext private val context: Context,
) {

    val okHttpClient =
        baseOkHttpClient.newBuilder()
            .addInterceptor { chain ->
                val token = dusiTokenProvider.getDusiToken()
                if (token != null) {
                    // NOTE: sensitive token printing should be disabled unless necessary debug
                    // Log.i(TAG, "Attaching DUSI token to request: Bearer $token")
                }
                val request =
                    token?.let { chain.request().newBuilder().header("Authorization", "Bearer $it").build() }
                        ?: chain.request()
                chain.proceed(request)
            }
            .authenticator { _, response ->
                if (response.priorResponse != null) {
                    // We already retried and failed, give up to prevent infinite loops.
                    null
                } else {
                    Log.i(TAG, "Received 401, clearing cached token and retrying...")
                    dusiTokenProvider.clearCachedToken()
                    val newToken = dusiTokenProvider.getDusiToken()

                    if (newToken != null) {
                        response.request.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .build()
                    } else {
                        null
                    }
                }
            }
            .build()

    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(okHttpClient))
            }
            .build()
    }

    fun createDataSourceFactories(): Pair<OkHttpDataSource.Factory, OkHttpDataSource.Factory> {
        val cookieStore = ConcurrentHashMap<String, okhttp3.Cookie>()

        val cookieJar = object : okhttp3.CookieJar {
            override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                for (cookie in cookies) {
                    cookieStore["${cookie.domain}:${cookie.name}"] = cookie
                }
                Log.d(TAG, "Saved ${cookies.size} cookies for ${url.host}")
            }

            override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
                return cookieStore.values.filter { it.matches(url) }
            }
        }

        val manifestClient = okHttpClient.newBuilder()
            .cookieJar(cookieJar)
            .build()

        val chunkClient = baseOkHttpClient.newBuilder()
            .cookieJar(cookieJar)
            .build()

        return Pair(
            OkHttpDataSource.Factory(manifestClient),
            OkHttpDataSource.Factory(chunkClient)
        )
    }

    companion object {
        private const val TAG = "CameraMediaAuth"
    }
}
