const api = require('../../utils/api')

// 协议/隐私政策已移至独立合规页面：pages/agreement、pages/privacy

Page({
  data: {
    loading: false,
    agreed: true,          // 默认勾选协议，保持“一键登录”零摩擦（合规文案以上线版为准）
    statusBarHeight: 44,   // 自定义导航栏状态栏高度（px）
    mode: 'wx',            // wx=微信一键登录 | account=账密登录
    account: '',
    password: ''
  },

  onLoad(options) {
    this.redirect = options.redirect || ''
    // 自定义导航栏：读取状态栏高度
    const win = (wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()) || {}
    this.setData({ statusBarHeight: win.statusBarHeight || 44 })
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

  /** 微信一键登录；code 缺省时用 mock 新用户（生产改为 wx.login 真实 code） */
  async doLogin(code) {
    if (this.data.loading) return
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读并同意《用户协议》与《隐私政策》', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    wx.showLoading({ title: '登录中...' })

    try {
      // 演示账号：xiaomei / xiaoming / new（新用户）；一键登录走 mock 新用户
      const loginCode = (typeof code !== 'string' || code === 'new') ? 'mp-' + Date.now() : code
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