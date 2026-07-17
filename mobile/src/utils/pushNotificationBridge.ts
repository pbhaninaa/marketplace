export type PushNotificationMessage = {
  type: 'SHOW_PUSH_NOTIFICATION';
  id: string;
  title: string;
  body: string;
  navPath?: string;
};

export function isPushNotificationMessage(
  raw: unknown,
): raw is PushNotificationMessage {
  if (!raw || typeof raw !== 'object') return false;
  const msg = raw as PushNotificationMessage;
  return (
    msg.type === 'SHOW_PUSH_NOTIFICATION' &&
    typeof msg.title === 'string' &&
    msg.title.trim().length > 0
  );
}

export function normalizeNotificationNavPath(path: unknown): string {
  const raw = String(path ?? '').trim();
  if (!raw) return '/notifications';
  const normalized = raw.startsWith('/') ? raw : `/${raw}`;
  return normalized || '/notifications';
}
