App({
  globalData: {
    token: null,
    userInfo: null,
    // 后端地址（本地联调：Windows 侧连 WSL 直连 IP，绕过 localhost 转发失效问题）
    // 注意：WSL 重启后 IP 可能变化，用 `hostname -I` 查询最新值再改
    baseUrl: 'http://172.28.58.76:8080/api'
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