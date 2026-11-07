/**
 * Client-side copy of the backend password policy (backend/.../security/PasswordPolicy.java and
 * resources/security/common-passwords.txt). The server is the authority; this only gives early,
 * friendly feedback. Keep the rules and the list in sync with the backend.
 */

export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 64;
const MAX_BCRYPT_BYTES = 72;

export const PASSWORD_HINT =
  `${PASSWORD_MIN_LENGTH}–${PASSWORD_MAX_LENGTH} characters. Avoid common passwords (like "Password123") and your email name.`;

/** Same entries as the backend's common-passwords.txt (a test checks they match). */
export const COMMON_PASSWORDS = new Set(
  `password passw0rd p@ssword p@ssw0rd pass passwd password1 qwerty qwertyuiop qwertz azerty asdf asdfgh
  asdfghjkl zxcvbn zxcvbnm qazwsx qwer abcd abcdef abcdefg abcdefgh abc123 abcd1234 aaaa letmein welcome
  welcome1 admin administrator root toor login user guest test tester testing demo changeme default secret
  master monkey dragon shadow sunshine princess football soccer baseball basketball rugby cricket hockey
  superman batman spiderman starwars pokemon iloveyou lovely love trustno1 whatever freedom hello hello123
  hellohello computer internet michael jennifer jordan jordan23 hunter hunter2 ranger buster thomas robert
  daniel andrew joshua matthew charlie george william jessica ashley amanda nicole michelle samantha summer
  winter spring autumn flower cookie chocolate cheese pepper ginger orange banana apple mustang ferrari
  porsche corvette mercedes harley yamaha toyota honda nissan killer pussy fuckyou fuck sexy access ninja
  cowboy maverick tigger tiger lion eagle falcon phoenix matrix zxcv asdfasdf qwerqwer qwe123 q1w2e3r4
  q1w2e3r4t5 1q2w3e4r 1q2w3e4r5t 1qaz2wsx zaq12wsx zaq1zaq1 !qaz2wsx 12345678 123456789 1234567890 0123456789
  87654321 11111111 00000000 12341234 11223344 12121212 123123123 password12 iloveyou1 blessed blessing jesus
  jesuschrist godisgood faith angel angels mother family friends forever myspace facebook google yahoo gmail
  linkedin instagram twitter youtube netflix amazon microsoft windows apple123 samsung nokia iphone android
  agrimarket marketplace agrimarketplace farmer farming tractor harvest southafrica mzansi capetown
  johannesburg durban pretoria kaizerchiefs orlandopirates bafana springbok springboks amandla ubuntu eskom
  january february march april june july august september october november december monday tuesday
  wednesday thursday friday saturday sunday`
    .split(/\s+/)
    .filter(Boolean),
);

function utf8Length(value) {
  return new TextEncoder().encode(value).length;
}

function isCommon(lower) {
  if (COMMON_PASSWORDS.has(lower)) return true;
  const stripped = lower.replace(/[^a-z]+$/, '');
  return stripped.length >= 4 && COMMON_PASSWORDS.has(stripped);
}

function emailName(email) {
  if (typeof email !== 'string') return '';
  const e = email.trim().toLowerCase();
  const at = e.indexOf('@');
  return at >= 0 ? e.slice(0, at) : e;
}

/**
 * @param {string} password
 * @param {string} [email] account email; its part before "@" must not appear in the password
 * @returns {string} a message for the user, or '' when the password is acceptable
 */
export function passwordProblem(password, email) {
  if (typeof password !== 'string' || !password.trim()) return 'Password is required';
  if (password.length < PASSWORD_MIN_LENGTH) {
    return `Password must be at least ${PASSWORD_MIN_LENGTH} characters`;
  }
  if (password.length > PASSWORD_MAX_LENGTH || utf8Length(password) > MAX_BCRYPT_BYTES) {
    return `Password must be at most ${PASSWORD_MAX_LENGTH} characters`;
  }
  const lower = password.toLowerCase();
  if (isCommon(lower)) return 'This password is too common. Choose something harder to guess';
  if (new Set(password).size < 3) return 'Password is too simple. Use a mix of different characters';
  const name = emailName(email);
  if (name.length >= 3 && lower.includes(name)) return 'Password must not contain your email name';
  return '';
}
