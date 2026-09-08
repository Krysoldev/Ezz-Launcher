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
    dependsOn(rootProject.tasks.named("buildAllVersions"))
}

tasks.named("build") {
    dependsOn(packageModJars)
}
