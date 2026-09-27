package com.treatbord.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后回调工具。
 *
 * <p>用途：把「通知、审计日志」这类**非核心、可失败**的旁路操作放到事务提交【之后】执行：
 * <ul>
 *   <li>缩短事务、减少数据库连接与行锁的持有时间；</li>
 *   <li>事务回滚时不会留下"操作成功"的假记录；</li>
 *   <li>回调内部异常只记日志，不影响已提交的业务结果。</li>
 * </ul>
 *
 * <p>若当前没有活动事务（业务入口理论上都有事务），则直接执行，保证语义不丢。
 */
public final class AfterCommit {

    private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);

    private AfterCommit() {
    }

    public static void run(Runnable task) {
        if (task == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeRun(task);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeRun(task);
            }
        });
    }

    private static void safeRun(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("事务提交后回调执行失败（已忽略，不影响已提交的业务）", e);
        }
    }
}
