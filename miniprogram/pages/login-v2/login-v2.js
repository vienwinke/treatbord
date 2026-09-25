const api = require('../../utils/api')

// 协议占位文案（MVP；上线前需按平台模板补全文案，见 docs/SECURITY_REVIEW.md §2）
const AGREEMENT = '欢迎使用 Treatbord 任务接取平台。\n\n' +
  '1. 本平台仅提供任务发布与接取的信息撮合服务，交易双方自行确认协作方式与结算。\n' +
  '2. 请勿发布违法违规、诈骗、赌博类任务；接取者需如实提交完成凭证。\n' +
  '3. 平台对违规内容可采取下架、封禁等处置，并保留账号注销（含数据匿名化）权利。\n\n' +
  '（详细条款以正式上线的完整版《用户协议》为准）'

const PRIVACY = '我们仅收集提供服务所必需的个人信息：\n\n' +
  '1. 微信登录标识（openid，用于账号识别，不做其他用途）；\n' +
  '2. 您主动填写的昵称、头像；发布/接取的任务内容。\n\n' +
  '您的数据仅用于本平台功能，不向第三方出售或共享。可随时注销账号，' +
  '注销后个人数据将被匿名化处理。\n\n' +
  '（详细条款以正式上线的完整版《隐私政策》为准）'

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

  /** 用户协议 */
  showAgreement() {
    wx.showModal({
      title: '用户协议',
      content: AGREEMENT,
      showCancel: false,
      confirmText: '知道了',
      confirmColor: '#3478F6'
    })
  },

  /** 隐私政策 */
  showPrivacy() {
    wx.showModal({
      title: '隐私政策',
      content: PRIVACY,
      showCancel: false,
      confirmText: '知道了',
      confirmColor: '#3478F6'
    })
  }
})