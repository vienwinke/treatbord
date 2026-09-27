package com.treatbord.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Statement;

/**
 * 慢 SQL 日志（MyBatis 插件）。
 *
 * <p>挂在 <b>StatementHandler</b> 的 query/update 上——这是真正执行 JDBC 的扩展点，
 * 因此统计到的耗时就包含数据库执行时间（比挂在 Executor 上更贴近"慢 SQL"语义）。
 * 超过阈值打 WARN，日志只记录 MappedStatement id（不打印 SQL 与参数，避免泄露业务数据/隐私）。
 *
 * <p>阈值：{@code treatbord.observability.slow-sql-ms}（默认 300ms）。
 * 该 Bean 与 {@code MybatisPlusInterceptor} 同属 MyBatis 插件链（由 MP 自动配置收集）。
 */
@Slf4j
@Component
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query",
                args = {Statement.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update", args = {Statement.class}),
        @Signature(type = StatementHandler.class, method = "batch", args = {Statement.class})
})
public class SlowSqlInterceptor implements Interceptor {

    @Value("${treatbord.observability.slow-sql-ms:300}")
    private long slowSqlMs;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost >= slowSqlMs) {
                log.warn("[SLOW-SQL] {} ms sqlId={}", cost, statementId(invocation.getTarget()));
            }
        }
    }

    /** 通过 MetaObject 读取 RoutingStatementHandler 内部代理的 MappedStatement id */
    private String statementId(Object target) {
        try {
            MetaObject meta = SystemMetaObject.forObject(target);
            if (meta.hasGetter("delegate.mappedStatement")) {
                MappedStatement ms = (MappedStatement) meta.getValue("delegate.mappedStatement");
                return ms == null ? "unknown" : ms.getId();
            }
            if (meta.hasGetter("mappedStatement")) {
                MappedStatement ms = (MappedStatement) meta.getValue("mappedStatement");
                return ms == null ? "unknown" : ms.getId();
            }
        } catch (Exception ignored) {
            // 取不到 id 不影响主流程
        }
        return "unknown";
    }
}
