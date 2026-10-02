package com.treatbord.module.ai.service;

import java.util.function.BiConsumer;

/**
 * SSE 行 → 帧 的组装器（与小程序端 `utils/sse.js` 的分帧规则一致）。
 *
 * 一行一行喂进来，遇到空行就回调一次 (event, data)。
 * 同时认 LF LF 与 LF CR LF —— 中间层（网关/代理）可能把换行改成 CRLF，
 * 只认一种的话会出现"整段堆到最后一次性蹦出来"。
 */
public final class AiSseFramer {

    private final BiConsumer<String, String> onFrame;
    private String event;
    private final StringBuilder data = new StringBuilder();

    public AiSseFramer(BiConsumer<String, String> onFrame) {
        this.onFrame = onFrame;
    }

    public void accept(String line) {
        String text = line == null ? "" : line;
        if (text.isEmpty()) {
            flush();
            return;
        }
        if (text.charAt(text.length() - 1) == '\r') {
            text = text.substring(0, text.length() - 1);
        }
        if (text.isEmpty()) {
            flush();
            return;
        }
        if (text.charAt(0) == ':') {
            return;                                  // 注释帧（如 ": connected"）
        }
        int colon = text.indexOf(':');
        String field = colon == -1 ? text : text.substring(0, colon);
        String value = colon == -1 ? "" : text.substring(colon + 1);
        if (!value.isEmpty() && value.charAt(0) == ' ') {
            value = value.substring(1);
        }
        if ("event".equals(field)) {
            event = value;
        } else if ("data".equals(field)) {
            if (data.length() > 0) {
                data.append('\n');
            }
            data.append(value);
        }
    }

    /** 收尾：把没有以空行结束的残留帧也吐出来 */
    public void finish() {
        flush();
    }

    private void flush() {
        if (event == null && data.length() == 0) {
            return;
        }
        onFrame.accept(event, data.toString());
        event = null;
        data.setLength(0);
    }
}
