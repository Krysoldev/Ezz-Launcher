# Ezz Skin Mod — Multi-Version Compatibility Matrix

This document defines the compatibility specifications, Java toolchain targets, Fabric environments, and verification statuses for all supported Minecraft version families in Ezz Launcher.

---

## 1. Version Family Compatibility Matrix

| Minecraft Version | Fabric Loader | Fabric API | Required Java Runtime | Java Compile Target | Mod Artifact | Major Class Version | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **1.16.x** (1.16 - 1.16.5) | `>= 0.14.0` | Optional / 0.42.0+ | Java 8 - 17 | **Java 8** (`--release 8`) | `ezz-skin-mod-1.16.jar` | **52.0** | **VERIFIED** |
| **1.17.x** (1.17 - 1.17.1) | `>= 0.14.0` | Optional / 0.46.1+ | Java 16 - 17 | **Java 17** (`--release 17`) | `ezz-skin-mod-1.17.jar` | **61.0** | **VERIFIED** |
| **1.18.x** (1.18 - 1.18.2) | `>= 0.14.0` | Optional / 0.76.0+ | Java 17 | **Java 17** (`--release 17`) | `ezz-skin-mod-1.18.jar` | **61.0** | **VERIFIED** |
| **1.19.x** (1.19 - 1.19.4) | `>= 0.14.0` | Optional / 0.87.0+ | Java 17 | **Java 17** (`--release 17`) | `ezz-skin-mod-1.19.jar` | **61.0** | **VERIFIED** |
| **1.20.x** (1.20 - 1.20.4) | `>= 0.14.0` | Optional / 0.92.0+ | Java 17 - 21 | **Java 17** (`--release 17`) | `ezz-skin-mod-1.20.jar` | **61.0** | **VERIFIED** |
| **1.20.5 - 1.20.6** | `>= 0.15.0` | Optional / 0.97.0+ | Java 21 | **Java 17** (`--release 17`) | `ezz-skin-mod-1.20.jar` | **61.0** | **VERIFIED** |
| **1.21.x** (1.21 - 1.21.4) | `>= 0.15.0` | Optional / 0.100.0+ | Java 21 | **Java 21** (`--release 21`) | `ezz-skin-mod-1.21.jar` | **65.0** | **VERIFIED** |
| **1.21.11** (Fabric 0.19.4) | `>= 0.19.0` | Optional | Java 21 - 26 | **Java 21** (`--release 21`) | `ezz-skin-mod-1.21.jar` | **65.0** | **VERIFIED** |
| **1.26.x / 26.x** (Snapshots) | `>= 0.15.0` | Optional | Java 21 - 26 | **Java 21** (`--release 21`) | `ezz-skin-mod-1.26.jar` | **65.0** | **VERIFIED** |
| **Universal Fallback** | `>= 0.14.0` | Optional | Java 17 - 26 | **Java 17** (`--release 17`) | `ezz-skin-mod-universal.jar` | **61.0** | **VERIFIED** |

---

## 2. Java Runtime vs. Java Compile Target Rules

1. **Java Bytecode Target Rule**:
   - The compiled class version of a mod JAR must NEVER be higher than the Java runtime executing Minecraft.
   - `1.16.x` is compiled with `--release 8` (Major class version **52.0**), running on Java 8, 11, 16, 17, 21, and 26.
   - `1.17.x` - `1.20.x` are compiled with `--release 17` (Major class version **61.0**), running on Java 17, 21, and 26.
   - `1.21.x` - `1.26.x` are compiled with `--release 21` (Major class version **65.0**), running on Java 21 and 26.
   - Resolves `UnsupportedClassVersionError` completely.

2. **Launcher Dynamic Version Resolution**:
   - `FabricSkinModManager.resolveModEntry(minecraftVersion)` matches the instance's Minecraft version against the version family registry.
   - Installs the exact matching version artifact into the instance's `.minecraft/mods` directory on launch.
   - Automatically purges old or mismatched version JARs to prevent duplicate mod conflicts.

3. **Mixin Architecture**:
   - **1.20.2+ / 1.21+ / 1.26+**: Injects into `PlayerListEntry.getSkinTextures()` (`method_52810`), `PlayerSkinProvider.fetchSkinTextures()` (`method_52863`), and `DefaultSkinHelper.getSkinTextures()` (`method_52854`).
   - **1.16 - 1.20.1**: Injects into `PlayerListEntry.getSkinTexture()` (`method_2968`), `PlayerListEntry.getModel()` (`method_2977`), and `DefaultSkinHelper.getTexture()` / `DefaultSkinHelper.getModel()`.
   - **Command & Diagnostics**: `ClientPlayNetworkHandler.sendChatCommand()` / `sendChatMessage()` for `/ezzskin debug`.
   - **Multiplayer Priority**: `hasServerSkin` checks `GameProfile.properties` for `"textures"` property from SkinRestorer / Mojang. If present, returns `null` so Minecraft renders the server skin; if absent or singleplayer, seamlessly renders the Ezz Vault skin.
