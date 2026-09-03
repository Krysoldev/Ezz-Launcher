plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:network"))
            implementation(project(":core:storage"))
            implementation("io.ktor:ktor-client-core:2.3.11")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            implementation("com.microsoft.azure:msal4j:1.15.1")
            implementation("com.microsoft.azure:msal4j-brokers:1.0.3-beta")
            implementation("com.microsoft.azure:msal4j-persistence-extension:1.3.0")
            implementation("net.java.dev.jna:jna:5.13.0")
            implementation("net.java.dev.jna:jna-platform:5.13.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("com.microsoft.azure:msal4j:1.15.1")
            implementation("com.microsoft.azure:msal4j-brokers:1.0.3-beta")
        }
    }
}
