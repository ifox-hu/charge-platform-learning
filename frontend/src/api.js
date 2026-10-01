import axios from 'axios'
import { mockApi } from './mockApi'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
const http = axios.create({ baseURL: apiBaseUrl, timeout: 10000 })

export const authStore = {
  getToken: () => localStorage.getItem('charge_token'),
  setToken: token => localStorage.setItem('charge_token', token),
  clear: () => localStorage.removeItem('charge_token')
}

http.interceptors.request.use(config => {
  const token = authStore.getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  // 后端统一返回 { code, message, data, timestamp }，业务层只接收 data。
  response => response.config.responseType === 'blob' ? response.data : response.data.data,
  error => {
    if (error.response?.status === 401 && !error.config?.url?.includes('/auth/login')) {
      authStore.clear()
      window.dispatchEvent(new Event('auth-expired'))
    }
    return Promise.reject(new Error(error.response?.data?.message || '请求失败，请确认后端已启动'))
  }
)

const realApi = {
  login: data => http.post('/auth/login', data),
  me: () => http.get('/auth/me'),
  users: params => http.get('/auth/users', { params }),
  updateUserStatus: (id, enabled) => http.patch(`/auth/users/${id}/status`, null, { params: { enabled } }),
  resetUserPassword: (id, password) => http.post(`/auth/users/${id}/reset-password`, { password }),
  dashboard: () => http.get('/dashboard'),
  dependencies: () => http.get('/health/dependencies'),
  simulatorStatus: () => http.get('/simulator/status'),
  simulatorCommand: (deviceId, data) => http.post(`/simulator/command/${deviceId}`, data),
  stations: () => http.get('/stations'),
  stationPage: params => http.get('/stations/page', { params }),
  createStation: data => http.post('/stations', data),
  updateStation: (id, data) => http.put(`/stations/${id}`, data),
  updateStationCoordinates: (id, data) => http.put(`/stations/${id}/coordinates`, data),
  deleteStation: id => http.delete(`/stations/${id}`),
  chargers: stationId => http.get('/chargers', { params: stationId ? { stationId } : {} }),
  createCharger: data => http.post('/chargers', data),
  updateCharger: (id, data) => http.put(`/chargers/${id}`, data),
  deleteCharger: id => http.delete(`/chargers/${id}`),
  updateChargerStatus: (id, status) => http.put(`/chargers/${id}/status`, { status }),
  connectors: chargerId => http.get('/connectors', { params: chargerId ? { chargerId } : {} }),
  createConnector: data => http.post('/connectors', data),
  updateConnector: (id, data) => http.put(`/connectors/${id}`, data),
  deleteConnector: id => http.delete(`/connectors/${id}`),
  prices: stationId => http.get('/price-periods', { params: { stationId } }),
  createPrice: data => http.post('/price-periods', data),
  updatePrice: (id, data) => http.put(`/price-periods/${id}`, data),
  deletePrice: id => http.delete(`/price-periods/${id}`),
  orders: () => http.get('/orders'),
  orderPage: params => http.get('/orders/page', { params }),
  exportOrders: params => http.get('/orders/export', { params, responseType: 'blob' }),
  startOrder: data => http.post('/orders/start', data),
  orderLive: id => http.get(`/orders/${id}/live`),
  stopOrder: id => http.post(`/orders/${id}/stop`, {}),
  clearCompletedTestOrders: ids => http.delete('/orders/completed-test', { data: { ids } })
  ,auditLogs: params => http.get('/audit-logs', { params }),
  cleanupAuditLogs: days => http.delete('/audit-logs/before', { params: { days } })
  ,rabbitOverview: () => http.get('/rabbitmq/overview')
  ,retryDeadLetters: limit => http.post('/rabbitmq/dead-letters/retry', null, { params: { limit } })
  ,purgeDeadLetters: () => http.delete('/rabbitmq/dead-letters')
}

// GitHub Pages builds use the browser-only demo API; local development keeps the real backend by default.
export const demoMode = import.meta.env.VITE_DEMO_MODE === 'true'
export const api = demoMode ? mockApi : realApi
