const DEFAULT_PRESENTATION = {
  icon: 'bell',
  category: 'Update',
  tone: 'info',
};

const TYPE_META = {
  NEW_ORDER: { icon: 'receipt', category: 'New order', tone: 'new' },
  NEW_RENTAL: { icon: 'event', category: 'New rental', tone: 'new' },
  ORDER_STATUS: { icon: 'local_shipping', category: 'Order update', tone: 'info' },
  RENTAL_STATUS: { icon: 'inventory_2', category: 'Rental update', tone: 'info' },
  ORDER_CANCELLED: { icon: 'cancel', category: 'Cancelled', tone: 'error' },
  SUBSCRIPTION_PROOF_PENDING: { icon: 'description', category: 'Proof review', tone: 'action' },
  SUBSCRIPTION_PROOF_DECISION: { icon: 'task_alt', category: 'Subscription', tone: 'success' },
  SUBSCRIPTION_REMINDER: { icon: 'schedule', category: 'Reminder', tone: 'warning' },
};

export function getNotificationPresentation(notification) {
  const type = String(notification?.notificationType || '').trim().toUpperCase();
  return { ...DEFAULT_PRESENTATION, ...(TYPE_META[type] || {}) };
}

export function truncateNotificationText(text, max = 140) {
  const value = String(text || '').trim();
  if (!value) return '';
  if (value.length <= max) return value;
  return `${value.slice(0, max - 1).trim()}...`;
}
