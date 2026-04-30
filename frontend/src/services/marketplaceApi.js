/**
 * Central place for HTTP calls to the marketplace backend.
 * Views and stores should import from here instead of string-building paths on axios.
 */
import { api, withSession, postMultipart, putMultipart } from '../api';

export const authApi = {
  login(body) {
    return api.post('/api/auth/login', body);
  },
  changePassword(body) {
    return api.post('/api/auth/change-password', body);
  },
};

export const publicSessionApi = {
  createCartSession() {
    return api.post('/api/public/cart/session');
  },
};

export const publicSetupApi = {
  getSetupStatus() {
    return api.get('/api/public/setup-status');
  },
  createFirstAdmin(body) {
    return api.post('/api/public/first-admin', body);
  },
};

export const publicAccountApi = {
  forgotPassword(body) {
    return api.post('/api/public/forgot-password', body);
  },
  resetPassword(body) {
    return api.post('/api/public/reset-password', body);
  },
  registerProvider(body) {
    return api.post('/api/public/provider/register', body);
  },
};

export const publicClientOtpApi = {
  request(body) {
    return api.post('/api/public/client/otp/request', body);
  },
  verify(body) {
    return api.post('/api/public/client/otp/verify', body);
  },
};

export const publicCatalogApi = {
  categoryOptions(params) {
    return api.get('/api/public/category-options', { params });
  },
  providerOptions(params) {
    return api.get('/api/public/provider-options', { params });
  },
  listings(params) {
    return api.get('/api/public/listings', { params });
  },
};

export const publicCartApi = {
  getCart(sessionId) {
    return api.get('/api/public/cart', withSession(sessionId));
  },
  addLine(sessionId, body) {
    return api.post('/api/public/cart/add', body, withSession(sessionId));
  },
  clear(sessionId) {
    return api.delete('/api/public/cart', withSession(sessionId));
  },
  updateLineQuantity(sessionId, lineId, quantity) {
    return api.patch(
      `/api/public/cart/items/${lineId}/quantity`,
      { quantity },
      withSession(sessionId),
    );
  },
  removeLine(sessionId, lineId) {
    return api.delete(`/api/public/cart/items/${lineId}`, withSession(sessionId));
  },
  checkout(sessionId, body) {
    return api.post('/api/public/cart/checkout', body, withSession(sessionId));
  },
};

export const providerSettingsApi = {
  get() {
    return api.get('/api/provider/me/settings');
  },
  patch(body) {
    return api.patch('/api/provider/me/settings', body);
  },
  deleteAccount() {
    return api.delete('/api/provider/me/account');
  },
};

export const providerListingsApi = {
  list() {
    return api.get('/api/provider/me/listings');
  },
  create(body) {
    return api.post('/api/provider/me/listings', body);
  },
  update(id, body) {
    return api.put(`/api/provider/me/listings/${id}`, body);
  },
  remove(id) {
    return api.delete(`/api/provider/me/listings/${id}`);
  },
  createWithImages(formData) {
    return postMultipart('/api/provider/me/listing-with-images', formData);
  },
  updateWithImages(id, formData) {
    return putMultipart(`/api/provider/me/listings/${id}/with-images`, formData);
  },
};

function triggerBlobDownload(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export const providerOrdersApi = {
  listPurchases(params) {
    return api.get('/api/provider/me/orders/purchases', { params });
  },
  listRentals(params) {
    return api.get('/api/provider/me/orders/rentals', { params });
  },
  getPurchase(id) {
    return api.get(`/api/provider/me/orders/purchases/${id}`);
  },
  getRental(id) {
    return api.get(`/api/provider/me/orders/rentals/${id}`);
  },
  getOrderItems(orderId) {
    return api.get(`/api/provider/me/orders/purchases/${orderId}/items`);
  },
  updateStock(stockUpdates) {
    return api.put('/api/provider/me/listings/update-stock', stockUpdates);
  },
  updatePurchaseStatus(id, status) {
    return api.put(`/api/provider/me/orders/purchases/${id}/status`, null, {
      params: { status },
    });
  },
   updateRentalStatus(id, status) {
    return api.put(`/api/provider/me/orders/rentals/${id}/status`, null, {
      params: { status },
    });
  },
  deletePurchase(id) {
    return api.delete(`/api/provider/me/orders/purchases/${id}`);
  },
  deleteRental(id) {
    return api.delete(`/api/provider/me/orders/rentals/${id}`);
  },
  deletePurchases(ids) {
    return api.delete('/api/provider/me/orders/purchases', {
      params: { ids },
    });
  },
  verifyPurchaseCode(code) {
    return api.post(`/api/provider/me/verify/order/${encodeURIComponent(code)}`);
  },
  verifyBookingCode(code) {
    return api.post(`/api/provider/me/verify/booking/${encodeURIComponent(code)}`);
  },
  deleteAllPurchases() {
    return api.delete('/api/provider/me/orders/purchases');
  },
  deleteAllRentals() {
    return api.delete('/api/provider/me/orders/rentals');
  },
  async downloadPurchaseInvoice(orderId) {
    const res = await api.get(`/api/provider/me/orders/purchases/${orderId}/invoice`, { responseType: 'blob' });
    triggerBlobDownload(res.data, `invoice-order-${orderId}.pdf`);
  },
  async downloadRentalInvoice(rentalId) {
    const res = await api.get(`/api/provider/me/orders/rentals/${rentalId}/invoice`, { responseType: 'blob' });
    triggerBlobDownload(res.data, `invoice-rental-${rentalId}.pdf`);
  },
};

/** Guest: numeric order id + email, or verification code (optional email must match if provided). */
export const publicOrderInvoiceApi = {
  async download({ orderRef, email }) {
    const res = await api.get('/api/public/order-invoice', {
      params: { orderRef, ...(email ? { email } : {}) },
      responseType: 'blob',
    });
    const safe = String(orderRef || 'order').replace(/[^a-zA-Z0-9_-]/g, '') || 'order';
    triggerBlobDownload(res.data, `invoice-${safe}.pdf`);
  },
};

export const adminOrderInvoiceApi = {
  async download(orderRef) {
    const res = await api.get('/api/admin/orders/invoice', { params: { orderRef }, responseType: 'blob' });
    const safe = String(orderRef || 'order').replace(/[^a-zA-Z0-9_-]/g, '') || 'order';
    triggerBlobDownload(res.data, `invoice-${safe}.pdf`);
  },
};

export const supportOrderInvoiceApi = {
  async download(orderRef) {
    const res = await api.get('/api/support/orders/invoice', { params: { orderRef }, responseType: 'blob' });
    const safe = String(orderRef || 'order').replace(/[^a-zA-Z0-9_-]/g, '') || 'order';
    triggerBlobDownload(res.data, `invoice-${safe}.pdf`);
  },
};

export const providerDashboardApi = {
  stats(params) {
    return api.get('/api/provider/me/dashboard/stats', params);
  },
};

export const providerSubscriptionApi = {
  status() {
    return api.get('/api/provider/me/subscription/status').catch((e) => {
      // Backend may not have subscription module enabled yet.
      if (e?.response?.status === 404) {
        return { data: { valid: false, plan: null, billingCycle: null, status: null, expiresAt: null, amountDue: null, paymentReference: null } };
      }
      throw e;
    });
  },
  bankDetails() {
    return api.get('/api/provider/me/subscription/bank-details');
  },
  quote(plan) {
    return api.get('/api/provider/me/subscription/quote', { params: { plan } });
  },
  uploadProof({ file, intentId }) {
    const form = new FormData();
    form.append('intentId', String(intentId));
    form.append('file', file);
    return api.post('/api/provider/me/subscription/proof', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  select(body) {
    return api.post('/api/provider/me/subscription/select', body);
  },
};

export const adminDashboardApi = {
  stats(params) {
    return api.get('/api/admin/dashboard/stats', { params });
  },
};

export const adminSubscriptionProofsApi = {
  pending() {
    return api.get('/api/admin/subscription-proofs/pending');
  },
  async openFile(proofId) {
    const res = await api.get(`/api/admin/subscription-proofs/${proofId}/file`, { responseType: 'blob' });
    const url = URL.createObjectURL(res.data);
    window.open(url, '_blank', 'noopener,noreferrer');
    // let the browser load the blob URL first
    setTimeout(() => URL.revokeObjectURL(url), 30_000);
  },
  decide(proofId, body) {
    return api.post(`/api/admin/subscription-proofs/${proofId}/decide`, body);
  },
};

export const adminSettingsApi = {
  get() {
    return api.get('/api/admin/settings');
  },
  update(body) {
    return api.put('/api/admin/settings', body);
  },
};

export const providerTeamApi = {
  getContext() {
    return api.get('/api/provider/me/context');
  },
  listStaff() {
    return api.get('/api/provider/me/staff');
  },
  listPayrollEntries() {
    return api.get('/api/provider/me/payroll-entries');
  },
  updateStaff(userId, body) {
    return api.patch(`/api/provider/me/staff/${userId}`, body);
  },
  removeStaff(userId) {
    return api.delete(`/api/provider/me/staff/${userId}`);
  },
  inviteStaff(body) {
    return api.post('/api/provider/me/staff', body);
  },
  addPayroll(staffUserId, body) {
    return api.post(`/api/provider/me/staff/${staffUserId}/payroll`, body);
  },
};

export const adminUsersApi = {
  list(params) {
    return api.get('/api/admin/users', { params });
  },
  remove(id) {
    return api.delete(`/api/admin/users/${id}`);
  },
  removeAll() {
    return api.delete('/api/admin/users');
  },
};

export const adminSupportUsersApi = {
  list() {
    return api.get('/api/admin/support-users');
  },
  create(body) {
    return api.post('/api/admin/create-support-user', body);
  },
};

export const adminListingsApi = {
  list(params) {
    return api.get('/api/admin/listings', { params });
  },
  setActive(id, active) {
    return api.patch(`/api/admin/listings/${id}/active`, { active });
  },
  remove(id) {
    return api.delete(`/api/admin/listings/${id}`);
  },
  removeAll() {
    return api.delete('/api/admin/listings');
  },
};

export const adminProvidersApi = {
  list(params) {
    return api.get('/api/admin/providers', { params });
  },
  setStatus(id, status) {
    return api.patch(`/api/admin/providers/${id}/status`, { status });
  },
};

export const adminProviderDetailApi = {
  getProvider(providerId) {
    return api.get(`/api/admin/providers/${providerId}`);
  },
  getListings(providerId) {
    return api.get(`/api/admin/providers/${providerId}/listings`);
  },
  getStaff(providerId) {
    return api.get(`/api/admin/providers/${providerId}/staff`);
  },
  deleteListing(providerId, listingId) {
    return api.delete(`/api/admin/providers/${providerId}/listings/${listingId}`);
  },
  disableStaff(providerId, userId) {
    return api.patch(`/api/admin/providers/${providerId}/staff/${userId}/disable`);
  },
};

export const adminMaintenanceApi = {
  cleanDb() {
    return api.post('/api/admin/maintenance/clean-db');
  },
};

export const supportApi = {
  getOverview() {
    return api.get('/api/support/overview');
  },
  getTickets() {
    return api.get('/api/support/tickets');
  },
  getUsers() {
    return api.get('/api/support/users');
  },
  searchUsers(params) {
    return api.get('/api/support/users', { params });
  },
  resendClientOtp(body) {
    return api.post('/api/support/client/otp/resend', body);
  },
  shadowProvider(providerId) {
    return api.post(`/api/support/shadow/provider/${providerId}`);
  },
};
