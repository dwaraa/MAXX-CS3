pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "TamilDhoolProvider"

include(":TamilDhool")
include(":Einthusan")
include(":TamilUltra")
include(":SpecialityStream")
include(":TamilMV")
