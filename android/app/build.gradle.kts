import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
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
        // One step per release; the git tag is v<versionName> (docs/RELEASING.md).
        versionCode = 2
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Base URL for the deployed backend (override via -Papp.baseUrl=…). Defaults to production.
        val baseUrl = (project.findProperty("app.baseUrl") as String?) ?: "https://blackwater-macros.jordanmontt.fr/"
        buildConfigField("String", "API_BASE_URL", "\"$baseUrl\"")
        // The phone-model catalog, read from the released branch of the public repository
        // (docs/RELEASING.md «Publishing a phone model»), never from the Blackwater server.
        buildConfigField(
            "String",
            "MODEL_CATALOG_URL",
            "\"https://raw.githubusercontent.com/jordanmontt/Blackwater-Macros/main/android/catalog/local-models.json\"",
        )
    }

    signingConfigs {
        // A real key, if configured (keystore.properties, never committed); see docs/TECHNICAL.md §14.
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
    // F-Droid builds only `-PreleaseAbi=arm64-v8a` (the phones the on-device AI runs on).
    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            reset()
            val only = project.findProperty("releaseAbi") as String?
            if (only != null) include(only) else include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    // No dependency list encrypted with Google's key inside the APK: F-Droid's scanner rejects
    // it, and it only serves the Play Store.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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

/**
 * The texts the app shares with the web come from the web's dictionaries
 * (`src/i18n/<lang>.json`), so they are written once per language.
 * `strings-from-web.json` names, for each Android string, its path in those
 * dictionaries and, for texts with «{name}» markers, the order and type of the
 * format arguments («n:d» → `%1$d`). Android-only texts stay in `strings.xml`.
 * See docs/TECHNICAL.md «i18n».
 */
abstract class GenerateWebStrings : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mapFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dictionaries: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val entries = JsonSlurper().parse(mapFile.get().asFile) as Map<String, Any>
        val out = outputDirectory.get().asFile.apply { deleteRecursively() }
        // English is Android's default language (`values`), Spanish the web's reference.
        for ((lang, dir) in listOf("en" to "values", "es" to "values-es", "fr" to "values-fr", "it" to "values-it", "de" to "values-de")) {
            val dictionary = JsonSlurper().parse(dictionaries.files.single { it.name == "$lang.json" })
            val xml = StringBuilder()
            xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            xml.append("<!-- Generated from src/i18n/$lang.json (see android/app/strings-from-web.json). Do not edit. -->\n")
            xml.append("<resources>\n")
            for ((name, spec) in entries) {
                val path = if (spec is List<*>) spec.first() as String else spec as String
                val args = if (spec is List<*>) spec.drop(1).map { it as String } else emptyList()
                val found = path.split('.').fold<String, Any?>(dictionary) { node, key -> (node as? Map<*, *>)?.get(key) }
                var text = escapeAndroidString(
                    found as? String ?: throw GradleException("strings-from-web.json: «$name» → «$path» is not a text in $lang.json"),
                    hasArgs = args.isNotEmpty(),
                )
                args.forEachIndexed { index, arg ->
                    val (argName, type) = arg.split(':')
                    val marker = "{$argName}"
                    if (marker !in text) throw GradleException("strings-from-web.json: «$name» in $lang.json has no $marker")
                    text = text.replace(marker, "%${index + 1}$$type")
                }
                Regex("""\{\w+\}""").find(text)?.let {
                    throw GradleException("strings-from-web.json: «$name» in $lang.json has ${it.value} but no argument for it")
                }
                xml.append("    <string name=\"$name\">$text</string>\n")
            }
            xml.append("</resources>\n")
            File(out, "$dir/strings_web.xml").apply { parentFile.mkdirs() }.writeText(xml.toString())
        }
    }

    /** What aapt needs escaped in a string resource; `%` only matters when the text takes arguments. */
    private fun escapeAndroidString(text: String, hasArgs: Boolean): String {
        val escaped = text
            .replace("\\", "\\\\")
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("'", "\\'").replace("\"", "\\\"")
            .replace("\n", "\\n")
            .let { if (hasArgs) it.replace("%", "%%") else it }
        return if (escaped.startsWith("@") || escaped.startsWith("?")) "\\$escaped" else escaped
    }
}

val generateWebStrings = tasks.register<GenerateWebStrings>("generateWebStrings") {
    mapFile.set(layout.projectDirectory.file("strings-from-web.json"))
    dictionaries.from(fileTree(rootDir.resolve("../src/i18n")) { include("*.json") })
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(generateWebStrings, GenerateWebStrings::outputDirectory)
    }
}
