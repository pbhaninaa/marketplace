<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api';
import { useSetupStore } from '../stores/setup';
import FormField from '../components/ui/FormField.vue';

const router = useRouter();
const setup = useSetupStore();

const businessName = ref('');
const description = ref('');
const location = ref('');
const subtype = ref('RESELLER');
const ownerEmail = ref('');
const password = ref('');
const error = ref('');
const done = ref(false);

onMounted(() => {
  setup.fetchStatus().catch(() => {});
});

async function submit() {
  error.value = '';
  try {
    await api.post('/api/public/provider/register', {
      businessName: businessName.value.trim(),
      description: description.value.trim(),
      location: location.value.trim(),
      subtype: subtype.value,
      ownerEmail: ownerEmail.value.trim(),
      password: password.value,
    });
    done.value = true;
    setTimeout(() => {
      router.push({ path: '/login', query: { email: ownerEmail.value.trim() } });
    }, 1200);
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}
</script>

<template>
  <div class="page-document register-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">New provider</p>
      <h1 class="page-hero__title">Sign up</h1>
      <p class="page-hero__lead">
        Create your business profile and owner account. Available after the platform administrator has been created.
        After approval you can sign in to manage listings and team.
      </p>
    </header>

    <form class="surface-panel register-form" @submit.prevent="submit">
      <FormField label="Business name" capitalize-first>
        <input v-model="businessName" type="text" required maxlength="200" autocomplete="organization" />
      </FormField>
      <FormField label="Description" capitalize-first>
        <textarea
          v-model="description"
          required
          maxlength="4000"
          rows="4"
          placeholder="What you offer, service area, specialties…"
        />
      </FormField>
      <FormField label="Location" capitalize-first>
        <input v-model="location" type="text" required maxlength="500" autocomplete="street-address" />
      </FormField>
      <FormField label="Provider type">
        <select v-model="subtype" required>
          <option value="RESELLER">Reseller</option>
          <option value="RENTING_OWNER">Renting owner</option>
        </select>
      </FormField>
      <FormField label="Owner email">
        <input v-model="ownerEmail" type="email" required autocomplete="email" />
      </FormField>
      <FormField label="Password">
        <input v-model="password" type="password" required minlength="8" maxlength="100" autocomplete="new-password" />
      </FormField>
      <p v-if="error" class="err-toast">{{ error }}</p>
      <p v-if="done" class="ok-msg">Account created. Redirecting to sign in…</p>
      <button type="submit" class="btn btn-primary" :disabled="done">Create account</button>
      <p class="register-footer">
        Already have an account?
        <router-link to="/login">Sign in</router-link>
      </p>
    </form>
  </div>
</template>

<style scoped>
.register-page {
  padding: 0.5rem 0 2rem;
}

.register-form {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  max-width: 440px;
  margin: 0 auto;
}

.register-form textarea {
  width: 100%;
  resize: vertical;
  min-height: 5rem;
}

.register-form .btn {
  margin-top: 0.75rem;
}

.register-footer {
  margin-top: 1rem;
  font-size: 0.9rem;
  text-align: center;
  color: var(--color-muted, #5c6570);
}

.register-footer a {
  margin-left: 0.25rem;
}
</style>
