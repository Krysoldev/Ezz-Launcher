# Ezz Public Minecraft API Reference

All public endpoints are privacy-safe, edge-cached by Cloudflare, and do not expose private user credentials.

---

## 1. Resolve Profile by Username

### `GET /api/minecraft/profile/:username`

Resolves public Minecraft profile data (stable UUID, active skin URL, and arm model) for EzzLauncher.

#### Request Parameters
- `username` (string, required): Minecraft username (3-16 alphanumeric characters).

#### Success Response (`200 OK`)
```json
{
  "id": "c061596af66f42e79a4f561b29d49463",
  "uuid": "c061596a-f66f-42e7-9a4f-561b29d49463",
  "name": "KrysolDev",
  "properties": [
    {
      "name": "textures",
      "value": "eyJ0aW1lc3RhbXAiOjE3MjQ2OTAwMDAwMDAsInByb2ZpbGVJZCI6ImMwNjE1OTZhZjY2ZjQyZTc5YTRmNTYxYjI5ZDQ5NDYzIiwicHJvZmlsZU5hbWUiOiJLcnlzb2xEZXYiLCJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vZXp6bGF1bmNoZXIuZHBkbnMub3JnL2FwaS9taW5lY3JhZnQvdGV4dHVyZS8yYmJiYzFkMjE0NzY0OWJiYmE1YjBhODAyYTQyYmU5OCJ9fX0="
    }
  ]
}
```

#### Decoded `textures` Object
```json
{
  "timestamp": 1724690000000,
  "profileId": "c061596af66f42e79a4f561b29d49463",
  "profileName": "KrysolDev",
  "textures": {
    "SKIN": {
      "url": "https://ezzlauncher.dpdns.org/api/minecraft/texture/2bbbc1d2147649bbba5b0a802a42be98",
      "metadata": {
        "model": "slim"
      }
    }
  }
}
```

#### Error Responses
- `400 Bad Request`: `INVALID_USERNAME`
- `404 Not Found`: `PROFILE_NOT_FOUND`
- `500 Internal Error`: `INTERNAL_ERROR`

---

## 2. Retrieve Raw Skin Texture

### `GET /api/minecraft/texture/:hash`

Proxies raw 64x64 PNG binary bytes with immutable edge cache headers.

#### Response Headers
```http
HTTP/1.1 200 OK
Content-Type: image/png
Cache-Control: public, max-age=31536000, s-maxage=31536000, immutable
Access-Control-Allow-Origin: *
```

---

## 3. Validate Username Availability

### `POST /api/minecraft/validate-username`

Checks syntax and database availability for profile registration.

#### Request Body
```json
{
  "username": "KrysolDev"
}
```

#### Response (`200 OK`)
```json
{
  "valid": true,
  "available": true,
  "username": "KrysolDev",
  "normalized": "krysoldev"
}
```
