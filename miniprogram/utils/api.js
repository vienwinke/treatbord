/**
 * API 接口封装（对应 docs/API_DESIGN.md）
 */
const req = require('./request')

module.exports = {
  // ---- 认证 §2 ----
  login: (code) => req.post('/auth/login', { code }),
  logout: () => req.post('/auth/logout', {}, { auth: true }),
  me: () => req.get('/users/me', { auth: true }),
  deleteAccount: () => req.del('/users/me', { auth: true }),

  // ---- 任务 §3 ----
  tasks: (params) => req.get('/tasks?page=' + (params.page || 1) + '&pageSize=' + (params.pageSize || 10) +
    (params.status ? '&status=' + params.status : '') +
    (params.keyword ? '&keyword=' + encodeURIComponent(params.keyword) : '')),
  taskDetail: (id) => req.get('/tasks/' + id),
  createTask: (data) => req.post('/tasks', data, { auth: true }),
  cancelTask: (id) => req.post('/tasks/' + id + '/cancel', {}, { auth: true }),
  myTasks: (page) => req.get('/me/tasks?page=' + (page || 1) + '&pageSize=10', { auth: true }),

  // ---- 接取 §4 ----
  claimTask: (id) => req.post('/tasks/' + id + '/claim', {}, { auth: true }),
  cancelClaim: (id) => req.del('/claims/' + id, { auth: true }),
  myClaims: (page) => req.get('/me/claims?page=' + (page || 1) + '&pageSize=10', { auth: true }),

  // ---- 凭证 §5 ----
  submit: (claimId, data) => req.post('/claims/' + claimId + '/submit', data, { auth: true }),
  claimDetail: (id) => req.get('/claims/' + id, { auth: true }),

  // ---- 审核 §6 ----
  review: (claimId, action, note) => req.post('/claims/' + claimId + '/review',
    { action, note }, { auth: true }),

  // ---- 通知 §8 ----
  notifications: (page) => req.get('/notifications?page=' + (page || 1) + '&pageSize=10', { auth: true }),
  markRead: (id) => req.post('/notifications/' + id + '/read', {}, { auth: true }),
  readAll: () => req.post('/notifications/read-all', {}, { auth: true }),
  unreadCount: () => req.get('/notifications?page=1&pageSize=1&isRead=0', { auth: true }),

  // ---- 互评 §9 / 举报 §10 ----
  reviewPeer: (data) => req.post('/reviews', data, { auth: true }),
  report: (data) => req.post('/reports', data, { auth: true }),

  // ---- 管理端 §11（role=ADMIN） ----
  adminSummary: () => req.get('/admin/stats/summary', { auth: true }),
  adminReports: (status, page) => req.get('/admin/reports?page=' + (page || 1) + '&pageSize=10' +
    (status !== null && status !== undefined ? '&status=' + status : ''), { auth: true }),
  handleReport: (id, status, note) => req.put('/admin/reports/' + id, { status, note }, { auth: true }),
  adminUsers: (status, page) => req.get('/admin/users?page=' + (page || 1) + '&pageSize=10' +
    (status !== null && status !== undefined ? '&status=' + status : ''), { auth: true }),
  adminBan: (id) => req.put('/admin/users/' + id + '/ban', {}, { auth: true }),
  adminUnban: (id) => req.put('/admin/users/' + id + '/unban', {}, { auth: true }),
  adminOfflineTask: (id) => req.put('/admin/tasks/' + id + '/offline', {}, { auth: true })
}