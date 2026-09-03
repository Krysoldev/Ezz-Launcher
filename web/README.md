# Ezz Official Web Platform

The official serverless web platform for Ezz Launcher.

## Tech Stack
- **Framework**: Vite + Vanilla JavaScript / CSS
- **3D Engine**: `skinview3d` (Nearest-neighbor pixel-perfect Minecraft rendering)
- **Backend / Database**: Supabase PostgreSQL & Auth
- **Storage**: Supabase Storage (`minecraft-skins` bucket)
- **Edge Compute & Hosting**: Cloudflare Pages + Pages Functions (`web/functions/api`)

## Local Development
```bash
npm install
npm run dev
```

## Running Automated Tests
```bash
npm test
```

## Production Build
```bash
npm run build
```
Produces optimized static output in `dist/` ready for Cloudflare Pages.
