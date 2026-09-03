/**
 * Cloudflare Pages Function: GET /api/minecraft/profile/:username
 * Public Minecraft-compatible Profile Resolution Endpoint for EzzLauncher
 *
 * Privacy & Security:
 * - Exposes ONLY public Minecraft properties (username, stable UUID, skin URL, model).
 * - Never leaks user_id, email, password hashes, or private tokens.
 */
export async function onRequestGet(context) {
  const { params, env } = context;
  const username = (params.username || '').trim();

  // 1. Input validation
  if (!username || username.length < 3 || username.length > 16 || !/^[a-zA-Z0-9_]+$/.test(username)) {
    return new Response(JSON.stringify({ error: 'INVALID_USERNAME', message: 'Username must be 3-16 alphanumeric characters' }), {
      status: 400,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  }

  const supabaseUrl = env?.SUPABASE_URL || 'https://api.ezzlauncher.com';
  const supabaseKey = env?.SUPABASE_ANON_KEY || 'public-anon-key-ezz';

  try {
    // Call secure Postgres RPC get_public_minecraft_profile
    const rpcRes = await fetch(`${supabaseUrl}/rest/v1/rpc/get_public_minecraft_profile`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'apikey': supabaseKey,
        'Authorization': `Bearer ${supabaseKey}`
      },
      body: JSON.stringify({ lookup_name: username.toLowerCase() })
    });

    if (!rpcRes.ok) {
      return new Response(JSON.stringify({ error: 'LOOKUP_FAILED', message: 'Error communicating with profile database' }), {
        status: 502,
        headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
      });
    }

    const profileData = await rpcRes.json();

    if (!profileData || !profileData.uuid) {
      return new Response(JSON.stringify({ error: 'PROFILE_NOT_FOUND', message: `No Ezz Minecraft profile found for '${username}'` }), {
        status: 404,
        headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
      });
    }

    // Format Minecraft Yggdrasil-compatible textures payload
    const isSlim = profileData.skinModel === 'ALEX';
    const textureUrl = profileData.skinUrl || `https://ezzlauncher.dpdns.org/api/minecraft/texture/default_steve`;

    const texturesPayload = {
      timestamp: Date.now(),
      profileId: profileData.uuid.replace(/-/g, ''),
      profileName: profileData.username,
      textures: {
        SKIN: {
          url: textureUrl,
          ...(isSlim ? { metadata: { model: 'slim' } } : {})
        }
      }
    };

    const base64Textures = btoa(JSON.stringify(texturesPayload));

    const responsePayload = {
      id: profileData.uuid.replace(/-/g, ''),
      uuid: profileData.uuid,
      name: profileData.username,
      properties: [
        {
          name: 'textures',
          value: base64Textures
        }
      ]
    };

    return new Response(JSON.stringify(responsePayload, null, 2), {
      status: 200,
      headers: {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
        'Cache-Control': 'public, max-age=60, s-maxage=300, stale-while-revalidate=600'
      }
    });
  } catch (err) {
    return new Response(JSON.stringify({ error: 'INTERNAL_ERROR', message: err.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  }
}
