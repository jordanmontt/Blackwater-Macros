plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Java 17 bytecode, like the app module, built with whatever JDK runs Gradle (21 here):
// no separate JDK 17 has to be installed.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // JSON trees only (no compiler plugin): Open Food Facts and AI answers.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}