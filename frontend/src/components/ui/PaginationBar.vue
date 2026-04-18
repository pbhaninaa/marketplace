<script setup>
defineProps({
  page: { type: Number, required: true },
  totalPages: { type: Number, required: true },
});

const emit = defineEmits(['prev', 'next']);
</script>

<template>
  <div class="pagination-bar">
    <div class="pagination-bar__inner">
      <button type="button" class="btn btn-ghost" :disabled="page <= 0" @click="emit('prev')">Previous</button>
      <span class="pagination-bar__meta">Page {{ page + 1 }} / {{ totalPages }}</span>
      <button type="button" class="btn btn-ghost" :disabled="page + 1 >= totalPages" @click="emit('next')">
        Next
      </button>
    </div>
  </div>
</template>

<style scoped>
.pagination-bar {
  margin-top: 2rem;
  display: flex;
  justify-content: center;
}

.pagination-bar__inner {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
  justify-content: center;
  padding: 0.5rem 0.75rem;
  background: var(--color-surface-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  box-shadow: var(--shadow-sm);
}

.pagination-bar__meta {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  padding: 0 0.5rem;
}
</style>
