import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The version lives in gradle.properties, shared with the watch app.
val appVersionName: String by project
val appVersionNumber: String by project

// Builds for Google Play are signed with the upload key described in
// keystore.properties (kept out of git). They are asked for with -Pplay, which
// scripts\bundle.ps1 does. Every other build keeps the debug key, so builds
// for your own devices install over each other without losing data.
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
        applicationId = "com.august.intervaltimer"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionNumber.toInt() * 10
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
        // Debug builds are a separate app ("Interval Timer Test") with their
        // own empty data, so trying things out never touches real workouts.
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Room writes a description of each database version here. Keeping these
// files in git lets future upgrades be tested against real old versions.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.reorderable)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
