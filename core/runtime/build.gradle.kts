plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:auth"))
            implementation(project(":core:network"))
            implementation(project(":core:minecraft"))
            implementation(project(":core:storage"))
            implementation("com.squareup.okio:okio:3.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
