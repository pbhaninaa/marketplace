<script setup>
defineProps({
  columns: {
    type: Array,
    required: true,
    // Each column: { key: 'id', label: 'ID', sortable: false, width: '10%' }
  },
  rows: {
    type: Array,
    required: true,
  },
  striped: {
    type: Boolean,
    default: true,
  },
  hoverable: {
    type: Boolean,
    default: true,
  },
  compact: {
    type: Boolean,
    default: false,
  },
  emptyMessage: {
    type: String,
    default: 'No records found.',
  },
});

defineEmits(['row-click']);
</script>

<template>
  <div class="base-table-wrapper">
    <table
      :class="[
        'base-table',
        {
          'base-table--striped': striped,
          'base-table--hoverable': hoverable,
          'base-table--compact': compact,
        },
      ]"
    >
      <thead>
        <tr>
          <th v-for="col in columns" :key="col.key" :style="{ width: col.width }">
            {{ col.label }}
          </th>
        </tr>
      </thead>
      <tbody v-if="rows.length">
        <tr v-for="(row, idx) in rows" :key="idx" @click="$emit('row-click', row)">
          <td v-for="col in columns" :key="col.key">
            <slot :name="`cell-${col.key}`" :row="row" :value="row[col.key]">
              {{ row[col.key] }}
            </slot>
          </td>
        </tr>
      </tbody>
      <tbody v-else>
        <tr>
          <td :colspan="columns.length" class="base-table__empty">
            {{ emptyMessage }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.base-table-wrapper {
  width: 100%;
  overflow-x: auto;
}

.base-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.95rem;
}

.base-table thead {
  background: var(--color-canopy);
  color: white;
}

.base-table thead th {
  padding: 1rem 0.8rem;
  text-align: left;
  font-weight: 600;
  font-size: 0.9rem;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  border-bottom: 2px solid var(--color-canopy-mid);
}

.base-table tbody tr {
  border-bottom: 1px solid var(--color-border);
}

.base-table tbody td {
  padding: 0.85rem 0.8rem;
  color: var(--color-text);
}

/* ==================== VARIANTS ==================== */
.base-table--striped tbody tr:nth-child(odd) {
  background: var(--color-bg);
}

.base-table--striped tbody tr:nth-child(even) {
  background: var(--color-surface-elevated);
}

.base-table--hoverable tbody tr:hover {
  background: rgba(61, 122, 102, 0.05);
  cursor: pointer;
}

.base-table--compact tbody td {
  padding: 0.5rem 0.6rem;
}

.base-table--compact thead th {
  padding: 0.65rem 0.6rem;
}

/* ==================== EMPTY STATE ==================== */
.base-table__empty {
  text-align: center;
  color: var(--color-muted);
  padding: 2rem !important;
  font-style: italic;
}
</style>
