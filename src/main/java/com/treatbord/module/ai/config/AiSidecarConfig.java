package com.treatbord.module.ai.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 边车转发用的线程池。
 *
 * 为什么不让 SSE 在请求线程里跑：`SseEmitter` 需要异步写，占着 Tomcat 工作线程会把
 * 线程池拖干（一个慢问题就卡住一个线程 8 秒）。这里用有限并发的独立池，
 * 并把边车侧已封顶的并发数（`SIDECAR_MAX_CONCURRENCY`）对齐过来。
 */
@Configuration
public class AiSidecarConfig {

    @Bean(name = "aiSidecarExecutor", destroyMethod = "shutdown")
    public ExecutorService aiSidecarExecutor() {
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "ai-sidecar-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        return Executors.newFixedThreadPool(16, factory);
    }
}
