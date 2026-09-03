const api = require('../../utils/api')

const CLAIM_TEXT = {
  CLAIMED: '待提交', SUBMITTED: '待审核', APPROVED: '已通过',
  REJECTED: '已驳回', CANCELLED: '已取消'
}

Page({
  data: {
    taskId: null,
    content: '',
    fileIds: [],
    fileUrls: [],
    claimId: null,
    claimStatus: null,
    loading: true,
    submitting: false
  },

  onLoad(options) {
    this.setData({ taskId: options.taskId })
    this.locateClaim()
  },

  /** 从"我接取的"里找到这个任务的 claim */
  locateClaim() {
    api.myClaims(1).then(res => {
      const list = res.list || []
      const hit = list.find(c => Number(c.taskId) === Number(this.data.taskId))
      if (hit) {
        this.setData({
          claimId: hit.id,
          claimStatus: hit.status,
          claimStatusText: CLAIM_TEXT[hit.status] || hit.status,
          claimTagClass: 'tag-' + hit.status.toLowerCase(),
          loading: false
        })
      } else {
        this.setData({ loading: false })
        wx.showToast({ title: '未找到你的接取记录', icon: 'none' })
      }
    }).catch(err => {
      this.setData({ loading: false })
      wx.showToast({ title: err.message, icon: 'none' })
    })
  },

  onInput(e) {
    this.setData({ content: e.detail.value })
  },

  /** 选择并上传凭证图片 */
  chooseImage() {
    wx.chooseMedia({
      count: 3 - this.data.fileIds.length,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: res => {
        const items = res.tempFiles || []
        items.forEach(item => this.uploadOne(item.tempFilePath))
      }
    })
  },

  /** 单张上传（wx.uploadFile 手动带鉴权头） */
  uploadOne(path) {
    const token = wx.getStorageSync('token')
    if (!token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    wx.showLoading({ title: '上传中...' })
    wx.uploadFile({
      url: getApp().globalData.baseUrl + '/files',
      filePath: path,
      name: 'file',
      formData: { bizType: 'submission' },
      header: { Authorization: 'Bearer ' + token },
      success: res => {
        wx.hideLoading()
        const body = JSON.parse(res.data)
        if (body.code === 0) {
          const file = body.data
          this.setData({
            fileIds: this.data.fileIds.concat([file.id]),
            fileUrls: this.data.fileUrls.concat([file.url])
          })
        } else {
          wx.showToast({ title: body.message || '上传失败', icon: 'none' })
        }
      },
      fail: err => {
        wx.hideLoading()
        wx.showToast({ title: '上传失败', icon: 'none' })
      }
    })
  },

  removeImage(e) {
    const idx = e.currentTarget.dataset.index
    const fileIds = this.data.fileIds.slice()
    const fileUrls = this.data.fileUrls.slice()
    fileIds.splice(idx, 1)
    fileUrls.splice(idx, 1)
    this.setData({ fileIds, fileUrls })
  },

  /** 提交凭证 */
  doSubmit() {
    if (!this.data.content.trim() && !this.data.fileIds.length) {
      return wx.showToast({ title: '请填写文字说明或上传图片', icon: 'none' })
    }
    this.setData({ submitting: true })
    api.submit(this.data.claimId, {
      content: this.data.content,
      fileIds: this.data.fileIds
    }).then(() => {
      wx.showToast({ title: '提交成功', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 800)
    }).catch(err => {
      wx.showToast({ title: err.message, icon: 'none' })
    }).finally(() => {
      this.setData({ submitting: false })
    })
  }
})