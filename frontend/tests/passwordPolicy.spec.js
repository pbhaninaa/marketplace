import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { COMMON_PASSWORDS, passwordProblem, PASSWORD_HINT } from '../src/utils/passwordPolicy';

// Mirrors backend PasswordPolicyTest so the client and server agree.
describe('passwordProblem', () => {
  it('accepts reasonable passwords', () => {
    expect(passwordProblem('blue-Kettle-42', 'thandi@farm.test')).toBe('');
    expect(passwordProblem('SecurePass123', 'owner-1@integration.test')).toBe('');
  });

  it('rejects missing, short and overlong passwords', () => {
    expect(passwordProblem('', 'u@x.test')).toBe('Password is required');
    expect(passwordProblem('   ', 'u@x.test')).toBe('Password is required');
    expect(passwordProblem('Ab3$xyz', 'u@x.test')).toContain('at least 8');
    expect(passwordProblem('a'.repeat(30) + 'B'.repeat(35), 'u@x.test')).toContain('at most');
    // 24 four-byte emoji = 96 bytes: over BCrypt's 72-byte limit.
    expect(passwordProblem('😀'.repeat(24), 'u@x.test')).toContain('at most');
  });

  it('rejects common passwords, including decorated ones', () => {
    for (const p of ['password', 'Password123!', 'QWERTYUIOP', '123456789', 'Springbok2024', 'Tractor2026!']) {
      expect(passwordProblem(p, 'u@x.test')).toContain('too common');
    }
  });

  it('rejects repeated characters', () => {
    expect(passwordProblem('zzzzzzzzzz', 'u@x.test')).toContain('too simple');
    expect(passwordProblem('ababababab', 'u@x.test')).toContain('too simple');
  });

  it('rejects passwords containing the email name', () => {
    expect(passwordProblem('Thandi#2026x', 'thandi@farm.test')).toContain('email');
    expect(passwordProblem('blue-Kettle-42', 'bl@farm.test')).toBe('');
  });

  it('has a hint matching the limits', () => {
    expect(PASSWORD_HINT).toContain('8');
    expect(PASSWORD_HINT).toContain('64');
  });

  it('uses the same blocklist as the backend', () => {
    const backendList = readFileSync(
      resolve(process.cwd(), '../backend/src/main/resources/security/common-passwords.txt'),
      'utf8',
    )
      .split(/\r?\n/)
      .map((l) => l.trim().toLowerCase())
      .filter((l) => l && !l.startsWith('#'));
    expect([...COMMON_PASSWORDS].sort()).toEqual([...new Set(backendList)].sort());
  });
});
