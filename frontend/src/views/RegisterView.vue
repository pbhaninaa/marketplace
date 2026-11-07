<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { publicAccountApi, publicReferralApi } from '../services/marketplaceApi';
import { useSetupStore } from '../stores/setup';
import FormField from '../components/ui/FormField.vue';
import PasswordHint from '../components/ui/PasswordHint.vue';
import { passwordProblem } from '../utils/passwordPolicy';
import { apiErrorMessage } from '../utils/apiError';
import { businessNameError, emailError, textError, isValidEmail, LIMITS } from '../utils/validation';
import {
  captureInviteFromQuery,
  clearInviteRef,
  normalizeReferralCode,
  readInviteRef,
} from '../utils/referralInvite';

const router = useRouter();
const route = useRoute();
const setup = useSetupStore();

// Referral code: pre-filled from ?ref= or a remembered invite link, but always editable so it can be
// typed on a different device than the one that opened the link.
const referralCode = ref(captureInviteFromQuery(route.query) || readInviteRef());
const referralHint = ref('');
const referralError = ref('');

async function checkReferralCode() {
  referralHint.value = '';
  referralError.value = '';
  const code = normalizeReferralCode(referralCode.value);
  if (!referralCode.value.trim()) return;
  if (!code) {
    referralError.value = 'Referral codes use letters and numbers only.';
    return;
  }
  try {
    const { data } = await publicReferralApi.lookup(code);
    if (data?.valid) {
      referralHint.value = data.referrerName ? `Referred by ${data.referrerName}` : 'Valid referral code';
    } else {
      referralError.value = 'That referral code was not found. Check it or leave it blank.';
    }
  } catch {
    // lookup is a convenience; the server validates on submit
  }
}

const businessName = ref('');
const description = ref('');
const location = ref('');
const subtype = ref('RESELLER');
const ownerEmail = ref('');
const password = ref('');
const emailCode = ref('');
const error = ref('');
const done = ref(false);
const submitting = ref(false);
/** Inline messages show after the first submit attempt (same rules as the API). */
const showErrors = ref(false);

const fieldErrors = computed(() => ({
  businessName: businessNameError(businessName.value, { label: 'Business name' }),
  description: textError(description.value, { required: true, label: 'Description', max: LIMITS.providerDescription }),
  location: textError(location.value, { required: true, label: 'Location', max: LIMITS.location }),
  ownerEmail: emailError(ownerEmail.value, { label: 'Owner email' }),
  password: password.value ? passwordProblem(password.value, ownerEmail.value) : 'Password is required.',
}));
const shownError = (field) => (showErrors.value ? fieldErrors.value[field] : '');

const codeSending = ref(false);
const codeSentTo = ref('');
const codeInfo = ref('');
const resendIn = ref(0);
let resendTimer = null;

const needsCode = computed(() => setup.signupEmailVerificationRequired !== false);
const pwProblem = computed(() => (password.value ? passwordProblem(password.value, ownerEmail.value) : ''));

onMounted(() => {
  setup.fetchStatus().catch(() => {});
  if (referralCode.value) checkReferralCode();
});

onUnmounted(() => {
  if (resendTimer) clearInterval(resendTimer);
});

// A code proves ownership of one address: changing the email means asking for a new code.
watch(ownerEmail, (v) => {
  if (codeSentTo.value && v.trim().toLowerCase() !== codeSentTo.value) {
    codeSentTo.value = '';
    codeInfo.value = '';
    emailCode.value = '';
  }
});

function startResendCountdown(seconds) {
  resendIn.value = seconds;
  if (resendTimer) clearInterval(resendTimer);
  resendTimer = setInterval(() => {
    resendIn.value -= 1;
    if (resendIn.value <= 0) {
      clearInterval(resendTimer);
      resendTimer = null;
    }
  }, 1000);
}

async function sendCode() {
  error.value = '';
  const email = ownerEmail.value.trim();
  if (!isValidEmail(email)) {
    error.value = 'Enter a valid owner email first.';
    return;
  }
  codeSending.value = true;
  try {
    await publicAccountApi.requestSignupEmailCode({ email });
    codeSentTo.value = email.toLowerCase();
    codeInfo.value = `We sent a 6-digit code to ${email}. It expires in 10 minutes. Check your spam folder if it doesn't arrive.`;
    startResendCountdown(60);
  } catch (e) {
    error.value = apiErrorMessage(e, 'Could not send the code. Please try again.');
  } finally {
    codeSending.value = false;
  }
}

async function submit() {
  error.value = '';
  showErrors.value = true;
  if (Object.values(fieldErrors.value).some(Boolean)) return;
  if (needsCode.value && !emailCode.value.trim()) {
    error.value = codeSentTo.value
      ? 'Enter the code we emailed you.'
      : 'Confirm your email first: tap "Email me a code" and enter the code we send.';
    return;
  }
  submitting.value = true;
  try {
    await publicAccountApi.registerProvider({
      businessName: businessName.value.trim(),
      description: description.value.trim(),
      location: location.value.trim(),
      subtype: subtype.value,
      ownerEmail: ownerEmail.value.trim(),
      password: password.value,
      referralCode: normalizeReferralCode(referralCode.value) || null,
      emailVerificationCode: needsCode.value ? emailCode.value.trim() : null,
    });
    clearInviteRef();
    done.value = true;
    setTimeout(() => {
      router.push({
        path: '/login',
        query: {
          email: ownerEmail.value.trim(),
          redirect: '/provider/subscription',
        },
      });
    }, 1200);
  } catch (e) {
    error.value = apiErrorMessage(e, 'Could not create your account. Please try again.');
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="page-document register-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">New provider</p>
      <h1 class="page-hero__title">Sign up</h1>
      <p class="page-hero__lead">
        Create your business profile and owner account. After you sign in, choose a subscription plan first.
        Monthly access runs for 30 days from approval. Then you can manage listings and your team.
      </p>
    </header>

    <form class="surface-panel register-form" @submit.prevent="submit">
      <FormField label="Business name" capitalize-first :error="shownError('businessName')">
        <input v-model="businessName" type="text" required :maxlength="LIMITS.businessName" autocomplete="organization" />
      </FormField>
      <FormField label="Description" capitalize-first :error="shownError('description')">
        <textarea
          v-model="description"
          required
          :maxlength="LIMITS.providerDescription"
          rows="4"
          placeholder="What you offer, service area, specialties…"
        />
      </FormField>
      <FormField label="Location" capitalize-first :error="shownError('location')">
        <input v-model="location" type="text" required :maxlength="LIMITS.location" autocomplete="street-address" />
      </FormField>
      <FormField label="Provider type">
        <select v-model="subtype" required>
          <option value="RESELLER">Reseller</option>
          <option value="RENTING_OWNER">Renting owner</option>
        </select>
      </FormField>
      <FormField label="Owner email" :error="shownError('ownerEmail')">
        <input v-model="ownerEmail" type="email" required :maxlength="LIMITS.email" autocomplete="email" />
      </FormField>
      <FormField label="Referral code (optional)" :error="referralError">
        <input
          v-model="referralCode"
          type="text"
          maxlength="32"
          autocomplete="off"
          placeholder="From the business that invited you"
          @change="checkReferralCode"
        />
        <p v-if="referralHint" class="referral-hint">{{ referralHint }}</p>
      </FormField>
      <template v-if="needsCode">
        <div class="register-code">
          <button
            type="button"
            class="btn btn-ghost"
            :disabled="codeSending || resendIn > 0 || done"
            @click="sendCode"
          >
            {{
              codeSending
                ? 'Sending…'
                : resendIn > 0
                  ? `Resend code in ${resendIn}s`
                  : codeSentTo
                    ? 'Resend code'
                    : 'Email me a code'
            }}
          </button>
          <p v-if="codeInfo" class="muted small" role="status">{{ codeInfo }}</p>
          <p v-else class="muted small">We email you a code to confirm the address belongs to you.</p>
        </div>
        <FormField label="Email verification code">
          <input
            v-model="emailCode"
            type="text"
            inputmode="numeric"
            autocomplete="one-time-code"
            pattern="[0-9]*"
            maxlength="6"
            placeholder="6-digit code"
            :required="needsCode"
          />
        </FormField>
      </template>
      <FormField label="Password" :error="pwProblem || shownError('password')">
        <input
          v-model="password"
          type="password"
          required
          minlength="8"
          maxlength="64"
          autocomplete="new-password"
        />
      </FormField>
      <PasswordHint v-if="!pwProblem" :password="password" :email="ownerEmail" />
      <p v-if="error" class="err-toast" role="alert">{{ error }}</p>
      <p v-if="done" class="ok-msg">Account created. Sign in, then choose your subscription plan…</p>
      <button type="submit" class="btn btn-primary" :disabled="done || submitting">
        {{ submitting ? 'Creating…' : 'Create account' }}
      </button>
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

.register-code {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 0.25rem;
}

.register-code .btn {
  margin-top: 0;
  align-self: flex-start;
}

.register-footer {
  margin-top: 1rem;
  font-size: 0.9rem;
  text-align: center;
  color: var(--color-muted, #5c6570);
}

.referral-hint {
  margin: 0.3rem 0 0;
  font-size: 0.85rem;
  color: var(--color-sage, #3d7a66);
}

.register-footer a {
  margin-left: 0.25rem;
}
</style>
