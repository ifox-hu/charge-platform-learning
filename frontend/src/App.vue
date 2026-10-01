<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { api, authStore, demoMode } from './api'

const menus = [
  { key: 'dashboard', icon: '⌁', label: '运营看板' },
  { key: 'stations', icon: '⌂', label: '充电站' },
  { key: 'devices', icon: 'ϟ', label: '设备管理' },
  { key: 'prices', icon: '¥', label: '分时电价' },
  { key: 'orders', icon: '▤', label: '充电订单' }
  ,{ key: 'users', icon: '◎', label: '用户账号' }
  ,{ key: 'audit', icon: '◷', label: '操作审计' }
  ,{ key: 'rabbitmq', icon: '⇄', label: '消息监控' }
]
const active = ref('dashboard')
const loggedIn = ref(Boolean(authStore.getToken()))
const currentUser = ref(null)
const loginForm = reactive({ username: 'admin', password: '' })
const loading = ref(false)
const notice = reactive({ text: '', error: false })
let noticeTimer
const dashboard = ref({})
const dependencyHealth = ref({ redis: 'UNKNOWN', rabbitmq: 'UNKNOWN' })
const simulatorStatus = ref({ enabled: false, devices: [] })
const mapContainer = ref(null)
const mapError = ref('')
const mapLocationLabel = ref('正在获取当前位置')
const demoMapFallback = ref(false)
const demoMapMarkers = computed(() => stations.value.map((station, index) => ({
  ...station,
  left: `${24 + (index % 3) * 27}%`,
  top: `${34 + (index % 2) * 25}%`
})))
let mapInstance
let mapScriptPromise
let mapElement
const stations = ref([])
const stationRows = ref([])
const chargers = ref([])
const connectors = ref([])
const prices = ref([])
const orders = ref([])
const auditRows = ref([])
const userRows = ref([])
const rabbitOverview = ref({ ready: 0, consumers: 0, deadLetters: 0, deadLetterConsumers: 0, queue: '', deadLetterQueue: '' })
const selectedOrderIds = ref([])
const stationPagination = reactive({ page: 1, size: 10, total: 0, totalPages: 0, name: '' })
const orderPagination = reactive({ page: 1, size: 10, total: 0, totalPages: 0, plateNumber: '' })
const auditPagination = reactive({ page: 1, size: 20, total: 0, totalPages: 0, username: '' })
const userPagination = reactive({ page: 1, size: 20, total: 0, totalPages: 0, keyword: '', role: '' })
const stationForm = reactive({ name: '', address: '', description: '' })
const chargerForm = reactive({ stationId: '', code: '', name: '' })
const connectorForm = reactive({ chargerId: '', code: '', name: '', ratedPower: 120 })
const priceForm = reactive({ stationId: '', startTime: '00:00:00', endTime: '23:59:59', electricityPrice: 0.8, servicePrice: 0.4 })
const orderForm = reactive({ stationId: '', connectorId: '', plateNumber: '' })
const regionOptions = ['京','津','沪','渝','冀','豫','云','辽','黑','湘','皖','鲁','新','苏','浙','赣','鄂','桂','甘','晋','蒙','陕','吉','闽','贵','粤','青','藏','川','宁','琼','港','澳','台']
const letterOptions = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('')
const plateInput = reactive({ region: '京', letter: 'A', digits: '' })
const simulatorPlateInput = reactive({ region: '京', letter: 'A', digits: '' })
function composePlate(input) { return `${input.region}${input.letter}${input.digits}` }
const simulatorPlans = [
  { code: 'FULL', label: '充满自停', desc: '达到目标电量后自动结束', target: 10 },
  { code: '1H', label: '短时补电', desc: '预计充电 1 小时', target: 7.2 },
  { code: '2H', label: '日常充电', desc: '预计充电 2 小时', target: 14.4 },
  { code: '3H', label: '深度充电', desc: '预计充电 3 小时', target: 21.6 },
  { code: 'KWH20', label: '按电量充', desc: '目标 20 kWh', target: 20 },
  { code: 'FAST', label: '快速补能', desc: '15 分钟体验充电', target: 2 }
]
const simulatorModal = reactive({ open: false, mode: 'packages', deviceId: '', connectorId: 1, packageCode: 'FULL', plateNumber: '' })
const editModal = reactive({ open: false, type: '', id: null, title: '', form: {} })
const orderDetailModal = reactive({ open: false, order: null })
const selectedSimulatorDevice = computed(() => simulatorStatus.value.devices.find(item => item.deviceId === simulatorModal.deviceId))
const selectedSimulatorConnector = computed(() => selectedSimulatorDevice.value?.connectors?.[simulatorModal.connectorId] || selectedSimulatorDevice.value?.connectors?.[String(simulatorModal.connectorId)])
const selectedSimulatorPlan = computed(() => simulatorPlans.find(item => item.code === simulatorModal.packageCode) || simulatorPlans[0])
let simulatorTimer
let realtimeSocket
let realtimeReconnectTimer
const currentTitle = computed(() => menus.find(item => item.key === active.value)?.label)
const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')
const canManageUsers = computed(() => ['ADMIN', 'OPERATOR'].includes(currentUser.value?.role))
const stationDisplayNumber = index => (stationPagination.page - 1) * stationPagination.size + index + 1
const chargerDisplayNumber = chargerId => chargers.value.findIndex(item => item.id === chargerId) + 1
const stationDisplayById = stationId => stations.value.findIndex(item => item.id === stationId) + 1

function show(message, error = false) {
  if (noticeTimer) window.clearTimeout(noticeTimer)
  notice.text = message; notice.error = error
  noticeTimer = window.setTimeout(() => { if (notice.text === message) notice.text = '' }, 3000)
}
async function run(action, success) {
  loading.value = true
  try { await action(); if (success) show(success) } catch (e) { show(e.message, true) } finally { loading.value = false }
}
async function refreshAll() {
  await run(async () => {
    const [summary, dependencies, simulator, allStations, stationPage, allChargers, allConnectors, orderPage] = await Promise.all([
      api.dashboard(), api.dependencies(), api.simulatorStatus(), api.stations(), api.stationPage(stationPagination), api.chargers(), api.connectors(), api.orderPage(orderPagination)
    ])
    dashboard.value = summary; dependencyHealth.value = dependencies; simulatorStatus.value = simulator; stations.value = allStations; stationRows.value = stationPage.rows; chargers.value = allChargers; connectors.value = allConnectors; orders.value = orderPage.rows
    Object.assign(stationPagination, { total: stationPage.total, totalPages: stationPage.totalPages })
    Object.assign(orderPagination, { total: orderPage.total, totalPages: orderPage.totalPages })
    if (isAdmin.value) {
      const auditPage = await api.auditLogs(auditPagination)
      auditRows.value = auditPage.rows
      Object.assign(auditPagination, { total: auditPage.total, totalPages: auditPage.totalPages })
      rabbitOverview.value = await api.rabbitOverview()
    }
    if (canManageUsers.value) {
      const userPage = await api.users(userPagination)
      userRows.value = userPage.rows
      Object.assign(userPagination, { total: userPage.total, totalPages: userPage.totalPages })
    }
    if (!stationForm.name && stations.value.length) {
      chargerForm.stationId ||= stations.value[0].id
      priceForm.stationId ||= stations.value[0].id
      orderForm.stationId ||= stations.value[0].id
    }
    connectorForm.chargerId ||= chargers.value[0]?.id || ''
    orderForm.connectorId ||= connectors.value[0]?.id || ''
    if (priceForm.stationId) prices.value = await api.prices(priceForm.stationId)
    if (active.value === 'dashboard') nextTick(initStationMap)
  })
}
function loadAmap() {
  if (window.AMap) return Promise.resolve(window.AMap)
  if (mapScriptPromise) return mapScriptPromise
  mapScriptPromise = new Promise((resolve, reject) => {
    const key = import.meta.env.VITE_AMAP_KEY
    const securityCode = import.meta.env.VITE_AMAP_SECURITY_CODE
    if (!key) return reject(new Error('未配置高德地图 Key'))
    window._AMapSecurityConfig = { securityJsCode: securityCode }
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${key}&plugin=AMap.Geocoder`
    script.onload = () => resolve(window.AMap)
    script.onerror = () => reject(new Error('高德地图加载失败'))
    document.head.appendChild(script)
  })
  return mapScriptPromise
}
async function initStationMap() {
  if (!mapContainer.value || !stations.value.length) return
  if (demoMode && !import.meta.env.VITE_AMAP_KEY) {
    demoMapFallback.value = true
    mapError.value = ''
    mapLocationLabel.value = '演示默认位置'
    return
  }
  try {
    demoMapFallback.value = false
    const AMap = await loadAmap()
    const currentLocation = await getBrowserLocation()
    const center = currentLocation || [113.394, 23.057]
    if (mapInstance && mapElement !== mapContainer.value) {
      mapInstance.destroy()
      mapInstance = null
    }
    if (!mapInstance) {
      mapInstance = new AMap.Map(mapContainer.value, { zoom: 12, center, mapStyle: 'amap://styles/whitesmoke' })
      mapElement = mapContainer.value
    }
    else mapInstance.setCenter(center)
    mapInstance.clearMap()
    const geocoder = new AMap.Geocoder()
    const points = []
    let unresolvedStations = 0
    for (const station of stations.value) {
      const storedLongitude = Number(station.longitude)
      const storedLatitude = Number(station.latitude)
      const hasStoredLocation = isChinaCoordinate(storedLongitude, storedLatitude)
      const address = String(station.address || '').trim()
      // 地址是业务上的真实来源；历史坐标可能是旧地址解析结果，只有地址解析失败时才回退。
      const geocodedLocation = address
        ? await new Promise(resolve => geocoder.getLocation(address, (status, result) => {
          const point = status === 'complete' && result.geocodes?.[0]?.location
          resolve(point ? (typeof point.toArray === 'function' ? point.toArray() : point) : null)
        }))
        : null
      const location = geocodedLocation || (hasStoredLocation ? [storedLongitude, storedLatitude] : null)
      const resolvedLocation = isChinaCoordinate(location?.[0], location?.[1]) ? location : null
      if (!resolvedLocation) { unresolvedStations += 1; continue }
      const coordinateChanged = !hasStoredLocation
        || Math.abs(storedLongitude - Number(resolvedLocation[0])) > 0.00001
        || Math.abs(storedLatitude - Number(resolvedLocation[1])) > 0.00001
      if (coordinateChanged) {
        station.latitude = Number(location[1])
        station.longitude = Number(location[0])
        station.coordinateType = 'GCJ02'
        api.updateStationCoordinates(station.id, { latitude: station.latitude, longitude: station.longitude, coordinateType: 'GCJ02' }).catch(() => {})
      }
      points.push({ station, location: resolvedLocation })
      const marker = new AMap.Marker({ position: resolvedLocation, title: station.name, label: { content: `<div class="map-label">${station.name}</div>`, direction: 'top' } })
      marker.on('click', () => show(`${station.name} · ${station.address || '暂无地址'}`))
      mapInstance.add(marker)
    }
    const locationMarker = new AMap.Marker({ position: center, title: currentLocation ? '我的位置' : '默认位置', anchor: 'bottom-center', content: `<div class="map-user-location"><span>${currentLocation ? '当前位置' : '默认位置'}</span><i></i></div>` })
    mapInstance.add(locationMarker)
    if (points.length) mapInstance.setFitView()
    mapError.value = unresolvedStations
      ? `${unresolvedStations} 个站点地址暂未解析出有效坐标，请补充省市信息`
      : ''
  } catch (error) {
    if (demoMode) {
      demoMapFallback.value = true
      mapError.value = ''
      mapLocationLabel.value = '演示默认位置'
    } else mapError.value = error.message || '地图加载失败'
  }
}
function refreshPage() { window.location.reload() }
async function relocateMap() {
  mapLocationLabel.value = '正在重新定位'
  await initStationMap()
  if (!mapError.value && mapLocationLabel.value === '正在重新定位') mapLocationLabel.value = '已定位到当前位置'
}
async function retryDeadLetters() { await run(async () => { const result = await api.retryDeadLetters(100); await refreshAll(); show(`已重试 ${result.count || 0} 条死信`) }) }
async function purgeDeadLetters() { if (!window.confirm('确认清空死信队列？')) return; await run(async () => { const result = await api.purgeDeadLetters(); await refreshAll(); show(`已清空 ${result.count || 0} 条死信`) }) }
function isChinaCoordinate(longitude, latitude) {
  return Number.isFinite(Number(longitude)) && Number.isFinite(Number(latitude))
    && Number(longitude) >= 73 && Number(longitude) <= 135
    && Number(latitude) >= 3 && Number(latitude) <= 54
}
function getBrowserLocation() {
  return new Promise(resolve => {
    if (!navigator.geolocation) { mapLocationLabel.value = '浏览器不支持定位'; return resolve(null) }
    navigator.geolocation.getCurrentPosition(position => {
      const gpsLocation = [position.coords.longitude, position.coords.latitude]
      if (!window.AMap?.convertFrom) {
        mapLocationLabel.value = '已定位到当前位置'
        return resolve(gpsLocation)
      }
      window.AMap.convertFrom(gpsLocation, 'gps', (status, result) => {
        const converted = status === 'complete' && result.locations?.[0]
        const location = converted && typeof converted.toArray === 'function' ? converted.toArray() : gpsLocation
        mapLocationLabel.value = '已定位到当前位置'
        resolve(location)
      })
    }, () => { mapLocationLabel.value = '定位权限未开启，使用默认位置'; resolve(null) }, { enableHighAccuracy: true, timeout: 8000, maximumAge: 60000 })
  })
}
async function refreshSimulator() {
  if (!loggedIn.value) return
  if (realtimeSocket?.readyState === WebSocket.OPEN) return
  try { simulatorStatus.value = await api.simulatorStatus() } catch (e) { if (e.message?.includes('登录')) logout() }
}
function connectRealtime() {
  if (demoMode || !loggedIn.value || !window.WebSocket) return
  if (realtimeSocket && [WebSocket.OPEN, WebSocket.CONNECTING].includes(realtimeSocket.readyState)) return
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const token = authStore.getToken()
  if (!token) return
  realtimeSocket = new WebSocket(`${protocol}//${window.location.host}/ws/status?token=${encodeURIComponent(token)}`)
  realtimeSocket.onmessage = event => {
    try {
      const message = JSON.parse(event.data)
      if (message.type === 'SIMULATOR_STATUS' && message.data) simulatorStatus.value = message.data
    } catch { /* fallback polling handles malformed push data */ }
  }
  realtimeSocket.onclose = () => {
    realtimeSocket = null
    if (loggedIn.value && !realtimeReconnectTimer) {
      realtimeReconnectTimer = window.setTimeout(() => { realtimeReconnectTimer = null; connectRealtime() }, 3000)
    }
  }
}
function openSimulatorConnector(device, connector) {
  simulatorModal.deviceId = device.deviceId
  simulatorModal.connectorId = connector.connectorId
  simulatorModal.packageCode = 'FULL'
  Object.assign(simulatorPlateInput, { region: '京', letter: 'A', digits: '' })
  simulatorModal.mode = connector.status === 'CHARGING' ? 'detail' : 'packages'
  simulatorModal.open = true
}
function closeSimulatorModal() { simulatorModal.open = false }
async function startSimulatorPackage() {
  await run(async () => {
    const binding = resolveSimulatorConnector()
    const plateNumber = composePlate(simulatorPlateInput)
    if (!binding) throw new Error('该模拟枪尚未绑定数据库充电枪')
    if (!/^.[A-Z][0-9]{5,6}$/.test(plateNumber)) throw new Error('请选择地区、字母，并输入5或6位数字')
    await api.startOrder({ stationId: binding.charger.stationId, connectorId: binding.connector.id, plateNumber, testOrder: true })
    await refreshAll()
    simulatorModal.mode = 'detail'
  }, `${simulatorModal.deviceId} 枪${simulatorModal.connectorId} 已开始充电`)
}
async function stopSimulatorCharging() {
  await run(async () => {
    const binding = resolveSimulatorConnector()
    if (!binding) throw new Error('该模拟枪尚未绑定数据库充电枪')
    const activeOrders = await api.orders()
    const order = activeOrders.find(item => item.connectorId === binding.connector.id && item.status === 'CHARGING')
    if (!order) throw new Error('未找到该模拟枪对应的进行中订单')
    const completedOrder = await api.stopOrder(order.id)
    await refreshAll()
    closeSimulatorModal()
    openOrderDetail(completedOrder)
  }, `${simulatorModal.deviceId} 枪${simulatorModal.connectorId} 已停止充电`)
}
function resolveSimulatorConnector() {
  const code = simulatorModal.deviceId.replace(/^SIM-/, '')
  const charger = chargers.value.find(item => item.code === code || item.code === simulatorModal.deviceId)
  if (!charger) return null
  const rows = connectors.value.filter(item => item.chargerId === charger.id).sort((left, right) => left.id - right.id)
  const connector = rows[simulatorModal.connectorId - 1]
  return connector ? { charger, connector } : null
}
async function login() {
  await run(async () => {
    const result = await api.login(loginForm)
    authStore.setToken(result.token); currentUser.value = { username: result.username, displayName: result.displayName, role: result.role }; loggedIn.value = true
    await refreshAll()
    if (simulatorTimer) window.clearInterval(simulatorTimer)
    connectRealtime()
    simulatorTimer = window.setInterval(refreshSimulator, 5000)
  }, '登录成功')
}
function logout() { authStore.clear(); loggedIn.value = false; currentUser.value = null; if (simulatorTimer) { window.clearInterval(simulatorTimer); simulatorTimer = null } if (realtimeReconnectTimer) { window.clearTimeout(realtimeReconnectTimer); realtimeReconnectTimer = null } if (realtimeSocket) { const socket = realtimeSocket; realtimeSocket = null; socket.close() } }
async function changeStationPage(step) { stationPagination.page += step; await refreshAll() }
async function changeOrderPage(step) { orderPagination.page += step; await refreshAll() }
async function changeAuditPage(step) { auditPagination.page += step; await refreshAll() }
async function changeUserPage(step) { userPagination.page += step; await refreshAll() }
async function updateUserStatus(user) {
  await run(async () => { await api.updateUserStatus(user.id, !user.enabled); await refreshAll() }, user.enabled ? '账号已禁用' : '账号已启用')
}
async function resetUserPassword(user) {
  const password = window.prompt(`请输入 ${user.username} 的新密码（至少6位）`, '123456')
  if (password === null) return
  if (password.length < 6) { show('密码至少需要6位', true); return }
  await run(async () => { await api.resetUserPassword(user.id, password) }, '密码已重置')
}
async function cleanupAuditLogs(days) {
  if (!confirm(`确认永久清理 ${days} 天前的审计日志？此操作不可恢复。`)) return
  await run(async () => { await api.cleanupAuditLogs(days); auditPagination.page = 1; await refreshAll() }, `已清理 ${days} 天前的审计日志`)
}
async function createStation() {
  await run(async () => { await api.createStation(stationForm); Object.assign(stationForm, { name: '', address: '', description: '' }); await refreshAll() }, '站点创建成功')
}
async function removeStation(id) {
  if (!confirm('确认删除这个站点？')) return
  await run(async () => { await api.deleteStation(id); await refreshAll() }, '站点已删除')
}
function openEdit(type, item) {
  editModal.type = type
  editModal.id = item.id
  editModal.title = type === 'station' ? '编辑充电站' : type === 'charger' ? '编辑充电桩' : type === 'connector' ? '编辑充电枪' : '编辑电价时段'
  editModal.form = type === 'station'
    ? { name: item.name, address: item.address, description: item.description || '', status: item.status }
    : type === 'charger'
      ? { code: item.code, name: item.name, status: item.status }
      : type === 'connector'
        ? { code: item.code, name: item.name, ratedPower: item.ratedPower }
        : { stationId: priceForm.stationId, startTime: item.startTime, endTime: item.endTime, electricityPrice: item.electricityPrice, servicePrice: item.servicePrice }
  editModal.open = true
}
function closeEdit() { editModal.open = false }
function openOrderDetail(order) { orderDetailModal.order = order; orderDetailModal.open = true }
function closeOrderDetail() { orderDetailModal.open = false; orderDetailModal.order = null }
async function saveEdit() {
  await run(async () => {
    const form = { ...editModal.form }
    if (editModal.type === 'station') await api.updateStation(editModal.id, form)
    if (editModal.type === 'charger') await api.updateCharger(editModal.id, form)
    if (editModal.type === 'connector') await api.updateConnector(editModal.id, { ...form, ratedPower: Number(form.ratedPower) })
    if (editModal.type === 'price') await api.updatePrice(editModal.id, { ...form, stationId: Number(form.stationId), electricityPrice: Number(form.electricityPrice), servicePrice: Number(form.servicePrice) })
    await refreshAll()
    if (editModal.type === 'price') await loadPrices()
    closeEdit()
  }, '编辑已保存')
}
async function createCharger() {
  await run(async () => { await api.createCharger({ ...chargerForm, stationId: Number(chargerForm.stationId) }); Object.assign(chargerForm, { ...chargerForm, code: '', name: '' }); await refreshAll() }, '充电桩创建成功')
}
async function createConnector() {
  await run(async () => { await api.createConnector({ ...connectorForm, chargerId: Number(connectorForm.chargerId), ratedPower: Number(connectorForm.ratedPower) }); Object.assign(connectorForm, { ...connectorForm, code: '', name: '' }); await refreshAll() }, '充电枪创建成功')
}
async function toggleCharger(charger) {
  await run(async () => { await api.updateChargerStatus(charger.id, charger.status === 'ONLINE' ? 'OFFLINE' : 'ONLINE'); await refreshAll() }, '设备状态已更新')
}
async function removeCharger(charger) {
  if (!confirm(`确认删除充电桩“${charger.name}”？请先确认该桩下没有充电枪。`)) return
  await run(async () => { await api.deleteCharger(charger.id); await refreshAll() }, '充电桩已删除')
}
async function removeConnector(connector) {
  if (!confirm(`确认删除充电枪“${connector.name}”？`)) return
  await run(async () => { await api.deleteConnector(connector.id); await refreshAll() }, '充电枪已删除')
}
async function loadPrices() { prices.value = priceForm.stationId ? await api.prices(priceForm.stationId) : [] }
async function createPrice() {
  await run(async () => { await api.createPrice({ ...priceForm, stationId: Number(priceForm.stationId) }); await loadPrices() }, '电价时段创建成功')
}
async function removePrice(price) {
  if (!confirm(`确认删除 ${price.startTime} - ${price.endTime} 这个电价时段？`)) return
  await run(async () => { await api.deletePrice(price.id); await loadPrices() }, '电价时段已删除')
}
async function startOrder() {
  const plateNumber = composePlate(plateInput)
  if (!/^.[A-Z][0-9]{5,6}$/.test(plateNumber)) return show('请选择地区、字母，并输入5或6位数字', true)
  await run(async () => { await api.startOrder({ ...orderForm, plateNumber, testOrder: true, stationId: Number(orderForm.stationId), connectorId: Number(orderForm.connectorId) }); orderForm.plateNumber = ''; await refreshAll() }, '模拟充电已启动')
}
async function stopOrder(order) {
  await run(async () => {
    const completedOrder = await api.stopOrder(order.id)
    await refreshAll()
    openOrderDetail(completedOrder)
  }, '已按模拟桩实时电量结束订单并结算')
}
async function exportOrders() {
  await run(async () => {
    const blob = await api.exportOrders({ plateNumber: orderPagination.plateNumber })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `charge-orders-${new Date().toISOString().slice(0, 10)}.csv`
    link.click()
    URL.revokeObjectURL(url)
  }, '订单报表已导出')
}
async function clearCompletedTestOrders() {
  if (!selectedOrderIds.value.length) return show('请先选择要清理的已完成订单', true)
  if (!confirm(`确认清理选中的 ${selectedOrderIds.value.length} 条订单？此操作不可撤销。`)) return
  await run(async () => { await api.clearCompletedTestOrders(selectedOrderIds.value); selectedOrderIds.value = []; await refreshAll() }, '已清理选中订单')
}
function toggleOrderSelection(order) {
  if (order.status !== 'COMPLETED') return
  selectedOrderIds.value = selectedOrderIds.value.includes(order.id)
    ? selectedOrderIds.value.filter(id => id !== order.id)
    : [...selectedOrderIds.value, order.id]
}
function toggleAllCompletedOrders() {
  const ids = orders.value.filter(order => order.status === 'COMPLETED').map(order => order.id)
  selectedOrderIds.value = selectedOrderIds.value.length === ids.length ? [] : ids
}
const statusText = value => ({ OPERATING: '运营中', CLOSED: '已关闭', ONLINE: '在线', OFFLINE: '离线', FAULT: '故障', IDLE: '空闲', CHARGING: '充电中', COMPLETED: '已完成' }[value] || value)
const money = value => `¥${Number(value || 0).toFixed(2)}`
const dependencyText = value => ({ UP: '可用', PONG: '可用', DISABLED: '未启用', UNAVAILABLE: '不可用', UNHEALTHY: '异常' }[value] || value)
watch(active, async value => {
  if (value !== 'dashboard') return
  await nextTick()
  await initStationMap()
})
onMounted(async () => {
  window.addEventListener('auth-expired', logout)
  if (!loggedIn.value) return
  try { currentUser.value = await api.me(); await refreshAll(); connectRealtime(); simulatorTimer = window.setInterval(refreshSimulator, 5000) } catch { logout() }
})
onUnmounted(() => { window.removeEventListener('auth-expired', logout); if (simulatorTimer) window.clearInterval(simulatorTimer); if (realtimeReconnectTimer) window.clearTimeout(realtimeReconnectTimer); if (realtimeSocket) realtimeSocket.close() })
</script>

<template>
  <div v-if="!loggedIn" class="login-shell">
    <div class="login-copy"><span class="login-logo">ϟ</span><p>CHARGE PLATFORM LITE</p><h1>连接城市的<br><em>每一度电</em></h1><div class="login-line"></div><small>轻量、清晰、可追溯的新能源充电运营平台</small></div>
    <form class="login-card" @submit.prevent="login"><div><small>WELCOME BACK</small><h2>登录运营后台</h2><p>使用你的账号进入充电管理平台</p><small v-if="demoMode" class="demo-hint">在线演示：任意用户名 / 123456</small></div><label>用户名<input v-model="loginForm.username" autocomplete="username"></label><label>密码<input v-model="loginForm.password" type="password" autocomplete="current-password"></label><button class="primary" type="submit">登录平台</button></form>
    <div v-if="notice.text" class="notice" :class="{ error: notice.error }">{{ notice.text }}</div>
    <div v-if="loading" class="loading">正在登录…</div>
  </div>
  <div v-else class="shell">
    <aside>
      <div class="brand"><span class="bolt">ϟ</span><div><strong>ChargeLite</strong><small>充电运营平台</small></div></div>
       <nav><button v-for="item in menus" v-show="(item.key !== 'rabbitmq' || isAdmin) && (item.key !== 'users' || canManageUsers)" :key="item.key" :class="{ active: active === item.key }" @click="active = item.key"><span>{{ item.icon }}</span>{{ item.label }}</button></nav>
      <div class="aside-foot"><span class="dot"></span>后端服务连接正常<small class="dependency-status">Redis：{{ dependencyText(dependencyHealth.redis) }} · RabbitMQ：{{ dependencyText(dependencyHealth.rabbitmq) }}</small></div>
    </aside>
    <main>
      <header><div><p>CHARGE OPERATIONS</p><h1>{{ currentTitle }}</h1></div><div class="header-actions"><span class="user-chip"><b>{{ currentUser?.displayName }}</b><small>{{ currentUser?.role }}</small></span><button class="ghost" @click="refreshAll">刷新数据</button><button class="ghost" @click="logout">退出</button></div></header>
      <div v-if="notice.text" class="notice" :class="{ error: notice.error }" role="alert">{{ notice.text }}</div>
      <div v-if="loading" class="loading">正在处理…</div>

      <section v-if="active === 'dashboard'">
        <div class="hero"><div><span>新能源基础设施</span><h2>让每一次充电<br>都有迹可循</h2><p>从设备接入、状态管理到订单计费，一条完整的充电业务链路。</p></div><div class="hero-mark">ϟ</div></div>
        <div class="stats"><article><small>充电站</small><b>{{ dashboard.stationCount || 0 }}</b><em>STATIONS</em></article><article><small>充电桩</small><b>{{ dashboard.chargerCount || 0 }}</b><em>CHARGERS</em></article><article><small>充电枪</small><b>{{ dashboard.connectorCount || 0 }}</b><em>CONNECTORS</em></article><article class="green"><small>正在充电</small><b>{{ dashboard.chargingOrderCount || 0 }}</b><em>ACTIVE ORDERS</em></article><article><small>已完成订单</small><b>{{ dashboard.completedOrderCount || 0 }}</b><em>COMPLETED</em></article></div>
        <div class="simulator-strip"><div class="simulator-mark">ϟ</div><div class="simulator-copy"><small>LIVE DEVICE FLEET</small><strong>虚拟充电桩集群</strong><span>{{ simulatorStatus.enabled ? 'TCP 状态同步已启用 · 点击枪状态可进行测试操作' : '模拟桩适配器未启用' }}</span></div><div class="simulator-devices"><span v-for="device in simulatorStatus.devices" :key="device.deviceId" class="simulator-device" :class="{ online: device.connected }"><i></i><b>{{ device.deviceId }} · {{ device.connected ? 'ONLINE' : 'OFFLINE' }}</b><small v-for="connector in Object.values(device.connectors || {})" :key="connector.connectorId" class="simulator-connector-control" :class="{ charging: connector.status === 'CHARGING' }" title="点击进行测试操作" @click.stop="openSimulatorConnector(device, connector)">枪{{ connector.connectorId }} {{ statusText(connector.status) }}<template v-if="connector.status === 'CHARGING'"> · {{ Number(connector.powerKw || 0).toFixed(1) }} kW · {{ Number(connector.currentA || ((Number(connector.powerKw || 0) * 1000) / 220) || 0).toFixed(1) }} A · {{ Number(connector.energyKwh || 0).toFixed(3) }} kWh</template></small></span></div></div>
        <div class="map-panel"><div class="map-panel-head"><div><small>LIVE LOCATION</small><h3>附近充电站</h3></div><div class="map-actions"><div class="map-refresh-row"><span>若无显示请刷新</span><button class="map-refresh" type="button" @click="refreshPage" title="刷新网页">↻ 刷新</button></div><div class="map-location-status"><span>{{ mapLocationLabel }}</span><button type="button" @click="relocateMap">重新定位</button></div></div></div><div ref="mapContainer" class="station-map"><div v-if="demoMapFallback" class="demo-map"><div class="demo-map-grid"></div><div class="demo-map-water"></div><span class="demo-map-road road-one"></span><span class="demo-map-road road-two"></span><button v-for="station in demoMapMarkers" :key="station.id" class="demo-map-marker" :style="{ left: station.left, top: station.top }" type="button" @click="show(`${station.name} · ${station.address || '暂无地址'}`)"><i></i><strong>{{ station.name }}</strong><small>{{ Number(station.latitude).toFixed(4) }}, {{ Number(station.longitude).toFixed(4) }}</small></button><div class="demo-map-location"><i></i><span>默认位置</span></div><div class="demo-map-caption">演示地图 · 配置高德 Key 后显示真实地图</div></div></div><p v-if="mapError" class="map-error">{{ mapError }} · 当前仍可使用下方站点列表</p></div>
        <div class="panel flow"><div><b>01</b><span>创建站点</span></div><i>→</i><div><b>02</b><span>添加桩与枪</span></div><i>→</i><div><b>03</b><span>配置电价</span></div><i>→</i><div><b>04</b><span>模拟充电</span></div><i>→</i><div><b>05</b><span>自动结算</span></div></div>
      </section>

      <section v-else-if="active === 'stations'" class="grid" :class="{ 'read-only': !isAdmin }">
        <div v-if="isAdmin" class="panel form-panel"><h3>新建充电站</h3><label>站点名称<input v-model="stationForm.name" placeholder="例如：大学城北门站"></label><label>详细地址<input v-model="stationForm.address" placeholder="请输入地址"></label><label>站点描述<textarea v-model="stationForm.description" placeholder="简要介绍站点"></textarea></label><button class="primary" @click="createStation">创建站点</button></div>
        <div class="panel table-panel"><div class="panel-title"><h3>站点列表</h3><span>{{ stationPagination.total }} 个站点</span></div><div class="search-bar"><input v-model="stationPagination.name" placeholder="按站点名称搜索" @keyup.enter="stationPagination.page=1;refreshAll()"><button class="ghost" @click="stationPagination.page=1;refreshAll()">查询</button></div><table><thead><tr><th>序号</th><th>名称</th><th>地址</th><th>状态</th><th></th></tr></thead><tbody><tr v-for="(s, index) in stationRows" :key="s.id"><td>#{{ stationDisplayNumber(index) }}</td><td><strong>{{ s.name }}</strong><small>{{ s.description }}</small></td><td>{{ s.address }}</td><td><span class="badge">{{ statusText(s.status) }}</span></td><td><button v-if="isAdmin" class="edit-link" @click="openEdit('station', s)">编辑</button><button v-if="isAdmin" class="danger-link" @click="removeStation(s.id)">删除</button></td></tr></tbody></table><div class="pagination"><button :disabled="stationPagination.page<=1" @click="changeStationPage(-1)">上一页</button><span>{{ stationPagination.page }} / {{ stationPagination.totalPages || 1 }}</span><button :disabled="stationPagination.page>=stationPagination.totalPages" @click="changeStationPage(1)">下一页</button></div></div>
      </section>

      <section v-else-if="active === 'devices'">
        <div v-if="isAdmin" class="dual"><div class="panel form-panel"><h3>添加充电桩</h3><label>所属站点<select v-model="chargerForm.stationId"><option v-for="s in stations" :value="s.id">{{ s.name }}</option></select></label><label>设备编码<input v-model="chargerForm.code" placeholder="PILE-002"></label><label>设备名称<input v-model="chargerForm.name" placeholder="2 号快充桩"></label><button class="primary" @click="createCharger">添加充电桩</button></div><div class="panel form-panel"><h3>添加充电枪</h3><label>所属充电桩<select v-model="connectorForm.chargerId"><option v-for="c in chargers" :value="c.id">{{ c.name }}</option></select></label><label>枪编码<input v-model="connectorForm.code" placeholder="GUN-002-A"></label><label>枪名称<input v-model="connectorForm.name" placeholder="A 枪"></label><label>额定功率（kW）<input v-model="connectorForm.ratedPower" type="number"></label><button class="primary" @click="createConnector">添加充电枪</button></div></div>
        <div class="cards"><article v-for="(c, index) in chargers" :key="c.id" class="device-card"><div class="device-icon">ϟ</div><div><small>#{{ index + 1 }} · {{ c.code }}</small><h3>{{ c.name }}</h3><p>所属站点 · #{{ stationDisplayById(c.stationId) }}</p></div><div class="device-actions"><button v-if="isAdmin" class="badge clickable" :class="c.status.toLowerCase()" @click="toggleCharger(c)">{{ statusText(c.status) }}</button><span v-else class="badge" :class="c.status.toLowerCase()">{{ statusText(c.status) }}</span><button v-if="isAdmin" class="edit-link" @click="openEdit('charger', c)">编辑</button><button v-if="isAdmin" class="danger-link" @click="removeCharger(c)">删除</button></div></article></div>
        <div class="panel table-panel"><div class="panel-title"><h3>充电枪列表</h3><span>{{ connectors.length }} 把枪</span></div><table><thead><tr><th>序号</th><th>编码</th><th>名称</th><th>所属桩</th><th>功率</th><th>状态</th><th></th></tr></thead><tbody><tr v-for="(c, index) in connectors" :key="c.id"><td>#{{ index + 1 }}</td><td>{{ c.code }}</td><td>{{ c.name }}</td><td>#{{ chargerDisplayNumber(c.chargerId) }}</td><td>{{ c.ratedPower }} kW</td><td><span class="badge" :class="c.status.toLowerCase()">{{ statusText(c.status) }}</span></td><td><button v-if="isAdmin" class="edit-link" @click="openEdit('connector', c)">编辑</button><button v-if="isAdmin" class="danger-link" @click="removeConnector(c)">删除</button></td></tr></tbody></table></div>
      </section>

      <section v-else-if="active === 'prices'" class="grid" :class="{ 'read-only': !isAdmin }">
        <div v-if="isAdmin" class="panel form-panel"><h3>配置分时电价</h3><label>充电站<select v-model="priceForm.stationId" @change="loadPrices"><option v-for="s in stations" :value="s.id">{{ s.name }}</option></select></label><div class="row"><label>开始时间<input v-model="priceForm.startTime" type="time" step="1"></label><label>结束时间<input v-model="priceForm.endTime" type="time" step="1"></label></div><div class="row"><label>电费（元/度）<input v-model="priceForm.electricityPrice" type="number" step="0.01"></label><label>服务费（元/度）<input v-model="priceForm.servicePrice" type="number" step="0.01"></label></div><button class="primary" @click="createPrice">保存时段</button></div>
        <div class="panel table-panel"><div class="panel-title"><h3>当前价格方案</h3><span>{{ prices.length }} 个时段</span></div><table><thead><tr><th>时间范围</th><th>电费</th><th>服务费</th><th>合计</th><th></th></tr></thead><tbody><tr v-for="p in prices" :key="p.id"><td>{{ p.startTime }} — {{ p.endTime }}</td><td>{{ money(p.electricityPrice) }}</td><td>{{ money(p.servicePrice) }}</td><td><strong>{{ money(Number(p.electricityPrice)+Number(p.servicePrice)) }}/度</strong></td><td><button v-if="isAdmin" class="edit-link" @click="openEdit('price', p)">编辑</button><button v-if="isAdmin" class="danger-link" @click="removePrice(p)">删除</button></td></tr></tbody></table></div>
      </section>

      <section v-else-if="active === 'users'" class="users-page">
        <div class="panel table-panel">
          <div class="panel-title"><div><h3>用户账号</h3><p class="rabbitmq-subtitle">管理小程序注册的车主账号和运营账号状态</p></div><span>{{ userPagination.total }} 个账号</span></div>
          <div class="search-bar user-search"><input v-model="userPagination.keyword" placeholder="按用户名或昵称搜索" @keyup.enter="userPagination.page=1;refreshAll()"><select v-model="userPagination.role" @change="userPagination.page=1;refreshAll()"><option value="">全部角色</option><option value="USER">普通用户</option><option value="OPERATOR">运营员</option><option value="ADMIN">管理员</option></select><button class="ghost" @click="userPagination.page=1;refreshAll()">查询</button></div>
          <table><thead><tr><th>账号</th><th>角色</th><th>状态</th><th>说明</th><th>操作</th></tr></thead><tbody><tr v-for="user in userRows" :key="user.id"><td><strong>{{ user.displayName }}</strong><small>{{ user.username }}</small></td><td><span class="badge" :class="user.role.toLowerCase()">{{ user.role === 'USER' ? '普通用户' : user.role === 'OPERATOR' ? '运营员' : '管理员' }}</span></td><td><span class="badge" :class="user.enabled ? 'completed' : 'fault'">{{ user.enabled ? '正常' : '已禁用' }}</span></td><td>{{ user.role === 'USER' ? '可使用小程序下单充电' : '可登录运营后台' }}</td><td><button v-if="isAdmin && user.role !== 'ADMIN'" class="edit-link" @click="updateUserStatus(user)">{{ user.enabled ? '禁用' : '启用' }}</button><button v-if="isAdmin && user.role !== 'ADMIN'" class="edit-link" @click="resetUserPassword(user)">重置密码</button><span v-if="user.role === 'ADMIN'" class="muted">受保护</span></td></tr><tr v-if="!userRows.length"><td colspan="5" class="empty-cell">暂无匹配账号</td></tr></tbody></table>
          <div class="pagination"><button :disabled="userPagination.page<=1" @click="changeUserPage(-1)">上一页</button><span>{{ userPagination.page }} / {{ userPagination.totalPages || 1 }}</span><button :disabled="userPagination.page>=userPagination.totalPages" @click="changeUserPage(1)">下一页</button></div>
        </div>
      </section>
      <section v-else-if="active === 'audit'">
        <div class="audit-toolbar"><span>审计日志保留策略</span><button class="ghost" @click="cleanupAuditLogs(7)">清理7天前</button><button class="ghost" @click="cleanupAuditLogs(30)">清理30天前</button></div>
        <div class="panel table-panel"><div class="panel-title"><h3>操作审计</h3><span>{{ auditPagination.total }} 条记录</span></div><div class="search-bar"><input v-model="auditPagination.username" placeholder="按用户名搜索" @keyup.enter="auditPagination.page=1;refreshAll()"><button class="ghost" @click="auditPagination.page=1;refreshAll()">查询</button></div><table><thead><tr><th>时间</th><th>用户 / 角色</th><th>操作</th><th>接口</th><th>结果</th><th>来源 IP</th></tr></thead><tbody><tr v-for="log in auditRows" :key="log.id"><td>{{ log.createdAt ? new Date(log.createdAt).toLocaleString() : '-' }}</td><td><strong>{{ log.username }}</strong><small>{{ log.role }}</small></td><td>{{ log.method }}</td><td>{{ log.path }}</td><td><span class="badge" :class="log.statusCode >= 400 ? 'fault' : 'completed'">{{ log.statusCode }}</span></td><td>{{ log.clientIp || '-' }}</td></tr></tbody></table><div class="pagination"><button :disabled="auditPagination.page<=1" @click="changeAuditPage(-1)">上一页</button><span>{{ auditPagination.page }} / {{ auditPagination.totalPages || 1 }}</span><button :disabled="auditPagination.page>=auditPagination.totalPages" @click="changeAuditPage(1)">下一页</button></div></div>
      </section>
       <section v-else-if="active === 'rabbitmq'" class="rabbitmq-page">
         <div class="stats rabbitmq-stats">
           <article><small>待处理消息</small><b>{{ rabbitOverview.ready || 0 }}</b><em>{{ rabbitOverview.queue || 'ORDER QUEUE' }}</em></article>
           <article><small>消费者</small><b>{{ rabbitOverview.consumers || 0 }}</b><em>CONSUMERS</em></article>
           <article class="green"><small>死信消息</small><b>{{ rabbitOverview.deadLetters || 0 }}</b><em>{{ rabbitOverview.deadLetterQueue || 'DLQ' }}</em></article>
           <article><small>死信消费者</small><b>{{ rabbitOverview.deadLetterConsumers || 0 }}</b><em>DLQ CONSUMERS</em></article>
         </div>
         <div class="panel table-panel rabbitmq-panel">
           <div class="panel-title"><div><h3>RabbitMQ 消息队列</h3><p class="rabbitmq-subtitle">订单完成事件的投递、消费与异常处理</p></div><span>{{ rabbitOverview.exchange || '-' }} · {{ rabbitOverview.routingKey || '-' }}</span></div>
           <div class="rabbitmq-flow"><div><i class="flow-dot main"></i><strong>主队列</strong><small>订单完成事件正常消费</small></div><span>→</span><div><i class="flow-dot dead"></i><strong>死信队列</strong><small>失败重试后集中处理</small></div></div>
           <div class="rabbitmq-actions"><p class="tip">消费失败超过重试次数后进入死信队列，可批量重试或清空。</p><div class="audit-toolbar"><button class="primary" @click="retryDeadLetters">重试死信（最多100条）</button><button class="ghost" @click="purgeDeadLetters">清空死信队列</button><button class="ghost" @click="refreshAll">刷新状态</button></div></div>
         </div>
       </section>
      <section v-else class="grid">
        <div class="panel form-panel"><h3>模拟启动充电</h3><label>充电站<select v-model="orderForm.stationId"><option v-for="s in stations" :value="s.id">{{ s.name }}</option></select></label><label>充电枪<select v-model="orderForm.connectorId"><option v-for="c in connectors" :value="c.id" :disabled="c.status !== 'IDLE'">{{ c.name }} · {{ statusText(c.status) }}</option></select></label><label>车牌号<div class="plate-fields"><select v-model="plateInput.region"><option v-for="region in regionOptions" :value="region">{{ region }}</option></select><select v-model="plateInput.letter"><option v-for="letter in letterOptions" :value="letter">{{ letter }}</option></select><input v-model="plateInput.digits" maxlength="6" inputmode="numeric" pattern="[0-9]*" placeholder="5或6位数字"></div></label><button class="primary" @click="startOrder">启动充电</button><p class="tip">请选择地区和大写字母，再输入5或6位数字。</p></div>
        <div class="panel table-panel"><div class="panel-title"><h3>订单记录</h3><div class="panel-title-actions"><span>{{ orderPagination.total }} 笔订单</span><button v-if="isAdmin" class="ghost" @click="clearCompletedTestOrders">清理选中</button><button class="ghost" @click="exportOrders">导出报表</button></div></div><div class="search-bar"><input v-model="orderPagination.plateNumber" placeholder="按车牌号搜索" @keyup.enter="orderPagination.page=1;refreshAll()"><button class="ghost" @click="orderPagination.page=1;refreshAll()">查询</button></div><table><thead><tr><th v-if="isAdmin"><input type="checkbox" :checked="orders.some(o => o.status === 'COMPLETED') && selectedOrderIds.length === orders.filter(o => o.status === 'COMPLETED').length" @change="toggleAllCompletedOrders"></th><th>订单号 / 车辆</th><th>状态</th><th>电量</th><th>费用</th><th>开始时间</th><th></th></tr></thead><tbody><tr v-for="o in orders" :key="o.id"><td v-if="isAdmin"><input v-if="o.status === 'COMPLETED'" type="checkbox" :checked="selectedOrderIds.includes(o.id)" @change="toggleOrderSelection(o)"><span v-else class="order-selection-placeholder"></span></td><td><strong>{{ o.orderNo }}</strong><small>{{ o.plateNumber }}</small></td><td><span class="badge" :class="o.status.toLowerCase()">{{ statusText(o.status) }}</span></td><td>{{ o.energyKwh || 0 }} kWh</td><td>{{ money(o.totalAmount) }}</td><td>{{ new Date(o.startTime).toLocaleString() }}</td><td><button class="edit-link" @click="openOrderDetail(o)">查看</button><button v-if="o.status === 'CHARGING'" class="stop" @click="stopOrder(o)">结束充电</button></td></tr></tbody></table><div class="pagination"><button :disabled="orderPagination.page<=1" @click="changeOrderPage(-1)">上一页</button><span>{{ orderPagination.page }} / {{ orderPagination.totalPages || 1 }}</span><button :disabled="orderPagination.page>=orderPagination.totalPages" @click="changeOrderPage(1)">下一页</button></div></div>
      </section>
    </main>
    <div v-if="simulatorModal.open" class="simulator-modal-backdrop" @click.self="closeSimulatorModal">
      <section class="simulator-modal">
        <button class="modal-close" aria-label="关闭" @click="closeSimulatorModal">×</button>
        <template v-if="simulatorModal.mode === 'packages'">
          <small class="modal-eyebrow">{{ simulatorModal.deviceId }} · 枪{{ simulatorModal.connectorId }}</small>
          <h2>选择充电套餐</h2><p class="modal-subtitle">选择后立即启动模拟桩 TCP 充电</p>
          <div class="simulator-plan-list"><button v-for="plan in simulatorPlans" :key="plan.code" class="simulator-plan" :class="{ selected: simulatorModal.packageCode === plan.code }" @click="simulatorModal.packageCode = plan.code"><strong>{{ plan.label }}</strong><span>{{ plan.desc }}</span></button></div>
          <div class="plate-fields"><select v-model="simulatorPlateInput.region"><option v-for="region in regionOptions" :value="region">{{ region }}</option></select><select v-model="simulatorPlateInput.letter"><option v-for="letter in letterOptions" :value="letter">{{ letter }}</option></select><input v-model="simulatorPlateInput.digits" class="plate-input" maxlength="6" inputmode="numeric" pattern="[0-9]*" placeholder="5或6位数字"></div>
          <button class="primary modal-primary" @click="startSimulatorPackage">开始充电</button>
        </template>
        <template v-else>
          <small class="modal-eyebrow">{{ simulatorModal.deviceId }} · 枪{{ simulatorModal.connectorId }} · TCP LIVE</small>
          <h2>充电详情</h2><div class="charging-state"><i></i><strong>{{ statusText(selectedSimulatorConnector?.status || 'CHARGING') }}</strong><span>{{ selectedSimulatorPlan.label }}</span></div>
          <div class="charge-energy"><b>{{ Number(selectedSimulatorConnector?.energyKwh || 0).toFixed(3) }}</b><span>kWh 实测累计电量</span></div>
          <div class="charge-progress"><div><span>模拟充电进度</span><b>{{ Math.min(100, Math.round(Number(selectedSimulatorConnector?.energyKwh || 0) / selectedSimulatorPlan.target * 100)) }}%</b></div><progress :value="Math.min(100, Number(selectedSimulatorConnector?.energyKwh || 0) / selectedSimulatorPlan.target * 100)" max="100"></progress></div>
          <div class="charge-metrics"><div><small>实时功率</small><b>{{ Number(selectedSimulatorConnector?.powerKw || 0).toFixed(1) }} kW</b></div><div><small>实时电流</small><b>{{ Number(selectedSimulatorConnector?.currentA || ((Number(selectedSimulatorConnector?.powerKw || 0) * 1000) / 220) || 0).toFixed(1) }} A</b></div><div><small>预计剩余</small><b>{{ Math.max(0, Math.ceil((selectedSimulatorPlan.target - Number(selectedSimulatorConnector?.energyKwh || 0)) * 3600 / (Number(selectedSimulatorConnector?.powerKw) || 7.2))) }} 秒</b></div></div>
          <button class="stop modal-stop" @click="stopSimulatorCharging">提前结束充电</button>
        </template>
      </section>
    </div>
    <div v-if="orderDetailModal.open" class="simulator-modal-backdrop" @click.self="closeOrderDetail">
      <section class="simulator-modal edit-modal">
        <button class="modal-close" aria-label="关闭" @click="closeOrderDetail">×</button>
        <small class="modal-eyebrow">ORDER DETAIL</small>
        <h2>订单详情</h2>
        <div v-if="orderDetailModal.order" class="order-detail-grid">
          <div><small>订单号</small><b>{{ orderDetailModal.order.orderNo }}</b></div>
          <div><small>车牌号</small><b>{{ orderDetailModal.order.plateNumber }}</b></div>
          <div><small>订单类型</small><b>{{ orderDetailModal.order.testOrder ? '测试订单' : '真实订单' }}</b></div>
          <div><small>状态</small><b>{{ statusText(orderDetailModal.order.status) }}</b></div>
          <div><small>充电电量</small><b>{{ orderDetailModal.order.energyKwh || 0 }} kWh</b></div>
          <div><small>总金额</small><b>{{ money(orderDetailModal.order.totalAmount) }}</b></div>
          <div><small>电费 / 服务费</small><b>{{ money(orderDetailModal.order.electricityFee) }} / {{ money(orderDetailModal.order.serviceFee) }}</b></div>
          <div><small>开始时间</small><b>{{ orderDetailModal.order.startTime ? new Date(orderDetailModal.order.startTime).toLocaleString() : '-' }}</b></div>
          <div><small>结束时间</small><b>{{ orderDetailModal.order.endTime ? new Date(orderDetailModal.order.endTime).toLocaleString() : '进行中' }}</b></div>
        </div>
      </section>
    </div>
    <div v-if="editModal.open" class="simulator-modal-backdrop" @click.self="closeEdit">
      <section class="simulator-modal edit-modal">
        <button class="modal-close" aria-label="关闭" @click="closeEdit">×</button>
        <small class="modal-eyebrow">EDIT RECORD</small>
        <h2>{{ editModal.title }}</h2>
        <template v-if="editModal.type === 'station'">
          <label class="edit-field">站点名称<input v-model="editModal.form.name"></label>
          <label class="edit-field">详细地址<input v-model="editModal.form.address"></label>
          <label class="edit-field">站点描述<textarea v-model="editModal.form.description"></textarea></label>
          <label class="edit-field">状态<select v-model="editModal.form.status"><option value="OPERATING">运营中</option><option value="CLOSED">已关闭</option></select></label>
        </template>
        <template v-else-if="editModal.type === 'charger'">
          <label class="edit-field">设备编码<input v-model="editModal.form.code"></label>
          <label class="edit-field">设备名称<input v-model="editModal.form.name"></label>
          <label class="edit-field">状态<select v-model="editModal.form.status"><option value="ONLINE">在线</option><option value="OFFLINE">离线</option><option value="FAULT">故障</option></select></label>
        </template>
        <template v-else-if="editModal.type === 'connector'">
          <label class="edit-field">枪编码<input v-model="editModal.form.code"></label>
          <label class="edit-field">枪名称<input v-model="editModal.form.name"></label>
          <label class="edit-field">额定功率（kW）<input v-model="editModal.form.ratedPower" type="number" min="1"></label>
        </template>
        <template v-else>
          <div class="row"><label class="edit-field">开始时间<input v-model="editModal.form.startTime" type="time" step="1"></label><label class="edit-field">结束时间<input v-model="editModal.form.endTime" type="time" step="1"></label></div>
          <div class="row"><label class="edit-field">电费（元/度）<input v-model="editModal.form.electricityPrice" type="number" min="0" step="0.01"></label><label class="edit-field">服务费（元/度）<input v-model="editModal.form.servicePrice" type="number" min="0" step="0.01"></label></div>
        </template>
        <div class="edit-actions"><button class="ghost" @click="closeEdit">取消</button><button class="primary" @click="saveEdit">保存修改</button></div>
      </section>
    </div>
  </div>
</template>
