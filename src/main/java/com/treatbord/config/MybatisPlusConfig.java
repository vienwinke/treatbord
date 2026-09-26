package com.treatbord.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.treatbord.module.config.service.AppConfigService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：乐观锁 + 分页插件（docs/SECURITY_REVIEW.md 4.4 分页一致性）。
 *
 * <p>⚠️ 乐观锁必须注册 {@link OptimisticLockerInnerInterceptor} 才生效：
 * 只写 {@code @Version} 而不注册拦截器时，版本号不会进入 WHERE 条件。
 * 生效范围：{@code updateById(entity)} / {@code update(entity, wrapper)}；
 * 自定义 {@code @Update} SQL（如 TaskMapper.casStatus）不走插件，需自行维护 version。
 *
 * <p>顺序：先乐观锁、分页插件放最后（对齐 MP 官方对 InnerInterceptor 顺序的约定）。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public PaginationInnerInterceptor paginationInnerInterceptor() {
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(20L); // 兜底上限，启动后由 app_config: page.size.max 覆盖
        return pagination;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(PaginationInnerInterceptor pagination) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 乐观锁：Task.version —— 更新时自动追加 version = 旧值 条件并把 version + 1
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 分页上限拦截（docs/API_DESIGN.md §1：pageSize ≤ 20）
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    /**
     * 让 app_config 的 {@code page.size.max} 真正生效（此前该键已种入但无人读取）。
     * 用 ApplicationRunner：等上下文与数据源就绪后再读配置，避免与 SqlSessionFactory 形成循环依赖。
     */
    @Bean
    public ApplicationRunner pageSizeMaxLimitInitializer(PaginationInnerInterceptor pagination,
                                                         AppConfigService appConfigService) {
        return args -> {
            try {
                int max = appConfigService.getInt("page.size.max", 20);
                if (max > 0) {
                    pagination.setMaxLimit((long) max);
                }
            } catch (Exception ignored) {
                // 配置读取失败不阻塞启动，保留兜底上限 20
            }
        };
    }
}
