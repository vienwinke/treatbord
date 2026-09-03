const api = require('../../utils/api')

// 通知类型 → 图标与文案
const TYPE_META = {
  CLAIMED: { icon: '📥', text: '接取通知' },
  SUBMITTED: { icon: '📝', text: '凭证提交' },
  REVIEW_RESULT: { icon: '✅', text: '审核结果' },
  AUTO_APPROVED: { icon: '⏱️', text: '自动通过' },
  TASK_EXPIRED: { icon: '⏰', text: '任务过期' },
  TASK_OFFLINE: { icon: '🚫', text: '任务下架' }
}

Page({
  data: {
    list: [],
    loading: false,
    total: 0
  },

  onShow() {
    if (!wx.getStorageSync('token')) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.load()
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh())
  },

  load() {
    this.setData({ loading: true })
    return api.notifications(1).then(res => {
      const list = res.list.map(n => Object.assign({}, n, {
        typeText: (TYPE_META[n.type] || {}).text || n.type,
        typeIcon: (TYPE_META[n.type] || {}).icon || '🔔',
        timeText: (n.createTime || '').slice(5, 16),
        unreadClass: n.isRead === 0 ? 'unread' : ''
      }))
      this.setData({ list, total: res.total, loading: false })
    }).catch(err => {
      this.setData({ loading: false })
      wx.showToast({ title: err.message, icon: 'none' })
    })
  },

  /** 标记已读 + 跳转关联业务 */
  goDetail(e) {
    const ds = e.currentTarget.dataset
    const done = () => {
      if (ds.biz) {
        wx.navigateTo({ url: '/pages/detail/detail?id=' + ds.biz })
      }
    }
    if (ds.read === 0) {
      api.markRead(ds.id)
        .then(() => done())
        .catch(err => wx.showToast({ title: err.message, icon: 'none' }))
    } else {
      done()
    }
  },

  /** 全部已读 */
  readAll() {
    wx.showModal({
      title: '全部已读',
      content: '确定将所有通知标记为已读吗？',
      success: res => {
        if (!res.confirm) return
        api.readAll().then(() => {
          wx.showToast({ title: '已全部标记已读', icon: 'success' })
          this.load()
        }).catch(err => wx.showToast({ title: err.message, icon: 'none' }))
      }
    })
  }
})