-- ==============================================================================
-- EZZ LAUNCHER & WEBSITE — SUPABASE DATABASE SCHEMA
-- Centralized Account, Minecraft Profile, Cloud Skin & Session Architecture
-- ==============================================================================

-- 1. Create Table: minecraft_profiles
CREATE TABLE IF NOT EXISTS public.minecraft_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    uuid UUID NOT NULL UNIQUE, -- Permanent Stable Minecraft UUID (generated once)
    username VARCHAR(16) NOT NULL,
    username_normalized VARCHAR(16) NOT NULL UNIQUE,
    skin_id UUID,
    skin_model VARCHAR(10) NOT NULL DEFAULT 'STEVE', -- 'STEVE' (Classic) or 'ALEX' (Slim)
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    deleted_at TIMESTAMPTZ, -- Soft delete grace period

    CONSTRAINT valid_username_length CHECK (char_length(username) >= 3 AND char_length(username) <= 16),
    CONSTRAINT valid_username_chars CHECK (username ~ '^[a-zA-Z0-9_]+$'),
    CONSTRAINT valid_skin_model CHECK (skin_model IN ('STEVE', 'ALEX'))
);

-- Index for instant public lookup by normalized username
CREATE INDEX IF NOT EXISTS idx_minecraft_profiles_normalized 
ON public.minecraft_profiles(username_normalized) 
WHERE deleted_at IS NULL;

-- Index for lookup by UUID
CREATE INDEX IF NOT EXISTS idx_minecraft_profiles_uuid 
ON public.minecraft_profiles(uuid) 
WHERE deleted_at IS NULL;

-- 2. Create Table: skins
CREATE TABLE IF NOT EXISTS public.skins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL REFERENCES public.minecraft_profiles(id) ON DELETE CASCADE,
    storage_path TEXT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    model VARCHAR(10) NOT NULL DEFAULT 'STEVE',
    width INT NOT NULL DEFAULT 64,
    height INT NOT NULL DEFAULT 64,
    file_size BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),

    CONSTRAINT valid_skin_dims CHECK (width = 64 AND (height = 64 OR height = 32)),
    CONSTRAINT valid_skin_model CHECK (model IN ('STEVE', 'ALEX'))
);

CREATE INDEX IF NOT EXISTS idx_skins_profile_id ON public.skins(profile_id);
CREATE INDEX IF NOT EXISTS idx_skins_sha256 ON public.skins(sha256);

-- Foreign key link from profile active skin to skins table
ALTER TABLE public.minecraft_profiles 
DROP CONSTRAINT IF EXISTS fk_active_skin;

ALTER TABLE public.minecraft_profiles 
ADD CONSTRAINT fk_active_skin 
FOREIGN KEY (skin_id) REFERENCES public.skins(id) ON DELETE SET NULL;

-- 3. Row Level Security (RLS)
ALTER TABLE public.minecraft_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.skins ENABLE ROW LEVEL SECURITY;

-- Profiles RLS: Users can only CRUD their own profiles
DROP POLICY IF EXISTS "Users can view own profile" ON public.minecraft_profiles;
CREATE POLICY "Users can view own profile" 
ON public.minecraft_profiles FOR SELECT 
TO authenticated 
USING (auth.uid() = user_id AND deleted_at IS NULL);

DROP POLICY IF EXISTS "Users can create own profile" ON public.minecraft_profiles;
CREATE POLICY "Users can create own profile" 
ON public.minecraft_profiles FOR INSERT 
TO authenticated 
WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can update own profile" ON public.minecraft_profiles;
CREATE POLICY "Users can update own profile" 
ON public.minecraft_profiles FOR UPDATE 
TO authenticated 
USING (auth.uid() = user_id AND deleted_at IS NULL)
WITH CHECK (auth.uid() = user_id);

-- Skins RLS: Users can only view/manage skins belonging to their profiles
DROP POLICY IF EXISTS "Users can view own skins" ON public.skins;
CREATE POLICY "Users can view own skins" 
ON public.skins FOR SELECT 
TO authenticated 
USING (
    EXISTS (
        SELECT 1 FROM public.minecraft_profiles p 
        WHERE p.id = skins.profile_id AND p.user_id = auth.uid()
    )
);

DROP POLICY IF EXISTS "Users can insert own skins" ON public.skins;
CREATE POLICY "Users can insert own skins" 
ON public.skins FOR INSERT 
TO authenticated 
WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.minecraft_profiles p 
        WHERE p.id = skins.profile_id AND p.user_id = auth.uid()
    )
);

DROP POLICY IF EXISTS "Users can delete own skins" ON public.skins;
CREATE POLICY "Users can delete own skins" 
ON public.skins FOR DELETE 
TO authenticated 
USING (
    EXISTS (
        SELECT 1 FROM public.minecraft_profiles p 
        WHERE p.id = skins.profile_id AND p.user_id = auth.uid()
    )
);

-- 4. Secure Public Profile Lookup RPC Function
-- Used by EzzLauncher and public session API to resolve profiles safely.
-- PRIVACY: Exposes ONLY Minecraft public metadata (username, UUID, skin URL, model, hash).
-- NEVER exposes user_id, email, tokens, or private timestamps.
CREATE OR REPLACE FUNCTION public.get_public_minecraft_profile(lookup_name text)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    clean_name text;
    result JSONB;
BEGIN
    clean_name := lower(trim(lookup_name));
    
    IF clean_name IS NULL OR length(clean_name) < 3 OR length(clean_name) > 16 THEN
        RETURN NULL;
    END IF;

    SELECT jsonb_build_object(
        'uuid', p.uuid::text,
        'username', p.username,
        'usernameNormalized', p.username_normalized,
        'skinUrl', CASE 
            WHEN s.storage_path IS NOT NULL THEN 'https://api.ezzlauncher.com/storage/v1/object/public/skins/' || s.storage_path
            ELSE NULL 
        END,
        'skinModel', p.skin_model,
        'skinHash', s.sha256,
        'capeUrl', NULL
    ) INTO result
    FROM public.minecraft_profiles p
    LEFT JOIN public.skins s ON p.skin_id = s.id
    WHERE (p.username_normalized = clean_name OR p.uuid::text = clean_name)
      AND p.deleted_at IS NULL
    LIMIT 1;

    RETURN result;
END;
$$;

-- Grant public anonymous access to the safe profile lookup function
GRANT EXECUTE ON FUNCTION public.get_public_minecraft_profile(text) TO anon, authenticated;
