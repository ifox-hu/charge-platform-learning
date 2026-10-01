const STORAGE_KEY = 'charge-platform-demo-state'

const clone = value => JSON.parse(JSON.stringify(value))
const now = () => new Date().toISOString()
const pageOf = (rows, params = {}) => {
  const page = Math.max(1, Number(params.page) || 1)
  const size = Math.max(1, Number(params.size) || 10)
  const total = rows.length
  return { rows: clone(rows.slice((page - 1) * size, page * size)), total, page, size, totalPages: Math.max(1, Math.ceil(total / size)) }
}

function seed() {
  const station = { id: 1, name: '城市中心示范站', address: '广州市越秀区环市东路', description: '全天候开放的快充示范站', status: 'OPERATING', latitude: 23.1291, longitude: 113.2644, coordinateType: 'GCJ02' }
  const chargers = [1, 2, 3].map((id, index) => ({ id, stationId: 1, code: `PILE-00${id}`, name: `${index + 1} 号快充桩`, status: 'ONLINE' }))
  const connectors = chargers.flatMap(charger => [1, 2].map(localId => ({ id: charger.id * 10 + localId, chargerId: charger.id, code: `${charger.code}-G${localId}`, name: `${localId} 号枪`, ratedPower: charger.id === 3 ? 60 : 120, status: 'IDLE' })))
  return { stations: [station], chargers, connectors, prices: [{ id: 1, stationId: 1, startTime: '00:00:00', endTime: '23:59:59', electricityPrice: 0.8, servicePrice: 0.4 }], orders: [], users: [{ id: 1, username: 'demo_admin', displayName: '演示管理员', role: 'ADMIN', enabled: true }, { id: 2, username: 'demo_operator', displayName: '演示运营员', role: 'OPERATOR', enabled: true }, { id: 3, username: 'demo_user', displayName: '演示车主', role: 'USER', enabled: true }], auditRows: [], rabbit: { ready: 0, consumers: 1, deadLetters: 0, deadLetterConsumers: 0, queue: 'charge.order.completed', deadLetterQueue: 'charge.order.completed.dlq', exchange: 'charge.order.exchange', routingKey: 'order.completed' } }
}

function load() {
  try { return { ...seed(), ...JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') } } catch { return seed() }
}
let state = load()
function save() { localStorage.setItem(STORAGE_KEY, JSON.stringify(state)) }
function result(value) { return Promise.resolve(clone(value)) }
function audit(method, path, statusCode = 200) { state.auditRows.unshift({ id: Date.now() + Math.random(), username: 'demo_admin', role: 'ADMIN', method, path, statusCode, clientIp: 'demo-browser', createdAt: now() }); state.auditRows = state.auditRows.slice(0, 100); save() }
function findOrder(id) { return state.orders.find(item => Number(item.id) === Number(id)) }
function simulatorStatus() {
  const devices = state.chargers.map(charger => ({ deviceId: `SIM-${charger.code}`, connected: charger.status === 'ONLINE', connectors: Object.fromEntries(state.connectors.filter(item => item.chargerId === charger.id).map((connector, index) => { const order = state.orders.find(o => o.connectorId === connector.id && o.status === 'CHARGING'); const energy = order ? Math.min(40, Number(((Date.now() - new Date(order.startTime).getTime()) / 3600000 * connector.ratedPower).toFixed(3))) : 0; return [index + 1, { connectorId: index + 1, status: order ? 'CHARGING' : connector.status, energyKwh: energy, powerKw: order ? connector.ratedPower : 0, voltageV: order ? 220 : 0, currentA: order ? Number((connector.ratedPower * 1000 / 220).toFixed(1)) : 0, lastMessageAt: now() }] })) }))
  return { enabled: true, devices }
}

export const mockApi = {
  login: ({ username, password }) => { if (!username || !password || (password !== '123456' && !(username === 'demo_admin' && password === '123456'))) return Promise.reject(new Error('用户名或密码错误')); const role = username.includes('admin') ? 'ADMIN' : 'OPERATOR'; const user = { token: `demo-token-${role}`, tokenType: 'Bearer', expiresIn: 7200, username, displayName: role === 'ADMIN' ? '演示管理员' : '演示运营员', role }; localStorage.setItem('charge-demo-user', JSON.stringify(user)); return result(user) },
  me: () => result(JSON.parse(localStorage.getItem('charge-demo-user') || '{"username":"demo_admin","displayName":"演示管理员","role":"ADMIN"}')),
  users: (params = {}) => result(pageOf((state.users || []).filter(row => (!params.keyword || row.username.includes(params.keyword) || row.displayName.includes(params.keyword)) && (!params.role || row.role === params.role)), params)),
  updateUserStatus: (id, enabled) => { const row = (state.users || []).find(x => x.id === Number(id)); if (row) row.enabled = enabled; save(); return result(null) },
  resetUserPassword: () => result(null),
  deleteUser: (id, force = false) => { const hasOrders = state.orders.some(order => order.ownerUsername === (state.users || []).find(x => x.id === Number(id))?.username); if (hasOrders && !force) return Promise.reject(new Error('该用户已有订单，请确认后强制删除')); state.users = (state.users || []).filter(x => x.id !== Number(id)); save(); return result(null) },
  userOrders: id => { const user = (state.users || []).find(x => x.id === Number(id)); return result(state.orders.filter(order => order.ownerUsername === user?.username)) },
  dashboard: () => result({ stationCount: state.stations.length, chargerCount: state.chargers.length, connectorCount: state.connectors.length, chargingCount: state.orders.filter(o => o.status === 'CHARGING').length, completedOrderCount: state.orders.filter(o => o.status === 'COMPLETED').length, todayAmount: state.orders.filter(o => o.status === 'COMPLETED').reduce((sum, o) => sum + Number(o.totalAmount || 0), 0) }),
  dependencies: () => result({ redis: 'PONG', rabbitmq: 'UP' }),
  simulatorStatus: () => result(simulatorStatus()),
  simulatorCommand: () => result({ ok: true }),
  stations: () => result(state.stations),
  stationPage: params => result(pageOf(state.stations.filter(row => !params.name || row.name.includes(params.name)), params)),
  createStation: data => { const row = { ...data, id: Math.max(0, ...state.stations.map(x => x.id)) + 1, status: 'OPERATING', latitude: 23.13, longitude: 113.27, coordinateType: 'GCJ02' }; state.stations.push(row); audit('POST', '/api/stations', 201); save(); return result(row) },
  updateStation: (id, data) => { const row = state.stations.find(x => x.id === id); Object.assign(row, data); save(); return result(row) },
  updateStationCoordinates: (id, data) => { const row = state.stations.find(x => x.id === id); Object.assign(row, data); save(); return result(row) },
  deleteStation: id => { state.stations = state.stations.filter(x => x.id !== id); save(); return result(null) },
  chargers: stationId => result(state.chargers.filter(x => !stationId || x.stationId === stationId)),
  createCharger: data => { const row = { ...data, id: Math.max(0, ...state.chargers.map(x => x.id)) + 1, status: 'ONLINE' }; state.chargers.push(row); save(); return result(row) },
  updateCharger: (id, data) => { const row = state.chargers.find(x => x.id === id); Object.assign(row, data); save(); return result(row) },
  deleteCharger: id => { state.chargers = state.chargers.filter(x => x.id !== id); state.connectors = state.connectors.filter(x => x.chargerId !== id); save(); return result(null) },
  updateChargerStatus: (id, status) => { const row = state.chargers.find(x => x.id === id); row.status = status; save(); return result(row) },
  connectors: chargerId => result(state.connectors.filter(x => !chargerId || x.chargerId === chargerId)),
  createConnector: data => { const row = { ...data, id: Math.max(0, ...state.connectors.map(x => x.id)) + 1, status: 'IDLE' }; state.connectors.push(row); save(); return result(row) },
  updateConnector: (id, data) => { const row = state.connectors.find(x => x.id === id); Object.assign(row, data); save(); return result(row) },
  deleteConnector: id => { state.connectors = state.connectors.filter(x => x.id !== id); save(); return result(null) },
  prices: stationId => result(state.prices.filter(x => x.stationId === Number(stationId))),
  createPrice: data => { const row = { ...data, id: Math.max(0, ...state.prices.map(x => x.id)) + 1 }; state.prices.push(row); save(); return result(row) },
  updatePrice: (id, data) => { const row = state.prices.find(x => x.id === id); Object.assign(row, data); save(); return result(row) },
  deletePrice: id => { state.prices = state.prices.filter(x => x.id !== id); save(); return result(null) },
  orders: () => result(state.orders.sort((a, b) => b.id - a.id)),
  orderPage: params => result(pageOf(state.orders.filter(row => !params.plateNumber || row.plateNumber.includes(params.plateNumber)), params)),
  exportOrders: () => { const csv = `订单号,车牌号,状态,电量,总金额\n${state.orders.map(o => `${o.orderNo},${o.plateNumber},${o.status},${o.energyKwh},${o.totalAmount}`).join('\n')}`; return result(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' })) },
  startOrder: data => { const connector = state.connectors.find(x => x.id === Number(data.connectorId)); if (!connector || connector.status !== 'IDLE') return Promise.reject(new Error('该充电枪当前不可用')); const id = Math.max(0, ...state.orders.map(x => x.id)) + 1; connector.status = 'CHARGING'; const order = { id, orderNo: `DEMO${String(id).padStart(6, '0')}`, stationId: Number(data.stationId), connectorId: connector.id, plateNumber: data.plateNumber, status: 'CHARGING', testOrder: true, archived: false, startTime: now(), energyKwh: 0, electricityFee: 0, serviceFee: 0, totalAmount: 0, paymentStatus: 'NOT_REQUIRED' }; state.orders.push(order); state.rabbit.ready = 0; save(); return result(order) },
  orderLive: id => { const order = findOrder(id); const connector = state.connectors.find(x => x.id === order?.connectorId); const energy = order?.status === 'CHARGING' ? Number(((Date.now() - new Date(order.startTime).getTime()) / 3600000 * (connector?.ratedPower || 7.2)).toFixed(3)) : Number(order?.energyKwh || 0); return result({ orderId: Number(id), deviceId: `SIM-PILE-00${connector?.chargerId || 1}`, connectorId: 1, status: order?.status || 'IDLE', energyKwh: energy, powerKw: order?.status === 'CHARGING' ? connector?.ratedPower || 7.2 : 0, voltageV: 220, currentA: 30, lastMessageAt: now() }) },
  stopOrder: id => { const order = findOrder(id); if (!order || order.status !== 'CHARGING') return Promise.reject(new Error('订单不存在或已经结束')); const connector = state.connectors.find(x => x.id === order.connectorId); order.status = 'COMPLETED'; order.energyKwh = Number(((Date.now() - new Date(order.startTime).getTime()) / 3600000 * (connector?.ratedPower || 7.2)).toFixed(3)); order.electricityFee = Number((order.energyKwh * 0.8).toFixed(2)); order.serviceFee = Number((order.energyKwh * 0.4).toFixed(2)); order.totalAmount = Number((order.electricityFee + order.serviceFee).toFixed(2)); order.endTime = now(); order.paymentStatus = 'NOT_REQUIRED'; connector.status = 'IDLE'; state.rabbit.ready = 1; save(); return result(order) },
  clearCompletedTestOrders: ids => { state.orders = state.orders.filter(o => !ids.includes(o.id)); save(); return result(null) },
  auditLogs: params => result(pageOf(state.auditRows, params)),
  cleanupAuditLogs: days => { state.auditRows = []; save(); return result(null) },
  rabbitOverview: () => { const current = { ...state.rabbit }; if (current.ready > 0) { current.ready = 0; save() } return result(current) },
  retryDeadLetters: () => { const count = state.rabbit.deadLetters; state.rabbit.deadLetters = 0; state.rabbit.ready = count; save(); return result({ count }) },
  purgeDeadLetters: () => { const count = state.rabbit.deadLetters; state.rabbit.deadLetters = 0; save(); return result({ count }) }
}
