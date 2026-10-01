const { request } = require('../../utils/request')

Page({
  data: { username: '', password: '', displayName: '', registerMode: false, loading: false, hasSession: false },
  onShow() {
    const user = wx.getStorageSync('user')
    this.setData({ hasSession: Boolean(wx.getStorageSync('token')), displayName: user?.displayName || '' })
  },
  onUsernameInput(event) { this.setData({ username: event.detail.value }) },
  onPasswordInput(event) { this.setData({ password: event.detail.value }) },
  onDisplayNameInput(event) { this.setData({ displayName: event.detail.value }) },
  toggleMode() { this.setData({ registerMode: !this.data.registerMode, password: '' }) },
  submit() {
    if (!this.data.username || !this.data.password) return wx.showToast({ title: '请输入账号和密码', icon: 'none' })
    if (this.data.password.length < 6) return wx.showToast({ title: '密码至少需要6位', icon: 'none' })
    if (this.data.registerMode && !this.data.displayName) return wx.showToast({ title: '请输入昵称', icon: 'none' })
    this.setData({ loading: true })
    const payload = this.data.registerMode ? { username: this.data.username, password: this.data.password, displayName: this.data.displayName } : { username: this.data.username, password: this.data.password }
    request({ url: this.data.registerMode ? '/auth/register' : '/auth/login', method: 'POST', data: payload })
      .then(data => {
        wx.setStorageSync('token', data.token)
        wx.setStorageSync('user', data)
        wx.showToast({ title: this.data.registerMode ? '注册成功' : '登录成功' })
        setTimeout(() => wx.navigateBack(), 500)
      }).finally(() => this.setData({ loading: false, hasSession: Boolean(wx.getStorageSync('token')) }))
  },
  logout() {
    wx.removeStorageSync('token')
    wx.removeStorageSync('user')
    const app = getApp()
    if (app.globalData) app.globalData.user = null
    this.setData({ hasSession: false, username: '', password: '', displayName: '' })
    wx.showToast({ title: '已退出登录' })
  }
})
