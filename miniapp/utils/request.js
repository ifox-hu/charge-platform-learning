const env = require('../config/env')

function request(options) {
  const token = wx.getStorageSync('token')
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${env.baseUrl}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      header: Object.assign({ 'content-type': 'application/json' }, token ? { Authorization: `Bearer ${token}` } : {}, options.header || {}),
      success(res) {
        const body = res.data || {}
        if (res.statusCode === 401 || body.code === 401) {
          wx.removeStorageSync('token')
          wx.removeStorageSync('user')
          wx.showToast({ title: '请先登录', icon: 'none' })
          reject(new Error('UNAUTHORIZED'))
          return
        }
        if (res.statusCode === 403 || body.code === 403) {
          wx.showToast({ title: '没有访问权限', icon: 'none' })
          reject(new Error('FORBIDDEN'))
          return
        }
        if (res.statusCode < 200 || res.statusCode >= 300 || (body.code !== undefined && body.code >= 400)) {
          wx.showToast({ title: body.message || '请求失败', icon: 'none' })
          reject(new Error(body.message || 'REQUEST_FAILED'))
          return
        }
        resolve(body.data)
      },
      fail(error) {
        wx.showToast({ title: '网络连接失败', icon: 'none' })
        reject(error)
      }
    })
  })
}

module.exports = { request }
