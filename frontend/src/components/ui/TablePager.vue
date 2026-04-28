<script setup>
import { computed } from 'vue';

const props = defineProps({
  page: { type: Number, default: 1 },
  pageCount: { type: Number, default: 1 },
});

const emit = defineEmits(['update:page']);

const safePage = computed(() => {
  const p = Number(props.page || 1);
  if (!Number.isFinite(p) || p < 1) return 1;
  return Math.min(p, Math.max(1, Number(props.pageCount || 1)));
});

function setPage(p) {
  const next = Math.min(Math.max(1, p), Math.max(1, props.pageCount || 1));
  emit('update:page', next);
}
</script>

<template>
  <div class="table-pager" v-if="pageCount > 1">
    <button type="button" class="btn btn-ghost btn-xs" :disabled="safePage <= 1" @click="setPage(safePage - 1)">
      Prev
    </button>
    <span class="table-pager__meta muted small">Page <strong>{{ safePage }}</strong> of <strong>{{ pageCount }}</strong></span>
    <button type="button" class="btn btn-ghost btn-xs" :disabled="safePage >= pageCount" @click="setPage(safePage + 1)">
      Next
    </button>
  </div>
</template>

<style scoped>
.table-pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 0.75rem;
}
.table-pager__meta strong {
  color: var(--color-text);
}
</style>

