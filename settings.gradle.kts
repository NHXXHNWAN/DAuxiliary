pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://artifactory.appodeal.com/appodeal-public/")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Xposed API 82 is hosted in Appodeal's public Maven repository.
        maven("https://artifactory.appodeal.com/appodeal-public/")
    }
}

rootProject.name = "DAuxiliary"
include(":app")
