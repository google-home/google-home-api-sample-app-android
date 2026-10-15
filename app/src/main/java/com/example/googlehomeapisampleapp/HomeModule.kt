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

package com.example.googlehomeapisampleapp

import coil3.ImageLoader
import com.example.googlehomeapisampleapp.history.CameraMediaAuth
import com.google.home.DeviceType
import com.google.home.DeviceTypeFactory
import com.google.home.FactoryRegistry
import com.google.home.HomeClient
import com.google.home.HomeConfig
import com.google.home.Trait
import com.google.home.TraitFactory
import com.google.home.google.CameraAvStreamManagement
import com.google.home.google.CameraHistory
import com.google.home.google.CameraSnapshot
import com.google.home.google.CameraTimeline
import com.google.home.google.Chime as GoogleChime
import com.google.home.google.ChimeThemes
import com.google.home.google.GoogleCameraDevice
import com.google.home.google.GoogleDoorbellDevice
import com.google.home.google.PushAvStreamTransport
import com.google.home.google.RecordingMode
import com.google.home.google.WebRtcLiveView
import com.google.home.google.ZoneManagement
import com.google.home.matter.standard.AudioDoorbellDevice
import com.google.home.matter.standard.AudioOutput
import com.google.home.matter.standard.BasicInformation
import com.google.home.matter.standard.BooleanState
import com.google.home.matter.standard.CameraDevice
import com.google.home.matter.standard.Chime
import com.google.home.matter.standard.ChimeDevice
import com.google.home.matter.standard.ColorTemperatureLightDevice
import com.google.home.matter.standard.ContactSensorDevice
import com.google.home.matter.standard.DimmableLightDevice
import com.google.home.matter.standard.DoorLock
import com.google.home.matter.standard.DoorLockDevice
import com.google.home.matter.standard.DoorbellDevice
import com.google.home.matter.standard.ExtendedColorLightDevice
import com.google.home.matter.standard.FanControl
import com.google.home.matter.standard.FanDevice
import com.google.home.matter.standard.FloodlightCameraDevice
import com.google.home.matter.standard.GenericSwitchDevice
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.MediaInput
import com.google.home.matter.standard.MediaPlayback
import com.google.home.matter.standard.OccupancySensing
import com.google.home.matter.standard.OccupancySensorDevice
import com.google.home.matter.standard.OnOffLightDevice
import com.google.home.matter.standard.OnOffLightSwitchDevice
import com.google.home.matter.standard.OnOffPluginUnitDevice
import com.google.home.matter.standard.OnOffSensorDevice
import com.google.home.matter.standard.RootNodeDevice
import com.google.home.matter.standard.SnapshotCameraDevice
import com.google.home.matter.standard.SpeakerDevice
import com.google.home.matter.standard.TemperatureControl
import com.google.home.matter.standard.TemperatureMeasurement
import com.google.home.matter.standard.TemperatureSensorDevice
import com.google.home.matter.standard.VideoDoorbellDevice
import com.google.home.matter.standard.WindowCovering
import com.google.home.matter.standard.WindowCoveringDevice
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

/** Qualifier for the default unauthenticated [OkHttpClient] instance. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultOkHttpClient

/** Qualifier for the authenticated [ImageLoader] instance provided by [CameraMediaAuth]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthenticatedImageLoader

/** Hilt module for providing dependencies related to the Google Home APIs. */
@Module
@InstallIn(SingletonComponent::class)
object HomeModule {

  /** Provides a list of supported device types. */
  @Provides
  @Singleton
  fun provideSupportedDeviceTypes(): @JvmSuppressWildcards List<DeviceTypeFactory<out DeviceType>> =
    listOf(
      AudioDoorbellDevice,
      CameraDevice,
      ChimeDevice,
      ColorTemperatureLightDevice,
      ContactSensorDevice,
      DimmableLightDevice,
      DoorLockDevice,
      DoorbellDevice,
      ExtendedColorLightDevice,
      FanDevice,
      FloodlightCameraDevice,
      GenericSwitchDevice,
      GoogleCameraDevice,
      GoogleDoorbellDevice,
      OccupancySensorDevice,
      OnOffLightDevice,
      OnOffLightSwitchDevice,
      OnOffPluginUnitDevice,
      OnOffSensorDevice,
      RootNodeDevice,
      SnapshotCameraDevice,
      SpeakerDevice,
      TemperatureSensorDevice,
      VideoDoorbellDevice,
      WindowCoveringDevice,
    )

  /** Provides a list of supported device traits. */
  @Provides
  @Singleton
  fun provideSupportedTraits(): @JvmSuppressWildcards List<TraitFactory<out Trait>> =
    listOf(
      AudioOutput,
      BasicInformation,
      BooleanState,
      CameraAvStreamManagement,
      CameraHistory,
      CameraSnapshot,
      CameraTimeline,
      Chime,
      ChimeThemes,
      DoorLock,
      FanControl,
      GoogleChime,
      LevelControl,
      MediaInput,
      MediaPlayback,
      OccupancySensing,
      PushAvStreamTransport,
      RecordingMode,
      TemperatureControl,
      TemperatureMeasurement,
      WebRtcLiveView,
      WindowCovering,
      ZoneManagement,
    )

  /**
   * Provides the [FactoryRegistry] for the Home SDK.
   *
   * @param types The list of supported device types.
   * @param traits The list of supported device traits.
   */
  @Provides
  @Singleton
  fun provideFactoryRegistry(
    types: @JvmSuppressWildcards List<DeviceTypeFactory<out DeviceType>>,
    traits: @JvmSuppressWildcards List<TraitFactory<out Trait>>,
  ): FactoryRegistry = FactoryRegistry(types = types, traits = traits)

  /**
   * Provides the [HomeConfig] for the Home SDK.
   *
   * @param registry The [FactoryRegistry] for the Home SDK.
   */
  @Provides
  @Singleton
  fun provideHomeConfig(registry: FactoryRegistry): HomeConfig =
    HomeConfig(
      coroutineContext = Dispatchers.IO,
      factoryRegistry = registry,
      homePlatformScope = HomeConfig.HomePlatformScope.HOME_PLATFORM_SCOPE_VERSION_2,
    )

  /**
   * Provides the [HomeClient] instance.
   *
   * @param homeClientProvider The provider for obtaining a [HomeClient] instance.
   */
  @Provides
  @Singleton
  fun provideHomeClient(homeClientProvider: HomeClientProvider): HomeClient =
    homeClientProvider.getClient()

  /** Provides the default unauthenticated [OkHttpClient] instance. */
  @Provides
  @Singleton
  @DefaultOkHttpClient
  fun provideOkHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .build()

  /**
   * Provides the authenticated [ImageLoader] instance for camera thumbnails and snapshots.
   *
   * @param cameraMediaAuth The authentication helper managing DUSI tokens.
   */
  @Provides
  @Singleton
  @AuthenticatedImageLoader
  fun provideAuthenticatedImageLoader(cameraMediaAuth: CameraMediaAuth): ImageLoader =
    cameraMediaAuth.imageLoader
}
