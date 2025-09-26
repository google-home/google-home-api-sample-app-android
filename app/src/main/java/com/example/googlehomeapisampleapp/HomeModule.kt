package com.example.googlehomeapisampleapp

import android.content.Context
import com.example.googlehomeapisampleapp.HomeClientProvider
import com.google.home.DeviceType
import com.google.home.DeviceTypeFactory
import com.google.home.FactoryRegistry
import com.google.home.HomeClient
import com.google.home.HomeConfig
import com.google.home.Trait
import com.google.home.TraitFactory
import com.google.home.google.AreaAttendanceState
import com.google.home.google.AreaPresenceState
import com.google.home.google.Assistant
import com.google.home.google.AssistantBroadcast
import com.google.home.google.AssistantFulfillment
import com.google.home.google.GoogleDisplayDevice
import com.google.home.google.GoogleTVDevice
import com.google.home.google.Notification
import com.google.home.google.Time
import com.google.home.google.Volume
import com.google.home.matter.standard.BasicInformation
import com.google.home.matter.standard.BooleanState
import com.google.home.matter.standard.ColorTemperatureLightDevice
import com.google.home.matter.standard.ContactSensorDevice
import com.google.home.matter.standard.DimmableLightDevice
import com.google.home.matter.standard.ExtendedColorLightDevice
import com.google.home.matter.standard.GenericSwitchDevice
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.OccupancySensing
import com.google.home.matter.standard.OccupancySensorDevice
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOffLightDevice
import com.google.home.matter.standard.OnOffLightSwitchDevice
import com.google.home.matter.standard.OnOffPluginUnitDevice
import com.google.home.matter.standard.OnOffSensorDevice
import com.google.home.matter.standard.RootNodeDevice
import com.google.home.matter.standard.SpeakerDevice
import com.google.home.matter.standard.TemperatureControl
import com.google.home.matter.standard.TemperatureMeasurement
import com.google.home.matter.standard.Thermostat
import com.google.home.matter.standard.ThermostatDevice
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HomeModule {

    // 1. Provide supported device types (with @JvmSuppressWildcards)
    @Provides
    @Singleton
    fun provideSupportedDeviceTypes(): @JvmSuppressWildcards List<DeviceTypeFactory<out DeviceType>> = listOf(
        ContactSensorDevice,
        ColorTemperatureLightDevice,
        DimmableLightDevice,
        ExtendedColorLightDevice,
        GenericSwitchDevice,
        GoogleDisplayDevice,
        GoogleTVDevice,
        OccupancySensorDevice,
        OnOffLightDevice,
        OnOffLightSwitchDevice,
        OnOffPluginUnitDevice,
        OnOffSensorDevice,
        RootNodeDevice,
        SpeakerDevice,
        ThermostatDevice,
    )

    // 2. Provide supported device traits (with @JvmSuppressWildcards)
    @Provides
    @Singleton
    fun provideSupportedTraits(): @JvmSuppressWildcards List<TraitFactory<out Trait>> = listOf(
        AreaAttendanceState,
        AreaPresenceState,
        Assistant,
        AssistantBroadcast,
        AssistantFulfillment,
        BasicInformation,
        BooleanState,
        OccupancySensing,
        OnOff,
        Notification,
        LevelControl,
        TemperatureControl,
        TemperatureMeasurement,
        Thermostat,
        Time,
        Volume,
    )

    // 3. Provide the FactoryRegistry, also applying @JvmSuppressWildcards to parameters
    @Provides
    @Singleton
    fun provideFactoryRegistry(
        types: @JvmSuppressWildcards List<DeviceTypeFactory<out DeviceType>>,
        traits: @JvmSuppressWildcards List<TraitFactory<out Trait>>
    ): FactoryRegistry = FactoryRegistry(
        types = types,
        traits = traits
    )

    // 4. Provide HomeConfig
    @Provides
    @Singleton
    fun provideHomeConfig(registry: FactoryRegistry): HomeConfig = HomeConfig(
        coroutineContext = Dispatchers.IO,
        factoryRegistry = registry
    )

    // 5. Provide HomeClient instance
    @Provides
    @Singleton
    fun provideHomeClient(
        @ApplicationContext context: Context,
        homeConfig: HomeConfig
    ): HomeClient = HomeClientProvider.getClient(
        context = context,
        homeConfig = homeConfig
    )
}