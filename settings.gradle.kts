rootProject.name = "drying-day"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
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
