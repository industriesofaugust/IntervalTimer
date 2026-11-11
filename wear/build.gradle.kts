import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The version lives in gradle.properties, shared with the phone app.
val appVersionName: String by project
val appVersionNumber: String by project

// Signing works the same as in the phone app: see app/build.gradle.kts.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val signForPlay = providers.gradleProperty("play").isPresent
if (signForPlay && keystoreProperties.isEmpty) {
    throw GradleException("-Pplay needs keystore.properties; see docs/RELEASE.md")
}

android {
    namespace = "com.august.intervaltimer"
    compileSdk = 36

    defaultConfig {
        // Same applicationId as the phone app: required so the two apps are
        // recognized as a phone+watch pair and can sync.
        applicationId = "com.august.intervaltimer"
        minSdk = 30
        // Google Play's minimum for Wear OS apps since August 2026.
        targetSdk = 35
        // One more than the phone build: Play refuses two files with the same code.
        versionCode = appVersionNumber.toInt() * 10 + 1
        versionName = appVersionName
    }

    signingConfigs {
        if (signForPlay) {
            create("upload") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Matches the phone app's debug build, so a test phone and test watch still pair.
        debug {
            applicationIdSuffix = ".test"
        }
        // The build that goes on the devices and to Play: shrunk and optimised by R8.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName(if (signForPlay) "upload" else "debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.androidx.wear)
    implementation(libs.androidx.wear.ongoing)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.health.services)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.ui.tooling)
}
