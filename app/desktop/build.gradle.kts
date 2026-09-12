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
        jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            outputBaseDir.set(project.layout.buildDirectory.dir("dist"))
            packageName = "EzzLauncher"
            packageVersion = "1.0.1"
            description = "Ezz Launcher"
            vendor = "Ezz"

            modules(
                "java.base",
                "java.desktop",
                "java.datatransfer",
                "java.xml",
                "java.prefs",
                "java.logging",
                "java.management",
                "java.management.rmi",
                "java.instrument",
                "java.naming",
                "java.net.http",
                "java.sql",
                "java.security.jgss",
                "java.security.sasl",
                "jdk.crypto.ec",
                "jdk.crypto.mscapi",
                "jdk.crypto.cryptoki",
                "jdk.unsupported",
                "jdk.unsupported.desktop",
                "jdk.zipfs",
                "jdk.charsets",
                "jdk.localedata",
                "jdk.httpserver",
                "jdk.naming.dns",
                "jdk.security.auth",
                "jdk.management",
                "jdk.nio.mapmode",
                "jdk.random",
                "jdk.accessibility"
            )

            appResourcesRootDir.set(project.file("src/jvmMain/package-resources"))

            windows {
                iconFile.set(project.file("src/jvmMain/resources/icon.ico"))
                upgradeUuid = "18e2e7aa-6b88-3c5f-86ff-ff4314365ec5"
                menu = true
                menuGroup = "Ezz Launcher"
                shortcut = true
                console = false
                dirChooser = false
                perUserInstall = true
            }
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}

tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>().configureEach {
    doFirst {
        val resDir = project.layout.buildDirectory.dir("compose/tmp/resources").get().asFile
        resDir.mkdirs()
        val pkgRes = project.file("src/jvmMain/package-resources")
        if (pkgRes.exists()) {
            pkgRes.copyRecursively(resDir, overwrite = true)
        }
    }
}
