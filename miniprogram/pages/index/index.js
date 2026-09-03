const api = require('../../utils/api')

const STATUS_TEXT = {
  OPEN: '接取中', IN_PROGRESS: '进行中', REVIEWING: '审核中',
  SETTLED: '已结算', EXPIRED: '已过期', CANCELLED: '已取消'
}

Page({
  data: {
    tasks: [],
    keyword: '',
    currentStatus: '',
    statusOptions: [
      { label: '全部', value: '' },
      { label: '接取中', value: 'OPEN' },
      { label: '进行中', value: 'IN_PROGRESS' },
      { label: '已结算', value: 'SETTLED' }
    ],
    page: 1,
    hasMore: true,
    loading: false,
    firstLoad: true
  },

  onShow() {
    this.refresh()
  },

  onPullDownRefresh() {
    this.refresh().then(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    this.loadMore()
  },

  refresh() {
    this.setData({ page: 1, hasMore: true })
    return this.loadTasks(true)
  },

  loadTasks(reset) {
    if (this.data.loading || !this.data.hasMore) return Promise.resolve()
    this.setData({ loading: true })

    return api.tasks({
      page: this.data.page,
      pageSize: 10,
      status: this.data.currentStatus,
      keyword: this.data.keyword
    }).then(res => {
      const raw = reset ? res.list : this.data.tasks.concat(res.list)
      const tasks = raw.map(t => Object.assign({}, t, {
        statusText: STATUS_TEXT[t.status] || t.status,
        tagClass: 'tag-' + t.status.toLowerCase()
      }))
      this.setData({
        tasks,
        hasMore: raw.length < res.total,
        page: this.data.page + 1,
        loading: false,
        firstLoad: false
      })
    }).catch(err => {
      this.setData({ loading: false, firstLoad: false })
      wx.showToast({ title: err.message, icon: 'none' })
    })
  },

  loadMore() {
    if (!this.data.hasMore) return
    this.loadTasks(false)
  },

  onStatusTap(e) {
    this.setData({ currentStatus: e.currentTarget.dataset.value })
    this.refresh()
  },

  onSearchInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  onSearch() {
    this.refresh()
  },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/detail/detail?id=' + e.currentTarget.dataset.id })
  }
})