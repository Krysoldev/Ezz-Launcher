# Ezz Platform Architecture — 100% Serverless & VPS-less

The official Ezz Platform provides a unified, decentralized Minecraft account, profile, and skin management system. It requires **no VPS**, **no dedicated servers**, and **no client-side skin injection mods**.

```
+-------------------------------------------------------------------------------+
|                                  USER / BROWSER                               |
|          Ezz Website (Vite + Vanilla JS + 3D Skin Studio + CSS Grid)          |
+---------------------------------------+---------------------------------------+
                                        |
                 +----------------------+----------------------+
                 |                                             |
                 v                                             v
+-----------------------------------+        +-----------------------------------+
|         CLOUDFLARE PAGES          |        |     CLOUDFLARE WORKERS / API      |
|    Frontend Hosting & Static Edge |        |   /api/minecraft/profile/:user    |
|       (ezzlauncher.dpdns.org)     |        |   /api/minecraft/texture/:hash    |
+----------------+------------------+        +-----------------+-----------------+
                 |                                             |
                 |              +------------------------------+
                 v              v
+-------------------------------------------------------------------------------+
|                                SUPABASE CLOUD                                 |
|  * Supabase Auth: Secure JWT identity (email & password)                      |
|  * Supabase PostgreSQL: minecraft_profiles & skins tables with RLS            |
|  * Supabase Storage: 'minecraft-skins' CDN bucket (<uuid>/<hash>.png)         |
+-------------------------------------------------------------------------------+
                                        ^
                                        | Lightweight Public Profile Resolution
                                        | (Offline Username Lookup)
+---------------------------------------+---------------------------------------+
|                                 EZZ LAUNCHER                                  |
|         Desktop Client (Kotlin Multiplatform / Compose Desktop)               |
+-------------------------------------------------------------------------------+
```

---

## Key Design Principles

1. **Zero VPS / Serverless Compute**:
   - Compute is distributed globally across Cloudflare's Edge Network (Cloudflare Pages & Workers) and Supabase's managed Postgres/Storage engine.
   - Zero Docker daemon management, zero Linux maintenance, zero downtime.

2. **Web-Only Account Registration**:
   - Users create and manage their Ezz Account and Minecraft Profile strictly on the web platform.
   - The desktop launcher never collects passwords for Ezz accounts; it resolves cloud profiles seamlessly through the user's offline username.

3. **Permanent Minecraft Identity**:
   - Each registered Minecraft username is paired with a permanent, immutable UUID generated upon profile creation.
   - Skins and arm models (Steve/Alex) can be swapped infinitely without altering the UUID.

4. **Zero Mod Injection / Pure Vanilla Compatibility**:
   - Minecraft launches cleanly with standard authentication parameters.
   - Remote multiplayer players keep their own legitimate skins without any client-side override mods.
