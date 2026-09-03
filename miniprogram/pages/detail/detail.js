const api = require('../../utils/api')

const STATUS_TEXT = {
  OPEN: '接取中', IN_PROGRESS: '进行中', REVIEWING: '审核中',
  SETTLED: '已结算', EXPIRED: '已过期', CANCELLED: '已取消'
}
const CLAIM_TEXT = {
  CLAIMED: '待提交', SUBMITTED: '待审核', APPROVED: '已通过',
  REJECTED: '已驳回', CANCELLED: '已取消'
}

Page({
  data: {
    id: null,
    task: null,
    loading: true,
    isPublisher: false
  },

  onLoad(options) {
    this.setData({ id: options.id })
    this.load()
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh())
  },

  load() {
    return api.taskDetail(this.data.id)
      .then(task => {
        const me = wx.getStorageSync('userInfo')
        const isPublisher = me && me.id === task.publisherId
        this.setData({
          task: Object.assign({}, task, {
            statusText: STATUS_TEXT[task.status] || task.status,
            claimStatusText: CLAIM_TEXT[task.myClaimStatus] || ''
          }),
          isPublisher,
          loading: false
        })
      })
      .catch(err => {
        this.setData({ loading: false })
        wx.showToast({ title: err.message, icon: 'none' })
      })
  },

  /** 登录引导：未登录的操作（接取/举报/提交）先弹窗去登录，登录后回跳本页 */
  ensureLogin() {
    if (wx.getStorageSync('token')) return true
    wx.showModal({
      title: '需要登录',
      content: '该操作需要登录后使用，是否前往登录？',
      confirmText: '去登录',
      success: res => {
        if (res.confirm) {
          const back = '/pages/detail/detail?id=' + this.data.id
          wx.redirectTo({ url: '/pages/login/login?redirect=' + encodeURIComponent(back) })
        }
      }
    })
    return false
  },

  /** 接取任务 */
  doClaim() {
    if (!this.ensureLogin()) return
    wx.showModal({
      title: '确认接取',
      content: '确定接取该任务并遵守完成时限吗？',
      success: res => {
        if (!res.confirm) return
        api.claimTask(this.data.id)
          .then(() => {
            wx.showToast({ title: '接取成功', icon: 'success' })
            this.load()
          })
          .catch(err => wx.showToast({ title: err.message, icon: 'none' }))
      }
    })
  },

  /** 发布者取消任务 */
  doCancelTask() {
    wx.showModal({
      title: '取消任务',
      content: '确定取消该任务吗？',
      success: res => {
        if (!res.confirm) return
        api.cancelTask(this.data.id)
          .then(() => {
            wx.showToast({ title: '已取消', icon: 'success' })
            this.load()
          })
          .catch(err => wx.showToast({ title: err.message, icon: 'none' }))
      }
    })
  },

  /** 跳提交凭证页（带 taskId，submit 页自查我的 claim） */
  goSubmit() {
    if (!this.ensureLogin()) return
    wx.navigateTo({ url: '/pages/submit/submit?taskId=' + this.data.id })
  },

  /** 举报该任务 */
  goReport() {
    if (!this.ensureLogin()) return
    const t = this.data.task
    wx.navigateTo({
      url: '/pages/report/report?targetType=task&targetId=' + t.id +
        '&targetTitle=' + encodeURIComponent(t.title || '')
    })
  }
})