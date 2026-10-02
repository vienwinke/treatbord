package com.treatbord.module.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 把 SSE 流收集成一次结果（给非流式的 `/api/ai/ask` 兜底用）。
 *
 * 只认契约里定义的几个事件；未知事件忽略 —— 边车以后加事件不该让旧版本 Java 崩掉。
 */
public final class AiSseCollector implements Consumer<String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiSseFramer framer = new AiSseFramer(this::onFrame);
    private final StringBuilder answer = new StringBuilder();
    private final List<JsonNode> tables = new ArrayList<>();
    private JsonNode done;
    private String errorCode;
    private String errorMessage;

    @Override
    public void accept(String line) {
        framer.accept(line);
    }

    private void onFrame(String event, String data) {
        if (event == null) {
            return;
        }
        JsonNode json = parse(data);
        switch (event) {
            case "delta" -> answer.append(json.path("text").asText(""));
            // 收集**整个 table 载荷**（columns/rows/row_count/...）。
            // 原先按 `name` 字段取名，而边车的 table 事件根本没有这个字段 → `/api/ai/ask`
            // 的 tables 恒为空，非流式兜底只剩文字（实测）。
            case "table" -> tables.add(json);
            case "done" -> done = json;
            case "error" -> {
                errorCode = json.path("code").asText("UNKNOWN");
                errorMessage = json.path("message").asText("");
            }
            default -> { /* 未知事件忽略 */ }
        }
    }

    private JsonNode parse(String data) {
        try {
            return MAPPER.readTree(data == null || data.isEmpty() ? "{}" : data);
        } catch (Exception e) {
            return MAPPER.createObjectNode();
        }
    }

    public String answer() {
        return answer.toString();
    }

    public List<JsonNode> tables() {
        return tables;
    }

    public JsonNode done() {
        return done;
    }

    public boolean failed() {
        return errorCode != null;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }
}
