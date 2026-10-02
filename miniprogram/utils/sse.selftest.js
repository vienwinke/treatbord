/**
 * sse.js 的自检（纯函数，不依赖微信环境）：`node utils/sse.selftest.js`
 *
 * 为什么值得单独跑：流式解析最容易翻车的地方都在这里 ——
 * 中文被切成两半、一个 chunk 含多帧、CRLF、注释帧、脏 JSON。
 * 这些一旦写错，现象是"偶尔出现乱码/偶尔丢事件"，很难在现场复现。
 */
const assert = require('assert')
const sse = require('./sse')

let passed = 0
function it(name, fn) {
  try {
    fn()
    passed += 1
    console.log('  ✓ ' + name)
  } catch (e) {
    console.error('  ✗ ' + name + '\n    ' + e.message)
    process.exitCode = 1
  }
}

const bytes = (str) => Buffer.from(str, 'utf8')

it('utf8Decode：ASCII / 中文 / emoji 都能解', () => {
  assert.strictEqual(sse.utf8Decode(bytes('hello')), 'hello')
  assert.strictEqual(sse.utf8Decode(bytes('你接了 8 个任务')), '你接了 8 个任务')
  assert.strictEqual(sse.utf8Decode(bytes('✅🎉')), '✅🎉')
})

it('utf8Decode：非法字节替换为 U+FFFD，不抛异常', () => {
  assert.strictEqual(sse.utf8Decode(new Uint8Array([0xff, 0x41])), '\ufffdA')
})

it('分帧：一个 chunk 含多帧', () => {
  const f = sse.createByteFramer()
  const frames = f.push(bytes('event: meta\ndata: {"a":1}\n\nevent: done\ndata: {"b":2}\n\n'))
  assert.strictEqual(frames.length, 2)
  assert.strictEqual(sse.parseFrame(frames[0]).event, 'meta')
  assert.strictEqual(sse.parseFrame(frames[1]).event, 'done')
})

it('分帧：帧被切成两个 chunk（半包）', () => {
  const f = sse.createByteFramer()
  assert.deepStrictEqual(f.push(bytes('event: meta\ndata: {"a"')), [])
  const frames = f.push(bytes(':1}\n\n'))
  assert.strictEqual(frames.length, 1)
  assert.deepStrictEqual(sse.parseData(sse.parseFrame(frames[0]).data), { a: 1 })
})

it('分帧：中文字符被切成两半也不乱码（关键用例）', () => {
  const f = sse.createByteFramer()
  const whole = bytes('event: delta\ndata: {"text":"共有 8 个任务"}\n\n')
  // 程序化找一个**落在多字节字符内部**的切点（续字节 10xxxxxx），
  // 而不是写死一个下标 —— 写死的话很容易变成"其实没切在字符中间"的假绿用例
  let cut = -1
  for (let i = 1; i < whole.length; i++) {
    if ((whole[i] & 0xc0) === 0x80) { cut = i; break }
  }
  assert.ok(cut > 0, '测试用例本身失效：没找到多字节字符内部的切点')

  assert.deepStrictEqual(f.push(whole.slice(0, cut)), [])
  const frames = f.push(whole.slice(cut))
  assert.strictEqual(frames.length, 1)
  assert.deepStrictEqual(sse.parseData(sse.parseFrame(frames[0]).data), { text: '共有 8 个任务' })
})

it('分帧：CRLF 换行也能处理', () => {
  const f = sse.createByteFramer()
  const frames = f.push(bytes('event: meta\r\ndata: {"ok":true}\r\n\r\n'))
  assert.strictEqual(frames.length, 1)
  assert.deepStrictEqual(sse.parseData(sse.parseFrame(frames[0]).data), { ok: true })
})

it('分帧：flush 吐残留（服务端没以空行收尾）', () => {
  const f = sse.createByteFramer()
  f.push(bytes('event: done\ndata: {"end":1}'))
  const tail = sse.parseFrame(f.flush())
  assert.strictEqual(tail.event, 'done')
  assert.strictEqual(f.flush(), '', 'flush 之后必须清空，避免重复处理')
})

it('parseFrame：注释帧（: connected）不算事件', () => {
  const frame = sse.parseFrame(': connected\n\n')
  assert.strictEqual(frame.event, null)
})

it('parseFrame：data 里含冒号与多行 data', () => {
  const frame = sse.parseFrame('event: sql\ndata: {"has_sql":true}\ndata: 第二行')
  assert.strictEqual(frame.event, 'sql')
  assert.strictEqual(frame.data, '{"has_sql":true}\n第二行')
})

it('parseFrame：`event:meta` 无空格也认', () => {
  assert.strictEqual(sse.parseFrame('event:meta\ndata:{}').event, 'meta')
})

it('parseData：脏 JSON 不抛异常', () => {
  assert.deepStrictEqual(sse.parseData('{坏数据'), { _raw: '{坏数据' })
  assert.deepStrictEqual(sse.parseData(''), {})
})

it('端到端：模拟边车完整帧序列（含注释帧与逐字 delta）', () => {
  const f = sse.createByteFramer()
  const wire = ': connected\n\nevent: meta\ndata: {"trace_id":"t1"}\n\n' +
    'event: scope\ndata: {"scope":"SELF","allowed":true}\n\n' +
    'event: delta\ndata: {"text":"共"}\n\nevent: delta\ndata: {"text":"有 8 个"}\n\n' +
    'event: done\ndata: {"elapsed_ms":1234,"route":"data"}\n\n'
  const events = []
  // 故意按 7 字节一块喂，最大化半包概率
  for (let i = 0; i < wire.length; i += 7) {
    f.push(bytes(wire.slice(i, i + 7))).forEach((t) => {
      const fr = sse.parseFrame(t)
      if (fr.event) events.push(fr.event)
    })
  }
  assert.deepStrictEqual(events, ['meta', 'scope', 'delta', 'delta', 'done'])
})

console.log(passed + ' 条自检通过')
