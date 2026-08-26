plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:network"))
            implementation(project(":core:auth"))
            implementation(project(":core:minecraft"))
            implementation(project(":core:runtime"))
            implementation(project(":core:storage"))
            implementation("com.squareup.okio:okio:3.9.0")
            implementation("io.ktor:ktor-client-core:2.3.11")
            
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
