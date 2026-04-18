<script setup>
import { ref } from 'vue';
import { api } from '../../api';
import { useAuthStore } from '../../stores/auth';
import { useDialog } from '../../composables/useDialog';

const auth = useAuthStore();
const { success, error: showError } = useDialog();

const verificationCode = ref('');
const verifying = ref(false);
const lastResult = ref(null);

async function verifyCode() {
  if (!verificationCode.value || verificationCode.value.trim().length === 0) {
    await showError('Please enter a verification code', 'Invalid Input');
    return;
  }

  const code = verificationCode.value.trim().toUpperCase();
  verifying.value = true;
  lastResult.value = null;

  try {
    // Try to verify as purchase order first
    let response;
    let type = 'order';

    try {
      response = await api.post(`/api/provider/me/verify/order/${code}`);
    } catch (err) {
      // If not found as order, try as booking
      if (err.response?.status === 404) {
        response = await api.post(`/api/provider/me/verify/booking/${code}`);
        type = 'booking';
      } else {
        throw err;
      }
    }

    const result = response.data;
    lastResult.value = { ...result, type };

    if (result.message.includes('already verified')) {
      await showError(
        `This ${type} was already verified on ${new Date(result.verifiedAt).toLocaleString()}`,
        'Already Verified'
      );
    } else {
      const orderInfo = type === 'order' ? result.order : result.booking;
      await success(
        `${type === 'order' ? 'Order' : 'Booking'} #${orderInfo.id} verified successfully!\n\nGuest: ${orderInfo.guestName}\nEmail: ${orderInfo.guestEmail}\nTotal: R ${orderInfo.totalAmount}`,
        'Verification Success'
      );
      verificationCode.value = '';
    }
  } catch (e) {
    const message = e.response?.data?.message || e.message;
    await showError(message, 'Verification Failed');
  } finally {
    verifying.value = false;
  }
}

function handleInput(event) {
  // Auto-format to XXXX-XXXX pattern
  let value = event.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '');
  if (value.length > 4 && !value.includes('-')) {
    value = value.slice(0, 4) + '-' + value.slice(4, 8);
  }
  verificationCode.value = value.slice(0, 9); // Max 8 chars + 1 hyphen
}
</script>

<template>
  <div class="verify-code-panel">
    <div class="verify-header">
      <span class="material-icons verify-icon">verified_user</span>
      <h3>Verify Order/Booking</h3>
    </div>

    <p class="verify-instructions">
      Enter the 8-character verification code provided by the customer to confirm order collection or delivery.
    </p>

    <div class="verify-form">
      <div class="code-input-wrapper">
        <input
          type="text"
          v-model="verificationCode"
          @input="handleInput"
          placeholder="XXXX-XXXX"
          maxlength="9"
          class="code-input"
          :disabled="verifying"
        />
      </div>

      <button
        @click="verifyCode"
        :disabled="verifying || !verificationCode"
        class="verify-button"
      >
        <span v-if="verifying" class="material-icons rotating">hourglass_empty</span>
        <span v-else class="material-icons">check_circle</span>
        {{ verifying ? 'Verifying...' : 'Verify Code' }}
      </button>
    </div>

    <div v-if="lastResult" class="last-result">
      <div class="result-card" :class="lastResult.message.includes('already') ? 'result-warning' : 'result-success'">
        <span class="material-icons">
          {{ lastResult.message.includes('already') ? 'info' : 'check_circle' }}
        </span>
        <div class="result-info">
          <strong>{{ lastResult.message }}</strong>
          <p class="result-details">
            {{ lastResult.type === 'order' ? 'Order' : 'Booking' }} #{{
              (lastResult.order || lastResult.booking).id
            }} • {{ (lastResult.order || lastResult.booking).guestEmail }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.verify-code-panel {
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 24px;
  max-width: 600px;
  margin: 0 auto;
}

.verify-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.verify-icon {
  font-size: 32px;
  color: #2196f3;
}

.verify-header h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.verify-instructions {
  color: #666;
  margin-bottom: 24px;
  line-height: 1.5;
}

.verify-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.code-input-wrapper {
  position: relative;
}

.code-input {
  width: 100%;
  padding: 16px;
  font-size: 24px;
  font-family: 'Courier New', monospace;
  text-align: center;
  letter-spacing: 4px;
  border: 2px solid #ddd;
  border-radius: 8px;
  text-transform: uppercase;
  transition: border-color 0.3s;
}

.code-input:focus {
  outline: none;
  border-color: #2196f3;
}

.code-input:disabled {
  background: #f5f5f5;
  cursor: not-allowed;
}

.verify-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 24px;
  background: #4caf50;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.3s;
}

.verify-button:hover:not(:disabled) {
  background: #45a049;
}

.verify-button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.verify-button .material-icons {
  font-size: 20px;
}

.rotating {
  animation: rotate 1.5s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.last-result {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #e0e0e0;
}

.result-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border-radius: 8px;
}

.result-success {
  background: #e8f5e9;
  border: 1px solid #4caf50;
}

.result-warning {
  background: #fff3e0;
  border: 1px solid #ff9800;
}

.result-card .material-icons {
  font-size: 28px;
  margin-top: 2px;
}

.result-success .material-icons {
  color: #4caf50;
}

.result-warning .material-icons {
  color: #ff9800;
}

.result-info {
  flex: 1;
}

.result-info strong {
  display: block;
  margin-bottom: 4px;
  font-size: 15px;
}

.result-details {
  margin: 0;
  color: #666;
  font-size: 14px;
}

@media (max-width: 768px) {
  .verify-code-panel {
    padding: 16px;
  }

  .code-input {
    font-size: 20px;
    padding: 12px;
    letter-spacing: 2px;
  }

  .verify-button {
    padding: 12px 20px;
    font-size: 15px;
  }
}
</style>
