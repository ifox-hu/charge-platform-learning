const { request } = require('../../utils/request')

Page({
  data: {
    charger: null, connectors: [], prices: [], simulatorDevices: [], loading: true, error: '', selectedConnector: null,
    selectedPackage: 'FULL', plateNumber: '测试车A001', starting: false,
    activeOrder: null, settling: false, energyKwh: '1.000'
  },
  onLoad(options) {
    this.stationId = options.stationId
    this.chargerId = options.id
    this.simulatorDeviceId = options.simulatorDevice || ''
    this.simulatorOnly = String(this.chargerId).startsWith('sim-')
    this.loadData()
  },
  onShow() {
    if (this.chargerId) {
      this.loadSimulatorStatus()
      this.simulatorTimer = setInterval(() => this.loadSimulatorStatus(), 5000)
    }
  },
  onHide() {
    if (this.simulatorTimer) {
      clearInterval(this.simulatorTimer)
      this.simulatorTimer = null
    }
  },
  goBack() {
    const pages = getCurrentPages()
    if (pages.length > 1) wx.navigateBack()
    else wx.reLaunch({ url: '/pages/index/index' })
  },
  loadData() {
    this.setData({ loading: true, error: '' })
    return Promise.all([
      request({ url: `/chargers?stationId=${this.stationId}` }),
      this.simulatorOnly ? Promise.resolve([]) : request({ url: `/connectors?chargerId=${this.chargerId}` }),
      request({ url: `/price-periods?stationId=${this.stationId}` }),
      request({ url: '/simulator/status' })
    ]).then(([chargers, connectors, prices, simulator]) => {
      const charger = (chargers || []).find(item => item.id === Number(this.chargerId)) || (this.simulatorOnly ? { code: this.simulatorDeviceId.replace('SIM-', 'PILE-'), name: '模拟充电桩', status: 'ONLINE' } : null)
      const statusText = { IDLE: '空闲', CHARGING: '充电中', FAULT: '故障', OFFLINE: '离线' }
      const simulatorDevices = this.formatSimulatorDevices(simulator, statusText)
      this.setData({ charger, prices: prices || [], simulatorDevices, connectors: (connectors || []).map(item => ({ ...item, statusText: statusText[item.status] || item.status || '未知' })) })
    }).catch(error => {
      if (error.message !== 'UNAUTHORIZED') this.setData({ error: '充电桩详情加载失败，请稍后重试' })
    }).finally(() => this.setData({ loading: false }))
  },
  loadSimulatorStatus() {
    const statusText = { IDLE: '空闲', CHARGING: '充电中', FAULT: '故障', OFFLINE: '离线' }
    return request({ url: '/simulator/status' }).then(simulator => {
      const simulatorDevices = this.formatSimulatorDevices(simulator, statusText)
      this.setData({ simulatorDevices })
    }).catch(() => {})
  },
  formatSimulatorDevices(simulator, statusText) {
    const devices = Array.isArray(simulator?.devices) ? simulator.devices : simulator?.deviceId ? [simulator] : []
    const filtered = this.simulatorDeviceId ? devices.filter(device => device.deviceId === this.simulatorDeviceId) : devices
    return filtered.sort((left, right) => left.deviceId.localeCompare(right.deviceId)).map(device => ({
      ...device,
      statusText: device.connected ? '在线' : '离线',
      connectorRows: Object.values(device.connectors || {}).map(item => ({ ...item, statusText: statusText[item.status] || item.status || '未知' }))
    }))
  },
  selectConnector(event) {
    const connector = this.data.connectors.find(item => item.id === event.currentTarget.dataset.id)
    if (!connector) return
    if (connector.status === 'CHARGING') return this.openActiveOrder(connector)
    if (connector.status !== 'IDLE') return wx.showToast({ title: '该充电枪当前不可用', icon: 'none' })
    this.setData({ selectedConnector: connector })
  },
  openActiveOrder(connector) {
    request({ url: '/orders' }).then(orders => {
      const order = (orders || []).find(item => item.connectorId === connector.id && item.status === 'CHARGING')
      if (!order) {
        wx.showToast({ title: '订单状态正在同步，请刷新重试', icon: 'none' })
        return this.loadData()
      }
      this.setData({ activeOrder: order, energyKwh: '1.000' })
    })
  },
  closeStartPanel() { this.setData({ selectedConnector: null }) },
  closeSettlePanel() { this.setData({ activeOrder: null }) },
  choosePackage(event) { this.setData({ selectedPackage: event.currentTarget.dataset.package }) },
  onPlateInput(event) { this.setData({ plateNumber: event.detail.value }) },
  onEnergyInput(event) { this.setData({ energyKwh: event.detail.value }) },
  startCharging() {
    const plateNumber = (this.data.plateNumber || '').trim()
    if (!plateNumber) return wx.showToast({ title: '请输入车牌号', icon: 'none' })
    this.setData({ starting: true })
    request({ url: '/orders/start', method: 'POST', data: { stationId: Number(this.stationId), connectorId: this.data.selectedConnector.id, plateNumber } })
      .then(order => wx.redirectTo({ url: `/pages/current-order/current-order?id=${order.id}&package=${this.data.selectedPackage}&power=${this.data.selectedConnector.ratedPower || 7.2}` }))
      .finally(() => this.setData({ starting: false }))
  },
  settleOrder() {
    const energyKwh = Number(this.data.energyKwh)
    if (!energyKwh || energyKwh <= 0) return wx.showToast({ title: '请输入大于 0 的电量', icon: 'none' })
    this.setData({ settling: true })
    request({ url: `/orders/${this.data.activeOrder.id}/stop`, method: 'POST', data: { energyKwh } })
      .then(() => {
        wx.showToast({ title: '订单已结单' })
        this.setData({ activeOrder: null })
        this.loadData()
      })
      .finally(() => this.setData({ settling: false }))
  }
})
