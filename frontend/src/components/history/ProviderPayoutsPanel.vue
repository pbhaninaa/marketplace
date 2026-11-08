<script setup>
// Read-only: the provider's online (Card / Instant EFT) payments, the platform fee and payout status.
import { computed, onMounted, ref } from 'vue';
import DataTableShell from '../ui/DataTableShell.vue';
import { providerPayoutsApi } from '../../services/marketplaceApi';
import { formatRand, settlementLabel, settlementTone } from '../../utils/payouts';

const data = ref(null);
const loading = ref(true);
const error = ref('');
const expanded = ref(false);

const payments = computed(() => data.value?.payments ?? []);
const shown = computed(() => (expanded.value ? payments.value : payments.value.slice(0, 10)));

function formatWhen(value) {
  if (!value) return 'Not set';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? 'Not set' : d.toLocaleDateString();
}

onMounted(async () => {
  try {
    const res = await providerPayoutsApi.get();
    data.value = res.data;
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Could not load payouts.';
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="surface-panel payouts-panel">
    <h2>Online payments and payouts</h2>
    <p class="muted small">
      Card / Instant EFT payments are collected by the platform and paid out to your bank account, less the platform
      fee<span v-if="data"> (currently {{ Number(data.onlineOrderFeePercent || 0).toFixed(2) }}%)</span>.
      Cash and manual EFT are paid to you directly and are not listed here.
    </p>
    <p v-if="loading" class="muted">Loading…</p>
    <p v-else-if="error" class="err-toast">{{ error }}</p>
    <template v-else>
      <div class="payout-stats">
        <div class="stat-card"><p class="stat-label">Waiting to be paid out</p><p class="stat-value">{{ formatRand(data.amountOwed) }}</p></div>
        <div class="stat-card"><p class="stat-label">Paid out to you</p><p class="stat-value">{{ formatRand(data.amountPaidOut) }}</p></div>
        <div class="stat-card"><p class="stat-label">Platform fees</p><p class="stat-value">{{ formatRand(data.platformFees) }}</p></div>
      </div>
      <p v-if="!payments.length" class="muted">No online payments yet.</p>
      <DataTableShell v-else caption="Online payments and payout status" :max-chars="32">
        <thead>
          <tr>
            <th>Paid</th>
            <th>Item</th>
            <th class="num">Buyer paid</th>
            <th class="num">Platform fee</th>
            <th class="num">You get</th>
            <th>Payout</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in shown" :key="p.paymentId">
            <td>{{ formatWhen(p.paidAt) }}</td>
            <td>
              <div>{{ p.kind === 'RENTAL' ? 'Rental' : 'Order' }} #{{ p.referenceId }}</div>
              <div class="muted small">{{ p.buyerName || '' }}</div>
            </td>
            <td class="num">{{ formatRand(p.amount) }}</td>
            <td class="num">{{ formatRand(p.platformFee) }}</td>
            <td class="num">{{ formatRand(p.settlementStatus === 'REFUND_DUE' || p.settlementStatus === 'REFUNDED' ? 0 : p.sellerPayoutAmount) }}</td>
            <td>
              <span class="badge" :class="`badge--${settlementTone(p.settlementStatus)}`">{{ settlementLabel(p.settlementStatus) }}</span>
              <div v-if="p.settlementStatus === 'PAID_OUT'" class="muted small">
                {{ formatWhen(p.paidOutAt) }}, ref {{ p.payoutReference }}
              </div>
            </td>
          </tr>
        </tbody>
      </DataTableShell>
      <button v-if="payments.length > 10" type="button" class="btn btn-ghost show-more" @click="expanded = !expanded">
        {{ expanded ? 'Show fewer' : `Show all ${payments.length}` }}
      </button>
    </template>
  </section>
</template>

<style scoped>
.payouts-panel {
  margin-bottom: 1.25rem;
}
.payout-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 0.75rem;
  margin: 0.75rem 0 1rem;
}
.stat-label {
  margin: 0;
  font-size: 0.8rem;
  color: var(--color-muted);
}
.stat-value {
  margin: 0.25rem 0 0;
  font-size: 1.2rem;
  font-weight: 700;
}
.num {
  text-align: right;
  white-space: nowrap;
}
.show-more {
  margin-top: 0.75rem;
}
.badge {
  display: inline-block;
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 600;
}
.badge--warn {
  background: #fef3c7;
  color: #92400e;
}
.badge--ok {
  background: #dcfce7;
  color: #15803d;
}
.badge--danger {
  background: #fee2e2;
  color: #b91c1c;
}
.badge--muted {
  background: #f1f5f9;
  color: #475569;
}
</style>
