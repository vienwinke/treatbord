/**
 * 网络请求封装：统一 baseUrl、鉴权头、错误处理。
 */
const app = () => getApp()

function request(method, path, data, { auth = false } = {}) {
  return new Promise((resolve, reject) => {
    const header = { 'Content-Type': 'application/json' }
    if (auth) {
      const token = wx.getStorageSync('token')
      if (!token) {
        wx.redirectTo({ url: '/pages/login/login' })
        reject(new Error('未登录'))
        return
      }
      header['Authorization'] = 'Bearer ' + token
    }

    wx.request({
      url: app().globalData.baseUrl + path,
      method,
      data,
      header,
      success(res) {
        const body = res.data
        if (body.code === 0) {
          resolve(body.data)
        } else if (body.code === 401) {
          // 登录失效：清 token 回登录页
          app().logout()
          wx.redirectTo({ url: '/pages/login/login' })
          reject(new Error(body.message))
        } else {
          const err = new Error(body.message || '请求失败')
          err.code = body.code
          reject(err)
        }
      },
      fail(err) {
        const msg = (err && err.errMsg) || ''
        if (msg.indexOf('not in domain list') > -1 || msg.indexOf('url not in domain list') > -1) {
          reject(new Error('请求被拦截：请在开发者工具「详情→本地设置」勾选「不校验合法域名」'))
          return
        }
        if (msg.indexOf('timeout') > -1) {
          reject(new Error('请求超时：请确认后端服务已启动'))
          return
        }
        reject(new Error('网络错误: ' + msg))
      }
    })
  })
}

module.exports = {
  get: (path, opts) => request('GET', path, null, opts),
  post: (path, data, opts) => request('POST', path, data, opts),
  put: (path, data, opts) => request('PUT', path, data, opts),
  del: (path, opts) => request('DELETE', path, null, opts)
}