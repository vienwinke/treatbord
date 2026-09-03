package com.treatbord.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treatbord.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}