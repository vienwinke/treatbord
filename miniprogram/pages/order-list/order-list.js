const api = require('../../utils/api')

/* 接取态文案 */
const CLAIM_STATUS = {
  CLAIMED: '进行中', SUBMITTED: '待确认', APPROVED: '已完成',
  REJECTED: '已关闭', CANCELLED: '已关闭'
}
/* 任务态文案 */
const TASK_STATUS = {
  OPEN: '待接取', IN_PROGRESS: '进行中', REVIEWING: '待确认',
  SETTLED: '已完成', EXPIRED: '已关闭', CANCELLED: '已关闭'
}
function claimText(s) { return CLAIM_STATUS[s] || (s || '') }
function taskText(s) { return TASK_STATUS[s] || (s || '') }
function claimTag(s) { return 'tag-' + String(s).toLowerCase() }

/* 状态 → 标题 / 空态文案 */
const STATUS_TITLE = { DOING: '进行中', CONFIRM: '待确认', DONE: '已完成', CLOSED: '已关闭' }
const EMPTY_TIP = {
  DOING: '暂无进行中的订单', CONFIRM: '暂无待确认的订单',
  DONE: '暂无已完成的订单', CLOSED: '暂无已关闭的订单'
}

/** 订单状态键 → claim 匹配规则 */
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
    status: '',
    title: '订单',
    emptyTip: '暂无订单',
    list: [],
    loading: false
  },

  onLoad(options) {
    const status = options.status || ''
    this.setData({
      status,
      title: STATUS_TITLE[status] || '订单',
      emptyTip: EMPTY_TIP[status] || '暂无订单'
    })
    this.load()
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh())
  },

  load() {
    this.setData({ loading: true })
    return api.myClaims(1).then(res => {
      const status = this.data.status
      const claims = (res.list || []).filter(c => matchOrder(c, status))
      const list = claims.map(c => Object.assign({}, c, {
        claimStatusText: claimText(c.status),
        taskStatusText: taskText(c.taskStatus),
        tagClass: claimTag(c.status)
      }))
      this.setData({ list, loading: false })
    }).catch(err => {
      this.setData({ loading: false })
      wx.showToast({ title: err.message, icon: 'none' })
    })
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
          this.load()
        }).catch(err => wx.showToast({ title: err.message, icon: 'none' }))
      }
    })
  },

  goPeerReview(e) {
    const { claimid, taskid } = e.currentTarget.dataset
    wx.navigateTo({ url: '/pages/peer-review/peer-review?claimId=' + claimid + '&taskId=' + taskid })
  }
})