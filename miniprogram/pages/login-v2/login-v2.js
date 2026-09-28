const api = require('../../utils/api')

// 协议/隐私政策已移至独立合规页面：pages/agreement、pages/privacy

/**
 * 上下文提示：登录页是二级页（被其它页面 redirectTo 拦下来），
 * 用 redirect 路径推导"登录后继续做什么"文案。纯前端映射，不改任何接口。
 */
const HINT_RULES = [
  { re: /^\/pages\/detail\//, hint: '登录后继续：接取该任务' },
  { re: /^\/pages\/my-claims\//, hint: '登录后查看我的接取' },
  { re: /^\/pages\/order-list\//, hint: '登录后查看接取列表' },
  { re: /^\/pages\/submit\//, hint: '登录后提交完成凭证' },
  { re: /^\/pages\/notifications\//, hint: '登录后查看通知' },
  { re: /^\/pages\/peer-review\//, hint: '登录后提交互评' },
  { re: /^\/pages\/publish\//, hint: '登录后发布任务' },
  { re: /^\/pages\/admin\//, hint: '登录后进入管理台' }
]

function hintFor(redirect) {
  if (!redirect) return '登录后开始接取任务'
  let path = redirect
  try { path = decodeURIComponent(redirect) } catch (e) { /* 保留原值 */ }
  for (const rule of HINT_RULES) {
    if (rule.re.test(path)) return rule.hint
  }
  return '登录后继续你的操作'
}

Page({
  data: {
    loading: false,
    agreed: false,         // 合规：默认【不勾选】，由用户主动同意（PIPL 要求明示同意）
    statusBarHeight: 44,   // 自定义导航栏状态栏高度（px）
    mode: 'wx',            // wx=微信一键登录 | account=账密登录
    account: '',
    password: '',
    showPassword: false,   // 账密模式下是否明文显示密码
    contextHint: ''        // 由 redirect 推导（见 hintFor）
  },

  onLoad(options) {
    this.redirect = options.redirect || ''
    // 自定义导航栏：读取状态栏高度
    const win = (wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()) || {}
    this.setData({
      statusBarHeight: win.statusBarHeight || 44,
      contextHint: hintFor(this.redirect)
    })
    // 已有登录态则直接进入（带回跳时回跳）
    if (wx.getStorageSync('token')) {
      this.afterLogin()
    }
  },

  /** 登录后去向：有回跳地址回跳，否则进大厅 */
  afterLogin() {
    if (this.redirect) {
      wx.reLaunch({ url: decodeURIComponent(this.redirect) })
    } else {
      wx.switchTab({ url: '/pages/index/index' })
    }
  },

  /** 返回：有上一页则返回；若带 redirect 回跳则回来源页；否则回落任务大厅 */
  goBack() {
    if (getCurrentPages().length > 1) {
      wx.navigateBack()
    } else if (this.redirect) {
      wx.reLaunch({ url: decodeURIComponent(this.redirect) })
    } else {
      wx.switchTab({ url: '/pages/index/index' })
    }
  },

  /** 勾选 / 取消《用户协议》《隐私政策》 */
  toggleAgree() {
    this.setData({ agreed: !this.data.agreed })
  },

  /** 演示账号一键登录（开发联调保留；新版 UI 已移除演示入口） */
  demoLogin(e) {
    this.doLogin(e.currentTarget.dataset.code)
  },

  /**
   * 取登录 code（三档）：
   *   1. 显式传入（演示账号入口）→ 直接用
   *   2. 本地联调（globalData.useMockLogin=true）→ 用【固定】devMockCode
   *      （同设备重复登录 = 同一个 openid = 同一个账号，不再造新用户）
   *   3. 生产 → wx.login() 真实 code（同一微信用户 openid 固定）
   */
  async resolveLoginCode(explicit) {
    if (typeof explicit === 'string' && explicit && explicit !== 'new') {
      return explicit
    }
    const app = getApp()
    if (app.globalData.useMockLogin) {
      return app.globalData.devMockCode || 'dev-local-user'
    }
    const code = await new Promise(resolve => {
      wx.login({ success: r => resolve(r.code || ''), fail: () => resolve('') })
    })
    if (!code) {
      throw new Error('微信登录失败，请重试')
    }
    return code
  },

  /** 微信一键登录 */
  async doLogin(explicitCode) {
    if (this.data.loading) return
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读并同意《用户协议》与《隐私政策》', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    wx.showLoading({ title: '登录中...' })

    try {
      const loginCode = await this.resolveLoginCode(explicitCode)
      const loginResult = await api.login(loginCode)
      getApp().setAuth(loginResult.token, loginResult.user)
      wx.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => {
        this.afterLogin()
      }, 800)
    } catch (e) {
      wx.showToast({ title: e.message, icon: 'none' })
    } finally {
      wx.hideLoading()
      this.setData({ loading: false })
    }
  },

  /* ══ 账密登录 ══ */
  switchAccountLogin() { this.setData({ mode: 'account' }) },
  backWx() { this.setData({ mode: 'wx', account: '', password: '' }) },
  onAccountInput(e) { this.setData({ account: e.detail.value }) },
  onPasswordInput(e) { this.setData({ password: e.detail.value }) },
  /** 切换密码明文/密文显示 */
  togglePassword() { this.setData({ showPassword: !this.data.showPassword }) },
  async doAccountLogin() {
    if (this.data.loading) return
    const { account, password } = this.data
    if (!account.trim()) return wx.showToast({ title: '请输入账号', icon: 'none' })
    if (!password) return wx.showToast({ title: '请输入密码', icon: 'none' })
    this.setData({ loading: true })
    wx.showLoading({ title: '登录中...' })
    try {
      const loginResult = await api.accountLogin(account.trim(), password)
      getApp().setAuth(loginResult.token, loginResult.user)
      wx.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => this.afterLogin(), 800)
    } catch (e) {
      wx.showToast({ title: e.message, icon: 'none' })
    } finally {
      wx.hideLoading()
      this.setData({ loading: false })
    }
  },

  /** 用户协议（跳转完整页面） */
  showAgreement() {
    wx.navigateTo({ url: '/pages/agreement/agreement' })
  },

  /** 隐私政策（跳转完整页面） */
  showPrivacy() {
    wx.navigateTo({ url: '/pages/privacy/privacy' })
  }
})