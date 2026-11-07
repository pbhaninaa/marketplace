<script setup>
import { computed } from 'vue';
import { PASSWORD_HINT, passwordProblem } from '../../utils/passwordPolicy';

/** Shows the password rules, or what is wrong with the current value (same rules as the server). */
const props = defineProps({
  password: { type: String, default: '' },
  email: { type: String, default: '' },
});

const problem = computed(() => (props.password ? passwordProblem(props.password, props.email) : ''));
</script>

<template>
  <p class="muted small password-hint" :class="{ 'password-hint--warn': problem }" aria-live="polite">
    {{ problem || PASSWORD_HINT }}
  </p>
</template>

<style scoped>
.password-hint {
  margin: 0.15rem 0 0.35rem;
}

.password-hint--warn {
  color: var(--color-danger, #b42318);
}
</style>
