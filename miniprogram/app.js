App({
  globalData: {
    token: null,
    userInfo: null,
    // 后端地址（本地联调：Windows 侧连 WSL 直连 IP，绕过 localhost 转发失效问题）
    // 注意：WSL 重启后 IP 可能变化，用 `hostname -I` 查询最新值再改
    baseUrl: 'http://172.28.58.76:8080/api',

    // ⚠️ 登录模式开关
    //   true  = 本地联调：登录用【固定 mock code】，不调 wx.login（后端 WX_LOGIN_ENABLED=false 时 code 直映射 openid）
    //   false = 生产：调 wx.login() 取真实 code（同一微信用户 openid 固定不变）
    // 生产上线前必须改为 false，否则后端 code2session 会因假 code 失败
    useMockLogin: true,
    // 固定 mock code：保证【同一设备重复登录命中同一个账号】
    // （原实现用 'mp-' + Date.now() 每次都换 code ⇒ 每次登录都新建用户，是垃圾账号的根因）
    devMockCode: 'dev-local-user'
  },

  onLaunch() {
    const token = wx.getStorageSync('token')
    if (token) {
      this.globalData.token = token
    }
  },

  /** 保存登录态 */
  setAuth(token, userInfo) {
    this.globalData.token = token
    this.globalData.userInfo = userInfo
    wx.setStorageSync('token', token)
    wx.setStorageSync('userInfo', userInfo)
  },

  logout() {
    this.globalData.token = null
    this.globalData.userInfo = null
    wx.removeStorageSync('token')
    wx.removeStorageSync('userInfo')
  }
})