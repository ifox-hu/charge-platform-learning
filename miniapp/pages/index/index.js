const { request } = require('../../utils/request')

Page({
  data: { stations: [], stationCount: 0, operatingCount: 0, loading: false, error: '', loginRequired: false, displayName: '', latitude: 23.057, longitude: 113.394, mapScale: 12, markers: [], locationLabel: '正在获取当前位置', locationAccuracy: null, mapNotice: '' },
  onLoad() {
    this.requestLocationPermission()
  },
  onShow() {
    const user = wx.getStorageSync('user')
    this.setData({ displayName: user ? (user.displayName || user.username) : '' })
    this.loadStations()
  },
  requestLocationPermission() {
    wx.getSetting({ success: setting => {
      const authorized = setting.authSetting?.['scope.userLocation'] === true
      if (authorized) return this.loadLocation()
      if (setting.authSetting?.['scope.userLocation'] === false) return this.promptLocationSetting()
      wx.authorize({ scope: 'scope.userLocation', success: () => this.loadLocation(), fail: () => {
        this.promptLocationSetting()
      } })
    }, fail: () => this.promptLocationSetting() })
  },
  promptLocationSetting() {
    wx.showModal({ title: '需要位置权限', content: '开启位置权限后才能定位到你的位置并显示附近充电站。', confirmText: '去开启', cancelText: '暂不', success: result => {
      if (!result.confirm) return
      wx.openSetting({ success: next => {
        if (next.authSetting?.['scope.userLocation']) this.loadLocation()
        else this.setDefaultLocation()
      } })
    } })
    this.setDefaultLocation()
  },
  setDefaultLocation() {
    this.setData({ locationLabel: '定位权限未开启，使用默认位置', locationAccuracy: null })
    this.updateLocationMarker(this.data.latitude, this.data.longitude, '默认位置')
  },
  loadLocation() {
    wx.getLocation({ type: 'gcj02', isHighAccuracy: true, highAccuracyExpireTime: 5000, success: location => {
      const accuracy = Number(location.accuracy)
      const accuracyText = Number.isFinite(accuracy) && accuracy > 0 ? `，精度约${Math.round(accuracy)}米` : ''
      this.setData({ latitude: location.latitude, longitude: location.longitude, mapScale: 14, locationAccuracy: accuracy, locationLabel: `已定位到当前位置${accuracyText}` })
      this.updateLocationMarker(location.latitude, location.longitude, '我的位置')
    }, fail: error => { this.setData({ locationLabel: '定位失败，请检查手机定位服务', locationAccuracy: null }); this.updateLocationMarker(this.data.latitude, this.data.longitude, '默认位置'); wx.showToast({ title: error.errMsg || '定位失败', icon: 'none' }) } })
  },
  updateLocationMarker(latitude, longitude, label) {
    const markers = (this.data.markers || []).filter(marker => marker.id !== 999999)
    markers.push({ id: 999999, latitude, longitude, title: label, iconPath: '/images/location-green.png', width: 36, height: 36, callout: { content: label, display: 'ALWAYS', padding: 6, borderRadius: 12, bgColor: '#183e2e', color: '#b8ff5e' } })
    this.setData({ markers })
  },
  onPullDownRefresh() { this.loadStations().finally(() => wx.stopPullDownRefresh()) },
  loadStations() {
    if (!wx.getStorageSync('token')) {
      this.setData({ stations: [], stationCount: 0, operatingCount: 0, markers: (this.data.markers || []).filter(marker => marker.id === 999999), loading: false, loginRequired: true, error: '登录后即可查看充电站' })
      return Promise.resolve()
    }
    const showLoading = !this.data.stations.length
    this.setData({ loading: showLoading, error: '', loginRequired: false })
    return request({ url: '/stations' }).then(stations => {
      const statusText = { OPERATING: '运营中', CLOSED: '已停业', MAINTENANCE: '维护中' }
      const rows = (Array.isArray(stations) ? stations : []).map(station => ({
        ...station,
        statusText: statusText[station.status] || station.status || '未知'
      }))
      const stationMarkers = rows.filter(station => Number.isFinite(Number(station.latitude)) && Number.isFinite(Number(station.longitude))).map(station => ({ id: station.id, latitude: Number(station.latitude), longitude: Number(station.longitude), title: station.name, callout: { content: station.name, display: 'ALWAYS', padding: 6, borderRadius: 4 } }))
      const mapNotice = stationMarkers.length < rows.length ? '部分站点尚未完成地址解析' : ''
      const locationMarker = { id: 999999, latitude: this.data.latitude, longitude: this.data.longitude, title: this.data.locationLabel === '已定位到当前位置' ? '我的位置' : '默认位置', iconPath: '/images/location-green.png', width: 36, height: 36, callout: { content: this.data.locationLabel === '已定位到当前位置' ? '我的位置' : '默认位置', display: 'ALWAYS', padding: 6, borderRadius: 12, bgColor: '#183e2e', color: '#b8ff5e' } }
      this.setData({
        stations: rows,
        stationCount: rows.length,
        operatingCount: rows.filter(item => item.status === 'OPERATING').length,
        markers: stationMarkers.concat(locationMarker),
        mapNotice,
        loginRequired: false
      })
    }).catch(error => {
      if (error.message === 'UNAUTHORIZED') {
        this.setData({ stations: [], stationCount: 0, operatingCount: 0, markers: (this.data.markers || []).filter(marker => marker.id === 999999), displayName: '', loginRequired: true, error: '登录已过期，请重新登录' })
      } else {
        this.setData({ error: '充电站加载失败，请重试' })
      }
    }).finally(() => this.setData({ loading: false }))
  },
  openStation(event) {
    wx.navigateTo({ url: `/pages/station-detail/station-detail?id=${event.currentTarget.dataset.id}` })
  },
  onMarkerTap(event) {
    const station = this.data.stations.find(item => item.id === event.detail.markerId)
    if (station) wx.navigateTo({ url: `/pages/station-detail/station-detail?id=${station.id}` })
  },
  openLogin() { wx.navigateTo({ url: '/pages/login/login' }) },
  retryStations() { this.loadStations() }
})
