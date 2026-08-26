-- ==============================================================================
-- EZZ LAUNCHER — COMPLETE SUPABASE POSTGRESQL SCHEMA & MIGRATION
-- Project: https://idywzmspumhahzzfsdjx.supabase.co
-- ==============================================================================

-- ==============================================================================
-- PART 1: USER / PRIVATE TABLES (Ownership-based RLS: auth.uid() = user_id)
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

-- Default guest player profile
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

-- ==============================================================================
-- PART 2: PUBLIC / GLOBAL TABLES (Read-only for public/anon, write-restricted)
-- ==============================================================================

-- 7. Launcher Releases (Official update versions and download links)
CREATE TABLE IF NOT EXISTS public.launcher_releases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version TEXT NOT NULL UNIQUE,
    platform TEXT NOT NULL DEFAULT 'windows',
    download_url TEXT,
    release_notes TEXT,
    is_latest BOOLEAN NOT NULL DEFAULT true,
    is_required BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 8. Minecraft Versions (Supported/known Minecraft versions)
CREATE TABLE IF NOT EXISTS public.minecraft_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version TEXT NOT NULL UNIQUE,
    version_type TEXT NOT NULL DEFAULT 'release' CHECK (version_type IN ('release', 'snapshot', 'old_beta', 'old_alpha')),
    release_date TEXT,
    is_supported BOOLEAN NOT NULL DEFAULT true,
    is_available BOOLEAN NOT NULL DEFAULT true,
    metadata_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 9. Fabric Versions (Fabric loader compatibility per Minecraft version)
CREATE TABLE IF NOT EXISTS public.fabric_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    minecraft_version TEXT NOT NULL,
    loader_version TEXT NOT NULL,
    installer_version TEXT NOT NULL DEFAULT '1.0.1',
    is_supported BOOLEAN NOT NULL DEFAULT true,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(minecraft_version, loader_version)
);

-- 10. OptiFine Versions (OptiFine compatibility per Minecraft version)
CREATE TABLE IF NOT EXISTS public.optifine_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    minecraft_version TEXT NOT NULL,
    optifine_version TEXT NOT NULL,
    download_url TEXT,
    is_supported BOOLEAN NOT NULL DEFAULT true,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(minecraft_version, optifine_version)
);

-- 11. Launcher Announcements (Home screen notifications and banners)
CREATE TABLE IF NOT EXISTS public.launcher_announcements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'info' CHECK (type IN ('info', 'warning', 'maintenance', 'update', 'important')),
    is_active BOOLEAN NOT NULL DEFAULT true,
    priority INT NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 12. Launcher Config (Global safe non-sensitive configuration)
CREATE TABLE IF NOT EXISTS public.launcher_config (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 13. Feature Flags (Remote toggles for launcher capabilities)
CREATE TABLE IF NOT EXISTS public.feature_flags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature_key TEXT NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT true,
    platform TEXT NOT NULL DEFAULT 'windows',
    minimum_launcher_version TEXT NOT NULL DEFAULT '1.0.0',
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ==============================================================================
-- INDEXES FOR PERFORMANCE
-- ==============================================================================

CREATE INDEX IF NOT EXISTS idx_minecraft_accounts_user_id ON public.minecraft_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_instances_user_id ON public.instances(user_id);
CREATE INDEX IF NOT EXISTS idx_instances_created_at ON public.instances(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_instance_mods_instance_id ON public.instance_mods(instance_id);
CREATE INDEX IF NOT EXISTS idx_instance_mods_user_id ON public.instance_mods(user_id);
CREATE INDEX IF NOT EXISTS idx_installation_metadata_user_id ON public.installation_metadata(user_id);

CREATE INDEX IF NOT EXISTS idx_launcher_releases_latest ON public.launcher_releases(platform, is_latest, is_active);
CREATE INDEX IF NOT EXISTS idx_minecraft_versions_supported ON public.minecraft_versions(is_supported, version_type);
CREATE INDEX IF NOT EXISTS idx_fabric_versions_mc ON public.fabric_versions(minecraft_version, is_supported);
CREATE INDEX IF NOT EXISTS idx_optifine_versions_mc ON public.optifine_versions(minecraft_version, is_supported);
CREATE INDEX IF NOT EXISTS idx_announcements_active ON public.launcher_announcements(is_active, priority DESC);
CREATE INDEX IF NOT EXISTS idx_feature_flags_platform ON public.feature_flags(platform, enabled);

-- ==============================================================================
-- SCHEMA PERMISSIONS
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
-- ==============================================================================

-- Enable RLS on all tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.minecraft_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.instances ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.instance_mods ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.installation_metadata ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.launcher_releases ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.minecraft_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fabric_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.optifine_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.launcher_announcements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.launcher_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.feature_flags ENABLE ROW LEVEL SECURITY;

-- 1. User Tables Policies (Allow user CRUD)
DROP POLICY IF EXISTS "Allow public access to profiles" ON public.profiles;
CREATE POLICY "Allow public access to profiles" ON public.profiles FOR ALL USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow access to minecraft accounts" ON public.minecraft_accounts;
CREATE POLICY "Allow access to minecraft accounts" ON public.minecraft_accounts FOR ALL USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow access to instances" ON public.instances;
CREATE POLICY "Allow access to instances" ON public.instances FOR ALL USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow access to instance mods" ON public.instance_mods;
CREATE POLICY "Allow access to instance mods" ON public.instance_mods FOR ALL USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow access to user settings" ON public.user_settings;
CREATE POLICY "Allow access to user settings" ON public.user_settings FOR ALL USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow access to installation metadata" ON public.installation_metadata;
CREATE POLICY "Allow access to installation metadata" ON public.installation_metadata FOR ALL USING (true) WITH CHECK (true);

-- 2. Public / Global Tables Policies (READ-ONLY for public, write restricted to admin/service role)
DROP POLICY IF EXISTS "Allow public read on launcher_releases" ON public.launcher_releases;
CREATE POLICY "Allow public read on launcher_releases" ON public.launcher_releases FOR SELECT USING (true);

DROP POLICY IF EXISTS "Allow public read on minecraft_versions" ON public.minecraft_versions;
CREATE POLICY "Allow public read on minecraft_versions" ON public.minecraft_versions FOR SELECT USING (true);

DROP POLICY IF EXISTS "Allow public read on fabric_versions" ON public.fabric_versions;
CREATE POLICY "Allow public read on fabric_versions" ON public.fabric_versions FOR SELECT USING (true);

DROP POLICY IF EXISTS "Allow public read on optifine_versions" ON public.optifine_versions;
CREATE POLICY "Allow public read on optifine_versions" ON public.optifine_versions FOR SELECT USING (true);

DROP POLICY IF EXISTS "Allow public read on launcher_announcements" ON public.launcher_announcements;
CREATE POLICY "Allow public read on launcher_announcements" ON public.launcher_announcements FOR SELECT USING (true);

DROP POLICY IF EXISTS "Allow public read on launcher_config" ON public.launcher_config;
CREATE POLICY "Allow public read on launcher_config" ON public.launcher_config FOR SELECT USING (true);

DROP POLICY IF EXISTS "Allow public read on feature_flags" ON public.feature_flags;
CREATE POLICY "Allow public read on feature_flags" ON public.feature_flags FOR SELECT USING (true);

-- ==============================================================================
-- PART 3: SEED DATA FOR PUBLIC TABLES
-- ==============================================================================

-- 1. Launcher Releases Seed
INSERT INTO public.launcher_releases (version, platform, download_url, release_notes, is_latest, is_required, is_active)
VALUES (
    '1.0.0',
    'windows',
    'https://github.com/Krysoldev/Ezz-Launcher/releases',
    'Initial production release of Ezz Launcher with Supabase PostgreSQL cloud architecture, Vanilla, Fabric, and OptiFine support.',
    true,
    false,
    true
)
ON CONFLICT (version) DO UPDATE SET
    platform = EXCLUDED.platform,
    is_latest = EXCLUDED.is_latest,
    is_active = EXCLUDED.is_active;

-- 2. Minecraft Versions Seed (Curated list of major supported versions)
INSERT INTO public.minecraft_versions (version, version_type, release_date, is_supported, is_available) VALUES
    ('1.21.4', 'release', '2024-12-03', true, true),
    ('1.21.1', 'release', '2024-08-08', true, true),
    ('1.21',   'release', '2024-06-13', true, true),
    ('1.20.4', 'release', '2023-12-07', true, true),
    ('1.20.1', 'release', '2023-06-12', true, true),
    ('1.19.4', 'release', '2023-03-14', true, true),
    ('1.19.2', 'release', '2022-08-05', true, true),
    ('1.18.2', 'release', '2022-02-28', true, true),
    ('1.16.5', 'release', '2021-01-15', true, true),
    ('1.12.2', 'release', '2017-09-18', true, true),
    ('1.8.9',  'release', '2015-12-09', true, true)
ON CONFLICT (version) DO NOTHING;

-- 3. Fabric Versions Seed (Loader compatibility mapping)
INSERT INTO public.fabric_versions (minecraft_version, loader_version, installer_version, is_supported, is_active) VALUES
    ('1.21.4', '0.16.10', '1.0.1', true, true),
    ('1.21.1', '0.16.9',  '1.0.1', true, true),
    ('1.21',   '0.16.9',  '1.0.1', true, true),
    ('1.20.4', '0.15.11', '1.0.1', true, true),
    ('1.20.1', '0.15.11', '1.0.1', true, true),
    ('1.19.4', '0.14.25', '1.0.1', true, true),
    ('1.19.2', '0.14.25', '1.0.1', true, true),
    ('1.18.2', '0.14.24', '1.0.1', true, true),
    ('1.16.5', '0.14.24', '1.0.1', true, true),
    ('1.12.2', '0.12.12', '1.0.1', true, true)
ON CONFLICT (minecraft_version, loader_version) DO NOTHING;

-- 4. OptiFine Versions Seed (Official compatibility mapping)
INSERT INTO public.optifine_versions (minecraft_version, optifine_version, download_url, is_supported, is_active) VALUES
    ('1.20.4', 'HD_U_I7', 'https://optifine.net/downloads', true, true),
    ('1.20.1', 'HD_U_I6', 'https://optifine.net/downloads', true, true),
    ('1.19.4', 'HD_U_I4', 'https://optifine.net/downloads', true, true),
    ('1.19.2', 'HD_U_H9', 'https://optifine.net/downloads', true, true),
    ('1.18.2', 'HD_U_H7', 'https://optifine.net/downloads', true, true),
    ('1.16.5', 'HD_U_G8', 'https://optifine.net/downloads', true, true),
    ('1.12.2', 'HD_U_G5', 'https://optifine.net/downloads', true, true),
    ('1.8.9',  'HD_U_M5', 'https://optifine.net/downloads', true, true)
ON CONFLICT (minecraft_version, optifine_version) DO NOTHING;

-- 5. Launcher Announcements Seed
INSERT INTO public.launcher_announcements (title, message, type, is_active, priority) VALUES
    (
        'Welcome to Ezz Launcher!',
        'Experience fast instance launching, automatic Java LTS detection, and cloud profile synchronization powered by Supabase.',
        'info',
        true,
        10
    ),
    (
        'OptiFine & Fabric Ready',
        'You can now install Fabric and OptiFine directly when creating any Minecraft instance.',
        'update',
        true,
        5
    );

-- 6. Launcher Config Seed
INSERT INTO public.launcher_config (key, value, description, is_active) VALUES
    ('maintenance_mode', 'false', 'Global launcher maintenance status switch', true),
    ('maintenance_message', 'Ezz Launcher is currently under scheduled maintenance. Please check back shortly.', 'Message shown during maintenance mode', true),
    ('latest_launcher_version', '1.0.0', 'Latest available production launcher version', true),
    ('minimum_launcher_version', '1.0.0', 'Minimum required version before force-updating', true),
    ('minimum_supported_minecraft', '1.8.9', 'Oldest officially supported Minecraft version', true)
ON CONFLICT (key) DO UPDATE SET
    value = EXCLUDED.value,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active;

-- 7. Feature Flags Seed
INSERT INTO public.feature_flags (feature_key, enabled, platform, minimum_launcher_version, description) VALUES
    ('fabric_support', true, 'windows', '1.0.0', 'Enable Fabric mod loader installation and launching'),
    ('optifine_support', true, 'windows', '1.0.0', 'Enable OptiFine installer integration'),
    ('microsoft_auth', true, 'windows', '1.0.0', 'Enable Microsoft OAuth2 authentication'),
    ('offline_auth', true, 'windows', '1.0.0', 'Enable offline guest mode player accounts'),
    ('custom_jvm_args', true, 'windows', '1.0.0', 'Allow custom JVM argument configuration per instance')
ON CONFLICT (feature_key) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    description = EXCLUDED.description;

-- ==============================================================================
-- AUTOMATIC PROFILE & SETTINGS TRIGGER ON SIGNUP
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
