plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()

    val ktorVersion = "2.3.11"

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation("io.ktor:ktor-client-core:$ktorVersion")
            implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
            implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
            implementation("io.ktor:ktor-client-logging:$ktorVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
        }
        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-cio:$ktorVersion")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
