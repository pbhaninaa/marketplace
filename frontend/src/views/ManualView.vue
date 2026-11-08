<script setup>
/**
 * Reads one user manual (src/manuals/*.md) in the web app: contents list, linkable sections,
 * print / save as PDF. Admin and support manuals are only shown to those roles.
 */
import { computed, nextTick, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { marked } from 'marked';
import { useAuthStore } from '../stores/auth';
import { MANUALS, canRead, findManual, slugify } from '../manuals';

const route = useRoute();
const auth = useAuthStore();

const html = ref('');
const toc = ref([]);
const loading = ref(false);
const error = ref('');
const contentRef = ref(null);

const manual = computed(() => findManual(String(route.params.slug || '')));
const allowed = computed(() => canRead(manual.value, auth));
const otherManuals = computed(() =>
  MANUALS.filter((m) => m.slug !== manual.value?.slug && canRead(m, auth)),
);

/** Add ids to headings (for the contents list and shareable links) and make tables scroll on phones. */
function enhance(rawHtml) {
  const doc = new DOMParser().parseFromString(`<div>${rawHtml}</div>`, 'text/html');
  const root = doc.body.firstElementChild;
  const used = new Set();
  const items = [];
  root.querySelectorAll('h2, h3').forEach((h) => {
    let id = slugify(h.textContent) || 'section';
    while (used.has(id)) id += '-x';
    used.add(id);
    h.id = id;
    if (h.tagName === 'H2') items.push({ id, text: h.textContent });
  });
  // The title comes from the page header; drop the duplicate H1 from the Markdown.
  root.querySelector('h1')?.remove();
  root.querySelectorAll('table').forEach((t) => {
    const wrap = doc.createElement('div');
    wrap.className = 'manual-table';
    t.replaceWith(wrap);
    wrap.appendChild(t);
  });
  toc.value = items;
  return root.innerHTML;
}

async function load() {
  html.value = '';
  toc.value = [];
  error.value = '';
  if (!manual.value || !allowed.value) return;
  loading.value = true;
  try {
    const mod = await manual.value.load();
    html.value = enhance(marked.parse(mod.default, { gfm: true }));
    await nextTick();
    // Opened from a shared link like /manuals/providers#orders-and-bookings
    if (route.hash) document.getElementById(route.hash.slice(1))?.scrollIntoView();
  } catch (e) {
    error.value = 'This manual could not be loaded. Please refresh the page.';
  } finally {
    loading.value = false;
  }
}

watch(() => [route.params.slug, allowed.value], load, { immediate: true });

function goTo(id) {
  const el = document.getElementById(id);
  if (!el) return;
  el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  history.replaceState(history.state, '', `#${id}`);
}

function printManual() {
  window.print();
}
</script>

<template>
  <div class="page-document page-document--wide manual-page">
    <nav class="manual-crumbs" aria-label="Breadcrumb">
      <router-link to="/manuals">User manuals</router-link>
      <span aria-hidden="true">/</span>
      <span>{{ manual?.title || 'Not found' }}</span>
    </nav>

    <!-- Unknown manual -->
    <section v-if="!manual" class="surface-panel manual-message">
      <h1>Manual not found</h1>
      <p class="muted">That manual doesn't exist. <router-link to="/manuals">See all user manuals</router-link>.</p>
    </section>

    <!-- Admin / support manual opened by someone without that role -->
    <section v-else-if="!allowed" class="surface-panel manual-message">
      <h1>{{ manual.title }}</h1>
      <p class="muted">
        This manual is for {{ manual.audience.toLowerCase() }}.
        <template v-if="!auth.isAuthenticated">
          <router-link :to="{ path: '/login', query: { redirect: route.fullPath } }">Sign in</router-link>
          with that account to read it.
        </template>
        <template v-else>Your account doesn't have access to it.</template>
      </p>
      <router-link to="/manuals" class="btn btn-ghost">See the manuals you can read</router-link>
    </section>

    <template v-else>
      <header class="page-hero manual-hero">
        <p class="page-hero__eyebrow">{{ manual.audience }}</p>
        <h1 class="page-hero__title">{{ manual.title }}</h1>
        <p class="page-hero__lead">{{ manual.description }}</p>
        <div class="manual-actions">
          <button type="button" class="btn btn-ghost btn-xs" @click="printManual">
            <span class="material-icons" aria-hidden="true">print</span>
            Print / Save as PDF
          </button>
        </div>
      </header>

      <p v-if="error" class="err-toast" role="alert">{{ error }}</p>
      <p v-else-if="loading" class="muted">Loading…</p>

      <div v-else class="manual-layout">
        <aside v-if="toc.length" class="manual-toc" aria-label="Contents">
          <p class="manual-toc__title">Contents</p>
          <ol>
            <li v-for="t in toc" :key="t.id">
              <a :href="`#${t.id}`" @click.prevent="goTo(t.id)">{{ t.text }}</a>
            </li>
          </ol>
        </aside>

        <!-- Content is our own Markdown from src/manuals, bundled with the app. -->
        <article ref="contentRef" class="surface-panel manual-content" v-html="html" />
      </div>

      <section v-if="otherManuals.length" class="manual-others" aria-label="Other manuals">
        <h2>Other manuals</h2>
        <div class="manual-others__grid">
          <router-link v-for="m in otherManuals" :key="m.slug" :to="`/manuals/${m.slug}`" class="manual-card">
            <span class="material-icons" aria-hidden="true">{{ m.icon }}</span>
            <span>
              <strong>{{ m.title }}</strong>
              <span class="muted small">{{ m.audience }}</span>
            </span>
          </router-link>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.manual-crumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-bottom: 0.75rem;
  font-size: 0.85rem;
  color: var(--color-muted);
}

.manual-crumbs a {
  font-weight: 600;
}

.manual-message h1 {
  margin-top: 0;
}

.manual-actions {
  margin-top: 0.75rem;
}

.manual-actions .btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.manual-actions .material-icons {
  font-size: 1.1rem;
}

.manual-layout {
  display: grid;
  grid-template-columns: minmax(12rem, 15rem) minmax(0, 1fr);
  gap: 1.25rem;
  align-items: start;
}

.manual-toc {
  position: sticky;
  top: 5rem;
  max-height: calc(100vh - 6rem);
  overflow-y: auto;
  padding: 0.85rem 0.9rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-elevated);
}

.manual-toc__title {
  margin: 0 0 0.4rem;
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-muted);
}

.manual-toc ol {
  margin: 0;
  padding-left: 1.1rem;
  display: grid;
  gap: 0.3rem;
  font-size: 0.88rem;
}

.manual-toc a {
  color: var(--color-text-secondary);
  text-decoration: none;
}

.manual-toc a:hover {
  color: var(--color-canopy);
  text-decoration: underline;
}

/* Rendered Markdown */
.manual-content {
  min-width: 0;
  line-height: 1.65;
  font-size: 0.97rem;
}

.manual-content :deep(h2) {
  margin: 2rem 0 0.6rem;
  padding-top: 1rem;
  border-top: 1px solid var(--color-border);
  font-size: 1.3rem;
  scroll-margin-top: 5rem;
}

.manual-content :deep(h2:first-child) {
  margin-top: 0;
  padding-top: 0;
  border-top: none;
}

.manual-content :deep(h3) {
  margin: 1.4rem 0 0.4rem;
  font-size: 1.05rem;
  scroll-margin-top: 5rem;
}

.manual-content :deep(p),
.manual-content :deep(ul),
.manual-content :deep(ol) {
  margin: 0 0 0.85rem;
}

.manual-content :deep(li) {
  margin-bottom: 0.3rem;
}

.manual-content :deep(li > ul),
.manual-content :deep(li > ol) {
  margin: 0.3rem 0 0;
}

.manual-content :deep(blockquote) {
  margin: 0 0 1rem;
  padding: 0.7rem 0.9rem;
  border-left: 3px solid var(--color-sage);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: var(--color-sage-soft);
}

.manual-content :deep(blockquote p:last-child) {
  margin-bottom: 0;
}

.manual-content :deep(code) {
  padding: 0.1rem 0.35rem;
  border-radius: 6px;
  background: var(--color-bg);
  font-size: 0.9em;
}

.manual-content :deep(.manual-table) {
  margin: 0 0 1rem;
  overflow-x: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.manual-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.92rem;
}

.manual-content :deep(th),
.manual-content :deep(td) {
  padding: 0.55rem 0.75rem;
  text-align: left;
  vertical-align: top;
  border-bottom: 1px solid var(--color-border);
}

.manual-content :deep(tr:last-child td) {
  border-bottom: none;
}

.manual-content :deep(th) {
  background: var(--color-sage-soft);
  color: var(--color-text);
  font-weight: 700;
}

.manual-others {
  margin-top: 1.5rem;
}

.manual-others h2 {
  font-size: 1.05rem;
}

.manual-others__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr));
  gap: 0.75rem;
}

.manual-card {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.8rem 0.9rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-elevated);
  color: var(--color-text);
  text-decoration: none;
}

.manual-card:hover {
  border-color: var(--color-sage);
}

.manual-card > span:last-child {
  display: grid;
}

.manual-card .material-icons {
  color: var(--color-sage);
}

@media (max-width: 860px) {
  .manual-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .manual-toc {
    position: static;
    max-height: none;
  }
}

/* Print / Save as PDF: just the manual, no app chrome or contents list. */
@media print {
  .manual-crumbs,
  .manual-actions,
  .manual-toc,
  .manual-others {
    display: none !important;
  }

  .manual-layout {
    display: block;
  }

  .manual-content {
    border: none !important;
    box-shadow: none !important;
    padding: 0 !important;
  }

  .manual-content :deep(h2) {
    break-after: avoid;
  }

  .manual-content :deep(table),
  .manual-content :deep(blockquote) {
    break-inside: avoid;
  }
}
</style>
