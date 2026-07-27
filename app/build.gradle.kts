import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
  id("com.google.dagger.hilt.android")
}

android {
  namespace = "com.ioconnect2026.googlehomeapisampleapp"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.ioconnect2026.googlehomeapisampleapp"
    minSdk = 29
    targetSdk = 36
    versionCode = 42
    versionName = "1.9.0"

    // Store your GCP project web client ID in local.properties and access it via project properties.
    // If local.properties doesn't exist in your app root folder, just create it
    // e.g. add this line to your local.properties
    // WEB_CLIENT_ID_DEV={ProjectNumber}....apps.googleusercontent.com
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
      localPropertiesFile.inputStream().use { localProperties.load(it) }
    }
    val webClientIdDevRaw = localProperties.getProperty("WEB_CLIENT_ID_DEV")
      ?: project.findProperty("WEB_CLIENT_ID_DEV") as? String
      ?: "YOUR_DEFAULT_WEB_CLIENT_ID"
    val webClientIdDev = webClientIdDevRaw.replace("\"", "")
    buildConfigField("String", "DEFAULT_WEB_CLIENT_ID", "\"$webClientIdDev\"")
  }
  lint {
    disable += "NullSafeMutableLiveData"
  }


  signingConfigs {
    create("sharedDebug") {
      storeFile = file("../debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
    debug {
      signingConfig = signingConfigs.getByName("sharedDebug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
}

dependencies {
  // Library dependencies:
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.navigation.compose)
  // Home API SDK dependency:
  implementation(libs.play.services.home)
  implementation(libs.play.services.home.types)
  // Dependency Injection
  implementation(libs.dagger.hilt.android)
  ksp(libs.hilt.android.compiler)
  implementation(libs.androidx.hilt.navigation.compose)
  ksp(libs.androidx.hilt.compiler)

  // Google Auth
  implementation(libs.googleid)
  implementation(libs.androidx.credentials.play.services.auth)
}
