# Supabase Database & Storage Guide

## 1. Schema Definition

The database schema is defined in `supabase/migrations/20260826000000_init_ezz_platform.sql`.

### Tables

#### `minecraft_profiles`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PRIMARY KEY | Profile internal ID |
| `user_id` | UUID | NOT NULL, REFERENCES auth.users | Owning Supabase Auth User ID |
| `uuid` | UUID | NOT NULL, UNIQUE | Permanent Minecraft Java UUID |
| `username` | TEXT | NOT NULL | Display username |
| `username_normalized` | TEXT | NOT NULL, UNIQUE | Lowercase username for lookups |
| `skin_model` | TEXT | NOT NULL, DEFAULT 'STEVE' | 'STEVE' (4px) or 'ALEX' (3px) |
| `skin_id` | UUID | NULLABLE, REFERENCES skins(id) | Active skin foreign key |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | Last update timestamp |
| `deleted_at` | TIMESTAMPTZ | NULLABLE | Soft deletion flag |

#### `skins`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PRIMARY KEY | Skin internal ID |
| `profile_id` | UUID | NOT NULL, REFERENCES minecraft_profiles | Owning profile ID |
| `name` | TEXT | NOT NULL | Custom skin display name |
| `storage_path` | TEXT | NOT NULL | Path in `minecraft-skins` bucket |
| `sha256` | TEXT | NOT NULL | Immutable SHA-256 hash of PNG |
| `model` | TEXT | NOT NULL | 'STEVE' or 'ALEX' |
| `width` | INTEGER | NOT NULL (64 or 128) | Pixel width |
| `height` | INTEGER | NOT NULL (32, 64, or 128) | Pixel height |
| `file_size` | INTEGER | NOT NULL | File size in bytes |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | Creation timestamp |

---

## 2. Row Level Security (RLS) Policies

All tables have RLS enabled by default:
- **Authenticated Users**: Full CRUD access restricted exclusively to records where `auth.uid() = user_id`.
- **Public / Anonymous**: Access restricted to the `get_public_minecraft_profile` RPC function.

---

## 3. Storage Bucket (`minecraft-skins`)

- **Bucket Name**: `minecraft-skins`
- **Path Format**: `<minecraft_uuid>/<sha256_hash>.png`
- **Access Policies**:
  - `SELECT`: Public access enabled for Minecraft clients and website previews.
  - `INSERT`: Authenticated users only.
