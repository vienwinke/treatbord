const api = require('../../utils/api')

const VERSION = 'v0.2.0'

/**
 * 设置页（二级页）：账号（昵称/账号密码）· 关于（协议/隐私/版本）· 退出登录 / 注销账号。
 * 昵称修改、退出、注销逻辑从 my-claims 迁到这里，避免两处重复。
 */
Page({
  data: {
    isLogin: false,
    nickname: '',
    hasCredentials: false,
    version: VERSION
  },

  onShow() {
    this.load()
  },

  /** 拉取账号状态（从「设置账号密码」页返回时也会刷新） */
  load() {
    if (!wx.getStorageSync('token')) {
      this.setData({ isLogin: false, nickname: '', hasCredentials: false })
      return
    }
    this.setData({ isLogin: true })
    api.me().then(u => {
      this.setData({ nickname: u.nickname || '', hasCredentials: !!u.username })
    }).catch(() => {})
  },

  goLogin() {
    wx.navigateTo({
      url: '/pages/login/login?redirect=' + encodeURIComponent('/pages/settings/settings')
    })
  },

  /** 修改昵称：弹窗输入 → PUT /api/users/me → 刷新页面与本地缓存 */
  editNickname() {
    if (!this.data.isLogin) return this.goLogin()
    wx.showModal({
      title: '修改昵称',
      editable: true,
      placeholderText: '请输入新昵称（最长 30 字）',
      success: res => {
        if (!res.confirm) return
        const nickname = (res.content || '').trim()
        if (!nickname) return wx.showToast({ title: '昵称不能为空', icon: 'none' })
        if (nickname.length > 30) return wx.showToast({ title: '昵称最长 30 个字符', icon: 'none' })
        api.updateProfile({ nickname })
          .then(user => {
            const name = user.nickname
            this.setData({ nickname: name })
            // 同步本地缓存，避免「我的」等页面仍显示旧昵称
            const info = wx.getStorageSync('userInfo') || {}
            const merged = Object.assign({}, info, { nickname: name })
            wx.setStorageSync('userInfo', merged)
            getApp().globalData.userInfo = merged
            wx.showToast({ title: '昵称已更新', icon: 'success' })
          })
          .catch(err => wx.showToast({ title: err.message, icon: 'none' }))
      }
    })
  },

  goCredentials() {
    if (!this.data.isLogin) return this.goLogin()
    wx.navigateTo({ url: '/pages/credentials/credentials' })
  },

  goAgreement() {
    wx.navigateTo({ url: '/pages/agreement/agreement' })
  },

  goPrivacy() {
    wx.navigateTo({ url: '/pages/privacy/privacy' })
  },

  /** 切换账号：登出后前往登录页 */
  switchAccount() {
    wx.showModal({
      title: '切换账号',
      content: '将退出当前账号并前往登录页',
      success: res => {
        if (!res.confirm) return
        getApp().logout()
        wx.redirectTo({ url: '/pages/login/login' })
      }
    })
  },

  /** 退出登录 */
  doLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定退出当前账号吗？',
      success: res => {
        if (!res.confirm) return
        api.logout().catch(() => {}).finally(() => {
          getApp().logout()
          wx.showToast({ title: '已退出', icon: 'success' })
          setTimeout(() => wx.switchTab({ url: '/pages/my-claims/my-claims' }), 600)
        })
      }
    })
  },

  /** 注销账号（合规强制：匿名化 + token 失效） */
  doDelete() {
    wx.showModal({
      title: '注销账号',
      content: '注销后个人数据将被匿名化处理，确定注销吗？',
      confirmColor: '#e74c3c',
      success: res => {
        if (!res.confirm) return
        api.deleteAccount().then(() => {
          getApp().logout()
          wx.redirectTo({ url: '/pages/login/login' })
        }).catch(err => wx.showToast({ title: err.message, icon: 'none' }))
      }
    })
  }
})
