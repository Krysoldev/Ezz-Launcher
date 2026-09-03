import { createClient } from '@supabase/supabase-js';

// Default Supabase Configuration (configurable at runtime or via environment variables)
const getStorageItem = (key) => (typeof localStorage !== 'undefined' ? localStorage.getItem(key) : null);
const DEFAULT_SUPABASE_URL = getStorageItem('ezz_supabase_url') || 'https://api.ezzlauncher.com';
const DEFAULT_SUPABASE_KEY = getStorageItem('ezz_supabase_anon_key') || 'public-anon-key-ezz';

let supabase = createClient(DEFAULT_SUPABASE_URL, DEFAULT_SUPABASE_KEY);

export function getSupabase() {
  return supabase;
}

export function updateSupabaseConfig(url, key) {
  if (!url || !key) return;
  localStorage.setItem('ezz_supabase_url', url);
  localStorage.setItem('ezz_supabase_anon_key', key);
  supabase = createClient(url, key);
}

// -------------------------------------------------------------
// SHA-256 and PNG Validation Helpers
// -------------------------------------------------------------
export async function computeSHA256(arrayBuffer) {
  const hashBuffer = await crypto.subtle.digest('SHA-256', arrayBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

export async function validatePngSkin(file) {
  if (!file) throw new Error('No file provided');
  if (file.size > 2 * 1024 * 1024) throw new Error('Skin file exceeds 2MB limit');

  const arrayBuffer = await file.arrayBuffer();
  const bytes = new Uint8Array(arrayBuffer);

  // Validate PNG magic bytes [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]
  if (
    bytes[0] !== 0x89 ||
    bytes[1] !== 0x50 ||
    bytes[2] !== 0x4E ||
    bytes[3] !== 0x47 ||
    bytes[4] !== 0x0D ||
    bytes[5] !== 0x0A ||
    bytes[6] !== 0x1A ||
    bytes[7] !== 0x0A
  ) {
    throw new Error('File is not a valid PNG image');
  }

  // Load image to verify dimensions
  return new Promise((resolve, reject) => {
    const blob = new Blob([arrayBuffer], { type: 'image/png' });
    const url = URL.createObjectURL(blob);
    const img = new Image();

    img.onload = async () => {
      URL.revokeObjectURL(url);
      const is64x64 = img.width === 64 && img.height === 64;
      const is64x32 = img.width === 64 && img.height === 32;

      if (!is64x64 && !is64x32) {
        return reject(new Error(`Invalid skin dimensions (${img.width}x${img.height}). Must be 64x64 or 64x32 pixels.`));
      }

      const sha256 = await computeSHA256(arrayBuffer);
      resolve({
        width: img.width,
        height: img.height,
        sha256,
        arrayBuffer,
        blob
      });
    };

    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('Corrupted image structure'));
    };

    img.src = url;
  });
}

// -------------------------------------------------------------
// Authentication
// -------------------------------------------------------------
export async function getCurrentUser() {
  const { data: { user } } = await supabase.auth.getUser();
  return user;
}

export async function resetPasswordForEmail(email) {
  const { error } = await supabase.auth.resetPasswordForEmail(email, {
    redirectTo: `${window.location.origin}/#reset-password`
  });
  if (error) throw error;
}

export async function updatePassword(newPassword) {
  const { error } = await supabase.auth.updateUser({ password: newPassword });
  if (error) throw error;
}

export async function deleteUserAccount() {
  const user = await getCurrentUser();
  if (!user) throw new Error('Not signed in');

  // Supabase RPC for cascading account deletion
  const { error } = await supabase.rpc('delete_current_user');
  if (error) {
    // If RPC not available, sign out
    await supabase.auth.signOut();
  }
}

// -------------------------------------------------------------
// Minecraft Profiles
// -------------------------------------------------------------
export async function getMinecraftProfile(userId) {
  if (!userId) return null;
  const { data, error } = await supabase
    .from('minecraft_profiles')
    .select('*, active_skin:skins!fk_active_skin(*)')
    .eq('user_id', userId)
    .is('deleted_at', null)
    .maybeSingle();

  if (error) {
    console.warn('Error fetching profile:', error);
  }
  return data;
}

export async function createMinecraftProfile(username, skinModel = 'STEVE') {
  const user = await getCurrentUser();
  if (!user) throw new Error('You must be signed in to create a profile.');

  const clean = username.trim();
  if (clean.length < 3 || clean.length > 16 || !/^[a-zA-Z0-9_]+$/.test(clean)) {
    throw new Error('Username must be 3-16 alphanumeric characters and underscores.');
  }

  const permanentUuid = crypto.randomUUID();

  const { data, error } = await supabase
    .from('minecraft_profiles')
    .insert({
      user_id: user.id,
      uuid: permanentUuid,
      username: clean,
      username_normalized: clean.toLowerCase(),
      skin_model: skinModel
    })
    .select()
    .single();

  if (error) {
    if (error.message.includes('unique') || error.code === '23505') {
      throw new Error(`The Minecraft username '${clean}' is already taken.`);
    }
    throw error;
  }
  return data;
}

// -------------------------------------------------------------
// Skin Storage & Management
// -------------------------------------------------------------
export async function getUserSkins(profileId) {
  if (!profileId) return [];
  const { data, error } = await supabase
    .from('skins')
    .select('*')
    .eq('profile_id', profileId)
    .order('created_at', { ascending: false });

  if (error) throw error;
  return data || [];
}

export async function uploadSkinFile(profile, file, skinName = 'Custom Skin', model = 'STEVE') {
  const user = await getCurrentUser();
  if (!user) throw new Error('Not signed in');
  if (!profile) throw new Error('No active Minecraft profile');

  // 1. Strict binary and dimension validation
  const validation = await validatePngSkin(file);
  const storagePath = `${profile.uuid}/${validation.sha256}.png`;

  // 2. Upload to Supabase Storage bucket 'minecraft-skins'
  const { error: uploadError } = await supabase.storage
    .from('minecraft-skins')
    .upload(storagePath, validation.blob, {
      contentType: 'image/png',
      upsert: true
    });

  if (uploadError && !uploadError.message.includes('already exists')) {
    console.warn('Storage upload notice:', uploadError);
  }

  // 3. Insert metadata into 'skins' table
  const { data: skinRecord, error: dbError } = await supabase
    .from('skins')
    .insert({
      profile_id: profile.id,
      name: skinName.trim() || 'Custom Skin',
      storage_path: storagePath,
      sha256: validation.sha256,
      model: model,
      width: validation.width,
      height: validation.height,
      file_size: file.size
    })
    .select()
    .single();

  if (dbError) throw dbError;

  // 4. Automatically set newly uploaded skin as active
  await setActiveSkin(profile.id, skinRecord.id, model);

  return skinRecord;
}

export async function setActiveSkin(profileId, skinId, model = null) {
  const updates = {
    skin_id: skinId,
    updated_at: new Date().toISOString()
  };
  if (model) {
    updates.skin_model = model;
  }

  const { data, error } = await supabase
    .from('minecraft_profiles')
    .update(updates)
    .eq('id', profileId)
    .select()
    .single();

  if (error) throw error;
  return data;
}

export async function renameSkin(skinId, newName) {
  const clean = newName.trim();
  if (!clean) throw new Error('Skin name cannot be empty');

  const { data, error } = await supabase
    .from('skins')
    .update({ name: clean, updated_at: new Date().toISOString() })
    .eq('id', skinId)
    .select()
    .single();

  if (error) throw error;
  return data;
}

export async function deleteSkin(skinId, profile) {
  if (profile.skin_id === skinId) {
    throw new Error('Cannot delete the active skin. Please select another active skin first.');
  }

  const { error } = await supabase
    .from('skins')
    .delete()
    .eq('id', skinId)
    .eq('profile_id', profile.id);

  if (error) throw error;
}
