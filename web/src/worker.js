/**
 * Official Cloudflare Worker & API Router for Ezz Platform
 * Serverless / VPS-less Architecture
 */
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;

    // 1. API Route: GET /api/minecraft/profile/:username
    if (path.startsWith('/api/minecraft/profile/') && request.method === 'GET') {
      const username = decodeURIComponent(path.replace('/api/minecraft/profile/', '').trim());
      return handleProfileLookup(username, env);
    }

    // 2. API Route: GET /api/minecraft/texture/:hash
    if (path.startsWith('/api/minecraft/texture/') && request.method === 'GET') {
      const hash = path.replace('/api/minecraft/texture/', '').trim();
      return handleTextureProxy(hash, env);
    }

    // 3. API Route: POST /api/minecraft/validate-username
    if (path === '/api/minecraft/validate-username' && request.method === 'POST') {
      return handleUsernameValidation(request, env);
    }

    // 4. Default: Serve Static SPA Assets from /dist
    if (env.ASSETS) {
      return env.ASSETS.fetch(request);
    }

    return new Response('Ezz Platform Worker Online', { status: 200 });
  }
};

// -------------------------------------------------------------
// Handlers
// -------------------------------------------------------------
async function handleProfileLookup(username, env) {
  if (!username || username.length < 3 || username.length > 16 || !/^[a-zA-Z0-9_]+$/.test(username)) {
    return new Response(JSON.stringify({ error: 'INVALID_USERNAME', message: 'Username must be 3-16 alphanumeric characters' }), {
      status: 400,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  }

  const supabaseUrl = env?.SUPABASE_URL || 'https://api.ezzlauncher.com';
  const supabaseKey = env?.SUPABASE_ANON_KEY || 'public-anon-key-ezz';

  try {
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
      return new Response(JSON.stringify({ error: 'LOOKUP_FAILED', message: 'Database query error' }), {
        status: 502,
        headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
      });
    }

    const profileData = await rpcRes.json();
    if (!profileData || !profileData.uuid) {
      return new Response(JSON.stringify({ error: 'PROFILE_NOT_FOUND', message: `No profile for '${username}'` }), {
        status: 404,
        headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
      });
    }

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

    return new Response(JSON.stringify({
      id: profileData.uuid.replace(/-/g, ''),
      uuid: profileData.uuid,
      name: profileData.username,
      properties: [{ name: 'textures', value: base64Textures }]
    }, null, 2), {
      status: 200,
      headers: {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
        'Cache-Control': 'public, max-age=60, s-maxage=300'
      }
    });
  } catch (err) {
    return new Response(JSON.stringify({ error: 'INTERNAL_ERROR', message: err.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  }
}

async function handleTextureProxy(hash, env) {
  if (!hash || hash.length < 4 || !/^[a-zA-Z0-9_\-]+$/.test(hash)) {
    return new Response('Invalid texture hash', { status: 400 });
  }

  const supabaseUrl = env?.SUPABASE_URL || 'https://api.ezzlauncher.com';
  const supabaseStorageUrl = `${supabaseUrl}/storage/v1/object/public/minecraft-skins/${hash}.png`;

  try {
    const res = await fetch(supabaseStorageUrl);
    if (!res.ok) {
      return new Response('Texture not found', { status: 404 });
    }

    const imageBytes = await res.arrayBuffer();
    return new Response(imageBytes, {
      status: 200,
      headers: {
        'Content-Type': 'image/png',
        'Access-Control-Allow-Origin': '*',
        'Cache-Control': 'public, max-age=31536000, s-maxage=31536000, immutable',
        'ETag': `"${hash}"`
      }
    });
  } catch (err) {
    return new Response(`Error retrieving texture: ${err.message}`, { status: 500 });
  }
}

async function handleUsernameValidation(request, env) {
  let body;
  try {
    body = await request.json();
  } catch (e) {
    return new Response(JSON.stringify({ valid: false, error: 'INVALID_JSON' }), {
      status: 400,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  }

  const username = (body.username || '').trim();
  const RESERVED_NAMES = ['admin', 'moderator', 'system', 'ezz', 'ezzlauncher', 'mojang', 'minecraft'];

  if (username.length < 3 || username.length > 16 || !/^[a-zA-Z0-9_]+$/.test(username) || RESERVED_NAMES.includes(username.toLowerCase())) {
    return new Response(JSON.stringify({ valid: false, error: 'INVALID_OR_RESERVED' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  }

  return new Response(JSON.stringify({ valid: true, available: true, username, normalized: username.toLowerCase() }), {
    status: 200,
    headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
  });
}
