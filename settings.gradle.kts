rootProject.name = "drying-day"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    // Google's Firebase and Play Services plugins only work with non-free libraries.
    // The open-source licences plugin (com.google.android.gms.oss-licenses-plugin) is allowed.
    resolutionStrategy {
        eachPlugin {
            val id = requested.id.id
            if (id.startsWith("com.google.gms.") || id.startsWith("com.google.firebase.")) {
                throw GradleException("Plugin $id needs non-free Google libraries, which F-Droid won't build.")
            }
        }
    }
    repositories {
        // Android's own groups resolve only from Google Maven, never from Maven Central.
        exclusiveContent {
            forRepository { google() }
            filter {
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\.testing\\.platform(\\..*)?")
                includeGroupByRegex("androidx(\\..*)?")
            }
        }
        // Other Google groups (Guava, Gson, Protobuf...) mostly live on Maven Central.
        google {
            content {
                includeGroupByRegex("com\\.google\\..*")
            }
        }
        mavenCentral()
    }
}

// F-Droid accepts prebuilt artifacts only from approved repositories,
// so Google Maven and Maven Central are the only ones used.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Android's own groups resolve only from Google Maven, never from Maven Central.
        exclusiveContent {
            forRepository { google() }
            filter {
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\.testing\\.platform(\\..*)?")
                includeGroupByRegex("androidx(\\..*)?")
            }
        }
        // Other Google groups (Guava, Gson, Protobuf...) mostly live on Maven Central.
        google {
            content {
                includeGroupByRegex("com\\.google\\..*")
            }
        }
        mavenCentral()
    }
}

include(":shared")
include(":androidApp")
