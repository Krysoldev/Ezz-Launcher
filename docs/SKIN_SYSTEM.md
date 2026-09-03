# Ezz Cloud Skin System Architecture

## 1. Overview

The Ezz Cloud Skin System replaces old local client-side skin injection mods with a modern, cloud-first, serverless architecture.

---

## 2. Technical Workflow

```
1. User Uploads Skin (64x64 PNG) on Website
   |
   +--> Browser checks magic bytes: 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
   +--> Browser verifies dimensions: 64x64 or 64x32
   +--> Browser computes SHA-256 hash in Web Crypto API
   +--> Live 3D Skin Studio displays skin with pixel-perfect nearest-neighbor filter
   |
2. Save & Upload
   |
   +--> Uploads to Supabase Storage: 'minecraft-skins/<uuid>/<sha256>.png'
   +--> Inserts record into 'skins' table
   +--> Sets 'minecraft_profiles.skin_id' to new skin ID
   |
3. Launcher Resolution
   |
   +--> User enters username in EzzLauncher
   +--> Launcher queries 'GET /api/minecraft/profile/:username'
   +--> Public Worker returns stable UUID and Yggdrasil textures property
   +--> Launcher boots vanilla/Fabric Minecraft with legitimate profile session
```

---

## 3. Pixel-Perfect 3D Skin Viewer

- **Renderer**: `skinview3d` with nearest-neighbor texture interpolation.
- **Arm Models**:
  - `STEVE`: 4px arm width (Classic Java Model).
  - `ALEX`: 3px arm width (Slim Java Model).
- **Outer Layer**: 3D extruded helmet, jacket, sleeves, and pants layers fully rendered.
- **Controls**:
  - Natural mouse drag: dragging right rotates the player right.
  - Mouse scroll: zoom in/out with bounded distance limits.
  - Auto-rotation: OFF by default.
  - Reset View button: restores front-facing camera angle instantly.
