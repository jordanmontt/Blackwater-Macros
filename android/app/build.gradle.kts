import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.blackwatermacros.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.blackwatermacros.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Base URL for the deployed backend (override via -Papp.baseUrl=…). Defaults to production.
        val baseUrl = (project.findProperty("app.baseUrl") as String?) ?: "https://blackwater-macros.jordanmontt.fr/"
        buildConfigField("String", "API_BASE_URL", "\"$baseUrl\"")
    }

    signingConfigs {
        // A real key, if configured (keystore.properties, never committed); see TECHNICAL.md §14.
        val keystoreFile = rootProject.file("keystore.properties")
        if (keystoreFile.exists()) {
            val props = Properties().apply { keystoreFile.inputStream().use { load(it) } }
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: removes unused code and resources (the debug APK is ~3× bigger).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a release key, sign with the local debug key so the APK can be shared and
            // installed for testing (it also updates a debug install). Store/F-Droid builds use
            // their own keys.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // Release: one APK per CPU type, so a phone downloads only its own native code
    // (the on-device AI engine alone is ~22 MB per type). Debug stays a single APK.
    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        // Native libraries compressed inside the APK: a much smaller file to download or send
        // (Android unpacks them once at install).
        jniLibs { useLegacyPackaging = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time is used by the wire/date models; needed on minSdk < 26.
        isCoreLibraryDesugaringEnabled = true
    }

    androidResources {
        // Lets Android 13+ users pick the app language in system settings (en default + es/fr/it/de).
        generateLocaleConfig = true
    }

    buildFeatures {
        compose = true
        // Generate BuildConfig so the base URL can be injected/overridden per build.
        buildConfig = true
    }

    testOptions {
        // Room + Android framework classes run on the JVM through Robolectric.
        unitTests.isIncludeAndroidResources = true
    }

}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.composeBom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.reorderable)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime)

    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.zxing.cpp)
    // EXIF rotation of photos (the platform class has known bugs on older Android versions).
    implementation(libs.exifinterface)
    implementation(libs.litertlm)

    coreLibraryDesugaring(libs.android.jdk.desugaring)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.retrofit.mock)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)

    debugImplementation(libs.compose.ui.tooling)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
