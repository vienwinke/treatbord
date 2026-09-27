const api = require('../../utils/api')

const CLAIM_STATUS = {
  CLAIMED: '进行中', SUBMITTED: '待确认', APPROVED: '已完成',
  REJECTED: '已关闭', CANCELLED: '已关闭'
}
function claimText(s) { return CLAIM_STATUS[s] || (s || '') }
function claimTag(s) { return 'tag-' + String(s).toLowerCase() }

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
          claimStatusText: claimText(c.status),
          tagClass: claimTag(c.status)
        }))
        this.setData({ claims, loading: false })
      })
      .catch(err => {
        this.setData({ loading: false })
        wx.showToast({ title: err.message, icon: 'none' })
      })
  },

  /**
   * 图片加载失败兜底：签名 URL 有效期 30 分钟，过期后服务端返回 403，
   * <image> 只会静默失败。这里重新拉一次列表（拿新签名 URL）并重试一次；
   * 用 _imgRetried 防止反复失败时进入死循环。
   */
  onImgError(e) {
    if (this._imgRetried) return
    this._imgRetried = true
    console.warn('[review] 凭证图加载失败（可能签名 URL 已过期），重新拉取', e && e.detail)
    this.load().then(() => { this._imgRetried = false }).catch(() => { this._imgRetried = false })
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