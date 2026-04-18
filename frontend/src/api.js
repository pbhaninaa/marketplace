import axios from 'axios';

const baseURL = import.meta.env.VITE_API_BASE || '';

export const api = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
});

export function setAuthToken(token) {
  if (token) {
    api.defaults.headers.common.Authorization = `Bearer ${token}`;
  } else {
    delete api.defaults.headers.common.Authorization;
  }
}

const SESSION_HEADER = 'X-Session-Id';

export function withSession(sessionId) {
  return { headers: { [SESSION_HEADER]: sessionId } };
}

/** FormData POST/PUT without forcing JSON Content-Type (browser sets multipart boundary). */
export function postMultipart(path, formData) {
  const headers = {};
  const auth = api.defaults.headers.common.Authorization;
  if (auth) headers.Authorization = auth;
  return axios.post(`${baseURL}${path}`, formData, { headers });
}

export function putMultipart(path, formData) {
  const headers = {};
  const auth = api.defaults.headers.common.Authorization;
  if (auth) headers.Authorization = auth;
  return axios.put(`${baseURL}${path}`, formData, { headers });
}
