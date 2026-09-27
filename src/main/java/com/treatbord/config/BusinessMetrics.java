package com.treatbord.config;

import com.treatbord.common.ResultCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 业务指标（Micrometer → Prometheus）。
 *
 * <p>指标清单：
 * <ul>
 *   <li>{@code treatbord_claim_result_total{result=...}}：接取结果（success/full/not_claimable/duplicate/self_claim/credit_not_enough/other）</li>
 *   <li>{@code treatbord_ratelimit_rejected_total{scope=...}}：被限流拒绝的次数（按场景）</li>
 *   <li>{@code treatbord_review_decision_total{result=APPROVED|REJECTED}}：审核决策次数</li>
 *   <li>{@code treatbord_review_wait_seconds}：提交到审核的等待时长（审核时长业务指标）</li>
 *   <li>{@code treatbord_schedule_runs_total{task=...,result=success|failure}}：定时任务执行结果</li>
 *   <li>{@code treatbord_schedule_processed_total{task=...}}：定时任务处理条数</li>
 * </ul>
 *
 * <p>说明：指标名里的点号会被 Micrometer 转成下划线（Prometheus 命名规范）。
 */
@Component
@RequiredArgsConstructor
public class BusinessMetrics {

    private final MeterRegistry registry;

    /** 接取结果计数 */
    public void claimResult(String result) {
        Counter.builder("treatbord.claim.result")
                .description("接取结果计数")
                .tag("result", result)
                .register(registry)
                .increment();
    }

    /** 把业务错误码映射为低基数标签（避免 result 维度爆炸） */
    public static String claimResultTag(int code) {
        if (code == ResultCode.TASK_FULL.getCode()) {
            return "full";
        }
        if (code == ResultCode.TASK_NOT_CLAIMABLE.getCode()) {
            return "not_claimable";
        }
        if (code == ResultCode.CLAIM_DUPLICATE.getCode()) {
            return "duplicate";
        }
        if (code == ResultCode.SELF_CLAIM_FORBIDDEN.getCode()) {
            return "self_claim";
        }
        if (code == ResultCode.CREDIT_NOT_ENOUGH.getCode()) {
            return "credit_not_enough";
        }
        if (code == ResultCode.TASK_NOT_FOUND.getCode()) {
            return "task_not_found";
        }
        return "other";
    }

    /** 限流拒绝计数（按场景） */
    public void rateLimitRejected(String scope) {
        Counter.builder("treatbord.ratelimit.rejected")
                .description("被限流拒绝的请求数")
                .tag("scope", scope == null ? "unknown" : scope)
                .register(registry)
                .increment();
    }

    /** 审核决策 + 审核等待时长（提交 → 审核） */
    public void reviewDecision(String result, Long waitSeconds) {
        Counter.builder("treatbord.review.decision")
                .description("审核决策计数")
                .tag("result", result)
                .register(registry)
                .increment();
        if (waitSeconds != null) {
            // 说明：task_claim.submitted_at 为秒精度 DATETIME，MySQL 写入时会四舍五入，
            // 紧接着审核时算出的差值可能是 -1s（"负等待"）。这属于时间精度噪声，按 0 计，
            // **不能丢弃样本**——否则指标会随机消失，排查问题时产生误导。
            long seconds = Math.max(0L, waitSeconds);
            Timer.builder("treatbord.review.wait")
                    .description("凭证提交到审核的等待时长（秒）")
                    .register(registry)
                    .record(Duration.ofSeconds(seconds));
        }
    }

    /** 定时任务执行结果 */
    public void scheduleRun(String task, boolean success, int processed) {
        Counter.builder("treatbord.schedule.runs")
                .description("定时任务执行次数")
                .tag("task", task)
                .tag("result", success ? "success" : "failure")
                .register(registry)
                .increment();
        if (processed > 0) {
            Counter.builder("treatbord.schedule.processed")
                    .description("定时任务处理条数")
                    .tag("task", task)
                    .register(registry)
                    .increment(processed);
        }
    }
}
