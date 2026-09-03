const api = require('../../utils/api')

const CLAIM_TEXT = {
  CLAIMED: '待提交', SUBMITTED: '待审核', APPROVED: '已通过',
  REJECTED: '已驳回', CANCELLED: '已取消'
}
const TASK_STATUS_TEXT = {
  OPEN: '接取中', IN_PROGRESS: '进行中', REVIEWING: '审核中',
  SETTLED: '已结算', EXPIRED: '已过期', CANCELLED: '已取消'
}

Page({
  data: {
    tab: 'claims',           // claims | published
    claims: [],
    myTasks: [],
    userInfo: null,
    loading: false,
    unreadCount: 0,
    isAdmin: false,
    isLogin: false
  },

  onShow() {
    const token = wx.getStorageSync('token')
    const userInfo = wx.getStorageSync('userInfo')
    const isLogin = !!token
    this.setData({ userInfo, isLogin, isAdmin: !!(userInfo && userInfo.role === 1) })
    if (!isLogin) {
      // 游客模式：不弹登录，页面显示空态 + 引导（不发鉴权请求，避免 401 被弹回登录）
      this.setData({ claims: [], myTasks: [], unreadCount: 0, loading: false })
      return
    }
    this.refresh()
    this.loadUnread()
  },

  /** 游客去登录（登录后回跳本页） */
  goLogin() {
    wx.navigateTo({
      url: '/pages/login/login?redirect=' + encodeURIComponent('/pages/my-claims/my-claims')
    })
  },

  onPullDownRefresh() {
    this.refresh().then(() => wx.stopPullDownRefresh())
  },

  refresh() {
    const fn = this.data.tab === 'claims' ? this.loadClaims : this.loadPublished
    return fn()
  },

  switchTab(e) {
    this.setData({ tab: e.currentTarget.dataset.tab })
    this.refresh()
  },

  loadClaims() {
    this.setData({ loading: true })
    return api.myClaims(1).then(res => {
      const claims = res.list.map(c => Object.assign({}, c, {
        claimStatusText: CLAIM_TEXT[c.status] || c.status,
        taskStatusText: TASK_STATUS_TEXT[c.taskStatus] || c.taskStatus,
        tagClass: 'tag-' + c.status.toLowerCase()
      }))
      this.setData({ claims, loading: false })
    }).catch(err => {
      this.setData({ loading: false })
      wx.showToast({ title: err.message, icon: 'none' })
    })
  },

  loadPublished() {
    this.setData({ loading: true })
    return api.myTasks(1).then(res => {
      const myTasks = res.list.map(t => Object.assign({}, t, {
        statusText: TASK_STATUS_TEXT[t.status] || t.status,
        tagClass: 'tag-' + t.status.toLowerCase()
      }))
      this.setData({ myTasks, loading: false })
    }).catch(err => {
      this.setData({ loading: false })
      wx.showToast({ title: err.message, icon: 'none' })
    })
  },

  /** 未读数（角标） */
  loadUnread() {
    api.unreadCount().then(res => {
      this.setData({ unreadCount: res.total || 0 })
    }).catch(() => {})
  },

  goNotifications() {
    wx.navigateTo({ url: '/pages/notifications/notifications' })
  },

  goAdmin() {
    wx.navigateTo({ url: '/pages/admin/admin' })
  },

  /** 去互评（接取已通过后，双方互评） */
  goPeerReview(e) {
    const { claimid, taskid } = e.currentTarget.dataset
    wx.navigateTo({ url: '/pages/peer-review/peer-review?claimId=' + claimid + '&taskId=' + taskid })
  },

  /** 去任务详情（接取的） */
  goTask(e) {
    wx.navigateTo({ url: '/pages/detail/detail?id=' + e.currentTarget.dataset.taskid })
  },

  /** 去提交凭证 */
  goSubmit(e) {
    wx.navigateTo({ url: '/pages/submit/submit?taskId=' + e.currentTarget.dataset.taskid })
  },

  /** 取消接取 */
  doCancelClaim(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '取消接取',
      content: '确定取消该接取吗？',
      success: res => {
        if (!res.confirm) return
        api.cancelClaim(id).then(() => {
          wx.showToast({ title: '已取消', icon: 'success' })
          this.loadClaims()
        }).catch(err => wx.showToast({ title: err.message, icon: 'none' }))
      }
    })
  },

  /** 去审核页（我发布的） */
  goReview(e) {
    wx.navigateTo({ url: '/pages/review/review?taskId=' + e.currentTarget.dataset.taskid })
  },

  /** 注销账号 */
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