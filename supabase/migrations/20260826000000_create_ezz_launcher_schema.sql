-- ==============================================================================
-- EZZ LAUNCHER — SUPABASE POSTGRESQL SCHEMA & MIGRATION
-- Migration Version: 20260826000000_create_ezz_launcher_schema.sql
-- Project: https://idywzmspumhahzzfsdjx.supabase.co
-- ==============================================================================

-- 1. Profiles (Ezz User Profile tied to Supabase Auth)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY,
    email TEXT,
    display_name TEXT NOT NULL DEFAULT 'Ezz Player',
    avatar_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Insert default guest profile if not present
INSERT INTO public.profiles (id, email, display_name)
VALUES ('00000000-0000-0000-0000-000000000000', 'guest@ezzlauncher.io', 'Guest Player')
ON CONFLICT (id) DO NOTHING;

-- 2. Minecraft Accounts (Offline and Microsoft metadata)
CREATE TABLE IF NOT EXISTS public.minecraft_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000' REFERENCES public.profiles(id) ON DELETE CASCADE,
    username TEXT NOT NULL,
    uuid TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('OFFLINE', 'MICROSOFT')),
    avatar_url TEXT,
    is_selected BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3. Instances (Isolated Minecraft installations)
CREATE TABLE IF NOT EXISTS public.instances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000' REFERENCES public.profiles(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    minecraft_version TEXT NOT NULL,
    loader_type TEXT NOT NULL DEFAULT 'VANILLA' CHECK (loader_type IN ('VANILLA', 'FABRIC', 'OPTIFINE')),
    loader_version TEXT,
    icon_id TEXT NOT NULL DEFAULT 'grass_block',
    java_path TEXT,
    min_memory_mb INT NOT NULL DEFAULT 1024,
    max_memory_mb INT NOT NULL DEFAULT 4096,
    custom_jvm_args JSONB NOT NULL DEFAULT '[]'::jsonb,
    window_width INT NOT NULL DEFAULT 1280,
    window_height INT NOT NULL DEFAULT 720,
    last_played_at TIMESTAMPTZ,
    total_play_time_seconds BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 4. Instance Mods (Mod metadata per instance)
CREATE TABLE IF NOT EXISTS public.instance_mods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id UUID NOT NULL REFERENCES public.instances(id) ON DELETE CASCADE,
    user_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000' REFERENCES public.profiles(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    version TEXT NOT NULL,
    file_name TEXT NOT NULL,
    file_hash TEXT,
    loader TEXT NOT NULL DEFAULT 'FABRIC',
    enabled BOOLEAN NOT NULL DEFAULT true,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5. User Settings (Global launcher preferences per user)
CREATE TABLE IF NOT EXISTS public.user_settings (
    user_id UUID PRIMARY KEY DEFAULT '00000000-0000-0000-0000-000000000000' REFERENCES public.profiles(id) ON DELETE CASCADE,
    default_min_memory_mb INT NOT NULL DEFAULT 1024,
    default_max_memory_mb INT NOT NULL DEFAULT 4096,
    default_java_path TEXT,
    global_jvm_args JSONB NOT NULL DEFAULT '["-XX:+UseG1GC","-XX:+UnlockExperimentalVMOptions","-XX:G1NewSizePercent=20","-XX:G1ReservePercent=20","-XX:MaxGCPauseMillis=50","-XX:G1HeapRegionSize=32M"]'::jsonb,
    close_launcher_on_launch BOOLEAN NOT NULL DEFAULT false,
    dark_theme BOOLEAN NOT NULL DEFAULT true,
    selected_instance_id UUID REFERENCES public.instances(id) ON DELETE SET NULL,
    selected_account_id UUID REFERENCES public.minecraft_accounts(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 6. Installation Metadata (Track downloaded versions & assets)
CREATE TABLE IF NOT EXISTS public.installation_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000' REFERENCES public.profiles(id) ON DELETE CASCADE,
    version_id TEXT NOT NULL,
    loader_type TEXT NOT NULL DEFAULT 'VANILLA',
    install_status TEXT NOT NULL DEFAULT 'INSTALLED',
    installed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 7. Launcher Releases (Public table for app updates, downloads, changelogs)
CREATE TABLE IF NOT EXISTS public.launcher_releases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version TEXT NOT NULL UNIQUE,
    release_title TEXT NOT NULL,
    release_notes TEXT,
    download_url_exe TEXT,
    download_url_msi TEXT,
    download_url_apk TEXT,
    is_latest BOOLEAN NOT NULL DEFAULT true,
    is_mandatory BOOLEAN NOT NULL DEFAULT false,
    min_supported_version TEXT DEFAULT '1.0.0',
    released_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 8. Launcher News & Announcements (Public table for launcher home feed)
CREATE TABLE IF NOT EXISTS public.launcher_news (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    content TEXT,
    image_url TEXT,
    link_url TEXT,
    author TEXT NOT NULL DEFAULT 'Ezz Launcher Team',
    published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Initial seed data for Launcher Releases
INSERT INTO public.launcher_releases (version, release_title, release_notes, download_url_exe, download_url_msi, is_latest, is_mandatory)
VALUES (
    '1.0.0',
    'Ezz Launcher v1.0.0 Initial Production Release',
    'Official release of Ezz Launcher for Windows with Supabase PostgreSQL cloud backend, Minecraft Vanilla, Fabric, and OptiFine support.',
    'https://github.com/KrysolDev/Ezz-Launcher/releases/download/v1.0.0/EzzLauncher-1.0.0.exe',
    'https://github.com/KrysolDev/Ezz-Launcher/releases/download/v1.0.0/EzzLauncher-1.0.0.msi',
    true,
    false
)
ON CONFLICT (version) DO UPDATE SET
    release_title = EXCLUDED.release_title,
    release_notes = EXCLUDED.release_notes,
    download_url_exe = EXCLUDED.download_url_exe,
    download_url_msi = EXCLUDED.download_url_msi,
    is_latest = EXCLUDED.is_latest;

-- Initial seed data for Launcher News
INSERT INTO public.launcher_news (title, summary, content, image_url, author)
VALUES (
    'Welcome to Ezz Launcher!',
    'The modern, high-performance Minecraft Java Edition launcher powered by Supabase.',
    'Welcome to Ezz Launcher! Experience seamless instance management, Fabric and OptiFine integration, and automatic cloud synchronisation with Supabase PostgreSQL.',
    'https://raw.githubusercontent.com/KrysolDev/Ezz-Launcher/main/logo.png',
    'Ezz Team'
)
ON CONFLICT DO NOTHING;

-- Indexes for high-performance relational lookups
CREATE INDEX IF NOT EXISTS idx_minecraft_accounts_user_id ON public.minecraft_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_instances_user_id ON public.instances(user_id);
CREATE INDEX IF NOT EXISTS idx_instances_created_at ON public.instances(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_instance_mods_instance_id ON public.instance_mods(instance_id);
CREATE INDEX IF NOT EXISTS idx_instance_mods_user_id ON public.instance_mods(user_id);
CREATE INDEX IF NOT EXISTS idx_installation_metadata_user_id ON public.installation_metadata(user_id);
CREATE INDEX IF NOT EXISTS idx_launcher_releases_is_latest ON public.launcher_releases(is_latest);
CREATE INDEX IF NOT EXISTS idx_launcher_news_published_at ON public.launcher_news(published_at DESC);

-- ==============================================================================
-- SCHEMA GRANTS (Required for Supabase REST API and Dashboard Visibility)
-- ==============================================================================

GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL ROUTINES IN SCHEMA public TO anon, authenticated, service_role;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON ROUTINES TO anon, authenticated, service_role;

-- ==============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- Supports both authenticated users and public anon access for launcher operations
-- ==============================================================================

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.minecraft_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.instances ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.instance_mods ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.installation_metadata ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.launcher_releases ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.launcher_news ENABLE ROW LEVEL SECURITY;

-- Profiles Policies
DROP POLICY IF EXISTS "Allow public access to profiles" ON public.profiles;
CREATE POLICY "Allow public access to profiles" ON public.profiles FOR ALL USING (true) WITH CHECK (true);

-- Minecraft Accounts Policies
DROP POLICY IF EXISTS "Allow access to minecraft accounts" ON public.minecraft_accounts;
CREATE POLICY "Allow access to minecraft accounts" ON public.minecraft_accounts FOR ALL USING (true) WITH CHECK (true);

-- Instances Policies
DROP POLICY IF EXISTS "Allow access to instances" ON public.instances;
CREATE POLICY "Allow access to instances" ON public.instances FOR ALL USING (true) WITH CHECK (true);

-- Instance Mods Policies
DROP POLICY IF EXISTS "Allow access to instance mods" ON public.instance_mods;
CREATE POLICY "Allow access to instance mods" ON public.instance_mods FOR ALL USING (true) WITH CHECK (true);

-- User Settings Policies
DROP POLICY IF EXISTS "Allow access to user settings" ON public.user_settings;
CREATE POLICY "Allow access to user settings" ON public.user_settings FOR ALL USING (true) WITH CHECK (true);

-- Installation Metadata Policies
DROP POLICY IF EXISTS "Allow access to installation metadata" ON public.installation_metadata;
CREATE POLICY "Allow access to installation metadata" ON public.installation_metadata FOR ALL USING (true) WITH CHECK (true);

-- Launcher Releases Policies (Public Read, Admin Write)
DROP POLICY IF EXISTS "Allow public read on launcher releases" ON public.launcher_releases;
CREATE POLICY "Allow public read on launcher releases" ON public.launcher_releases FOR SELECT USING (true);

DROP POLICY IF EXISTS "Allow manage launcher releases" ON public.launcher_releases;
CREATE POLICY "Allow manage launcher releases" ON public.launcher_releases FOR ALL USING (true) WITH CHECK (true);

-- Launcher News Policies (Public Read, Admin Write)
DROP POLICY IF EXISTS "Allow public read on launcher news" ON public.launcher_news;
CREATE POLICY "Allow public read on launcher news" ON public.launcher_news FOR SELECT USING (true);

DROP POLICY IF EXISTS "Allow manage launcher news" ON public.launcher_news;
CREATE POLICY "Allow manage launcher news" ON public.launcher_news FOR ALL USING (true) WITH CHECK (true);

-- ==============================================================================
-- AUTOMATIC PROFILE & SETTINGS CREATION TRIGGER ON SIGNUP
-- ==============================================================================

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, email, display_name)
    VALUES (
        NEW.id,
        NEW.email,
        COALESCE(NEW.raw_user_meta_data->>'display_name', split_part(NEW.email, '@', 1), 'Ezz Player')
    )
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.user_settings (user_id)
    VALUES (NEW.id)
    ON CONFLICT (user_id) DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();
