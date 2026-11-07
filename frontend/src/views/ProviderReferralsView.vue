<script setup>
import { computed, onMounted, ref } from 'vue';
import FormField from '../components/ui/FormField.vue';
import { providerReferralsApi } from '../services/marketplaceApi';
import {
  buildInviteLink,
  formatPercent,
  formatZar,
  normalizeReferralCode,
  referralStatusLabel,
} from '../utils/referralInvite';

const loading = ref(true);
const error = ref('');
const data = ref(null);
const copied = ref('');

const claimCode = ref('');
const claiming = ref(false);
const claimError = ref('');
const claimMessage = ref('');

const inviteLink = computed(() =>
  data.value?.code ? buildInviteLink(window.location.origin, data.value.code) : '',
);

function formatDate(value) {
  if (!value) return 'Not set';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? 'Not set' : d.toLocaleDateString();
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const res = await providerReferralsApi.mine();
    data.value = res.data;
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Failed to load referrals.';
  } finally {
    loading.value = false;
  }
}

async function copy(text, what) {
  copied.value = '';
  try {
    await navigator.clipboard.writeText(text);
    copied.value = what;
  } catch {
    copied.value = 'Copy failed. Select the text and copy it manually.';
  }
}

async function share() {
  if (navigator.share) {
    try {
      await navigator.share({
        title: 'Join me on the marketplace',
        text: `Sign up with my referral code ${data.value.code}`,
        url: inviteLink.value,
      });
      return;
    } catch {
      // cancelled — fall back to copying
    }
  }
  await copy(inviteLink.value, 'Invite link copied.');
}

async function submitClaim() {
  claimError.value = '';
  claimMessage.value = '';
  const code = normalizeReferralCode(claimCode.value);
  if (!code) {
    claimError.value = 'Enter the referral code you were given (letters and numbers).';
    return;
  }
  claiming.value = true;
  try {
    const res = await providerReferralsApi.claim(code);
    data.value = res.data;
    claimCode.value = '';
    claimMessage.value = 'Thanks, your referrer has been recorded.';
  } catch (e) {
    claimError.value = e.response?.data?.message || e.message || 'Could not add the referral code.';
  } finally {
    claiming.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page-document page-document--wide">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Provider</p>
      <h1 class="page-hero__title">My referrals</h1>
      <p class="page-hero__lead">
        Invite other farms and agri businesses. When a business you referred pays its first subscription, you earn
        a commission on that payment. Payouts are made by the platform team.
      </p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="loading" class="muted">Loading…</p>

    <template v-else-if="data">
      <section class="surface-panel">
        <h2>Your invite</h2>
        <div class="invite-grid">
          <FormField label="Referral code">
            <div class="copy-row">
              <input :value="data.code" type="text" readonly class="mono" />
              <button type="button" class="btn btn-ghost" @click="copy(data.code, 'Code copied.')">Copy</button>
            </div>
          </FormField>
          <FormField label="Invite link">
            <div class="copy-row">
              <input :value="inviteLink" type="text" readonly />
              <button type="button" class="btn btn-primary" @click="share">Share</button>
            </div>
          </FormField>
        </div>
        <p v-if="copied" class="ok-msg">{{ copied }}</p>
        <p class="muted small">
          Current commission: <strong>{{ formatPercent(data.currentCommissionPercent) }}</strong> of the referred
          business's first subscription payment. If they sign up on another device, they can type your code on the
          sign-up form, or add it from their portal within {{ data.claimWindowDays }} days of joining.
        </p>
      </section>

      <section class="surface-panel">
        <h2>Totals</h2>
        <div class="stats">
          <div class="stat-card">
            <p class="stat-label">Businesses referred</p>
            <p class="stat-value">{{ data.invitedCount }}</p>
          </div>
          <div class="stat-card">
            <p class="stat-label">Paid their first subscription</p>
            <p class="stat-value">{{ data.qualifiedCount }}</p>
          </div>
          <div class="stat-card">
            <p class="stat-label">Commission earned</p>
            <p class="stat-value">{{ formatZar(data.earnedAmount) }}</p>
          </div>
          <div class="stat-card">
            <p class="stat-label">Awaiting payout</p>
            <p class="stat-value">{{ formatZar(data.awaitingPayoutAmount) }}</p>
          </div>
          <div class="stat-card">
            <p class="stat-label">Paid out</p>
            <p class="stat-value">{{ formatZar(data.paidOutAmount) }}</p>
          </div>
        </div>

        <p v-if="!data.referrals.length" class="muted">No referrals yet. Share your link to get started.</p>
        <div v-else class="table-wrap">
          <table class="ref-table">
            <thead>
              <tr>
                <th>Business</th>
                <th>Joined</th>
                <th>Status</th>
                <th>Rate</th>
                <th>Commission</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in data.referrals" :key="r.id">
                <td>{{ r.businessName }}</td>
                <td>{{ formatDate(r.joinedAt) }}</td>
                <td>{{ referralStatusLabel(r.status) }}</td>
                <td>{{ r.commissionPercent != null ? formatPercent(r.commissionPercent) : 'Not set' }}</td>
                <td>{{ r.commissionAmount != null ? formatZar(r.commissionAmount) : 'Not set' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="surface-panel">
        <h2>Who referred your business?</h2>
        <p v-if="data.invite?.referredByName">
          Your business was referred by <strong>{{ data.invite.referredByName }}</strong>.
        </p>
        <template v-else-if="data.invite?.canClaim">
          <p class="muted small">
            Signed up without a code? Add it here until
            <strong>{{ formatDate(data.invite.claimDeadline) }}</strong> (and before your first subscription payment).
          </p>
          <form class="claim-row" @submit.prevent="submitClaim">
            <FormField label="Referral code" :error="claimError">
              <input v-model="claimCode" type="text" maxlength="32" autocomplete="off" />
            </FormField>
            <button type="submit" class="btn btn-primary" :disabled="claiming">
              {{ claiming ? 'Saving…' : 'Add code' }}
            </button>
          </form>
        </template>
        <p v-else class="muted">{{ data.invite?.reason || 'No referrer recorded for your business.' }}</p>
        <p v-if="claimMessage" class="ok-msg">{{ claimMessage }}</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.invite-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 2fr);
  gap: 0.85rem;
}
.copy-row {
  display: flex;
  gap: 0.5rem;
}
.copy-row input {
  flex: 1;
  min-width: 0;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  letter-spacing: 0.06em;
}
.small {
  font-size: 0.85rem;
  margin-top: 0.6rem;
}
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 0.75rem;
  margin-bottom: 1rem;
}
.stat-label {
  margin: 0;
  font-size: 0.8rem;
  color: var(--color-muted);
}
.stat-value {
  margin: 0.25rem 0 0;
  font-size: 1.3rem;
  font-weight: 700;
}
.table-wrap {
  overflow-x: auto;
}
.ref-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.92rem;
}
.ref-table th,
.ref-table td {
  text-align: left;
  padding: 0.5rem 0.6rem;
  border-bottom: 1px solid var(--color-border);
}
.ref-table th {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-secondary);
}
.claim-row {
  display: flex;
  gap: 0.6rem;
  align-items: flex-end;
  max-width: 480px;
}
.claim-row > :first-child {
  flex: 1;
}
@media (max-width: 720px) {
  .invite-grid {
    grid-template-columns: 1fr;
  }
}
</style>
