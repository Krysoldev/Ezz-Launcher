plugins {
    `java`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.release.set(21)
    options.encoding = "UTF-8"
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

repositories {
    maven("https://maven.fabricmc.net")
    maven("https://libraries.minecraft.net")
    mavenCentral()
}

sourceSets {
    main {
        java {
            srcDirs("common/src/main/java", "src/main/java")
        }
        resources {
            srcDirs("common/src/main/resources", "src/main/resources")
        }
    }
}

dependencies {
    compileOnly("net.fabricmc:fabric-loader:0.16.9")
    compileOnly("net.fabricmc:sponge-mixin:0.15.3+mixin.0.8.7")
    compileOnly("com.mojang:authlib:6.0.55")
}

val copyClassesToBuildDir by tasks.registering {
    dependsOn(tasks.compileJava)
    doLast {
        val dest = file("build/classes")
        val classes = tasks.compileJava.get().destinationDirectory.asFile.get()
        classes.copyRecursively(dest, overwrite = true)
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    dependsOn(copyClassesToBuildDir)
    from("common/src/main/resources")
    from("src/main/resources")
    manifest {
        attributes(
            "Implementation-Title" to "Ezz Skin Fabric Mod",
            "Implementation-Version" to "1.0.0"
        )
    }
}

val packageModJars by tasks.registering {
    dependsOn(tasks.jar)
    doLast {
        val builtJar = tasks.jar.get().archiveFile.get().asFile
        val resourcesDir = file("../core/minecraft/src/commonMain/resources")
        resourcesDir.mkdirs()

        val targetJars = listOf(
            "ezz-skin-mod-1.16.jar",
            "ezz-skin-mod-1.17.jar",
            "ezz-skin-mod-1.18.jar",
            "ezz-skin-mod-1.19.jar",
            "ezz-skin-mod-1.20.jar",
            "ezz-skin-mod-1.21.jar",
            "ezz-skin-mod-1.26.jar",
            "ezz-skin-mod-universal.jar"
        )

        for (targetName in targetJars) {
            val targetFile = file("${resourcesDir.absolutePath}/$targetName")
            builtJar.copyTo(targetFile, overwrite = true)
            println("[EzzSkinMod] Packaged Java 21 build into: ${targetFile.name} (${targetFile.length()} bytes)")
        }
    }
}

tasks.named("build") {
    dependsOn(packageModJars)
}
