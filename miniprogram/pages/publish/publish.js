const api = require('../../utils/api')

function pad(n) { return n < 10 ? '0' + n : '' + n }

Page({
  data: {
    title: '',
    description: '',
    reward: '',
    quota: '1',
    claimDate: '',        // yyyy-MM-dd
    claimTime: '',        // HH:mm
    deadlineDate: '',
    deadlineTime: '',
    submitting: false,
    today: '',
    maxDate: ''
  },

  onLoad() {
    const d = new Date()
    const max = new Date(d.getFullYear() + 1, d.getMonth(), d.getDate())
    this.setData({
      today: d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()),
      maxDate: max.getFullYear() + '-' + pad(max.getMonth() + 1) + '-' + pad(max.getDate())
    })
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [field]: e.detail.value })
  },

  onClaimDate(e) { this.setData({ claimDate: e.detail.value }) },
  onClaimTime(e) { this.setData({ claimTime: e.detail.value }) },
  onDeadlineDate(e) { this.setData({ deadlineDate: e.detail.value }) },
  onDeadlineTime(e) { this.setData({ deadlineTime: e.detail.value }) },

  /** 合成 yyyy-MM-dd HH:mm:ss */
  buildDT(date, time) {
    return (date && time) ? (date + ' ' + time + ':00') : ''
  },

  /** 提交发布 */
  doPublish() {
    const { title, description, reward, quota, claimDate, claimTime, deadlineDate, deadlineTime } = this.data
    if (!title.trim()) return wx.showToast({ title: '请输入标题', icon: 'none' })
    if (!reward || Number(reward) <= 0) return wx.showToast({ title: '请输入正确报酬', icon: 'none' })

    const claimDeadline = this.buildDT(claimDate, claimTime)
    const deadline = this.buildDT(deadlineDate, deadlineTime)
    if (!claimDeadline) return wx.showToast({ title: '请选择接取截止日期与时间', icon: 'none' })
    if (!deadline) return wx.showToast({ title: '请选择完成截止日期与时间', icon: 'none' })
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