package com.treatbord.module.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.treatbord.module.config.entity.AppConfig;
import com.treatbord.module.config.mapper.AppConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 运行期配置读取（app_config 表）。
 */
@Service
@RequiredArgsConstructor
public class AppConfigService {

    private final AppConfigMapper appConfigMapper;

    /** 读取字符串配置；不存在返回默认值 */
    public String get(String key, String defaultValue) {
        AppConfig c = appConfigMapper.selectOne(new LambdaQueryWrapper<AppConfig>()
                .eq(AppConfig::getConfigKey, key));
        return c == null ? defaultValue : c.getConfigValue();
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBool(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }
}