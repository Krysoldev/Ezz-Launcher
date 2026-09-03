/**
 * Cloudflare Pages Function: GET /api/minecraft/texture/:hash
 * High-performance edge CDN proxy for Minecraft skin textures.
 *
 * Features:
 * - Immutable CDN caching (Cache-Control: public, max-age=31536000, immutable)
 * - Returns raw PNG binary bytes for Minecraft game rendering
 */
export async function onRequestGet(context) {
  const { params, env } = context;
  const hash = (params.hash || '').trim();

  if (!hash || hash.length < 4 || !/^[a-zA-Z0-9_\-]+$/.test(hash)) {
    return new Response('Invalid skin hash', { status: 400 });
  }

  const supabaseUrl = env?.SUPABASE_URL || 'https://api.ezzlauncher.com';
  const supabaseStorageUrl = `${supabaseUrl}/storage/v1/object/public/minecraft-skins/${hash}.png`;

  try {
    const res = await fetch(supabaseStorageUrl);
    if (!res.ok) {
      // Fallback redirect or 404
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
