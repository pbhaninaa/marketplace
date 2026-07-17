<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { notificationsApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import { resolveNotificationFallback } from '../utils/notificationNavigation';
import { getNotificationPresentation, truncateNotificationText } from '../utils/notificationPresentation';

const auth = useAuthStore();
const router = useRouter();

const open = ref(false);
const items = ref([]);
const unread = ref(0);
const selected = ref(null);
let timer = null;

function resolvedPath(notification) {
  return String(notification?.linkPath || '').trim() || resolveNotificationFallback(notification, auth);
}

function actionFor(notification) {
  const path = resolvedPath(notification);
  if (!path) return null;
  return {
    path,
    label: 'Open in app',
  };
}

function presentationFor(notification) {
  return getNotificationPresentation(notification);
}

function whenLabel(value) {
  if (!value) return '';
  try {
    return new Date(value).toLocaleString();
  } catch {
    return String(value);
  }
}

function preview(notification) {
  return truncateNotificationText(notification?.body, 120);
}

async function refresh() {
  if (!auth.isAuthenticated) {
    items.value = [];
    unread.value = 0;
    return;
  }
  try {
    const [listRes, countRes] = await Promise.all([
      notificationsApi.list(),
      notificationsApi.unreadCount(),
    ]);
    items.value = listRes.data || [];
    unread.value = Number(countRes.data?.count || 0);
  } catch {
    /* optional */
  }
}

async function toggle() {
  open.value = !open.value;
  if (open.value) await refresh();
}

async function markRead(notification) {
  if (!notification || notification.read) return;
  try {
    await notificationsApi.markRead(notification.id);
    notification.read = true;
    unread.value = Math.max(0, unread.value - 1);
  } catch {
    /* ignore */
  }
}

async function openDetails(notification) {
  selected.value = notification;
  await markRead(notification);
}

async function goToAction(notification) {
  const action = actionFor(notification);
  if (!action?.path) return;
  await markRead(notification);
  selected.value = null;
  open.value = false;
  router.push(action.path);
}

async function markAll() {
  try {
    await notificationsApi.markAllRead();
    items.value = items.value.map((n) => ({ ...n, read: true }));
    unread.value = 0;
  } catch {
    /* ignore */
  }
}

function onDocClick(e) {
  if (!e.target.closest?.('.notif-bell')) {
    open.value = false;
    selected.value = null;
  }
}

onMounted(() => {
  refresh();
  timer = setInterval(refresh, 45000);
  document.addEventListener('click', onDocClick);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
  document.removeEventListener('click', onDocClick);
});

watch(
  () => auth.isAuthenticated,
  () => refresh(),
);

const selectedPresentation = computed(() => presentationFor(selected.value));
</script>

<template>
  <div v-if="auth.isAuthenticated" class="notif-bell">
    <button type="button" class="notif-bell__btn" aria-label="Notifications" @click.stop="toggle">
      <svg viewBox="0 0 24 24" width="18" height="18" fill="none" aria-hidden="true">
        <path
          d="M12 22a2 2 0 0 0 2-2h-4a2 2 0 0 0 2 2zm6-6V11a6 6 0 1 0-12 0v5l-2 2v1h16v-1l-2-2z"
          fill="currentColor"
        />
      </svg>
      <span v-if="unread > 0" class="notif-bell__badge">{{ unread > 9 ? '9+' : unread }}</span>
    </button>
    <div v-if="open" class="notif-bell__panel" role="dialog" aria-label="Notifications">
      <div class="notif-bell__head">
        <strong>Notifications</strong>
        <button v-if="unread" type="button" class="linkish" @click="markAll">Mark all read</button>
      </div>

      <div v-if="items.length" class="notif-bell__list">
        <article
          v-for="n in items"
          :key="n.id"
          class="notif-card"
          :class="[ `notif-card--${presentationFor(n).tone}`, { 'notif-card--unread': !n.read } ]"
          tabindex="0"
          role="button"
          @click="openDetails(n)"
          @keydown.enter="openDetails(n)"
        >
          <div class="notif-card__accent" />
          <div class="notif-card__icon-wrap">
            <span class="material-symbols-outlined notif-card__icon">{{ presentationFor(n).icon }}</span>
          </div>
          <div class="notif-card__content">
            <div class="notif-card__top">
              <div class="notif-card__title-row">
                <div class="notif-card__title">{{ n.title }}</div>
                <span v-if="!n.read" class="notif-card__dot" />
              </div>
              <span class="notif-card__time">{{ whenLabel(n.createdAt) }}</span>
            </div>
            <div class="notif-card__meta">
              <span class="notif-card__chip">{{ presentationFor(n).category }}</span>
              <span v-if="!n.read" class="notif-card__new">New</span>
            </div>
            <div class="notif-card__body">{{ preview(n) }}</div>
            <div v-if="actionFor(n)" class="notif-card__footer" @click.stop>
              <button type="button" class="notif-card__action" @click.stop="goToAction(n)">
                {{ actionFor(n).label }}
                <span class="material-symbols-outlined notif-card__action-icon">arrow_forward</span>
              </button>
            </div>
          </div>
        </article>
      </div>

      <p v-else class="muted empty">No notifications yet.</p>
    </div>

    <div v-if="selected" class="notif-detail-backdrop" @click.self="selected = null">
      <div class="notif-detail">
        <button type="button" class="notif-detail__close" aria-label="Close" @click="selected = null">
          <span class="material-symbols-outlined">close</span>
        </button>
        <div class="notif-detail__hero" :class="`notif-detail__hero--${selectedPresentation.tone}`">
          <div class="notif-detail__avatar">
            <span class="material-symbols-outlined">{{ selectedPresentation.icon }}</span>
          </div>
          <h3 class="notif-detail__title">{{ selected.title }}</h3>
          <p class="notif-detail__time">{{ whenLabel(selected.createdAt) }}</p>
        </div>
        <div class="notif-detail__body">{{ selected.body }}</div>
        <div class="notif-detail__actions">
          <button type="button" class="notif-detail__ghost" @click="selected = null">Close</button>
          <button v-if="actionFor(selected)" type="button" class="notif-detail__action" @click="goToAction(selected)">
            {{ actionFor(selected).label }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.notif-bell {
  position: relative;
}

.notif-bell__btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface-elevated);
  color: var(--color-canopy);
  cursor: pointer;
}

.notif-bell__badge {
  position: absolute;
  top: -0.2rem;
  right: -0.2rem;
  min-width: 1.1rem;
  height: 1.1rem;
  padding: 0 0.25rem;
  border-radius: 999px;
  background: var(--color-earth);
  color: #fff;
  font-size: 0.65rem;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.notif-bell__panel {
  position: absolute;
  right: 0;
  top: calc(100% + 0.4rem);
  width: min(26rem, 92vw);
  max-height: 32rem;
  overflow: auto;
  background: var(--color-surface-elevated);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  box-shadow: 0 18px 48px rgba(20, 40, 20, 0.16);
  z-index: 40;
  padding: 0.85rem;
}

.notif-bell__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.linkish {
  border: none;
  background: none;
  color: var(--color-canopy);
  font-weight: 600;
  cursor: pointer;
  font-size: 0.8rem;
}

.notif-bell__list {
  display: grid;
  gap: 0.8rem;
}

.notif-card {
  position: relative;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.85rem;
  padding: 1rem 1rem 1rem 1.15rem;
  border-radius: 1rem;
  border: 1px solid var(--color-border);
  background: color-mix(in srgb, var(--color-surface-elevated) 90%, white);
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.15s ease;
}

.notif-card:hover {
  border-color: color-mix(in srgb, var(--color-canopy) 28%, var(--color-border));
  box-shadow: 0 10px 28px rgba(20, 40, 20, 0.1);
  transform: translateY(-1px);
}

.notif-card--unread {
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--color-wheat-soft) 48%, white) 0%,
    var(--color-surface-elevated) 58%
  );
}

.notif-card__accent {
  position: absolute;
  left: 0;
  top: 14px;
  bottom: 14px;
  width: 4px;
  border-radius: 0 4px 4px 0;
  background: transparent;
}

.notif-card--unread .notif-card__accent {
  background: var(--color-canopy);
}

.notif-card__icon-wrap {
  width: 2.75rem;
  height: 2.75rem;
  border-radius: 0.9rem;
  background: var(--color-canopy);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 18px rgba(20, 40, 20, 0.14);
}

.notif-card--warning .notif-card__icon-wrap,
.notif-card--action .notif-card__icon-wrap {
  background: var(--color-earth);
}

.notif-card--error .notif-card__icon-wrap {
  background: #b64b3e;
}

.notif-card--success .notif-card__icon-wrap {
  background: #3b7d59;
}

.notif-card__icon {
  font-size: 1.3rem;
}

.notif-card__content {
  min-width: 0;
}

.notif-card__top {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  align-items: flex-start;
}

.notif-card__title-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.notif-card__title {
  font-weight: 700;
  font-size: 0.95rem;
  color: var(--color-canopy);
}

.notif-card__dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--color-canopy);
  flex-shrink: 0;
}

.notif-card__time {
  font-size: 0.74rem;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.notif-card__meta {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
  margin-top: 0.5rem;
}

.notif-card__chip,
.notif-card__new {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 0.15rem 0.5rem;
  font-size: 0.72rem;
  font-weight: 700;
}

.notif-card__chip {
  border: 1px solid var(--color-border);
  color: var(--color-canopy);
  background: color-mix(in srgb, var(--color-surface-elevated) 80%, white);
}

.notif-card__new {
  background: var(--color-canopy);
  color: #fff;
}

.notif-card__body {
  font-size: 0.84rem;
  line-height: 1.5;
  color: var(--color-text-secondary);
  margin-top: 0.6rem;
  white-space: pre-line;
}

.notif-card__footer {
  margin-top: 0.75rem;
}

.notif-card__action,
.notif-detail__action,
.notif-detail__ghost {
  border-radius: 999px;
  padding: 0.6rem 0.95rem;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid transparent;
}

.notif-card__action,
.notif-detail__action {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  background: var(--color-canopy);
  color: #fff;
}

.notif-card__action-icon {
  font-size: 1rem;
}

.notif-detail-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(14, 20, 14, 0.4);
  display: grid;
  place-items: center;
  z-index: 80;
  padding: 1rem;
}

.notif-detail {
  width: min(34rem, 100%);
  background: var(--color-surface-elevated);
  border: 1px solid var(--color-border);
  border-radius: 1.2rem;
  overflow: hidden;
  position: relative;
  box-shadow: 0 24px 64px rgba(20, 40, 20, 0.22);
}

.notif-detail__close {
  position: absolute;
  top: 0.9rem;
  right: 0.9rem;
  border: none;
  background: transparent;
  color: var(--color-canopy);
  cursor: pointer;
}

.notif-detail__hero {
  padding: 2rem 1.5rem 1.4rem;
  text-align: center;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--color-wheat-soft) 64%, white) 0%,
    var(--color-surface-elevated) 78%
  );
}

.notif-detail__hero--success {
  background: linear-gradient(135deg, rgba(59, 125, 89, 0.14) 0%, var(--color-surface-elevated) 78%);
}

.notif-detail__hero--warning,
.notif-detail__hero--action {
  background: linear-gradient(135deg, rgba(166, 106, 55, 0.14) 0%, var(--color-surface-elevated) 78%);
}

.notif-detail__hero--error {
  background: linear-gradient(135deg, rgba(182, 75, 62, 0.14) 0%, var(--color-surface-elevated) 78%);
}

.notif-detail__avatar {
  width: 3.4rem;
  height: 3.4rem;
  margin: 0 auto 0.9rem;
  border-radius: 1rem;
  background: var(--color-canopy);
  color: #fff;
  display: grid;
  place-items: center;
}

.notif-detail__title {
  margin: 0;
  color: var(--color-canopy);
  font-size: 1.15rem;
}

.notif-detail__time {
  margin: 0.5rem 0 0;
  color: var(--color-text-secondary);
  font-size: 0.82rem;
}

.notif-detail__body {
  padding: 1.35rem 1.5rem 0.4rem;
  color: var(--color-text-primary);
  white-space: pre-wrap;
  line-height: 1.6;
}

.notif-detail__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 1rem 1.5rem 1.5rem;
}

.notif-detail__ghost {
  background: transparent;
  border-color: var(--color-border);
  color: var(--color-canopy);
}

.empty {
  margin: 0.5rem 0;
  font-size: 0.85rem;
}

@media (max-width: 640px) {
  .notif-bell__panel {
    width: min(24rem, calc(100vw - 1rem));
    right: -0.35rem;
  }

  .notif-card__top {
    flex-direction: column;
    gap: 0.35rem;
  }

  .notif-detail__actions {
    flex-direction: column-reverse;
  }
}
</style>
