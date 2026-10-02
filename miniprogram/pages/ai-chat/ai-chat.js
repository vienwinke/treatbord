/**
 * AI 数据问答页。
 *
 * 两件事必须做对：
 * 1. **流式**：优先 `enableChunked` 打字机（首帧即时反馈）；基础库不支持时自动退回
 *    非流式 `/ai/ask`，绝不能白屏。
 * 2. **身份**：session/token 全由后端处理，前端不构造也不传 user_id（契约 §2.2）。
 */
const api = require('../../utils/api')
const sse = require('../../utils/sse')

const SESSION_KEY = 'ai_session_id'

function newSessionId() {
  return 's_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 8)
}

Page({
  data: {
    messages: [],
    input: '',
    sending: false,
    sessionId: '',
    sessions: [],
    drawerOpen: false,
    scrollTo: '',
    canStream: true,
    samples: [
      '我接取的任务里有多少已完成？',
      '最近 7 天每天新增的接取数量',
      '平台一共有多少个任务？'
    ]
  },

  onLoad() {
    if (!wx.getStorageSync('token')) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    let sid = wx.getStorageSync(SESSION_KEY)
    if (!sid) {
      sid = newSessionId()
      wx.setStorageSync(SESSION_KEY, sid)
    }
    this.setData({ sessionId: sid, canStream: sse.supportsChunked() })
  },

  onUnload() {
    if (this.task && this.task.abort) this.task.abort()
  },

  // ---------------------------------------------------------------- 输入与发送
  onInput(e) {
    this.setData({ input: e.detail.value })
  },

  onSample(e) {
    this.send(e.currentTarget.dataset.q)
  },

  onSend() {
    this.send()
  },

  send(preset) {
    const question = String(preset || this.data.input || '').trim()
    if (!question) return
    if (this.data.sending) return

    const messages = this.data.messages.concat([
      { key: 'u' + Date.now(), role: 'user', text: question },
      {
        key: 'a' + Date.now(), role: 'assistant', text: '', streaming: true,
        tables: [], citations: [], denied: false, error: '',
        scopeReason: '', elapsed: 0, tokens: 0, messageId: 0, rated: 0
      }
    ])
    const idx = messages.length - 1
    this.setData({ messages, input: '', sending: true, scrollTo: 'msg-bottom' })

    const patch = (obj) => {
      const keyed = {}
      Object.keys(obj).forEach((k) => { keyed['messages[' + idx + '].' + k] = obj[k] })
      this.setData(keyed)
    }
    this.patchCurrent = patch

    if (!this.data.canStream) {
      this.askOnce(question, patch)
      return
    }
    this.task = sse.sse('/ai/chat', {
      session_id: this.data.sessionId,
      question,
      client_msg_id: 'm_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 6)
    }, {
      onEvent: (frame) => this.handleFrame(frame, idx, patch),
      onError: (err) => patch({ error: this.friendly(err), streaming: false }),
      onDone: () => this.finish()
    })
  },

  /** 非流式兜底：基础库不支持 enableChunked 时用 */
  askOnce(question, patch) {
    api.aiAsk({ session_id: this.data.sessionId, question })
      .then((d) => patch({
        text: d.answer || '(没有答案)',
        // 兜底路径也要把表格渲染出来：/api/ai/ask 返回的就是 table 事件的完整载荷
        tables: (d.tables || []).map((t) => ({
          columns: t.columns || [],
          rows: t.rows || [],
          rowCount: t.row_count || 0,
          truncated: !!t.truncated,
          masked: t.masked_columns || []
        })),
        elapsed: d.elapsed_ms || 0,
        tokens: d.tokens || 0,
        streaming: false
      }))
      .catch((e) => patch({ error: this.friendly(e), streaming: false }))
      .then(() => this.finish())
  },

  finish() {
    this.setData({ sending: false })
    this.patchCurrent && this.patchCurrent({ streaming: false })
    this.loadSessions()
  },

  /** 处理一个 SSE 事件（事件名与契约 §2.3 一一对应） */
  handleFrame(frame, idx, patch) {
    const d = frame.json || {}
    const current = this.data.messages[idx] || {}
    switch (frame.event) {
      case 'scope':
        if (d.allowed === false) {
          patch({ denied: true, scopeReason: d.reason || '', text: current.text || '' })
        }
        break
      case 'delta':
        patch({ text: (current.text || '') + (d.text || '') })
        break
      case 'table':
        patch({
          tables: (current.tables || []).concat([{
            columns: d.columns || [],
            rows: d.rows || [],
            rowCount: d.row_count || 0,
            truncated: !!d.truncated,
            masked: d.masked_columns || []
          }])
        })
        break
      case 'citations':
        patch({ citations: current.citations || d || [] })
        break
      case 'saved':
        patch({ messageId: d.message_id || 0 })
        break
      case 'done':
        patch({
          streaming: false,
          elapsed: d.elapsed_ms || 0,
          tokens: d.tokens || 0,
          denied: !!d.denied
        })
        break
      case 'error':
        patch({ error: d.message || '出错了', streaming: false })
        break
      default:
        break
    }
  },

  /** 把后端错误码翻译成用户能懂的话（别把 502/JSON 直接糊到脸上） */
  friendly(err) {
    const code = err && err.code
    if (code === 401) return '登录已失效，请重新登录'
    if (code === 429) return '当前提问较多，请稍后重试'
    if (code === 503) return 'AI 问答未启用（后端未配置）'
    const msg = (err && (err.errMsg || err.message)) || ''
    if (msg.indexOf('timeout') > -1) return '超时了，请重试'
    if (msg.indexOf('domain list') > -1) {
      return '请求被拦截：请勾选开发者工具「不校验合法域名」'
    }
    return msg || '请求失败'
  },

  // ---------------------------------------------------------------- 会话抽屉
  toggleDrawer() {
    const open = !this.data.drawerOpen
    this.setData({ drawerOpen: open })
    if (open) this.loadSessions()
  },

  loadSessions() {
    if (!this.data.sessions.length && !this.data.drawerOpen) return
    api.aiSessions()
      .then((list) => this.setData({ sessions: list || [] }))
      .catch(() => { /* 会话列表失败不影响问答 */ })
  },

  newSession() {
    const sid = newSessionId()
    wx.setStorageSync(SESSION_KEY, sid)
    this.setData({ sessionId: sid, messages: [], drawerOpen: false })
  },

  openSession(e) {
    const id = e.currentTarget.dataset.id
    api.aiMessages(id)
      .then((list) => {
        const messages = (list || []).map((m, i) => ({
          key: 'h' + id + '_' + i,
          role: m.role,
          text: m.content || '',
          tables: [], citations: [], denied: false, error: '',
          scopeReason: '', elapsed: (m.payload && m.payload.elapsed_ms) || 0,
          tokens: (m.payload && m.payload.tokens) || 0,
          messageId: 0, rated: 0, streaming: false
        }))
        this.setData({ messages, drawerOpen: false, scrollTo: 'msg-bottom' })
      })
      .catch((err) => wx.showToast({ title: this.friendly(err), icon: 'none' }))
  },

  removeSession(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '删除会话',
      content: '该会话的消息会一并删除，不可恢复',
      success: (res) => {
        if (!res.confirm) return
        api.aiDeleteSession(id)
          .then(() => {
            if (String(id) === String(this.data.sessionId)) this.newSession()
            this.loadSessions()
            wx.showToast({ title: '已删除', icon: 'none' })
          })
          .catch((err) => wx.showToast({ title: this.friendly(err), icon: 'none' }))
      }
    })
  },

  // ---------------------------------------------------------------- 反馈
  rate(e) {
    const idx = e.currentTarget.dataset.idx
    const rating = Number(e.currentTarget.dataset.rating)
    const msg = this.data.messages[idx]
    if (!msg || !msg.messageId) {
      wx.showToast({ title: '该回答未关联消息（未开启会话存储）', icon: 'none' })
      return
    }
    const keyed = {}
    keyed['messages[' + idx + '].rated'] = rating
    this.setData(keyed)
    api.aiFeedback({ message_id: msg.messageId, rating })
      .then(() => wx.showToast({ title: '感谢反馈', icon: 'none' }))
      .catch(() => wx.showToast({ title: '反馈失败', icon: 'none' }))
  }
})
