/**
 * SSE 流式请求封装（小程序端）。
 *
 * 为什么要自己写：`wx.request` 不支持标准 SSE 消费，得用 `enableChunked: true` +
 * `onChunkReceived` 拿原始 ArrayBuffer。而 `onChunkReceived` 给的是**任意切分**的字节块 ——
 * 半个中文、半个帧都是常态。
 *
 * 关键设计：**按字节分帧，只在完整帧上做 UTF-8 解码**。
 * 如果先解码再分帧，遇到"一个中文字被切成两块"就会出乱码（替换字符）；
 * 先按 `\n\n` 切出完整帧（这两字节都是 ASCII，绝不会被切开语义），再解码就没有这个问题。
 * 为此也没有用 `TextDecoder`（小程序多数版本没有），自己实现 `utf8Decode`。
 */

const app = () => getApp()

/** Uint8Array → 字符串（按 UTF-8 解；非法字节用 U+FFFD 替换，不抛异常） */
function utf8Decode(bytes) {
  let out = ''
  for (let i = 0; i < bytes.length;) {
    const b0 = bytes[i]
    let cp
    let len
    if (b0 < 0x80) { cp = b0; len = 1 } else if (b0 < 0xc0) { out += '\ufffd'; i += 1; continue } else if (b0 < 0xe0) { cp = b0 & 0x1f; len = 2 } else if (b0 < 0xf0) { cp = b0 & 0x0f; len = 3 } else if (b0 < 0xf8) { cp = b0 & 0x07; len = 4 } else { out += '\ufffd'; i += 1; continue }

    if (i + len > bytes.length) { out += '\ufffd'; break }
    let ok = true
    for (let k = 1; k < len; k++) {
      const bk = bytes[i + k]
      if ((bk & 0xc0) !== 0x80) { ok = false; break }
      cp = (cp << 6) | (bk & 0x3f)
    }
    if (!ok) { out += '\ufffd'; i += 1; continue }

    if (cp > 0xffff) {
      cp -= 0x10000
      out += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff))
    } else {
      out += String.fromCharCode(cp)
    }
    i += len
  }
  return out
}

/**
 * 找帧边界（空行）：同时认 LF LF 与 LF CR LF。
 *
 * 为什么两种都要认：Spring 的 SseEmitter 写的是 `\n`，但**任何中间层**（网关/代理、
 * 某些容器）都可能把它改成 CRLF。只认 `\n\n` 的话，CRLF 流会"一帧都切不出来"——
 * 表现就是"文字全部堆在最后一次性蹦出来"，现场极难定位。
 *
 * 返回 { dataEnd, next }：帧内容 = [0, dataEnd)，下一帧起点 = next。
 */
function frameBoundary(bytes, from) {
  for (let i = from || 0; i < bytes.length; i++) {
    if (bytes[i] !== 0x0a) continue
    if (bytes[i + 1] === 0x0a) return { dataEnd: i, next: i + 2 }
    if (bytes[i + 1] === 0x0d && bytes[i + 2] === 0x0a) return { dataEnd: i, next: i + 3 }
  }
  return null
}

/** 按字节累积、按空行切帧的分帧器（跨 chunk 保留残留） */
function createByteFramer() {
  let buf = new Uint8Array(0)
  return {
    /** 喂一块 ArrayBuffer，返回本次切出的**完整帧**（已解码为字符串） */
    push(chunk) {
      const bytes = chunk instanceof Uint8Array ? chunk : new Uint8Array(chunk)
      const merged = new Uint8Array(buf.length + bytes.length)
      merged.set(buf, 0)
      merged.set(bytes, buf.length)
      buf = merged

      const frames = []
      let boundary = frameBoundary(buf, 0)
      while (boundary) {
        frames.push(utf8Decode(buf.slice(0, boundary.dataEnd)))
        buf = buf.slice(boundary.next)
        boundary = frameBoundary(buf, 0)
      }
      return frames
    },
    /** 收尾：把残留（没有以空行结束的最后一段）也吐出来 */
    flush() {
      const rest = buf.length ? utf8Decode(buf) : ''
      buf = new Uint8Array(0)
      return rest
    }
  }
}

/** 单帧文本 → { event, data }；注释帧（`:` 开头，如 `: connected`）返回 event 为 null */
function parseFrame(text) {
  let event = null
  const dataLines = []
  text.split('\n').forEach((raw) => {
    const line = raw.replace(/\r$/, '')
    if (!line || line.charAt(0) === ':') return
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    if (value.charAt(0) === ' ') value = value.slice(1)
    if (field === 'event') event = value
    else if (field === 'data') dataLines.push(value)
  })
  return { event, data: dataLines.join('\n') }
}

/** 把 data 解析成对象（失败就给空对象，绝不让 UI 因为脏数据崩掉） */
function parseData(data) {
  if (!data) return {}
  try {
    return JSON.parse(data)
  } catch (e) {
    return { _raw: data }
  }
}

/**
 * 发起流式问答。
 * @param {string} path  形如 '/ai/chat'
 * @param {object} data  请求体
 * @param {object} handlers { onEvent({event,data,json}), onError(Error), onDone({statusCode}) }
 * @returns {object|null} 可用于 abort 的 requestTask
 */
function sse(path, data, handlers) {
  const opts = handlers || {}
  const token = wx.getStorageSync('token')
  if (!token) {
    const err = new Error('未登录')
    err.code = 401
    if (opts.onError) opts.onError(err)
    return null
  }

  const framer = createByteFramer()
  let finished = false
  const finish = (payload) => {
    if (finished) return
    finished = true
    if (opts.onDone) opts.onDone(payload || {})
  }

  const task = wx.request({
    url: app().globalData.baseUrl + path,
    method: 'POST',
    data,
    header: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
    enableChunked: true,
    success(res) {
      // 非 200：后端可能直接回 JSON（401/503 等），当作错误处理
      if (res.statusCode !== 200) {
        let body = res.data
        if (body instanceof ArrayBuffer) body = utf8Decode(new Uint8Array(body))
        let message = '请求失败(' + res.statusCode + ')'
        let code = res.statusCode
        try {
          const parsed = typeof body === 'string' ? JSON.parse(body) : body
          if (parsed && parsed.message) message = parsed.message
          if (parsed && parsed.code !== undefined) code = parsed.code
        } catch (e) { /* 保持默认文案 */ }
        const err = new Error(message)
        err.code = code
        if (opts.onError) opts.onError(err)
        finish({ statusCode: res.statusCode })
        return
      }

      // 正常收尾：把残留帧也处理掉
      const tail = framer.flush()
      if (tail && opts.onEvent) {
        const frame = parseFrame(tail)
        if (frame.event) opts.onEvent({ event: frame.event, data: frame.data, json: parseData(frame.data) })
      }
      finish({ statusCode: res.statusCode })
    },
    fail(err) {
      if (opts.onError) opts.onError(err)
      finish({ failed: true })
    }
  })

  // 逐块解析：只有完整帧才会回调 onEvent
  if (task && typeof task.onChunkReceived === 'function') {
    task.onChunkReceived((res) => {
      framer.push(res.data).forEach((text) => {
        const frame = parseFrame(text)
        if (frame.event && opts.onEvent) {
          opts.onEvent({ event: frame.event, data: frame.data, json: parseData(frame.data) })
        }
      })
    })
  }
  return task
}

/** 当前基础库是否支持分块接收（不支持就退回非流式 /ai/ask） */
function supportsChunked() {
  try {
    return wx.canIUse('request.object.enableChunked')
  } catch (e) {
    return false
  }
}

module.exports = {
  sse,
  supportsChunked,
  // 下面这些导出只为了可测试（Node 下 require 后直接跑断言）
  utf8Decode,
  parseFrame,
  parseData,
  createByteFramer
}
