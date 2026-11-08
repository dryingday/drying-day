import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    android {
        namespace = "io.github.dryingday.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // Packages composeResources into the app; off by default in the KMP library plugin.
        androidResources {
            enable = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        // Runs commonTest on the JVM as part of `check`.
        withHostTest {}
    }

    // Off by default: iOS needs macOS with Xcode. See gradle.properties.
    if (providers.gradleProperty("dryingday.ios").orNull == "true") {
        iosArm64()
        iosSimulatorArm64()
        sourceSets.iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            // Its types are in the engine's public API.
            api(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    packageOfResClass = "io.github.dryingday.shared.resources"
}
