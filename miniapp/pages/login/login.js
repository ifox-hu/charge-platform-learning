const { request } = require('../../utils/request')

Page({
  data: { username: 'operator', password: '', loading: false },
  onUsernameInput(event) { this.setData({ username: event.detail.value }) },
  onPasswordInput(event) { this.setData({ password: event.detail.value }) },
  submit() {
    if (!this.data.username || !this.data.password) return wx.showToast({ title: '请输入账号和密码', icon: 'none' })
    this.setData({ loading: true })
    request({ url: '/auth/login', method: 'POST', data: { username: this.data.username, password: this.data.password } })
      .then(data => {
        wx.setStorageSync('token', data.token)
        wx.setStorageSync('user', data)
        wx.showToast({ title: '登录成功' })
        setTimeout(() => wx.navigateBack(), 500)
      }).finally(() => this.setData({ loading: false }))
  }
})
