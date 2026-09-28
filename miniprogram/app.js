App({
  globalData: {
    token: null,
    userInfo: null,
    // 后端地址（开发环境按候选列表自动探测，见 probeBackend）
    //   - 实测本机 WSL2 的 IPv4 回环转发失效，只有 IPv6 回环通 → localhost 默认解析到 ::1，可用
    //   - WSL 重启后直连 IP 会变，故把它作为兜底候选（`hostname -I` 可查最新值）
    // 生产：改成你的备案域名，例如 https://api.example.com/api
    baseUrl: 'http://localhost:8080/api',
    baseCandidates: [
      'http://localhost:8080/api',
      'http://172.28.58.76:8080/api'
    ],

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
    this.probeBackend()
  },

  /**
   * 依次探测候选后端地址，选中第一个可用的（仅开发便利；全部失败则保持默认值）。
   * 解决两个联调坑：Windows 侧 IPv4 回环不通、WSL 重启后直连 IP 变化。
   */
  probeBackend() {
    const list = this.globalData.baseCandidates || []
    const tryNext = i => {
      if (i >= list.length) return
      wx.request({
        url: list[i] + '/tasks?page=1&pageSize=1',
        method: 'GET',
        timeout: 1500,
        success: res => {
          if (res.statusCode === 200) {
            this.globalData.baseUrl = list[i]
            console.log('[app] 后端地址已就绪:', list[i])
          } else {
            tryNext(i + 1)
          }
        },
        fail: () => tryNext(i + 1)
      })
    }
    tryNext(0)
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