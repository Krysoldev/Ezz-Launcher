import { describe, it, expect } from 'vitest';
import { computeSHA256 } from '../src/supabase.js';

describe('Ezz Platform — Skin & Profile Validation Suite', () => {
  // Test 1: SHA-256 Computation
  it('should compute valid SHA-256 hex string from byte buffer', async () => {
    const encoder = new TextEncoder();
    const data = encoder.encode('Minecraft Skin Texture Data 2026');
    const hash = await computeSHA256(data.buffer);

    expect(hash).toBeDefined();
    expect(hash.length).toBe(64);
    expect(/^[0-9a-f]{64}$/.test(hash)).toBe(true);
  });

  // Test 2: Username syntax validation
  it('should validate Minecraft username formats correctly', () => {
    const validUsernames = ['Steve', 'Alex_123', 'KrysolDev', 'PVP_Master_99', 'Ezz_Player'];
    const invalidUsernames = [
      'ab', // too short (<3)
      'this_username_is_way_too_long_for_minecraft', // too long (>16)
      'user name', // contains space
      'user@domain', // invalid char @
      'hacker$pro', // invalid char $
      ''
    ];

    const validate = (u) => {
      const clean = (u || '').trim();
      return clean.length >= 3 && clean.length <= 16 && /^[a-zA-Z0-9_]+$/.test(clean);
    };

    validUsernames.forEach(u => expect(validate(u)).toBe(true));
    invalidUsernames.forEach(u => expect(validate(u)).toBe(false));
  });

  // Test 3: PNG Magic Bytes Validation
  it('should verify PNG binary signatures', () => {
    const validPngHeader = new Uint8Array([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00]);
    const fakeJpegHeader = new Uint8Array([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46]);
    const fakeGifHeader = new Uint8Array([0x47, 0x49, 0x46, 0x38, 0x39, 0x61]);

    const isPng = (bytes) => {
      return (
        bytes[0] === 0x89 &&
        bytes[1] === 0x50 &&
        bytes[2] === 0x4E &&
        bytes[3] === 0x47 &&
        bytes[4] === 0x0D &&
        bytes[5] === 0x0A &&
        bytes[6] === 0x1A &&
        bytes[7] === 0x0A
      );
    };

    expect(isPng(validPngHeader)).toBe(true);
    expect(isPng(fakeJpegHeader)).toBe(false);
    expect(isPng(fakeGifHeader)).toBe(false);
  });

  // Test 4: UUID validation
  it('should validate Minecraft UUID structure', () => {
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    const testUuid = 'c061596a-f66f-42e7-9a4f-561b29d49463';
    const invalidUuid = 'not-a-valid-uuid-string';

    expect(uuidRegex.test(testUuid)).toBe(true);
    expect(uuidRegex.test(invalidUuid)).toBe(false);
  });
});
