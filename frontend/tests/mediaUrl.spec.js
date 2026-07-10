import { describe, expect, it, vi } from 'vitest';
import { firstMediaUrl, parseMediaUrls, resolveMediaUrl } from '../src/utils/mediaUrl.js';

describe('mediaUrl', () => {
  it('prefixes API-relative image paths with VITE_API_BASE', () => {
    vi.stubEnv('VITE_API_BASE', 'https://api.example.com');
    expect(resolveMediaUrl('/api/public/images/42')).toBe('https://api.example.com/api/public/images/42');
  });

  it('keeps absolute and blob URLs unchanged', () => {
    vi.stubEnv('VITE_API_BASE', 'https://api.example.com');
    expect(resolveMediaUrl('https://cdn.example.com/a.jpg')).toBe('https://cdn.example.com/a.jpg');
    expect(resolveMediaUrl('blob:http://localhost/abc')).toBe('blob:http://localhost/abc');
  });

  it('parses comma-separated listing image URLs', () => {
    vi.stubEnv('VITE_API_BASE', 'https://api.example.com');
    expect(parseMediaUrls('/api/public/images/1,/api/public/images/2')).toEqual([
      'https://api.example.com/api/public/images/1',
      'https://api.example.com/api/public/images/2',
    ]);
    expect(firstMediaUrl('/api/public/images/9')).toBe('https://api.example.com/api/public/images/9');
  });
});
