<script setup>
import { computed, onMounted, ref } from 'vue';
import { adminProviderSubscriptionApi } from '../../services/marketplaceApi';
import { useDialog } from '../../composables/useDialog';
import BaseButton from '../ui/BaseButton.vue';
import BaseInput from '../ui/BaseInput.vue';
import BaseSelect from '../ui/BaseSelect.vue';
import BaseTextarea from '../ui/BaseTextarea.vue';
import DataTableShell from '../ui/DataTableShell.vue';
import {
  OVERRIDE_ACTION_LABELS,
  OVERRIDE_PLAN_OPTIONS,
  toIsoDate,
  validateActiveUntil,
  validateExtendDays,
  validateReason,
} from '../../utils/subscriptionOverride';

const props = defineProps({
  providerId: { type: Number, required: true },
});

const dialog = useDialog();

const loading = ref(true);
const busy = ref(false);
const error = ref('');
const message = ref('');
const sub = ref(null);

const days = ref('7');
const plan = ref('BASIC');
const activeUntil = ref('');
const reason = ref('');
const fieldErrors = ref({ days: '', activeUntil: '', reason: '' });

const today = toIsoDate(new Date());
const isOwner = computed(() => !!sub.value?.platformOwner);

const fmtDate = (v) => (v ? new Date(v).toLocaleDateString() : '—');
const fmtDateTime = (v) => (v ? new Date(v).toLocaleString() : '—');
const planLabel = (p) => (p === 'PREMIUM' ? 'Premium' : p === 'BASIC' ? 'Basic' : '—');

const stateLabel = computed(() => {
  const s = sub.value;
  if (!s) return '—';
  if (s.valid && s.plan && s.status === 'ACTIVE') return `${planLabel(s.plan)} — active`;
  if (s.onTrial) return 'Free trial';
  if (s.status) return s.status.replaceAll('_', ' ').toLowerCase();
  return 'No subscription';
});

onMounted(load);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const res = await adminProviderSubscriptionApi.get(props.providerId);
    sub.value = res.data;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

function checkReason() {
  fieldErrors.value.reason = validateReason(reason.value);
  return !fieldErrors.value.reason;
}

async function run(confirmMessage, confirmTitle, call, doneMessage) {
  error.value = '';
  message.value = '';
  const ok = await dialog.confirm(confirmMessage, confirmTitle);
  if (!ok) return;
  busy.value = true;
  try {
    const res = await call();
    sub.value = res.data;
    message.value = doneMessage;
    reason.value = '';
    fieldErrors.value = { days: '', activeUntil: '', reason: '' };
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    busy.value = false;
  }
}

function extendTrial() {
  fieldErrors.value.days = validateExtendDays(days.value);
  const reasonOk = checkReason();
  if (fieldErrors.value.days || !reasonOk) return;
  const n = Number(days.value);
  run(
    `Extend ${sub.value.providerName}'s free trial by ${n} day(s)? No payment is taken and the provider is notified.`,
    'Extend free trial',
    () => adminProviderSubscriptionApi.extendTrial(props.providerId, n, reason.value.trim()),
    `Trial extended by ${n} day(s).`,
  );
}

function endTrial() {
  if (!checkReason()) return;
  run(
    `End ${sub.value.providerName}'s free trial now? Unless they have a paid plan, provider features lock until they subscribe.`,
    'End free trial',
    () => adminProviderSubscriptionApi.endTrial(props.providerId, reason.value.trim()),
    'Trial ended.',
  );
}

function setSubscription() {
  fieldErrors.value.activeUntil = validateActiveUntil(activeUntil.value);
  const reasonOk = checkReason();
  if (fieldErrors.value.activeUntil || !reasonOk || !plan.value) return;
  const until = activeUntil.value ? activeUntil.value : '30 days from now';
  run(
    `Set ${sub.value.providerName} to ${planLabel(plan.value)} until ${until} without a Peach payment? Their current plan is replaced and they are notified.`,
    'Set subscription',
    () =>
      adminProviderSubscriptionApi.setSubscription(props.providerId, {
        plan: plan.value,
        activeUntil: activeUntil.value,
        reason: reason.value.trim(),
      }),
    'Subscription updated.',
  );
}
</script>

<template>
  <section class="surface-panel admin-panel">
    <div class="toolbar">
      <h2>Subscription</h2>
      <button type="button" class="btn btn-ghost" :disabled="loading || busy" @click="load">Refresh</button>
    </div>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="message" class="ok-msg">{{ message }}</p>
    <p v-if="loading" class="muted loading-line">Loading…</p>

    <template v-else-if="sub">
      <div class="kv">
        <div><span class="k">State</span><span class="v">{{ stateLabel }}</span></div>
        <div><span class="k">Plan</span><span class="v">{{ planLabel(sub.plan) }}</span></div>
        <div><span class="k">Active until</span><span class="v">{{ fmtDate(sub.expiresAt) }}</span></div>
        <div>
          <span class="k">Trial</span>
          <span class="v">
            {{ fmtDate(sub.trialStartedAt) }} → {{ fmtDate(sub.trialEndsAt) }}
            <template v-if="sub.onTrial"> ({{ sub.trialDaysRemaining }} days left)</template>
          </span>
        </div>
      </div>
      <p v-if="sub.overridden" class="tiny muted override-note">Current plan was set by the platform owner (no payment).</p>

      <div v-if="isOwner" class="owner-tools">
        <h3>Platform owner overrides</h3>
        <p class="small muted">
          Only you (the first platform admin) can change this. Every change is logged with your account, the time and the
          reason, and the provider is notified.
        </p>

        <BaseTextarea
          v-model="reason"
          label="Reason (required, recorded in the audit trail)"
          :rows="2"
          :error="fieldErrors.reason"
          placeholder="e.g. Pilot partner, test account, fixing a billing mistake"
        />

        <div class="override-grid">
          <div class="override-card">
            <h4>Free trial</h4>
            <BaseInput v-model="days" type="number" label="Extend by (days)" :error="fieldErrors.days" />
            <div class="actions">
              <BaseButton variant="primary" :disabled="busy" @click="extendTrial">Extend trial</BaseButton>
              <BaseButton variant="danger" :disabled="busy || !sub.onTrial" @click="endTrial">End trial now</BaseButton>
            </div>
          </div>

          <div class="override-card">
            <h4>Set subscription (no payment)</h4>
            <BaseSelect v-model="plan" :options="OVERRIDE_PLAN_OPTIONS" label="Plan" required />
            <BaseInput
              v-model="activeUntil"
              type="date"
              label="Active until (optional, default 30 days)"
              :error="fieldErrors.activeUntil"
              :placeholder="today"
            />
            <div class="actions">
              <BaseButton variant="primary" :disabled="busy || !plan" @click="setSubscription">Set subscription</BaseButton>
            </div>
          </div>
        </div>
      </div>
      <p v-else class="small muted owner-hint">Only the platform owner (first admin) can override trials and subscriptions.</p>

      <h3 class="history-title">Override history</h3>
      <DataTableShell caption="Subscription override history">
        <thead>
          <tr>
            <th>When</th>
            <th>Action</th>
            <th>By</th>
            <th>Reason</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="h in sub.history || []" :key="h.id">
            <td>{{ fmtDateTime(h.createdAt) }}</td>
            <td>
              <strong>{{ OVERRIDE_ACTION_LABELS[h.action] || h.action }}</strong>
              <div class="tiny muted">{{ h.details }}</div>
            </td>
            <td>{{ h.actorEmail }}</td>
            <td>{{ h.reason }}</td>
          </tr>
          <tr v-if="!(sub.history || []).length">
            <td colspan="4" class="muted small">No overrides yet.</td>
          </tr>
        </tbody>
      </DataTableShell>
    </template>
  </section>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.75rem;
}
.kv {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem 1.25rem;
}
.kv .k {
  display: inline-block;
  min-width: 82px;
  color: var(--color-muted);
  font-size: 0.85rem;
}
.kv .v {
  font-weight: 700;
}
.override-note,
.owner-hint {
  margin-top: 0.75rem;
}
.owner-tools {
  margin-top: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.owner-tools h3,
.history-title {
  margin: 0;
}
.history-title {
  margin-top: 1.25rem;
  margin-bottom: 0.5rem;
}
.override-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}
.override-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.override-card h4 {
  margin: 0;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
@media (max-width: 980px) {
  .kv,
  .override-grid {
    grid-template-columns: 1fr;
  }
}
</style>
