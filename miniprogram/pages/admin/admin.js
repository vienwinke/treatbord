const api = require('../../utils/api')

const REPORT_STATUS_TEXT = { 0: '待处理', 1: '已处理', 2: '驳回' }
const ROLES = ['用户', '管理员']
const TASK_FILTERS = [
  { key: '', text: '全部' },
  { key: 'OPEN', text: '接取中' },
  { key: 'IN_PROGRESS', text: '进行中' },
  { key: 'SETTLED', text: '已结算' },
  { key: 'EXPIRED', text: '已过期' }
]

Page({
  data: {
    tab: 'stats',
    loading: false,
    stats: null,
    // 举报
    reportFilter: 0,
    reportFilters: [
      { key: 0, text: '待处理' },
      { key: 1, text: '已处理' },
      { key: 2, text: '驳回' }
    ],
    reports: [],
    // 用户
    userFilter: null,
    userFilters: [
      { key: null, text: '全部' },
      { key: 0, text: '正常' },
      { key: 1, text: '封禁' }
    ],
    users: [],
    // 任务
    taskFilter: '',
    taskFilters: TASK_FILTERS,
    tasks: []
  },

  onShow() {
    const me = wx.getStorageSync('userInfo')
    if (!wx.getStorageSync('token')) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    if (!me || me.role !== 1) {
      wx.showToast({ title: '仅管理员可访问', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 600)
      return
    }
    this.refresh()
  },

  onPullDownRefresh() {
    this.refresh().then(() => wx.stopPullDownRefresh())
  },

  refresh() {
    const fn = { stats: this.loadStats, reports: this.loadReports, users: this.loadUsers, tasks: this.loadTasks }[this.data.tab]
    return fn.call(this)
  },

  switchTab(e) {
    this.setData({ tab: e.currentTarget.dataset.tab })
    this.refresh()
  },

  // ---- 统计 ----
  loadStats() {
    this.setData({ loading: true })
    return api.adminSummary().then(stats => {
      const cards = [
        { label: '任务总数', value: stats.taskCount },
        { label: '用户总数', value: stats.userCount },
        { label: '接取总数', value: stats.claimCount },
        { label: '结算笔数', value: stats.settlementCount },
        { label: '待结算', value: stats.pendingSettlementCount },
        { label: '待处理举报', value: stats.pendingReportCount, warn: stats.pendingReportCount > 0 }
      ]
      this.setData({ stats: cards, loading: false })
    }).catch(err => {
      this.setData({ loading: false })
      wx.showToast({ title: err.message, icon: 'none' })
    })
  },

  // ---- 举报 ----
  loadReports() {
    this.setData({ loading: true })
    return api.adminReports(this.data.reportFilter, 1).then(res => {
      const reports = res.list.map(r => Object.assign({}, r, {
        statusText: REPORT_STATUS_TEXT[r.status] || r.status,
        statusClass: r.status === 0 ? 'pending' : (r.status === 1 ? 'handled' : 'rejected')
      }))
      this.setData({ reports, loading: false })
    }).catch(err => {
      this.setData({ loading: false })
      wx.showToast({ title: err.message, icon: 'none' })
    })
  },

  setReportFilter(e) {
    const key = Number(e.currentTarget.dataset.key)
    this.setData({ reportFilter: key })
    this.loadReports()
  },

  /** 处理举报：已处理 / 驳回 */
  handleReport(e) {
    const id = e.currentTarget.dataset.id
    wx.showActionSheet({
      itemList: ['标记为已处理', '驳回举报'],
      itemColor: '#4A90D9',
      success: res => {
        const status = res.tapIndex === 0 ? 1 : 2
        api.handleReport(id, status, '')
          .then(() => {
            wx.showToast({ title: '已处理', icon: 'success' })
            this.loadReports()
          })
          .catch(err => wx.showToast({ title: err.message, icon: 'none' }))
      }
    })
  },

  // ---- 用户 ----
  loadUsers() {
    this.setData({ loading: true })
    return api.adminUsers(this.data.userFilter, 1).then(res => {
      const me = wx.getStorageSync('userInfo')
      const users = res.list.map(u => Object.assign({}, u, {
        roleText: ROLES[u.role] || u.role,
        statusText: u.status === 1 ? '封禁' : '正常',
        me: me && me.id === u.id,
        tagClass: u.status === 1 ? 'tag-cancelled' : 'tag-open'
      }))
      this.setData({ users, loading: false })
    }).catch(err => {
      this.setData({ loading: false })
      wx.showToast({ title: err.message, icon: 'none' })
    })
  },

  setUserFilter(e) {
    const key = e.currentTarget.dataset.key === '' ? null : Number(e.currentTarget.dataset.key)
    this.setData({ userFilter: key })
    this.loadUsers()
  },

  doBan(e) {
    const u = this.data.users[e.currentTarget.dataset.index]
    wx.showModal({
      title: u.status === 1 ? '解封用户' : '封禁用户',
      content: (u.status === 1 ? '解封' : '封禁并踢下线') + '「' + u.nickname + '」？',
      confirmColor: u.status === 1 ? '#4A90D9' : '#e74c3c',
      success: res => {
        if (!res.confirm) return
        const fn = u.status === 1 ? api.adminUnban : api.adminBan
        fn(u.id).then(() => {
          wx.showToast({ title: u.status === 1 ? '已解封' : '已封禁', icon: 'success' })
          this.loadUsers()
        }).catch(err => wx.showToast({ title: err.message, icon: 'none' }))
      }
    })
  },

  // ---- 任务 ----
  loadTasks() {
    this.setData({ loading: true })
    return api.tasks({ page: 1, pageSize: 10, status: this.data.taskFilter || undefined })
      .then(res => {
        const statusText = { OPEN: '接取中', IN_PROGRESS: '进行中', REVIEWING: '审核中', SETTLED: '已结算', EXPIRED: '已过期', CANCELLED: '已取消' }
        const tasks = res.list.map(t => Object.assign({}, t, {
          statusText: statusText[t.status] || t.status,
          tagClass: 'tag-' + t.status.toLowerCase()
        }))
        this.setData({ tasks, loading: false })
      })
      .catch(err => {
        this.setData({ loading: false })
        wx.showToast({ title: err.message, icon: 'none' })
      })
  },

  setTaskFilter(e) {
    this.setData({ taskFilter: e.currentTarget.dataset.key })
    this.loadTasks()
  },

  /** 管理端下架违规任务 */
  doOffline(e) {
    const t = this.data.tasks[e.currentTarget.dataset.index]
    wx.showModal({
      title: '下架任务',
      content: '确认下架「' + t.title + '」？将通知发布者并取消该任务',
      confirmColor: '#e74c3c',
      success: res => {
        if (!res.confirm) return
        api.adminOfflineTask(t.id).then(() => {
          wx.showToast({ title: '已下架', icon: 'success' })
          this.loadTasks()
        }).catch(err => wx.showToast({ title: err.message, icon: 'none' }))
      }
    })
  }
})