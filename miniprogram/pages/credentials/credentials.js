const api = require('../../utils/api')

Page({
  data: {
    username: '',
    password: '',
    saving: false
  },

  onLoad() {
    // 预填当前账号（微信登录自动生成的账号）
    api.me().then(user => {
      this.setData({ username: user.username || '' })
    }).catch(() => {})
  },

  onUsername(e) { this.setData({ username: e.detail.value }) },
  onPassword(e) { this.setData({ password: e.detail.value }) },

  save() {
    const { username, password } = this.data
    if (!username.trim()) return wx.showToast({ title: '请输入账号', icon: 'none' })
    if (!password) return wx.showToast({ title: '请输入密码', icon: 'none' })
    this.setData({ saving: true })
    api.setCredentials({ username: username.trim(), password })
      .then(() => {
        wx.showToast({ title: '已保存，可用账密登录', icon: 'success' })
        setTimeout(() => wx.navigateBack(), 800)
      })
      .catch(err => wx.showToast({ title: err.message, icon: 'none' }))
      .finally(() => this.setData({ saving: false }))
  }
})