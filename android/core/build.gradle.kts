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

// Food questions: the web's data file (rules, prompts, text templates) is this module's
// resource, so both platforms read one copy (docs/TECHNICAL.md §8.7).
tasks.processResources {
    from(rootProject.file("../src/lib/core/food-questions/food-questions.json")) { into("foodquestions") }
}

// The shared expectations both platforms test against (tests/fixtures/food-questions).
tasks.test {
    val fixtures = rootProject.file("../tests/fixtures")
    inputs.dir(fixtures)
    systemProperty("fixtures", fixtures.absolutePath)
}
