-- ==============================================================================
-- EZZ LAUNCHER PLATFORM — SUPABASE POSTGRESQL & STORAGE INITIAL MIGRATION
-- VPS-less, Serverless, Privacy-Safe Architecture
-- ==============================================================================

-- 1. Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ------------------------------------------------------------------------------
-- 2. Minecraft Profiles Table
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.minecraft_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    username TEXT NOT NULL,
    username_normalized TEXT NOT NULL UNIQUE,
    skin_model TEXT NOT NULL DEFAULT 'STEVE' CHECK (skin_model IN ('STEVE', 'ALEX')),
    skin_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

-- Indexes for performance & rapid lookup
CREATE INDEX IF NOT EXISTS idx_minecraft_profiles_user_id ON public.minecraft_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_minecraft_profiles_username_norm ON public.minecraft_profiles(username_normalized);
CREATE INDEX IF NOT EXISTS idx_minecraft_profiles_uuid ON public.minecraft_profiles(uuid);

-- ------------------------------------------------------------------------------
-- 3. Skins Table (Cloud Skin Wardrobe)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.skins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL REFERENCES public.minecraft_profiles(id) ON DELETE CASCADE,
    name TEXT NOT NULL DEFAULT 'Custom Skin',
    storage_path TEXT NOT NULL,
    sha256 TEXT NOT NULL,
    model TEXT NOT NULL DEFAULT 'STEVE' CHECK (model IN ('STEVE', 'ALEX')),
    width INTEGER NOT NULL DEFAULT 64 CHECK (width IN (64, 128)),
    height INTEGER NOT NULL DEFAULT 64 CHECK (height IN (32, 64, 128)),
    file_size INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Foreign key for active skin
ALTER TABLE public.minecraft_profiles 
    DROP CONSTRAINT IF EXISTS fk_active_skin,
    ADD CONSTRAINT fk_active_skin 
    FOREIGN KEY (skin_id) 
    REFERENCES public.skins(id) 
    ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_skins_profile_id ON public.skins(profile_id);
CREATE INDEX IF NOT EXISTS idx_skins_sha256 ON public.skins(sha256);

-- ------------------------------------------------------------------------------
-- 4. Row Level Security (RLS) Policies
-- ------------------------------------------------------------------------------
ALTER TABLE public.minecraft_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.skins ENABLE ROW LEVEL SECURITY;

-- Profiles: Authenticated users manage own profile
DROP POLICY IF EXISTS "Users can view own minecraft profile" ON public.minecraft_profiles;
CREATE POLICY "Users can view own minecraft profile"
    ON public.minecraft_profiles FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can insert own minecraft profile" ON public.minecraft_profiles;
CREATE POLICY "Users can insert own minecraft profile"
    ON public.minecraft_profiles FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can update own minecraft profile" ON public.minecraft_profiles;
CREATE POLICY "Users can update own minecraft profile"
    ON public.minecraft_profiles FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can delete own minecraft profile" ON public.minecraft_profiles;
CREATE POLICY "Users can delete own minecraft profile"
    ON public.minecraft_profiles FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- Skins: Authenticated users manage skins of their owned profiles
DROP POLICY IF EXISTS "Users can view own skins" ON public.skins;
CREATE POLICY "Users can view own skins"
    ON public.skins FOR SELECT
    TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.minecraft_profiles p 
        WHERE p.id = skins.profile_id AND p.user_id = auth.uid()
    ));

DROP POLICY IF EXISTS "Users can insert skins to own profile" ON public.skins;
CREATE POLICY "Users can insert skins to own profile"
    ON public.skins FOR INSERT
    TO authenticated
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.minecraft_profiles p 
        WHERE p.id = skins.profile_id AND p.user_id = auth.uid()
    ));

DROP POLICY IF EXISTS "Users can update own skins" ON public.skins;
CREATE POLICY "Users can update own skins"
    ON public.skins FOR UPDATE
    TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.minecraft_profiles p 
        WHERE p.id = skins.profile_id AND p.user_id = auth.uid()
    ));

DROP POLICY IF EXISTS "Users can delete own skins" ON public.skins;
CREATE POLICY "Users can delete own skins"
    ON public.skins FOR DELETE
    TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.minecraft_profiles p 
        WHERE p.id = skins.profile_id AND p.user_id = auth.uid()
    ));

-- ------------------------------------------------------------------------------
-- 5. Public RPC: Get Public Minecraft Profile (Privacy-Safe)
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_public_minecraft_profile(lookup_name text)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    result json;
BEGIN
    SELECT json_build_object(
        'uuid', p.uuid,
        'username', p.username,
        'skinModel', COALESCE(s.model, p.skin_model),
        'skinUrl', CASE 
            WHEN s.storage_path IS NOT NULL THEN 'https://ezzlauncher.dpdns.org/api/minecraft/texture/' || s.sha256
            ELSE NULL 
        END,
        'skinHash', s.sha256
    )
    INTO result
    FROM public.minecraft_profiles p
    LEFT JOIN public.skins s ON p.skin_id = s.id
    WHERE p.username_normalized = lower(trim(lookup_name))
      AND p.deleted_at IS NULL
    LIMIT 1;

    RETURN result;
END;
$$;

-- Allow anonymous & authenticated access to execute this lookup function
GRANT EXECUTE ON FUNCTION public.get_public_minecraft_profile(text) TO anon, authenticated;

-- ------------------------------------------------------------------------------
-- 6. Storage Bucket Configuration & Policies
-- ------------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public)
VALUES ('minecraft-skins', 'minecraft-skins', true)
ON CONFLICT (id) DO NOTHING;

-- Public read for textures
DROP POLICY IF EXISTS "Public access for skin textures" ON storage.objects;
CREATE POLICY "Public access for skin textures"
    ON storage.objects FOR SELECT
    TO public
    USING (bucket_id = 'minecraft-skins');

-- Authenticated upload
DROP POLICY IF EXISTS "Authenticated users can upload skin textures" ON storage.objects;
CREATE POLICY "Authenticated users can upload skin textures"
    ON storage.objects FOR INSERT
    TO authenticated
    WITH CHECK (bucket_id = 'minecraft-skins');
