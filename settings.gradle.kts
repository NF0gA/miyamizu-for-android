pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android\\..*")
                includeGroupByRegex("com\\.google\\.android\\..*")
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.google\\.test\\..*")
                includeGroupByRegex("android\\.arch\\..*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Miyamizu"
include(":app")
