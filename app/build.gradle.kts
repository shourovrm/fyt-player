import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// signing pulled from gitignored keystore.properties, never hardcoded
val keystoreFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystoreFile.exists()) load(FileInputStream(keystoreFile))
}

android {
    namespace = "com.fyiplayer.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fyiplayer.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 54
        versionName = "0.3.4"
    }

    // One APK per ABI: the python/ffmpeg payload is ~50 MB per ABI, so a universal APK would be huge.
    // 32-bit x86 is left out; almost no device needs it.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties.getProperty("keyAlias", "")
            keyPassword = keystoreProperties.getProperty("keyPassword", "")
            storeFile = rootProject.file(keystoreProperties.getProperty("storeFile", "fyi-release.jks"))
            storePassword = keystoreProperties.getProperty("storePassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // CI has no keystore: it builds an unsigned APK to prove the release build compiles
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    lint { lintConfig = file("lint.xml"); abortOnError = true }

    packaging {
        // extraction engine needs extracted native libs; libc++_shared ships in more than one artifact
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += "lib/arm64-v8a/libc++_shared.so"
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)
    implementation(libs.okhttp)
    implementation(libs.newpipe.extractor)
    // nanojson: extractor keeps it `implementation`, YoutubeTopicFeed reads its JsonObject directly
    implementation("com.github.TeamNewPipe:nanojson:1d9e1aea9049fc9f85e68b43ba39fe7be1c1f751")
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
}
