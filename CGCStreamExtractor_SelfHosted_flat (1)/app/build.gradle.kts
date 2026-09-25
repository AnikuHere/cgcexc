plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ge.cgc.streamextractor"
    compileSdk = 35

    defaultConfig {
        applicationId = "ge.cgc.streamextractor"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2-self-hosted"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
