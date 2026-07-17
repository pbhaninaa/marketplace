export type NavBadgeCounts = Record<string, number>;

export type NavBadgesMessage = {
  type: 'NAV_BADGES';
  navBadges: NavBadgeCounts;
};

export function normalizeNavBadgeCounts(raw: unknown): NavBadgeCounts {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return {};
  }
  const next: NavBadgeCounts = {};
  for (const [key, value] of Object.entries(raw as Record<string, unknown>)) {
    const n = Number(value);
    next[key] = Number.isFinite(n) ? Math.max(0, Math.floor(n)) : 0;
  }
  return next;
}

export function isNavBadgesMessage(raw: unknown): raw is NavBadgesMessage {
  if (!raw || typeof raw !== 'object') return false;
  const msg = raw as NavBadgesMessage;
  return msg.type === 'NAV_BADGES' && msg.navBadges != null && typeof msg.navBadges === 'object';
}

export function badgeCountForKey(counts: NavBadgeCounts, badgeKey?: string): number {
  if (!badgeKey) return 0;
  return Number(counts[badgeKey] ?? 0);
}

export function formatBadgeCount(count: number): string {
  if (count <= 0) return '';
  return count > 99 ? '99+' : String(count);
}
