const api = require('../../utils/api')

const CLAIM_TEXT = {
  CLAIMED: '待提交', SUBMITTED: '待审核', APPROVED: '已通过',
  REJECTED: '已驳回', CANCELLED: '已取消'
}

Page({
  data: {
    taskId: null,
    claims: [],
    loading: true
  },

  onLoad(options) {
    this.setData({ taskId: options.taskId })
    this.load()
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh())
  },

  load() {
    api.get('/tasks/' + this.data.taskId + '/claims', { auth: true })
      .then(list => {
        const claims = list.map(c => Object.assign({}, c, {
          claimStatusText: CLAIM_TEXT[c.status] || c.status,
          tagClass: 'tag-' + c.status.toLowerCase()
        }))
        this.setData({ claims, loading: false })
      })
      .catch(err => {
        this.setData({ loading: false })
        wx.showToast({ title: err.message, icon: 'none' })
      })
  },

  /** 去互评（接取通过后，发布者评价接取者） */
  goPeerReview(e) {
    const { id, touid, tonickname } = e.currentTarget.dataset
    wx.navigateTo({
      url: '/pages/peer-review/peer-review?claimId=' + id +
        '&taskId=' + this.data.taskId +
        '&toUserId=' + touid +
        '&toNickname=' + encodeURIComponent(tonickname || '')
    })
  },

  /** 审核通过/驳回 */
  doReview(e) {
    const { id, action } = e.currentTarget.dataset
    wx.showModal({
      title: action === 'approve' ? '审核通过' : '驳回凭证',
      content: action === 'approve' ? '确认通过该接取并进入结算？' : '确认驳回该凭证？',
      editable: action === 'reject',
      placeholderText: action === 'reject' ? '填写驳回原因（选填）' : '',
      success: res => {
        if (!res.confirm) return
        api.review(id, action, res.content || '')
          .then(() => {
            wx.showToast({ title: '已处理', icon: 'success' })
            this.load()
          })
          .catch(err => wx.showToast({ title: err.message, icon: 'none' }))
      }
    })
  }
})