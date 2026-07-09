<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { notificationsApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
const router = useRouter();

const open = ref(false);
const items = ref([]);
const unread = ref(0);
let timer = null;

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

async function openItem(n) {
  if (!n.read) {
    try {
      await notificationsApi.markRead(n.id);
      n.read = true;
      unread.value = Math.max(0, unread.value - 1);
    } catch {
      /* ignore */
    }
  }
  open.value = false;
  if (n.linkPath) router.push(n.linkPath);
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
  if (!e.target.closest?.('.notif-bell')) open.value = false;
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
      <ul v-if="items.length" class="notif-bell__list">
        <li
          v-for="n in items"
          :key="n.id"
          :class="{ unread: !n.read }"
          @click="openItem(n)"
        >
          <div class="title">{{ n.title }}</div>
          <div class="body">{{ n.body }}</div>
        </li>
      </ul>
      <p v-else class="muted empty">No notifications yet.</p>
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
  width: min(22rem, 88vw);
  max-height: 22rem;
  overflow: auto;
  background: var(--color-surface-elevated);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(20, 40, 20, 0.14);
  z-index: 40;
  padding: 0.65rem 0.75rem;
}

.notif-bell__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
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
  list-style: none;
  margin: 0;
  padding: 0;
}

.notif-bell__list li {
  padding: 0.55rem 0.4rem;
  border-radius: 10px;
  cursor: pointer;
}

.notif-bell__list li:hover {
  background: var(--color-sage-soft);
}

.notif-bell__list li.unread {
  background: color-mix(in srgb, var(--color-wheat-soft) 70%, transparent);
}

.title {
  font-weight: 700;
  font-size: 0.88rem;
  color: var(--color-canopy);
}

.body {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
  margin-top: 0.15rem;
}

.empty {
  margin: 0.5rem 0;
  font-size: 0.85rem;
}
</style>
