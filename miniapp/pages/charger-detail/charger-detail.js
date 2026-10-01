const { request } = require('../../utils/request')

Page({
  data: {
    regionOptions: ['京','津','沪','渝','冀','豫','云','辽','黑','湘','皖','鲁','新','苏','浙','赣','鄂','桂','甘','晋','蒙','陕','吉','闽','贵','粤','青','藏','川','宁','琼','港','澳','台'],
    letterOptions: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split(''),
    charger: null, connectors: [], prices: [], simulatorDevices: [], loading: true, error: '', selectedConnector: null,
    selectedPackage: 'FULL', plateRegion: '京', plateLetter: 'A', plateDigits: '', platePickerOpen: '', starting: false,
    activeOrder: null, activeOrderLive: null, settling: false
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
  onUnload() { this.onHide() },
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
      const charger = (chargers || []).find(item => item.id === Number(this.chargerId)) || (this.simulatorOnly ? { code: this.simulatorDeviceId.replace(/^SIM-/, ''), name: '模拟充电桩', status: 'ONLINE' } : null)
      const statusText = { IDLE: '空闲', CHARGING: '充电中', FAULT: '故障', OFFLINE: '离线' }
      const simulatorDevices = this.formatSimulatorDevices(simulator, statusText, this.simulatorOnly ? null : (connectors || []))
      this.setData({ charger, prices: prices || [], simulatorDevices, connectors: (connectors || []).map(item => ({ ...item, statusText: statusText[item.status] || item.status || '未知' })) })
    }).catch(error => {
      if (error.message !== 'UNAUTHORIZED') this.setData({ error: '充电桩详情加载失败，请稍后重试' })
    }).finally(() => this.setData({ loading: false }))
  },
  loadSimulatorStatus() {
    const statusText = { IDLE: '空闲', CHARGING: '充电中', FAULT: '故障', OFFLINE: '离线' }
    const connectorRequest = this.simulatorOnly ? Promise.resolve(this.data.connectors) : request({ url: `/connectors?chargerId=${this.chargerId}` })
    return Promise.all([request({ url: '/simulator/status' }), connectorRequest]).then(([simulator, connectors]) => {
      const normalizedConnectors = (connectors || []).map(item => ({ ...item, statusText: statusText[item.status] || item.status || '未知' }))
      const simulatorDevices = this.formatSimulatorDevices(simulator, statusText, this.simulatorOnly ? null : normalizedConnectors)
      this.setData({ simulatorDevices, ...(this.simulatorOnly ? {} : { connectors: normalizedConnectors }) })
    }).catch(() => {})
  },
  formatSimulatorDevices(simulator, statusText, databaseConnectors) {
    const devices = Array.isArray(simulator?.devices) ? simulator.devices : simulator?.deviceId ? [simulator] : []
    const filtered = this.simulatorDeviceId ? devices.filter(device => device.deviceId === this.simulatorDeviceId) : devices
    return filtered.sort((left, right) => left.deviceId.localeCompare(right.deviceId)).map(device => {
      const rows = Object.values(device.connectors || {})
      let visibleRows = rows
      if (Array.isArray(databaseConnectors)) {
        const liveById = new Map(rows.map(item => [Number(item.connectorId), item]))
        visibleRows = databaseConnectors.map((connector, index) => {
          const match = String(connector.code || '').trim().match(/(?:-|_)([A-Z]|\d+)$/i)
          const suffix = match ? match[1].toUpperCase() : ''
          const localId = suffix
            ? (/^[A-Z]$/.test(suffix) ? suffix.charCodeAt(0) - 64 : Number(suffix))
            : index + 1
          // Use live TCP data when available; otherwise keep the database status visible.
          return liveById.get(localId) || {
            connectorId: localId,
            status: connector.status || 'OFFLINE',
            energyKwh: 0,
            powerKw: 0,
            currentA: 0
          }
        })
      }
      return {
        ...device,
        statusText: device.connected ? '在线' : '离线',
        connectorRows: visibleRows.map(item => ({ ...item, statusText: statusText[item.status] || item.status || '未知' }))
      }
    })
  },
  selectConnector(event) {
    const connector = this.data.connectors.find(item => item.id === event.currentTarget.dataset.id)
    if (!connector) return
    if (connector.status === 'CHARGING') return this.openActiveOrder(connector)
    if (connector.status !== 'IDLE') return wx.showToast({ title: '该充电枪当前不可用', icon: 'none' })
    this.setData({ selectedConnector: connector })
  },
  openActiveOrder(connector) {
    request({ url: '/customer/orders' }).then(orders => {
      const order = (orders || []).find(item => item.connectorId === connector.id && item.status === 'CHARGING')
      if (!order) {
        wx.showToast({ title: '订单状态正在同步，请刷新重试', icon: 'none' })
        return this.loadData()
      }
      request({ url: `/customer/orders/${order.id}/live` })
        .then(live => this.setData({ activeOrder: order, activeOrderLive: live }))
        .catch(() => this.setData({ activeOrder: order, activeOrderLive: null }))
    })
  },
  closeStartPanel() { this.setData({ selectedConnector: null }) },
  closeSettlePanel() { this.setData({ activeOrder: null, activeOrderLive: null }) },
  choosePackage(event) { this.setData({ selectedPackage: event.currentTarget.dataset.package }) },
  openPlatePicker(event) { this.setData({ platePickerOpen: event.currentTarget.dataset.type }) },
  closePlatePicker() { this.setData({ platePickerOpen: '' }) },
  choosePlateRegion(event) { this.setData({ plateRegion: event.currentTarget.dataset.value, platePickerOpen: '' }) },
  choosePlateLetter(event) { this.setData({ plateLetter: event.currentTarget.dataset.value, platePickerOpen: '' }) },
  onPlateInput(event) { this.setData({ plateDigits: event.detail.value.replace(/[^0-9]/g, '').slice(0, 6) }) },
  startCharging() {
    const plateNumber = `${this.data.plateRegion}${this.data.plateLetter}${this.data.plateDigits || ''}`
    if (!/^[\u4e00-\u9fa5][A-Z][0-9]{5,6}$/.test(plateNumber)) return wx.showToast({ title: '请选择地区、字母并输入5或6位数字', icon: 'none' })
    this.setData({ starting: true })
    request({ url: '/customer/orders/start', method: 'POST', data: { stationId: Number(this.stationId), connectorId: this.data.selectedConnector.id, plateNumber } })
      .then(order => wx.redirectTo({ url: `/pages/current-order/current-order?id=${order.id}&package=${this.data.selectedPackage}&power=${this.data.selectedConnector.ratedPower || 7.2}` }))
      .finally(() => this.setData({ starting: false }))
  },
  settleOrder() {
    this.setData({ settling: true })
    request({ url: `/customer/orders/${this.data.activeOrder.id}/stop`, method: 'POST', data: {} })
      .then(() => {
        wx.showToast({ title: '订单已结单' })
        this.setData({ activeOrder: null, activeOrderLive: null })
        this.loadData()
      })
      .finally(() => this.setData({ settling: false }))
  }
})
