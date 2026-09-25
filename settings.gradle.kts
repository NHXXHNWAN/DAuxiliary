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

    }
}

rootProject.name = "DAuxiliary"
include(":app")
