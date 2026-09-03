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
  '您的数据仅用于本平台功能，不向第三方出售或共享。可随时在「我的」页注销账号，' +
  '注销后个人数据将被匿名化处理。\n\n' +
  '（详细条款以正式上线的完整版《隐私政策》为准）'

Page({
  data: {
    loading: false
  },

  onLoad(options) {
    this.redirect = options.redirect || ''
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

  /** 用户协议 */
  showAgreement() {
    wx.showModal({
      title: '用户协议',
      content: AGREEMENT,
      showCancel: false,
      confirmText: '知道了',
      confirmColor: '#4A90D9'
    })
  },

  /** 隐私政策 */
  showPrivacy() {
    wx.showModal({
      title: '隐私政策',
      content: PRIVACY,
      showCancel: false,
      confirmText: '知道了',
      confirmColor: '#4A90D9'
    })
  },

  /** 演示账号一键登录（开发联调） */
  demoLogin(e) {
    this.doLogin(e.currentTarget.dataset.code)
  },

  /** 微信一键登录；code 缺省时用 mock 新用户（生产改为 wx.login 真实 code） */
  async doLogin(code) {
    if (this.data.loading) return
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
  }
})