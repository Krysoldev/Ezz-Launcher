-- ==============================================================================
-- EZZ LAUNCHER — SECURE ADMIN RELEASE SYSTEM & ADMIN AUTHORIZATION
-- ==============================================================================

-- 1. Admin Users Table (Stores authorized admin identities)
CREATE TABLE IF NOT EXISTS public.admin_users (
    username TEXT PRIMARY KEY,
    role TEXT NOT NULL DEFAULT 'ADMIN',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Pre-seed verified admin identity
INSERT INTO public.admin_users (username, role, is_active)
VALUES ('KrysolDev', 'ADMIN', true)
ON CONFLICT (username) DO NOTHING;

-- Enable RLS
ALTER TABLE public.admin_users ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow public read on admin_users" ON public.admin_users;
CREATE POLICY "Allow public read on admin_users"
    ON public.admin_users FOR SELECT
    TO anon, authenticated
    USING (true);

-- ------------------------------------------------------------------------------
-- 2. Function: Check if username is an authorized admin
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.is_admin_user(lookup_username text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF lookup_username IS NULL OR trim(lookup_username) = '' THEN
        RETURN false;
    END IF;

    RETURN EXISTS (
        SELECT 1 FROM public.admin_users
        WHERE lower(username) = lower(trim(lookup_username))
          AND is_active = true
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.is_admin_user(text) TO anon, authenticated;

-- ------------------------------------------------------------------------------
-- 3. Function: Secure Server-Enforced Release Publisher
-- Only authorized admin ('KrysolDev') can execute successfully.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.publish_launcher_release(
    p_admin_username text,
    p_version text,
    p_platform text DEFAULT 'windows',
    p_download_url text DEFAULT NULL,
    p_release_notes text DEFAULT NULL,
    p_is_latest boolean DEFAULT true,
    p_is_required boolean DEFAULT false
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    is_authorized boolean;
    release_id uuid;
BEGIN
    -- Enforce admin authorization server-side
    SELECT EXISTS (
        SELECT 1 FROM public.admin_users
        WHERE lower(username) = lower(trim(p_admin_username))
          AND is_active = true
    ) INTO is_authorized;

    IF NOT is_authorized THEN
        RAISE EXCEPTION 'UNAUTHORIZED: User "%" is not an authorized administrator.', p_admin_username;
    END IF;

    -- If this is marked latest, demote previous latest releases for this platform
    IF p_is_latest THEN
        UPDATE public.launcher_releases
        SET is_latest = false,
            updated_at = now()
        WHERE platform = p_platform
          AND is_latest = true;
    END IF;

    -- Upsert release record
    INSERT INTO public.launcher_releases (
        version,
        platform,
        download_url,
        release_notes,
        is_latest,
        is_required,
        is_active,
        published_at,
        updated_at
    ) VALUES (
        trim(p_version),
        p_platform,
        p_download_url,
        p_release_notes,
        p_is_latest,
        p_is_required,
        true,
        now(),
        now()
    )
    ON CONFLICT (version) DO UPDATE SET
        platform = EXCLUDED.platform,
        download_url = EXCLUDED.download_url,
        release_notes = EXCLUDED.release_notes,
        is_latest = EXCLUDED.is_latest,
        is_required = EXCLUDED.is_required,
        is_active = true,
        updated_at = now()
    RETURNING id INTO release_id;

    RETURN jsonb_build_object(
        'success', true,
        'release_id', release_id,
        'version', trim(p_version),
        'platform', p_platform
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.publish_launcher_release(text, text, text, text, text, boolean, boolean) TO anon, authenticated;
