const api = require('../../utils/api')

const TYPE_TEXT = {
  task: '该任务',
  claim: '该接取',
  review: '该评价',
  user: '该用户'
}

Page({
  data: {
    targetType: 'task',
    targetId: null,
    targetTitle: '',
    reason: '',
    submitting: false
  },

  onLoad(options) {
    const targetType = options.targetType || 'task'
    this.setData({
      targetType,
      targetId: options.targetId,
      targetTitle: options.targetTitle ? decodeURIComponent(options.targetTitle) : '',
      typeText: TYPE_TEXT[targetType] || '该对象'
    })
    wx.setNavigationBarTitle({
      title: '举报' + (options.targetTitle ? ' - ' + decodeURIComponent(options.targetTitle).slice(0, 10) : '')
    })
  },

  onReasonInput(e) {
    this.setData({ reason: e.detail.value })
  },

  submit() {
    const reason = this.data.reason.trim()
    if (!reason) {
      wx.showToast({ title: '请填写举报原因', icon: 'none' })
      return
    }
    if (this.data.submitting) return
    this.setData({ submitting: true })
    api.report({
      targetType: this.data.targetType,
      targetId: Number(this.data.targetId),
      reason
    }).then(() => {
      wx.showToast({ title: '举报成功，平台将尽快处理', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 1200)
    }).catch(err => {
      this.setData({ submitting: false })
      wx.showToast({ title: err.message, icon: 'none' })
    })
  }
})