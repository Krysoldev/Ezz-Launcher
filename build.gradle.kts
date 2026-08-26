plugins {
    kotlin("multiplatform") version "1.9.23" apply false
    kotlin("plugin.serialization") version "1.9.23" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
}

allprojects {
    group = "io.ezz.launcher"
    version = "1.0.0"
}
