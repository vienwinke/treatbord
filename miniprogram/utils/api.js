/**
 * API 接口封装（对应 docs/API_DESIGN.md）
 */
const req = require('./request')

module.exports = {
  // ---- AI 问答 §AI ----
  // 注意：流式问答不走这里（要 enableChunked），见 utils/sse.js；这几个是非流式与会话接口
  aiAsk: (data) => req.post('/ai/ask', {
    session_id: data.session_id, question: data.question, client_msg_id: data.client_msg_id
  }, { auth: true }),
  aiSessions: () => req.get('/ai/sessions', { auth: true }),
  aiMessages: (id) => req.get('/ai/sessions/' + id + '/messages', { auth: true }),
  aiDeleteSession: (id) => req.del('/ai/sessions/' + id, { auth: true }),
  aiFeedback: (data) => req.post('/ai/feedback',
    { message_id: data.message_id, rating: data.rating, comment: data.comment }, { auth: true }),

  // ---- 认证 §2 ----
  login: (code) => req.post('/auth/login', { code }),
  accountLogin: (username, password) => req.post('/auth/account/login', { username, password }),
  logout: () => req.post('/auth/logout', {}, { auth: true }),
  me: () => req.get('/users/me', { auth: true }),
  updateProfile: (data) => req.put('/users/me', data, { auth: true }),
  setCredentials: (data) => req.post('/users/me/credentials', data, { auth: true }),
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
  myClaims: (page, pageSize) => req.get('/me/claims?page=' + (page || 1) + '&pageSize=' + (pageSize || 10), { auth: true }),

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