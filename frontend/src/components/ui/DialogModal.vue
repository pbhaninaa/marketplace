<script setup>
import { onMounted, onUnmounted } from 'vue';

const props = defineProps({
  title: { type: String, default: '' },
  message: { type: String, default: '' },
  type: { type: String, default: 'info' }, // 'info', 'success', 'error', 'warning', 'confirm'
  confirmText: { type: String, default: 'OK' },
  cancelText: { type: String, default: 'Cancel' },
  showCancel: { type: Boolean, default: false },
});

const emit = defineEmits(['confirm', 'cancel', 'close']);

function handleConfirm() {
  emit('confirm');
  emit('close');
}

function handleCancel() {
  emit('cancel');
  emit('close');
}

function handleEscape(e) {
  if (e.key === 'Escape') {
    handleCancel();
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleEscape);
  document.body.style.overflow = 'hidden';
});

onUnmounted(() => {
  document.removeEventListener('keydown', handleEscape);
  document.body.style.overflow = '';
});
</script>

<template>
  <Teleport to="body">
    <div class="dialog-overlay" @click.self="handleCancel">
      <div class="dialog-modal" :class="`dialog-modal--${type}`" role="dialog" aria-modal="true">
        
        <!-- Icon based on type -->
        <div class="dialog-icon">
          <span v-if="type === 'success'" class="material-icons">check_circle</span>
          <span v-else-if="type === 'error'" class="material-icons">error</span>
          <span v-else-if="type === 'warning'" class="material-icons">warning</span>
          <span v-else-if="type === 'confirm'" class="material-icons">help_outline</span>
          <span v-else class="material-icons">info</span>
        </div>

        <!-- Content -->
        <div class="dialog-content">
          <h3 v-if="title" class="dialog-title">{{ title }}</h3>
          <p class="dialog-message">{{ message }}</p>
        </div>

        <!-- Actions -->
        <div class="dialog-actions">
          <button
            v-if="showCancel"
            type="button"
            class="btn btn-ghost"
            @click="handleCancel"
          >
            {{ cancelText }}
          </button>
          <button
            type="button"
            :class="['btn', type === 'error' ? 'btn-danger' : 'btn-primary']"
            @click="handleConfirm"
            autofocus
          >
            {{ confirmText }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(28, 36, 24, 0.5);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 1rem;
  animation: fadeIn 0.2s var(--ease-out);
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.dialog-modal {
  background: var(--color-surface-elevated);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  max-width: 450px;
  width: 100%;
  padding: 1.75rem;
  animation: slideUp 0.3s var(--ease-out);
  border: 1px solid var(--color-border);
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.96);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.dialog-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  margin: 0 auto 1.25rem;
}

.dialog-icon .material-icons {
  font-size: 32px;
}

/* Icon colors based on type */
.dialog-modal--success .dialog-icon {
  background: var(--color-success-bg);
  color: var(--color-success-text);
}

.dialog-modal--error .dialog-icon {
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
}

.dialog-modal--warning .dialog-icon {
  background: var(--color-wheat-soft);
  color: var(--color-earth);
}

.dialog-modal--confirm .dialog-icon {
  background: var(--color-info-bg);
  color: var(--color-info-text);
}

.dialog-modal--info .dialog-icon {
  background: var(--color-sage-soft);
  color: var(--color-canopy);
}

.dialog-content {
  text-align: center;
  margin-bottom: 1.5rem;
}

.dialog-title {
  font-family: var(--font-display);
  font-size: 1.35rem;
  font-weight: 600;
  margin: 0 0 0.5rem;
  color: var(--color-canopy);
}

.dialog-message {
  font-size: 0.95rem;
  line-height: 1.6;
  color: var(--color-text-secondary);
  margin: 0;
}

.dialog-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
}

.dialog-actions .btn {
  min-width: 100px;
}

/* Danger button variant */
.btn-danger {
  background: var(--color-danger-text);
  color: white;
  border: 1px solid var(--color-danger-text);
}

.btn-danger:hover {
  background: #6b1f14;
  border-color: #6b1f14;
}

.btn-danger:active {
  background: #4a1510;
}
</style>
