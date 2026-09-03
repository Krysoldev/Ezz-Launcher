# Cloudflare Pages & Workers Deployment Guide

## 1. Cloudflare Configuration

- **Account ID**: `f33e2302655eedc2f93ac28d25d9595b` (`KrysolDev`)
- **Domain Zone**: `ezzlauncher.dpdns.org`
- **Zone ID**: `60f807a8b844cdae499f8f2718c0384f`
- **Nameservers**: `decker.ns.cloudflare.com`, `piper.ns.cloudflare.com`

---

## 2. Deploying Cloudflare Pages

1. Navigate to the `web/` directory:
   ```bash
   cd web
   npm install
   npm run build
   ```

2. Deploy using Wrangler CLI:
   ```bash
   npx wrangler pages deploy dist --project-name ezz-launcher-web
   ```

3. Bind custom domain `ezzlauncher.dpdns.org` in the Cloudflare Dashboard under:
   `Workers & Pages` > `ezz-launcher-web` > `Custom domains` > Add `ezzlauncher.dpdns.org`.

---

## 3. Environment Variables & Secrets

Configure the following variables in Cloudflare Dashboard (`Settings` > `Environment variables`):

### Public Variables (Pages):
- `VITE_SUPABASE_URL`: Your Supabase Project URL (`https://xyz.supabase.co`)
- `VITE_SUPABASE_ANON_KEY`: Your Supabase Public Anon Key

### Server / Worker Secrets (Functions):
- `SUPABASE_URL`: Supabase Project URL
- `SUPABASE_ANON_KEY`: Supabase Public Anon Key
- `SUPABASE_SERVICE_ROLE_KEY`: (Optional) Server-only administrative key
