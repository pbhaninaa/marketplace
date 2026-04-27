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
};

export const providerDashboardApi = {
  stats(params) {
    return api.get('/api/provider/me/dashboard/stats', params);
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
};
