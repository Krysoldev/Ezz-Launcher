plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":ui:common"))
            implementation(project(":core:model"))
            implementation(project(":core:network"))
            implementation(project(":core:storage"))
            implementation(project(":core:runtime"))
            implementation(project(":core:auth"))
            implementation(project(":core:minecraft"))
            
            implementation("com.squareup.okio:okio:3.9.0")
            implementation("io.ktor:ktor-client-core:2.3.11")
            implementation("io.ktor:ktor-client-cio:2.3.11")
            
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
            implementation("net.java.dev.jna:jna:5.13.0")
            implementation("net.java.dev.jna:jna-platform:5.13.0")
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.ezz.launcher.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "EzzLauncher"
            packageVersion = "1.0.0"
            description = "Ezz Launcher"
            vendor = "Ezz"

            windows {
                iconFile.set(project.file("src/jvmMain/resources/icon.ico"))
                menu = true
                shortcut = true
            }
        }
    }
}
