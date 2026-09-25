const env = require('./config/env')

App({
  globalData: {
    baseUrl: env.baseUrl,
    user: null
  },
  onLaunch() {
    const user = wx.getStorageSync('user')
    if (user) this.globalData.user = user
  }
})
