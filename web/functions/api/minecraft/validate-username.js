/**
 * Cloudflare Pages Function: POST /api/minecraft/validate-username
 * Validates availability and syntax of requested Minecraft username.
 */
export async function onRequestPost(context) {
  const { request, env } = context;
  
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
  const RESERVED_NAMES = ['admin', 'moderator', 'system', 'ezz', 'ezzlauncher', 'mojang', 'minecraft', 'null', 'undefined'];

  if (username.length < 3 || username.length > 16) {
    return new Response(JSON.stringify({ valid: false, error: 'LENGTH_ERROR', message: 'Username must be between 3 and 16 characters' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  }

  if (!/^[a-zA-Z0-9_]+$/.test(username)) {
    return new Response(JSON.stringify({ valid: false, error: 'CHAR_ERROR', message: 'Username contains invalid characters (alphanumeric and _ only)' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  }

  if (RESERVED_NAMES.includes(username.toLowerCase())) {
    return new Response(JSON.stringify({ valid: false, error: 'RESERVED_NAME', message: 'This username is reserved by the platform' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  }

  const supabaseUrl = env?.SUPABASE_URL || 'https://api.ezzlauncher.com';
  const supabaseKey = env?.SUPABASE_ANON_KEY || 'public-anon-key-ezz';

  try {
    const res = await fetch(`${supabaseUrl}/rest/v1/minecraft_profiles?username_normalized=eq.${encodeURIComponent(username.toLowerCase())}&select=id`, {
      headers: {
        'apikey': supabaseKey,
        'Authorization': `Bearer ${supabaseKey}`
      }
    });

    if (res.ok) {
      const records = await res.json();
      if (records && records.length > 0) {
        return new Response(JSON.stringify({ valid: false, available: false, error: 'USERNAME_TAKEN', message: 'Username is already taken' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
        });
      }
    }

    return new Response(JSON.stringify({ valid: true, available: true, username, normalized: username.toLowerCase() }), {
      status: 200,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  } catch (err) {
    return new Response(JSON.stringify({ valid: true, available: true, warning: 'Offline fallback' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  }
}
