pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://libraries.minecraft.net")
        maven("https://maven.fabricmc.net")
    }
}

rootProject.name = "ezz-launcher"

include(":core:model")
include(":core:network")
include(":core:storage")
include(":core:auth")
include(":core:minecraft")
include(":core:runtime")
include(":ui:common")
include(":app:desktop")
// include(":app:android") // Android development temporarily postponed for Windows stabilization

