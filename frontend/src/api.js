import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 10000 })

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

export const api = {
  login: data => http.post('/auth/login', data),
  me: () => http.get('/auth/me'),
  dashboard: () => http.get('/dashboard'),
  dependencies: () => http.get('/health/dependencies'),
  simulatorStatus: () => http.get('/simulator/status'),
  simulatorCommand: (deviceId, data) => http.post(`/simulator/command/${deviceId}`, data),
  stations: () => http.get('/stations'),
  stationPage: params => http.get('/stations/page', { params }),
  createStation: data => http.post('/stations', data),
  deleteStation: id => http.delete(`/stations/${id}`),
  chargers: stationId => http.get('/chargers', { params: stationId ? { stationId } : {} }),
  createCharger: data => http.post('/chargers', data),
  deleteCharger: id => http.delete(`/chargers/${id}`),
  updateChargerStatus: (id, status) => http.put(`/chargers/${id}/status`, { status }),
  connectors: chargerId => http.get('/connectors', { params: chargerId ? { chargerId } : {} }),
  createConnector: data => http.post('/connectors', data),
  deleteConnector: id => http.delete(`/connectors/${id}`),
  prices: stationId => http.get('/price-periods', { params: { stationId } }),
  createPrice: data => http.post('/price-periods', data),
  orders: () => http.get('/orders'),
  orderPage: params => http.get('/orders/page', { params }),
  exportOrders: params => http.get('/orders/export', { params, responseType: 'blob' }),
  startOrder: data => http.post('/orders/start', data),
  stopOrder: (id, energyKwh) => http.post(`/orders/${id}/stop`, { energyKwh })
}
