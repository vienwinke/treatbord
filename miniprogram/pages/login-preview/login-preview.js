const api = require('../../utils/api')
const req = require('../../utils/request')   // 预览版直接走 request（不新增原文件接口）

// 订单状态映射（claim.status + task.status 双字段判定）
// 待执行=CLAIMED | 待完成=SUBMITTED | 待确认=APPROVED且未结算 | 已完成=APPROVED且任务SETTLED | 售后=REJECTED

Page({
  data: {
    isLogin: false,
    nickname: '',
    nickname0: '?',
    creditScore: 100,
    stats: { pending: 0, doing: 0, confirm: 0, done: 0, aftersale: 0 }
  },

  onLoad(options) {
    this.redirect = options.redirect || ''
  },

  onShow() {
    this.refreshAuth()
    if (wx.getStorageSync('token')) {
      this.loadStats()
    }
  },

  onPullDownRefresh() {
    this.refreshAuth()
    if (wx.getStorageSync('token')) {
      this.loadStats().then(() => wx.stopPullDownRefresh())
    } else {
      wx.stopPullDownRefresh()
    }
  },

  /** 读取本地登录态 */
  refreshAuth() {
    const token = wx.getStorageSync('token')
    const u = wx.getStorageSync('userInfo') || {}
    this.setData({
      isLogin: !!token,
      nickname: u.nickname || '',
      nickname0: (u.nickname || '?')[0],
      creditScore: u.creditScore || 100
    })
  },

  /** 统计五状态订单数（拉最近 20 条接取在前端分组；超 20 条统计会截断，后续可加后端聚合接口） */
  loadStats() {
    return req.get('/me/claims?page=1&pageSize=20', { auth: true })
      .then(res => {
        const s = { pending: 0, doing: 0, confirm: 0, done: 0, aftersale: 0 }
        ;(res.list || []).forEach(c => {
          if (c.status === 'CLAIMED') s.pending += 1
          else if (c.status === 'SUBMITTED') s.doing += 1
          else if (c.status === 'APPROVED' && c.taskStatus === 'SETTLED') s.done += 1
          else if (c.status === 'APPROVED') s.confirm += 1
          else if (c.status === 'REJECTED') s.aftersale += 1
        })
        this.setData({ stats: s })
      })
      .catch(err => wx.showToast({ title: err.message, icon: 'none' }))
  },

  /** 点击个人信息区：未登录 → 跳登录页；已登录 → 弹出操作菜单（退出/切换账号） */
  onTapProfile() {
    if (!this.data.isLogin) {
      wx.redirectTo({
        url: '/pages/login/login?redirect=' + encodeURIComponent('/pages/login-preview/login-preview')
      })
      return
    }
    wx.showActionSheet({
      itemList: ['切换演示账号', '退出登录'],
      itemColor: '#4A90D9',
      success: res => {
        if (res.tapIndex === 0) this.switchAccount()
        else if (res.tapIndex === 1) this.doLogout()
      }
    })
  },

  /** 切换账号：清登录态 → 去登录页重新选 */
  switchAccount() {
    getApp().logout()
    this.refreshAuth()
    wx.redirectTo({
      url: '/pages/login/login?redirect=' + encodeURIComponent('/pages/login-preview/login-preview')
    })
  },

  /** 退出登录（后端 jti 拉黑 + 清本地） */
  doLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定退出当前账号吗？',
      success: res => {
        if (!res.confirm) return
        api.logout().catch(() => {}).finally(() => {
          getApp().logout()
          this.refreshAuth()
          this.setData({ stats: { pending: 0, doing: 0, confirm: 0, done: 0, aftersale: 0 } })
          wx.showToast({ title: '已退出', icon: 'success' })
        })
      }
    })
  },

  /** 点某状态：游客可进页面查看（接单/提交等操作页内再引导登录） */
  goOrders(e) {
    wx.switchTab({ url: '/pages/my-claims/my-claims' })
  },

  /** 全部订单入口：游客同样可进 */
  goAllOrders(e) {
    wx.switchTab({ url: '/pages/my-claims/my-claims' })
  },

  /** 未登录引导（图里无登录按钮，引导去现有登录页） */
  askLogin() {
    wx.showModal({
      title: '需要登录',
      content: '该功能需要登录后使用，是否前往登录？',
      success: res => {
        if (res.confirm) {
          wx.redirectTo({ url: '/pages/login/login?redirect=' + encodeURIComponent('/pages/login-preview/login-preview') })
        }
      }
    })
  }
})