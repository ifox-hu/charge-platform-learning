const { request } = require('../../utils/request')

Page({
  data: { station: null, chargers: [], prices: [], loading: true, error: '' },
  onLoad(options) { this.stationId = options.id; this.loadData() },
  goBack() {
    const pages = getCurrentPages()
    if (pages.length > 1) wx.navigateBack()
    else wx.reLaunch({ url: '/pages/index/index' })
  },
  loadData() {
    this.setData({ loading: true, error: '' })
    let chargerRows = []
    return Promise.all([
      request({ url: `/stations/${this.stationId}` }),
      request({ url: `/chargers?stationId=${this.stationId}` }),
      request({ url: `/price-periods?stationId=${this.stationId}` }),
      request({ url: '/simulator/status' })
    ]).then(([station, chargers, prices, simulator]) => {
      chargerRows = chargers || []
      const devices = Array.isArray(simulator?.devices) ? simulator.devices : simulator?.deviceId ? [simulator] : []
      const deviceCode = device => device.deviceId.replace(/^SIM-/, '')
      const known = new Set(chargerRows.map(item => item.code))
      const virtualRows = devices.filter(device => !known.has(deviceCode(device))).map(device => {
        const number = Number(deviceCode(device).match(/(\d+)$/)?.[1] || 0)
        return { id: `sim-${device.deviceId}`, code: deviceCode(device), name: `${number || 2} 号模拟充电桩`, status: device.connected ? 'ONLINE' : 'OFFLINE', simulatorDeviceId: device.deviceId, connectors: Object.values(device.connectors || {}) }
      })
      const rows = chargerRows.map((charger, index) => ({ ...charger, simulatorDeviceId: devices[index]?.deviceId || `SIM-PILE-${String(index + 1).padStart(3, '0')}` })).concat(virtualRows)
      this.setData({ station, chargers: rows, prices: prices || [], chargerCount: rows.length })
      return Promise.all(chargerRows.map(charger => request({ url: `/connectors?chargerId=${charger.id}` })))
    }).then(groups => {
      const statusText = { IDLE: '空闲', CHARGING: '充电中', FAULT: '故障', OFFLINE: '离线' }
      const connectors = groups.reduce((all, group) => all.concat(group || []), []).map(connector => ({
        ...connector,
        statusText: statusText[connector.status] || connector.status || '未知'
      }))
      const chargerCards = this.data.chargers.map(charger => ({
        ...charger,
        statusText: { ONLINE: '在线', OFFLINE: '离线' }[charger.status] || charger.status || '未知',
        connectors: charger.connectors?.length ? charger.connectors : connectors.filter(connector => connector.chargerId === charger.id)
      }))
      this.setData({ chargers: chargerCards, connectorCount: chargerCards.reduce((total, charger) => total + (charger.connectors || []).length, 0) })
    }).catch(error => {
      if (error.message !== 'UNAUTHORIZED') this.setData({ error: '站点详情加载失败，请稍后重试' })
    }).finally(() => this.setData({ loading: false }))
  },
  openCharger(event) {
    wx.navigateTo({ url: `/pages/charger-detail/charger-detail?stationId=${this.stationId}&id=${event.currentTarget.dataset.id}&simulatorDevice=${event.currentTarget.dataset.device}` })
  }
})
