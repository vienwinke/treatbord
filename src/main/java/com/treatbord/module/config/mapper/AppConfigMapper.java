package com.treatbord.module.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treatbord.module.config.entity.AppConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppConfigMapper extends BaseMapper<AppConfig> {
}