plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.googlehomeapisampleapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.googlehomeapisampleapp"
        minSdk = 29
        targetSdk = 34
        versionCode = 33
        versionName = "1.4.0"
        // Google Cloud Project ID used for authentication and Home API access
        buildConfigField("String", "GOOGLE_CLOUD_PROJECT_ID", "\"449111297489\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
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
    implementation(libs.androidx.navigation.compose)
    // Home API SDK dependency:
    implementation(libs.play.services.home)
    implementation("com.google.android.gms:play-services-home-types:17.0.0")
}
