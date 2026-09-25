<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { api, authStore } from './api'

const menus = [
  { key: 'dashboard', icon: '⌁', label: '运营看板' },
  { key: 'stations', icon: '⌂', label: '充电站' },
  { key: 'devices', icon: 'ϟ', label: '设备管理' },
  { key: 'prices', icon: '¥', label: '分时电价' },
  { key: 'orders', icon: '▤', label: '充电订单' }
]
const active = ref('dashboard')
const loggedIn = ref(Boolean(authStore.getToken()))
const currentUser = ref(null)
const loginForm = reactive({ username: 'admin', password: '' })
const loading = ref(false)
const notice = reactive({ text: '', error: false })
const dashboard = ref({})
const dependencyHealth = ref({ redis: 'UNKNOWN', rabbitmq: 'UNKNOWN' })
const simulatorStatus = ref({ enabled: false, devices: [] })
const mapContainer = ref(null)
const mapError = ref('')
const mapLocationLabel = ref('正在获取当前位置')
let mapInstance
let mapScriptPromise
const stations = ref([])
const stationRows = ref([])
const chargers = ref([])
const connectors = ref([])
const prices = ref([])
const orders = ref([])
const stationPagination = reactive({ page: 1, size: 10, total: 0, totalPages: 0, name: '' })
const orderPagination = reactive({ page: 1, size: 10, total: 0, totalPages: 0, plateNumber: '' })
const stationForm = reactive({ name: '', address: '', description: '' })
const chargerForm = reactive({ stationId: '', code: '', name: '' })
const connectorForm = reactive({ chargerId: '', code: '', name: '', ratedPower: 120 })
const priceForm = reactive({ stationId: '', startTime: '00:00:00', endTime: '23:59:59', electricityPrice: 0.8, servicePrice: 0.4 })
const orderForm = reactive({ stationId: '', connectorId: '', plateNumber: '' })
const simulatorPlans = [
  { code: 'FULL', label: '充满自停', desc: '达到目标电量后自动结束', target: 10 },
  { code: '1H', label: '短时补电', desc: '预计充电 1 小时', target: 7.2 },
  { code: '2H', label: '日常充电', desc: '预计充电 2 小时', target: 14.4 },
  { code: '3H', label: '深度充电', desc: '预计充电 3 小时', target: 21.6 },
  { code: 'KWH20', label: '按电量充', desc: '目标 20 kWh', target: 20 },
  { code: 'FAST', label: '快速补能', desc: '15 分钟体验充电', target: 2 }
]
const simulatorModal = reactive({ open: false, mode: 'packages', deviceId: '', connectorId: 1, packageCode: 'FULL' })
const selectedSimulatorDevice = computed(() => simulatorStatus.value.devices.find(item => item.deviceId === simulatorModal.deviceId))
const selectedSimulatorConnector = computed(() => selectedSimulatorDevice.value?.connectors?.[simulatorModal.connectorId] || selectedSimulatorDevice.value?.connectors?.[String(simulatorModal.connectorId)])
const selectedSimulatorPlan = computed(() => simulatorPlans.find(item => item.code === simulatorModal.packageCode) || simulatorPlans[0])
let simulatorTimer
const currentTitle = computed(() => menus.find(item => item.key === active.value)?.label)
const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')

function show(message, error = false) {
  notice.text = message; notice.error = error
  setTimeout(() => { if (notice.text === message) notice.text = '' }, 3000)
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
  try {
    const AMap = await loadAmap()
    const currentLocation = await getBrowserLocation()
    const center = currentLocation || [113.394, 23.057]
    if (!mapInstance) mapInstance = new AMap.Map(mapContainer.value, { zoom: 12, center, mapStyle: 'amap://styles/whitesmoke' })
    else mapInstance.setCenter(center)
    mapInstance.clearMap()
    const geocoder = new AMap.Geocoder({ city: '广州', citylimit: true })
    const points = []
    for (const station of stations.value) {
      const query = `${station.address || station.name}`.includes('广州') ? (station.address || station.name) : `广州市${station.address || station.name}`
      const location = await new Promise(resolve => geocoder.getLocation(query, (status, result) => resolve(status === 'complete' && result.geocodes?.[0]?.location)))
      const fallback = [113.394 + points.length * 0.014, 23.057 + points.length * 0.012]
      const resolvedLocation = location || fallback
      points.push({ station, location: resolvedLocation })
      const marker = new AMap.Marker({ position: resolvedLocation, title: station.name, label: { content: `<div class="map-label">${station.name}</div>`, direction: 'top' } })
      marker.on('click', () => show(`${station.name} · ${station.address || '暂无地址'}`))
      mapInstance.add(marker)
    }
    const locationMarker = new AMap.Marker({ position: center, title: currentLocation ? '我的位置' : '默认位置', anchor: 'bottom-center', content: `<div class="map-user-location"><span>${currentLocation ? '当前位置' : '默认位置'}</span><i></i></div>` })
    mapInstance.add(locationMarker)
    if (points.length) mapInstance.setFitView()
    mapError.value = points.length ? '' : '地址暂未解析出坐标，请检查地址'
  } catch (error) { mapError.value = error.message || '地图加载失败' }
}
function getBrowserLocation() {
  return new Promise(resolve => {
    if (!navigator.geolocation) { mapLocationLabel.value = '浏览器不支持定位'; return resolve(null) }
    navigator.geolocation.getCurrentPosition(position => {
      const location = [position.coords.longitude, position.coords.latitude]
      mapLocationLabel.value = '已定位到当前位置'
      resolve(location)
    }, () => { mapLocationLabel.value = '定位权限未开启，使用默认位置'; resolve(null) }, { enableHighAccuracy: true, timeout: 8000, maximumAge: 60000 })
  })
}
async function refreshSimulator() {
  if (!loggedIn.value) return
  try { simulatorStatus.value = await api.simulatorStatus() } catch (e) { if (e.message?.includes('登录')) logout() }
}
function openSimulatorConnector(device, connector) {
  simulatorModal.deviceId = device.deviceId
  simulatorModal.connectorId = connector.connectorId
  simulatorModal.packageCode = 'FULL'
  simulatorModal.mode = connector.status === 'CHARGING' ? 'detail' : 'packages'
  simulatorModal.open = true
}
function closeSimulatorModal() { simulatorModal.open = false }
async function startSimulatorPackage() {
  await run(async () => {
    await api.simulatorCommand(simulatorModal.deviceId, { command: 'START', connectorId: simulatorModal.connectorId })
    await refreshSimulator()
    simulatorModal.mode = 'detail'
  }, `${simulatorModal.deviceId} 枪${simulatorModal.connectorId} 已开始充电`)
}
async function stopSimulatorCharging() {
  await run(async () => {
    await api.simulatorCommand(simulatorModal.deviceId, { command: 'STOP', connectorId: simulatorModal.connectorId })
    await refreshSimulator()
    closeSimulatorModal()
  }, `${simulatorModal.deviceId} 枪${simulatorModal.connectorId} 已停止充电`)
}
async function login() {
  await run(async () => {
    const result = await api.login(loginForm)
    authStore.setToken(result.token); currentUser.value = { username: result.username, displayName: result.displayName, role: result.role }; loggedIn.value = true
    await refreshAll()
    if (simulatorTimer) window.clearInterval(simulatorTimer)
    simulatorTimer = window.setInterval(refreshSimulator, 5000)
  }, '登录成功')
}
function logout() { authStore.clear(); loggedIn.value = false; currentUser.value = null; if (simulatorTimer) { window.clearInterval(simulatorTimer); simulatorTimer = null } }
async function changeStationPage(step) { stationPagination.page += step; await refreshAll() }
async function changeOrderPage(step) { orderPagination.page += step; await refreshAll() }
async function createStation() {
  await run(async () => { await api.createStation(stationForm); Object.assign(stationForm, { name: '', address: '', description: '' }); await refreshAll() }, '站点创建成功')
}
async function removeStation(id) {
  if (!confirm('确认删除这个站点？')) return
  await run(async () => { await api.deleteStation(id); await refreshAll() }, '站点已删除')
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
async function startOrder() {
  await run(async () => { await api.startOrder({ ...orderForm, stationId: Number(orderForm.stationId), connectorId: Number(orderForm.connectorId) }); orderForm.plateNumber = ''; await refreshAll() }, '模拟充电已启动')
}
async function stopOrder(order) {
  await run(async () => {
    await refreshSimulator()
    const snapshots = simulatorStatus.value.devices.flatMap(device => Object.values(device.connectors || {}).map(connector => ({ device, connector })))
    const live = snapshots.find(item => item.connector.connectorId === order.connectorId && item.connector.status === 'CHARGING') || snapshots.find(item => item.connector.status === 'CHARGING')
    const energy = Number(live?.connector?.energyKwh || 0)
    if (!live || energy <= 0) throw new Error('暂未读取到模拟桩实时电量，请稍后再试')
    await api.simulatorCommand(live.device.deviceId, { command: 'STOP', connectorId: live.connector.connectorId })
    await api.stopOrder(order.id, energy)
    await refreshAll()
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
const statusText = value => ({ OPERATING: '运营中', CLOSED: '已关闭', ONLINE: '在线', OFFLINE: '离线', FAULT: '故障', IDLE: '空闲', CHARGING: '充电中', COMPLETED: '已完成' }[value] || value)
const money = value => `¥${Number(value || 0).toFixed(2)}`
const dependencyText = value => ({ UP: '可用', PONG: '可用', DISABLED: '未启用', UNAVAILABLE: '不可用', UNHEALTHY: '异常' }[value] || value)
onMounted(async () => {
  window.addEventListener('auth-expired', logout)
  if (!loggedIn.value) return
  try { currentUser.value = await api.me(); await refreshAll(); simulatorTimer = window.setInterval(refreshSimulator, 5000) } catch { logout() }
})
onUnmounted(() => { window.removeEventListener('auth-expired', logout); if (simulatorTimer) window.clearInterval(simulatorTimer) })
</script>

<template>
  <div v-if="!loggedIn" class="login-shell">
    <div class="login-copy"><span class="login-logo">ϟ</span><p>CHARGE PLATFORM LITE</p><h1>连接城市的<br><em>每一度电</em></h1><div class="login-line"></div><small>轻量、清晰、可追溯的新能源充电运营平台</small></div>
    <form class="login-card" @submit.prevent="login"><div><small>WELCOME BACK</small><h2>登录运营后台</h2><p>使用你的账号进入充电管理平台</p></div><label>用户名<input v-model="loginForm.username" autocomplete="username"></label><label>密码<input v-model="loginForm.password" type="password" autocomplete="current-password"></label><button class="primary" type="submit">登录平台</button></form>
    <div v-if="notice.text" class="notice" :class="{ error: notice.error }">{{ notice.text }}</div>
    <div v-if="loading" class="loading">正在登录…</div>
  </div>
  <div v-else class="shell">
    <aside>
      <div class="brand"><span class="bolt">ϟ</span><div><strong>ChargeLite</strong><small>充电运营平台</small></div></div>
      <nav><button v-for="item in menus" :key="item.key" :class="{ active: active === item.key }" @click="active = item.key"><span>{{ item.icon }}</span>{{ item.label }}</button></nav>
      <div class="aside-foot"><span class="dot"></span>后端服务连接正常<small class="dependency-status">Redis：{{ dependencyText(dependencyHealth.redis) }} · RabbitMQ：{{ dependencyText(dependencyHealth.rabbitmq) }}</small></div>
    </aside>
    <main>
      <header><div><p>CHARGE OPERATIONS</p><h1>{{ currentTitle }}</h1></div><div class="header-actions"><span class="user-chip"><b>{{ currentUser?.displayName }}</b><small>{{ currentUser?.role }}</small></span><button class="ghost" @click="refreshAll">刷新数据</button><button class="ghost" @click="logout">退出</button></div></header>
      <div v-if="notice.text" class="notice" :class="{ error: notice.error }">{{ notice.text }}</div>
      <div v-if="loading" class="loading">正在处理…</div>

      <section v-if="active === 'dashboard'">
        <div class="hero"><div><span>新能源基础设施</span><h2>让每一次充电<br>都有迹可循</h2><p>从设备接入、状态管理到订单计费，一条完整的充电业务链路。</p></div><div class="hero-mark">ϟ</div></div>
        <div class="stats"><article><small>充电站</small><b>{{ dashboard.stationCount || 0 }}</b><em>STATIONS</em></article><article><small>充电桩</small><b>{{ dashboard.chargerCount || 0 }}</b><em>CHARGERS</em></article><article><small>充电枪</small><b>{{ dashboard.connectorCount || 0 }}</b><em>CONNECTORS</em></article><article class="green"><small>正在充电</small><b>{{ dashboard.chargingOrderCount || 0 }}</b><em>ACTIVE ORDERS</em></article><article><small>已完成订单</small><b>{{ dashboard.completedOrderCount || 0 }}</b><em>COMPLETED</em></article></div>
        <div class="simulator-strip"><div class="simulator-mark">ϟ</div><div class="simulator-copy"><small>LIVE DEVICE FLEET</small><strong>虚拟充电桩集群</strong><span>{{ simulatorStatus.enabled ? 'TCP 状态同步已启用 · 点击枪状态可查看或启动' : '模拟桩适配器未启用' }}</span></div><div class="simulator-devices"><span v-for="device in simulatorStatus.devices" :key="device.deviceId" class="simulator-device" :class="{ online: device.connected }"><i></i><b>{{ device.deviceId }} · {{ device.connected ? 'ONLINE' : 'OFFLINE' }}</b><small v-for="connector in Object.values(device.connectors || {})" :key="connector.connectorId" class="simulator-connector-control" :class="{ charging: connector.status === 'CHARGING' }" title="点击查看详情或选择套餐" @click.stop="openSimulatorConnector(device, connector)">枪{{ connector.connectorId }} {{ statusText(connector.status) }}<template v-if="connector.status === 'CHARGING'"> · {{ Number(connector.powerKw || 0).toFixed(1) }} kW · {{ Number(connector.currentA || ((Number(connector.powerKw || 0) * 1000) / 220) || 0).toFixed(1) }} A · {{ Number(connector.energyKwh || 0).toFixed(3) }} kWh</template></small></span></div></div>
        <div class="map-panel"><div class="map-panel-head"><div><small>LIVE LOCATION</small><h3>附近充电站</h3></div><span>{{ mapLocationLabel }}</span></div><div ref="mapContainer" class="station-map"></div><p v-if="mapError" class="map-error">{{ mapError }} · 当前仍可使用下方站点列表</p></div>
        <div class="panel flow"><div><b>01</b><span>创建站点</span></div><i>→</i><div><b>02</b><span>添加桩与枪</span></div><i>→</i><div><b>03</b><span>配置电价</span></div><i>→</i><div><b>04</b><span>模拟充电</span></div><i>→</i><div><b>05</b><span>自动结算</span></div></div>
      </section>

      <section v-else-if="active === 'stations'" class="grid" :class="{ 'read-only': !isAdmin }">
        <div v-if="isAdmin" class="panel form-panel"><h3>新建充电站</h3><label>站点名称<input v-model="stationForm.name" placeholder="例如：大学城北门站"></label><label>详细地址<input v-model="stationForm.address" placeholder="请输入地址"></label><label>站点描述<textarea v-model="stationForm.description" placeholder="简要介绍站点"></textarea></label><button class="primary" @click="createStation">创建站点</button></div>
        <div class="panel table-panel"><div class="panel-title"><h3>站点列表</h3><span>{{ stationPagination.total }} 个站点</span></div><div class="search-bar"><input v-model="stationPagination.name" placeholder="按站点名称搜索" @keyup.enter="stationPagination.page=1;refreshAll()"><button class="ghost" @click="stationPagination.page=1;refreshAll()">查询</button></div><table><thead><tr><th>ID</th><th>名称</th><th>地址</th><th>状态</th><th></th></tr></thead><tbody><tr v-for="s in stationRows" :key="s.id"><td>#{{ s.id }}</td><td><strong>{{ s.name }}</strong><small>{{ s.description }}</small></td><td>{{ s.address }}</td><td><span class="badge">{{ statusText(s.status) }}</span></td><td><button v-if="isAdmin" class="danger-link" @click="removeStation(s.id)">删除</button></td></tr></tbody></table><div class="pagination"><button :disabled="stationPagination.page<=1" @click="changeStationPage(-1)">上一页</button><span>{{ stationPagination.page }} / {{ stationPagination.totalPages || 1 }}</span><button :disabled="stationPagination.page>=stationPagination.totalPages" @click="changeStationPage(1)">下一页</button></div></div>
      </section>

      <section v-else-if="active === 'devices'">
        <div v-if="isAdmin" class="dual"><div class="panel form-panel"><h3>添加充电桩</h3><label>所属站点<select v-model="chargerForm.stationId"><option v-for="s in stations" :value="s.id">{{ s.name }}</option></select></label><label>设备编码<input v-model="chargerForm.code" placeholder="PILE-002"></label><label>设备名称<input v-model="chargerForm.name" placeholder="2 号快充桩"></label><button class="primary" @click="createCharger">添加充电桩</button></div><div class="panel form-panel"><h3>添加充电枪</h3><label>所属充电桩<select v-model="connectorForm.chargerId"><option v-for="c in chargers" :value="c.id">{{ c.name }}</option></select></label><label>枪编码<input v-model="connectorForm.code" placeholder="GUN-002-A"></label><label>枪名称<input v-model="connectorForm.name" placeholder="A 枪"></label><label>额定功率（kW）<input v-model="connectorForm.ratedPower" type="number"></label><button class="primary" @click="createConnector">添加充电枪</button></div></div>
        <div class="cards"><article v-for="c in chargers" :key="c.id" class="device-card"><div class="device-icon">ϟ</div><div><small>{{ c.code }}</small><h3>{{ c.name }}</h3><p>站点 ID · {{ c.stationId }}</p></div><div class="device-actions"><button v-if="isAdmin" class="badge clickable" :class="c.status.toLowerCase()" @click="toggleCharger(c)">{{ statusText(c.status) }}</button><span v-else class="badge" :class="c.status.toLowerCase()">{{ statusText(c.status) }}</span><button v-if="isAdmin" class="danger-link" @click="removeCharger(c)">删除</button></div></article></div>
        <div class="panel table-panel"><div class="panel-title"><h3>充电枪列表</h3><span>{{ connectors.length }} 把枪</span></div><table><thead><tr><th>编码</th><th>名称</th><th>所属桩</th><th>功率</th><th>状态</th><th></th></tr></thead><tbody><tr v-for="c in connectors" :key="c.id"><td>{{ c.code }}</td><td>{{ c.name }}</td><td>#{{ c.chargerId }}</td><td>{{ c.ratedPower }} kW</td><td><span class="badge" :class="c.status.toLowerCase()">{{ statusText(c.status) }}</span></td><td><button v-if="isAdmin" class="danger-link" @click="removeConnector(c)">删除</button></td></tr></tbody></table></div>
      </section>

      <section v-else-if="active === 'prices'" class="grid" :class="{ 'read-only': !isAdmin }">
        <div v-if="isAdmin" class="panel form-panel"><h3>配置分时电价</h3><label>充电站<select v-model="priceForm.stationId" @change="loadPrices"><option v-for="s in stations" :value="s.id">{{ s.name }}</option></select></label><div class="row"><label>开始时间<input v-model="priceForm.startTime" type="time" step="1"></label><label>结束时间<input v-model="priceForm.endTime" type="time" step="1"></label></div><div class="row"><label>电费（元/度）<input v-model="priceForm.electricityPrice" type="number" step="0.01"></label><label>服务费（元/度）<input v-model="priceForm.servicePrice" type="number" step="0.01"></label></div><button class="primary" @click="createPrice">保存时段</button></div>
        <div class="panel table-panel"><div class="panel-title"><h3>当前价格方案</h3></div><table><thead><tr><th>时间范围</th><th>电费</th><th>服务费</th><th>合计</th></tr></thead><tbody><tr v-for="p in prices" :key="p.id"><td>{{ p.startTime }} — {{ p.endTime }}</td><td>{{ money(p.electricityPrice) }}</td><td>{{ money(p.servicePrice) }}</td><td><strong>{{ money(Number(p.electricityPrice)+Number(p.servicePrice)) }}/度</strong></td></tr></tbody></table></div>
      </section>

      <section v-else class="grid">
        <div class="panel form-panel"><h3>模拟启动充电</h3><label>充电站<select v-model="orderForm.stationId"><option v-for="s in stations" :value="s.id">{{ s.name }}</option></select></label><label>充电枪<select v-model="orderForm.connectorId"><option v-for="c in connectors" :value="c.id" :disabled="c.status !== 'IDLE'">{{ c.name }} · {{ statusText(c.status) }}</option></select></label><label>车牌号<input v-model="orderForm.plateNumber" placeholder="粤A12345"></label><button class="primary" @click="startOrder">启动充电</button><p class="tip">结束订单时会自动读取对应模拟桩的实时累计电量，并发送 STOP 后结算。</p></div>
        <div class="panel table-panel"><div class="panel-title"><h3>订单记录</h3><div class="panel-title-actions"><span>{{ orderPagination.total }} 笔订单</span><button class="ghost" @click="exportOrders">导出报表</button></div></div><div class="search-bar"><input v-model="orderPagination.plateNumber" placeholder="按车牌号搜索" @keyup.enter="orderPagination.page=1;refreshAll()"><button class="ghost" @click="orderPagination.page=1;refreshAll()">查询</button></div><table><thead><tr><th>订单号 / 车辆</th><th>状态</th><th>电量</th><th>费用</th><th>开始时间</th><th></th></tr></thead><tbody><tr v-for="o in orders" :key="o.id"><td><strong>{{ o.orderNo }}</strong><small>{{ o.plateNumber }}</small></td><td><span class="badge" :class="o.status.toLowerCase()">{{ statusText(o.status) }}</span></td><td>{{ o.energyKwh || 0 }} kWh</td><td>{{ money(o.totalAmount) }}</td><td>{{ new Date(o.startTime).toLocaleString() }}</td><td><button v-if="o.status === 'CHARGING'" class="stop" @click="stopOrder(o)">结束充电</button></td></tr></tbody></table><div class="pagination"><button :disabled="orderPagination.page<=1" @click="changeOrderPage(-1)">上一页</button><span>{{ orderPagination.page }} / {{ orderPagination.totalPages || 1 }}</span><button :disabled="orderPagination.page>=orderPagination.totalPages" @click="changeOrderPage(1)">下一页</button></div></div>
      </section>
    </main>
    <div v-if="simulatorModal.open" class="simulator-modal-backdrop" @click.self="closeSimulatorModal">
      <section class="simulator-modal">
        <button class="modal-close" aria-label="关闭" @click="closeSimulatorModal">×</button>
        <template v-if="simulatorModal.mode === 'packages'">
          <small class="modal-eyebrow">{{ simulatorModal.deviceId }} · 枪{{ simulatorModal.connectorId }}</small>
          <h2>选择充电套餐</h2><p class="modal-subtitle">选择后立即启动模拟桩 TCP 充电</p>
          <div class="simulator-plan-list"><button v-for="plan in simulatorPlans" :key="plan.code" class="simulator-plan" :class="{ selected: simulatorModal.packageCode === plan.code }" @click="simulatorModal.packageCode = plan.code"><strong>{{ plan.label }}</strong><span>{{ plan.desc }}</span></button></div>
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
  </div>
</template>
