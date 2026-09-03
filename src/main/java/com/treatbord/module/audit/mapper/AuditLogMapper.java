package com.treatbord.module.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treatbord.module.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}