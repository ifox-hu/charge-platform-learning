const { request } = require('../../utils/request')

Page({
  data: { order: null, loading: true, error: '', energyKwh: '0.000', simulatorEnergyKwh: '0.000', simulatorPowerKw: '0.0', simulatorCurrentA: '0.0', simulatorCharging: null, stopping: false, progress: 0, remainingLabel: '', simulated: true },
  onLoad(options) {
    this.orderId = options.id
    this.packageCode = options.package || 'FULL'
    this.packageConfig = {
      FULL: { label: '充满自停', seconds: 30, target: 10 },
      '1H': { label: '短时补电（1 小时）', seconds: 15, target: 7.2 },
      '2H': { label: '日常充电（2 小时）', seconds: 22, target: 14.4 },
      '3H': { label: '深度充电（3 小时）', seconds: 30, target: 21.6 },
      KWH20: { label: '按电量充（20 kWh）', seconds: 25, target: 20 },
      FAST: { label: '快速补能（15 分钟）', seconds: 8, target: 2 }
    }[this.packageCode] || { label: '充满自停', seconds: 30, target: 10 }
    this.power = Number(options.power) || 7.2
    this.startedAt = Date.now()
    this.setData({ packageLabel: this.packageConfig.label, targetEnergy: this.packageConfig.target.toFixed(1), power: this.power })
    this.loadOrder()
  },
  onShow() {
    if (this.data.order && this.data.order.status === 'CHARGING' && !this.timer) this.startSimulation()
  },
  loadOrder() {
    this.setData({ loading: true, error: '' })
    return request({ url: `/orders/${this.orderId}` }).then(order => {
      this.setData({ order, loading: false })
      if (order.status === 'CHARGING') {
        this.loadSimulatorStatus()
        this.startSimulation()
      }
    }).catch(error => {
      if (error.message !== 'UNAUTHORIZED') this.setData({ error: '订单加载失败，请稍后重试', loading: false })
    })
  },
  startSimulation() {
    if (this.timer || !this.packageConfig) return
    this.startedAt = Date.now()
    this.loadSimulatorStatus()
    this.simulatorTimer = setInterval(() => this.loadSimulatorStatus(), 2000)
    this.timer = setInterval(() => {
      const elapsed = (Date.now() - this.startedAt) / 1000
      if (this.data.simulatorCharging === false) {
        this.setData({ remainingLabel: '等待模拟桩恢复充电...' })
        return
      }
      const measuredEnergy = Number(this.data.simulatorEnergyKwh) || 0
      const measuredPower = Number(this.data.simulatorPowerKw) || this.power || 7.2
      const progress = Math.min(100, Math.round(measuredEnergy / this.packageConfig.target * 100))
      const remaining = Math.max(0, Math.ceil((this.packageConfig.target - measuredEnergy) * 3600 / measuredPower))
      this.setData({ progress, energyKwh: measuredEnergy.toFixed(3), remainingLabel: remaining ? `预计 ${remaining} 秒后自动结单` : '正在结单...' })
      if (progress >= 100) this.stopCharging(true)
    }, 1000)
  },
  loadSimulatorStatus() {
    if (!this.data.order) return Promise.resolve()
    return request({ url: '/simulator/status' }).then(fleet => {
      const devices = Array.isArray(fleet?.devices) ? fleet.devices : fleet?.deviceId ? [fleet] : []
      const snapshots = devices.flatMap(device => Object.values(device.connectors || {}).map(connector => ({ ...connector, deviceId: device.deviceId, connected: device.connected })))
      const exact = snapshots.find(connector => connector.connectorId === this.data.order.connectorId)
      const charging = exact || snapshots.find(connector => connector.status === 'CHARGING')
      this.setData({ simulatorCharging: Boolean(charging && charging.status === 'CHARGING'), simulatorEnergyKwh: charging?.energyKwh == null ? '0.000' : Number(charging.energyKwh).toFixed(3), simulatorPowerKw: charging?.powerKw == null ? '0.0' : Number(charging.powerKw).toFixed(1), simulatorCurrentA: charging?.currentA == null ? '0.0' : Number(charging.currentA).toFixed(1) })
    }).catch(() => {})
  },
  clearSimulation() {
    if (this.timer) { clearInterval(this.timer); this.timer = null }
    if (this.simulatorTimer) { clearInterval(this.simulatorTimer); this.simulatorTimer = null }
  },
  onUnload() { this.clearSimulation() },
  onHide() { this.clearSimulation() },
  stopCharging() {
    const energyKwh = Number(this.data.energyKwh)
    if (!energyKwh || energyKwh <= 0) return wx.showToast({ title: '请输入大于 0 的充电量', icon: 'none' })
    this.clearSimulation()
    this.setData({ stopping: true })
    request({ url: `/orders/${this.orderId}/stop`, method: 'POST', data: { energyKwh } })
      .then(order => { this.setData({ order, simulatorCharging: false }); wx.showToast({ title: '充电已结束' }) })
      .finally(() => this.setData({ stopping: false }))
  }
})
