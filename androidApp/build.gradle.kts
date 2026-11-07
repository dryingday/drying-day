plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.dryingday"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.dryingday"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // F-Droid rejects APKs carrying Google's encrypted dependency metadata block.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    constraints {
        implementation(libs.androidx.lifecycle.runtime) {
            because("the version Compose Multiplatform 1.12.2 pairs with; 2.9.x's lint checks break on AGP 9.5")
        }
    }
}
