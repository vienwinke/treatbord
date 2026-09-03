const api = require('../../utils/api')

Page({
  data: {
    title: '',
    description: '',
    reward: '',
    quota: '1',
    claimDeadline: '',
    deadline: '',
    submitting: false
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [field]: e.detail.value })
  },

  /** 选择接取截止时间 */
  pickClaimDeadline() {
    const now = new Date()
    wx.showModal({
      title: '选择接取截止',
      editable: true,
      placeholderText: '如 2026-09-10 18:00:00',
      content: this.data.claimDeadline,
      success: res => {
        if (res.confirm && res.content) {
          this.setData({ claimDeadline: res.content.trim() })
        }
      }
    })
  },

  /** 选择完成截止时间 */
  pickDeadline() {
    wx.showModal({
      title: '选择完成截止',
      editable: true,
      placeholderText: '如 2026-09-12 18:00:00',
      content: this.data.deadline,
      success: res => {
        if (res.confirm && res.content) {
          this.setData({ deadline: res.content.trim() })
        }
      }
    })
  },

  /** 提交发布 */
  doPublish() {
    const { title, description, reward, quota, claimDeadline, deadline } = this.data
    if (!title.trim()) return wx.showToast({ title: '请输入标题', icon: 'none' })
    if (!reward || Number(reward) <= 0) return wx.showToast({ title: '请输入正确报酬', icon: 'none' })
    if (!claimDeadline || !deadline) return wx.showToast({ title: '请选择截止时间', icon: 'none' })
    if (new Date(claimDeadline.replace(/-/g, '/')) >= new Date(deadline.replace(/-/g, '/'))) {
      return wx.showToast({ title: '完成截止须晚于接取截止', icon: 'none' })
    }

    this.setData({ submitting: true })
    api.createTask({
      title: title.trim(),
      description: description.trim(),
      reward: Number(reward),
      quota: Number(quota),
      claimDeadline,
      deadline
    }).then(id => {
      wx.showToast({ title: '发布成功', icon: 'success' })
      setTimeout(() => {
        wx.switchTab({ url: '/pages/index/index' })
      }, 800)
    }).catch(err => {
      wx.showToast({ title: err.message, icon: 'none' })
    }).finally(() => {
      this.setData({ submitting: false })
    })
  }
})