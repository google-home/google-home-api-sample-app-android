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

package com.example.googlehomeapisampleapp.camera.livestreamplayer

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule
import javax.inject.Inject
import javax.inject.Singleton

/** Provides a singleton instance of [PeerConnectionFactory]. */
@Singleton
class PeerConnectionFactoryProvider
@Inject
internal constructor(
  @param:ApplicationContext private val context: Context,
) {
  private val eglBase = EglBase.create()

  private var peerConnectionFactory: PeerConnectionFactory? = null
  private var audioDeviceModule: JavaAudioDeviceModule? = null

  fun initializeFactory(microphonePermissionGranted: Boolean) {
    if (peerConnectionFactory != null) {
      return
    }
    Log.d(
      TAG,
      "Initializing PeerConnectionFactory. Microphone permission granted: $microphonePermissionGranted"
    )
    PeerConnectionFactory.initialize(
      PeerConnectionFactory.InitializationOptions.builder(context)
        .setNativeLibraryName(NATIVE_LIBRARY_NAME)
        .createInitializationOptions()
    )

    val videoDecoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
    val videoEncoderFactory =
      DefaultVideoEncoderFactory(
        eglBase.eglBaseContext,
        /* enableIntelVp8Encoder= */ false,
        /* enableH264HighProfile= */ true,
      )

    val factoryBuilder = PeerConnectionFactory.builder()
      .setVideoDecoderFactory(videoDecoderFactory)
      .setVideoEncoderFactory(videoEncoderFactory)

    if (microphonePermissionGranted) {
      Log.d(TAG, "Microphone permission is granted. Initializing audio device module.")
      audioDeviceModule = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
      factoryBuilder.setAudioDeviceModule(audioDeviceModule)
    } else {
      Log.w(TAG, "Microphone permission not granted. Skipping audio device module initialization.")
    }

    peerConnectionFactory = factoryBuilder.createPeerConnectionFactory()
  }

  /** Returns the singleton instance of [PeerConnectionFactory]. */
  fun getPeerConnectionFactory(): PeerConnectionFactory {
    return checkNotNull(peerConnectionFactory) { "PeerConnectionFactory not initialized" }
  }

  /** Returns the singleton instance of [EglBase.Context]. */
  fun getEglBaseContext(): EglBase.Context {
    return eglBase.eglBaseContext
  }

  /** Returns the singleton instance of [JavaAudioDeviceModule]. */
  fun getAudioDeviceModule(): JavaAudioDeviceModule? {
    return audioDeviceModule
  }

  companion object {
    private const val TAG = "PeerConnectionFactoryProvider"
    private const val NATIVE_LIBRARY_NAME = "jingle_peerconnection_so"

    init {
      try {
        System.loadLibrary(NATIVE_LIBRARY_NAME)
        Log.d(TAG, "Loaded native library: $NATIVE_LIBRARY_NAME")
      } catch (e: UnsatisfiedLinkError) {
        Log.e(TAG, "Failed to load native library: $NATIVE_LIBRARY_NAME", e)
      }
    }
  }
}