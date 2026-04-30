<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { adminSubscriptionProofsApi } from '../services/marketplaceApi';
import FormField from '../components/ui/FormField.vue';

const router = useRouter();
const auth = useAuthStore();

const loading = ref(true);
const error = ref('');
const rows = ref([]);

const showDecide = ref(false);
const activeRow = ref(null);
const deciding = ref(false);
const decision = ref({ approve: true, note: '' });

const pendingCount = computed(() => rows.value?.length ?? 0);

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isPlatformAdmin) {
    router.replace({ path: '/login', query: { redirect: '/admin/manual-verifications' } });
    return;
  }
  await refresh();
});

async function refresh() {
  loading.value = true;
  error.value = '';
  try {
    const { data } = await adminSubscriptionProofsApi.pending();
    rows.value = Array.isArray(data) ? data : [];
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Failed to load pending proofs.';
  } finally {
    loading.value = false;
  }
}

function openDecide(row, approve) {
  activeRow.value = row;
  decision.value = { approve, note: '' };
  showDecide.value = true;
}

function closeDecide() {
  showDecide.value = false;
  activeRow.value = null;
  deciding.value = false;
  decision.value = { approve: true, note: '' };
}

async function submitDecision() {
  if (!activeRow.value) return;
  deciding.value = true;
  error.value = '';
  try {
    await adminSubscriptionProofsApi.decide(activeRow.value.proofId, {
      approve: !!decision.value.approve,
      note: decision.value.note?.trim() || null,
    });
    closeDecide();
    await refresh();
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Failed to submit decision.';
  } finally {
    deciding.value = false;
  }
}

function fmtDate(s) {
  if (!s) return '—';
  return String(s).slice(0, 19).replace('T', ' ');
}
</script>

<template>
  <div class="page-document page-document--wide">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Admin</p>
      <h1 class="page-hero__title">Manual payment verifications</h1>
      <p class="page-hero__lead">
        Proofs that failed auto-verification are listed here for manual review.
      </p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>

    <section class="surface-panel panel">
      <div class="panel-head">
        <div>
          <h2>Pending proofs</h2>
          <p class="muted small">{{ pendingCount }} waiting</p>
        </div>
        <button type="button" class="btn btn-ghost" :disabled="loading" @click="refresh">
          {{ loading ? 'Refreshing…' : 'Refresh' }}
        </button>
      </div>

      <p v-if="loading" class="muted">Loading…</p>
      <p v-else-if="rows.length === 0" class="muted">No pending proofs needing manual review.</p>

      <div v-else class="table-wrap">
        <table class="tbl">
          <thead>
            <tr>
              <th>Provider</th>
              <th>Plan</th>
              <th>Submitted</th>
              <th class="actions-col">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.proofId">
              <td>
                <strong>{{ r.providerName }}</strong>
                <div class="muted tiny">Provider #{{ r.providerId }} · Proof #{{ r.proofId }}</div>
              </td>
              <td>
                <strong>{{ r.plan }}</strong>
                <div class="muted tiny">{{ r.billingCycle }}</div>
              </td>
              <td>
                <span class="muted">{{ fmtDate(r.createdAt) }}</span>
              </td>
              <td class="actions-col">
                <button type="button" class="btn btn-ghost" @click="adminSubscriptionProofsApi.openFile(r.proofId)">
                  Open proof
                </button>
                <button type="button" class="btn btn-primary" @click="openDecide(r, true)">Approve</button>
                <button type="button" class="btn btn-ghost danger" @click="openDecide(r, false)">Reject</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <dialog class="decide-dialog" :open="showDecide" @cancel.prevent="closeDecide">
      <div class="decide-dialog__backdrop" @click="closeDecide" />
      <div class="decide-dialog__panel" role="document" aria-label="Review proof">
        <button type="button" class="decide-dialog__close" @click="closeDecide" aria-label="Close">✕</button>
        <h2 class="decide-dialog__title">Review proof</h2>
        <p class="muted small" v-if="activeRow">
          Provider: <strong>{{ activeRow.providerName }}</strong> · Plan:
          <strong>{{ activeRow.plan }}</strong>
        </p>

        <FormField label="Decision">
          <select v-model="decision.approve">
            <option :value="true">Approve</option>
            <option :value="false">Reject</option>
          </select>
        </FormField>
        <FormField label="Note (optional)">
          <textarea v-model="decision.note" rows="3" placeholder="Optional review note for audit trail" />
        </FormField>

        <div class="dlg-actions">
          <button type="button" class="btn btn-primary" :disabled="deciding" @click="submitDecision">
            {{ deciding ? 'Submitting…' : 'Submit decision' }}
          </button>
          <button type="button" class="btn btn-ghost" :disabled="deciding" @click="closeDecide">Cancel</button>
        </div>
      </div>
    </dialog>
  </div>
</template>

<style scoped>
.panel {
  margin: 0 auto;
}
.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.5rem;
}
.table-wrap {
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}
.tbl {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  min-width: 820px;
}
.tbl th,
.tbl td {
  padding: 0.75rem 0.85rem;
  border-bottom: none;
  text-align: left;
  vertical-align: top;
}
.tbl thead th {
  background: var(--color-sage-soft);
  font-size: 0.85rem;
  color: var(--color-text);
}
.tbl tbody tr:nth-child(even) td {
  background: rgba(99, 183, 156, 0.06);
}
.tbl tbody tr:hover td {
  background: var(--color-sage-soft);
}
.actions-col {
  width: 340px;
  white-space: nowrap;
}
.tiny {
  font-size: 0.78rem;
}
.danger {
  border-color: rgba(180, 40, 40, 0.35);
  color: var(--color-danger-text);
}
.decide-dialog {
  position: fixed;
  inset: 0;
  background: transparent;
  border: none;
  padding: 0;
}
.decide-dialog__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(3, 7, 18, 0.55);
}
.decide-dialog__panel {
  position: relative;
  margin: 8vh auto 0;
  max-width: 560px;
  width: calc(100% - 2rem);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  padding: 1rem 1.1rem 1.1rem;
}
.decide-dialog__close {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  border: 1px solid var(--color-border);
  background: transparent;
  border-radius: 10px;
  padding: 0.3rem 0.55rem;
}
.decide-dialog__title {
  margin: 0 0 0.35rem;
  font-family: var(--font-display);
}
.dlg-actions {
  display: flex;
  gap: 0.6rem;
  flex-wrap: wrap;
  margin-top: 0.85rem;
}
</style>

