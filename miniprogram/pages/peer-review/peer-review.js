const api = require('../../utils/api')

Page({
  data: {
    claimId: null,
    taskId: null,
    toUserId: null,
    toNickname: '',
    taskTitle: '',
    score: 0,
    stars: [1, 2, 3, 4, 5],
    content: '',
    loading: true,
    blocked: false,
    submitting: false
  },

  onLoad(options) {
    this.setData({ claimId: options.claimId, taskId: options.taskId })
    if (options.toUserId) {
      this.setData({
        toUserId: Number(options.toUserId),
        toNickname: options.toNickname ? decodeURIComponent(options.toNickname) : ''
      })
    }
    this.init()
  },

  /** 加载接取详情，确定"对方"身份 */
  init() {
    wx.setNavigationBarTitle({ title: '互评' })
    Promise.all([
      api.claimDetail(this.data.claimId),
      this.data.taskId ? api.taskDetail(this.data.taskId) : Promise.resolve(null)
    ]).then(([detail, taskDetail]) => {
      const claim = detail.claim
      const task = taskDetail || detail.task
      const me = wx.getStorageSync('userInfo')

      // 允许互评条件：接取已通过 或 任务已结算
      const completed = claim.status === 'APPROVED' || (task && task.status === 'SETTLED')
      if (!completed) {
        this.setData({ loading: false, blocked: true })
        return
      }

      let toUserId = this.data.toUserId
      let toNickname = this.data.toNickname
      if (!toUserId) {
        if (me && me.id === claim.userId) {
          // 我是接取者 → 对方是发布者
          toUserId = task.publisherId
          toNickname = taskDetail ? taskDetail.publisherNickname : '发布者'
        } else {
          // 我是发布者 → 对方是接取者
          toUserId = claim.userId
          toNickname = '用户' + claim.userId
        }
      }
      this.setData({
        toUserId,
        toNickname: toNickname || '对方用户',
        taskTitle: task ? task.title : '',
        loading: false
      })
    }).catch(err => {
      this.setData({ loading: false })
      wx.showToast({ title: err.message, icon: 'none' })
    })
  },

  setScore(e) {
    this.setData({ score: Number(e.currentTarget.dataset.s) })
  },

  onContentInput(e) {
    this.setData({ content: e.detail.value })
  },

  submit() {
    if (this.data.score < 1) {
      wx.showToast({ title: '请选择评分', icon: 'none' })
      return
    }
    if (this.data.submitting) return
    this.setData({ submitting: true })
    api.reviewPeer({
      claimId: Number(this.data.claimId),
      toUserId: Number(this.data.toUserId),
      score: this.data.score,
      content: this.data.content.trim()
    }).then(() => {
      wx.showToast({ title: '评价成功', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 1200)
    }).catch(err => {
      this.setData({ submitting: false })
      wx.showToast({ title: err.message, icon: 'none' })
    })
  }
})