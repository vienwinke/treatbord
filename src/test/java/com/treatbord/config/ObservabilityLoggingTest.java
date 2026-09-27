package com.treatbord.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.treatbord.module.task.mapper.TaskMapper;
import com.treatbord.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 慢 SQL / 慢接口日志验收（B6-3）：把阈值覆盖为 0，确认确实写出 WARN 日志。
 */
@SpringBootTest(properties = {
        "treatbord.observability.slow-sql-ms=0",
        "treatbord.observability.slow-api-ms=0"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObservabilityLoggingTest extends AbstractIntegrationTest {

    @Autowired private TaskMapper taskMapper;
    @Autowired private MockMvc mockMvc;

    private ListAppender<ILoggingEvent> attach(Class<?> loggerClass) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    @Test
    @DisplayName("慢 SQL 超过阈值写出 [SLOW-SQL] 日志")
    void slowSqlLogged() {
        ListAppender<ILoggingEvent> appender = attach(SlowSqlInterceptor.class);
        try {
            taskMapper.selectById(1L);
        } finally {
            ((Logger) LoggerFactory.getLogger(SlowSqlInterceptor.class)).detachAppender(appender);
        }
        assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("[SLOW-SQL]")),
                "应写出 [SLOW-SQL] 日志");
    }

    @Test
    @DisplayName("慢接口超过阈值写出 [SLOW-API] 日志")
    void slowApiLogged() throws Exception {
        ListAppender<ILoggingEvent> appender = attach(SlowRequestLoggingFilter.class);
        try {
            mockMvc.perform(get("/api/tasks").param("page", "1").param("pageSize", "5"));
        } finally {
            ((Logger) LoggerFactory.getLogger(SlowRequestLoggingFilter.class)).detachAppender(appender);
        }
        assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("[SLOW-API]")),
                "应写出 [SLOW-API] 日志");
    }
}
