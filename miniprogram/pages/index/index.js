const api = require('../../utils/api')

const PAGE_SIZE = 10

/* 任务态文案（大厅/发布者视角）：待接取 → 进行中 → 待确认 → 已完成；异常收口为已关闭 */
const TASK_STATUS = {
  OPEN: '待接取', IN_PROGRESS: '进行中', REVIEWING: '待确认',
  SETTLED: '已完成', EXPIRED: '已关闭', CANCELLED: '已关闭'
}
function taskText(s) { return TASK_STATUS[s] || (s || '') }
function taskTag(s) { return 'tag-' + String(s).toLowerCase() }

/** 金额格式化：50 -> 50.00 */
function formatMoney(n) {
  const v = Number(n)
  return isNaN(v) ? '0.00' : v.toFixed(2)
}

/** 取 yyyy-MM-dd HH:mm 的 MM-dd HH:mm，便于阅读。兼容后端 ISO 格式（2026-09-10T18:00:00）与空格格式 */
function shortDate(s) {
  if (!s) return ''
  const str = String(s).replace('T', ' ')
  return str.substring(5, 16)
}

Page({
  data: {
    tasks: [],
    keyword: '',
    currentStatus: '',             // 默认展示全部任务；已关闭(过期/取消)在「全部」内展示
    statusOptions: [
      { label: '全部', value: '' },
      { label: '待接取', value: 'OPEN' },
      { label: '进行中', value: 'IN_PROGRESS' },
      { label: '待确认', value: 'REVIEWING' },
      { label: '已完成', value: 'SETTLED' }
    ],
    page: 1,
    hasMore: true,
    loading: false,
    firstLoad: true,
    emptyText: '暂无任务'
  },

  onLoad() {
    // 首次 onShow 紧跟 onLoad，用 _ready 跳过首帧，避免重复拉取
    this._ready = false
    this.refresh()
  },

  onShow() {
    if (this._ready) {
      this.refresh()
    } else {
      this._ready = true
    }
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
      pageSize: PAGE_SIZE,
      status: this.data.currentStatus,
      keyword: this.data.keyword
    }).then(res => {
      const listData = (reset ? [] : this.data.tasks).concat(res.list || [])
      const tasks = listData.map(t => Object.assign({}, t, {
        statusText: taskText(t.status),
        tagClass: taskTag(t.status),
        rewardText: formatMoney(t.reward),
        claimDeadlineText: shortDate(t.claimDeadline),
        isFull: t.quota > 0 && t.claimedCount >= t.quota
      }))
      const total = res.total || 0
      this.setData({
        tasks,
        // 修复：按累计加载数量与 total 比较，避免 total 恰为 pageSize 整数倍时死循环翻空页
        hasMore: listData.length < total,
        page: this.data.page + 1,
        loading: false,
        firstLoad: false,
        emptyText: this.data.keyword ? '没有找到相关任务' : '暂无任务'
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