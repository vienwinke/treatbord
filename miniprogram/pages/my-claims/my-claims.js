const api = require('../../utils/api')

/* 任务态文案（我发布的/厅内任务） */
const TASK_STATUS = {
  OPEN: '待接取', IN_PROGRESS: '进行中', REVIEWING: '待确认',
  SETTLED: '已完成', EXPIRED: '已关闭', CANCELLED: '已关闭'
}
/* 接取态文案（我接取的） */
const CLAIM_STATUS = {
  CLAIMED: '进行中', SUBMITTED: '待确认', APPROVED: '已完成',
  REJECTED: '已关闭', CANCELLED: '已关闭'
}
function taskText(s) { return TASK_STATUS[s] || (s || '') }
function claimText(s) { return CLAIM_STATUS[s] || (s || '') }
function taskTag(s) { return 'tag-' + String(s).toLowerCase() }
function claimTag(s) { return 'tag-' + String(s).toLowerCase() }

/** 订单状态键 → claim 匹配规则（与顶部角标统计同一套映射） */
function matchOrder(c, key) {
  switch (key) {
    case 'DOING': return c.status === 'CLAIMED'
    case 'CONFIRM': return c.status === 'SUBMITTED'
    case 'DONE': return c.status === 'APPROVED'
    case 'CLOSED': return c.status === 'REJECTED' || c.status === 'CANCELLED'
    default: return true
  }
}

Page({
  data: {
    tab: 'claims',            // claims | published
    claims: [],               // 全部接取（原始）
    visibles: [],             // 当前筛选后的接取
    myTasks: [],
    userInfo: null,
    loading: false,
    isLogin: false,
    nickname: '',
    nickname0: '?',
    creditScore: 100,
    isAdmin: false,
    unread: 0,
    orderFilter: '',          // DOING/CONFIRM/DONE/CLOSED/''=全部
    stats: { doing: 0, confirm: 0, done: 0, closed: 0 }
  },

  onShow() {
    this.refreshAuth()
    if (wx.getStorageSync('token')) {
      this.refresh()
      this.loadStats()
      this.loadUnread()
    } else {
      // 游客模式：显示空态 + 引导（不发鉴权请求）
      this.setData({ claims: [], visibles: [], myTasks: [], loading: false, unread: 0 })
    }
  },

  onPullDownRefresh() {
    this.refreshAuth()
    if (wx.getStorageSync('token')) {
      Promise.all([this.refresh(), this.loadStats()]).then(() => wx.stopPullDownRefresh())
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
      userInfo: u,
      nickname: u.nickname || '',
      nickname0: (u.nickname || '?')[0],
      creditScore: u.creditScore || 100,
      isAdmin: !!u && Number(u.role) === 1
    })
  },

  refresh() {
    // 游客不触发鉴权请求/登录跳转；登录仅通过个人信息区（onTapProfile）
    if (!this.data.isLogin) {
      this.setData({ claims: [], visibles: [], myTasks: [], loading: false })
      return Promise.resolve()
    }
    // 必须用 this.method() 调用以保留 this 绑定；否则 loadClaims/loadPublished 里 this.setData 会抛错
    if (this.data.tab === 'claims') return this.loadClaims()
    return this.loadPublished()
  },

  switchTab(e) {
    this.setData({ tab: e.currentTarget.dataset.tab, orderFilter: '' })
    this.refresh()
  },

  /** 点顶部订单状态卡：游客提示登录；已登录进入对应状态的订单列表页 */
  goOrders(e) {
    if (!this.data.isLogin) {
      wx.showToast({ title: '请先登录（点击上方个人信息）', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/order-list/order-list?status=' + e.currentTarget.dataset.status })
  },

  /** 全部订单 */
  goAllOrders() {
    this.setData({ tab: 'claims', orderFilter: '' })
    this.refresh()
  },

  loadClaims() {
    if (!this.data.isLogin) return Promise.resolve()
    this.setData({ loading: true })
    return api.myClaims(1).then(res => {
      const claims = res.list || []
      const filtered = claims.filter(c => matchOrder(c, this.data.orderFilter))
      const visibles = filtered.map(c => Object.assign({}, c, {
        claimStatusText: claimText(c.status),
        taskStatusText: taskText(c.taskStatus),
        tagClass: claimTag(c.status)
      }))
      this.setData({ claims, visibles, loading: false })
    }).catch(err => {
      this.setData({ loading: false })
      wx.showToast({ title: err.message, icon: 'none' })
    })
  },

  loadPublished() {
    if (!this.data.isLogin) return Promise.resolve()
    this.setData({ loading: true })
    return api.myTasks(1).then(res => {
      const myTasks = res.list.map(t => Object.assign({}, t, {
        statusText: taskText(t.status),
        tagClass: taskTag(t.status)
      }))
      this.setData({ myTasks, loading: false })
    }).catch(err => {
      this.setData({ loading: false })
      wx.showToast({ title: err.message, icon: 'none' })
    })
  },

  /** 统计四种状态订单数（拉最近 20 条接取分组；订单超 20 条会截断，后续可加后端聚合接口） */
  loadStats() {
    return api.myClaims(1, 20).then(res => {
      const s = { doing: 0, confirm: 0, done: 0, closed: 0 }
      ;(res.list || []).forEach(c => {
        if (c.status === 'CLAIMED') s.doing += 1
        else if (c.status === 'SUBMITTED') s.confirm += 1
        else if (c.status === 'APPROVED') s.done += 1
        else if (c.status === 'REJECTED' || c.status === 'CANCELLED') s.closed += 1
      })
      this.setData({ stats: s })
    }).catch(() => {})
  },

  /** 未读通知数 */
  loadUnread() {
    if (!this.data.isLogin) return Promise.resolve()
    return api.unreadCount().then(res => {
      this.setData({ unread: (res && res.total) || 0 })
    }).catch(() => {})
  },

  /** 需要登录才可用的入口：游客不跳登录，提示从个人信息区登录 */
  requireLogin() {
    if (this.data.isLogin) return true
    wx.showToast({ title: '请先登录（点击上方个人信息）', icon: 'none' })
    return false
  },

  /* ══ 功能区：常用功能入口 ══ */
  goNotifications() {
    if (!this.requireLogin()) return
    wx.navigateTo({ url: '/pages/notifications/notifications' })
  },
  goPublished() {
    this.setData({ tab: 'published', orderFilter: '' })
    this.refresh()
  },
  goReport() {
    if (!this.requireLogin()) return
    wx.navigateTo({ url: '/pages/report/report' })
  },
  goCredit() {
    if (!this.requireLogin()) return
    wx.showModal({
      title: '信用分 ' + this.data.creditScore,
      content: '信用分反映履约情况。按时提交、诚信互评可加分；违规、超时或取消会被扣分。',
      showCancel: false,
      confirmText: '知道了'
    })
  },
  goAdmin() {
    if (!this.requireLogin()) return
    if (!this.data.isAdmin) {
      wx.showToast({ title: '仅管理员可访问', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/admin/admin' })
  },
  /** 设置（账号/关于/退出与注销 都在设置页） */
  goSettings() {
    if (!this.requireLogin()) return
    wx.navigateTo({ url: '/pages/settings/settings' })
  },

  goHelp() {
    wx.showModal({
      title: '帮助与反馈',
      content: '发布/接取遇到问题？可先到大厅查看任务详情；如需人工协助，请通过「举报与反馈」提交，我们会在 1-3 个工作日内处理。（MVP 文案）',
      showCancel: false,
      confirmText: '知道了'
    })
  },
  /** 点击个人信息区：未登录 → 跳登录页；已登录 → 弹操作菜单 */
  onTapProfile() {
    if (!this.data.isLogin) {
      wx.redirectTo({
        url: '/pages/login/login?redirect=' + encodeURIComponent('/pages/my-claims/my-claims')
      })
      return
    }
    // 已登录：直接进「设置」（账号/协议/退出/注销 都在设置页）
    wx.navigateTo({ url: '/pages/settings/settings' })
  },

  goTask(e) {
    wx.navigateTo({ url: '/pages/detail/detail?id=' + e.currentTarget.dataset.taskid })
  },

  goSubmit(e) {
    wx.navigateTo({ url: '/pages/submit/submit?taskId=' + e.currentTarget.dataset.taskid })
  },

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
          this.loadStats()
        }).catch(err => wx.showToast({ title: err.message, icon: 'none' }))
      }
    })
  },

  goReview(e) {
    wx.navigateTo({ url: '/pages/review/review?taskId=' + e.currentTarget.dataset.taskid })
  },

  goPeerReview(e) {
    const { claimid, taskid } = e.currentTarget.dataset
    wx.navigateTo({ url: '/pages/peer-review/peer-review?claimId=' + claimid + '&taskId=' + taskid })
  }
})