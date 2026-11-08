<script setup>
/** All user manuals the current visitor can read. */
import { computed } from 'vue';
import { useAuthStore } from '../stores/auth';
import { MANUALS, canRead, manualForRole } from '../manuals';

const auth = useAuthStore();
const visible = computed(() => MANUALS.filter((m) => canRead(m, auth)));
const suggested = computed(() => manualForRole(auth));
</script>

<template>
  <div class="page-document manuals-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Help</p>
      <h1 class="page-hero__title">User manuals</h1>
      <p class="page-hero__lead">
        Step-by-step guides for everything you can do on Agri Marketplace. Read them here, or print or save them as a PDF.
      </p>
    </header>

    <div class="manuals-grid">
      <router-link
        v-for="m in visible"
        :key="m.slug"
        :to="`/manuals/${m.slug}`"
        class="manual-tile"
        :class="{ 'manual-tile--suggested': m.slug === suggested }"
      >
        <span class="material-icons manual-tile__icon" aria-hidden="true">{{ m.icon }}</span>
        <span class="manual-tile__body">
          <span class="manual-tile__audience">{{ m.audience }}</span>
          <strong class="manual-tile__title">{{ m.title }}</strong>
          <span class="manual-tile__desc">{{ m.description }}</span>
        </span>
        <span v-if="m.slug === suggested && auth.isAuthenticated" class="manual-tile__badge">For you</span>
      </router-link>
    </div>

    <p v-if="!auth.isAuthenticated" class="muted small manuals-note">
      Administrators and support staff: sign in to see your guides too.
    </p>
  </div>
</template>

<style scoped>
.manuals-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(17rem, 1fr));
  gap: 1rem;
}

.manual-tile {
  position: relative;
  display: flex;
  gap: 0.85rem;
  padding: 1.1rem 1.15rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
  color: var(--color-text);
  text-decoration: none;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.manual-tile:hover {
  border-color: var(--color-sage);
  box-shadow: var(--shadow-md);
  text-decoration: none;
}

.manual-tile--suggested {
  border-color: var(--color-sage);
}

.manual-tile__icon {
  flex: none;
  width: 2.5rem;
  height: 2.5rem;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: var(--color-sage-soft);
  color: var(--color-sage);
}

.manual-tile__body {
  display: grid;
  gap: 0.2rem;
  min-width: 0;
}

.manual-tile__audience {
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-muted);
}

.manual-tile__title {
  font-size: 1.05rem;
}

.manual-tile__desc {
  font-size: 0.88rem;
  color: var(--color-text-secondary);
  line-height: 1.45;
}

.manual-tile__badge {
  position: absolute;
  top: 0.7rem;
  right: 0.8rem;
  padding: 0.1rem 0.5rem;
  border-radius: var(--radius-pill);
  background: var(--color-sage);
  color: #fff;
  font-size: 0.72rem;
  font-weight: 700;
}

.manuals-note {
  margin-top: 1rem;
}
</style>
