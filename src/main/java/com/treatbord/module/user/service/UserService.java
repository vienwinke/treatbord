package com.treatbord.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.user.entity.User;
import com.treatbord.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户服务：注册（首登建号）、查询、注销（openid 匿名化 - 方案 A）。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    /** 按 openid 查用户（未删除） */
    public User findByOpenid(String openid) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getOpenid, openid));
    }

    public User getById(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return u;
    }

    /** 批量查用户（列表联查昵称用，不存在的不抛错） */
    public java.util.List<User> listByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.List.of();
        }
        return userMapper.selectBatchIds(ids);
    }

    /**
     * 登录时获取用户：不存在则创建（状态正常、信用分默认 100、角色 USER）。
     */
    public User getOrCreate(String openid) {
        User user = findByOpenid(openid);
        if (user != null) {
            return user;
        }
        User nu = new User();
        nu.setOpenid(openid);
        nu.setNickname("微信用户");
        nu.setCreditScore(100);
        nu.setRole(0);
        nu.setStatus(0);
        nu.setRegisterTime(LocalDateTime.now());
        userMapper.insert(nu);
        return nu;
    }

    /**
     * 注销账号（方案 A：openid 匿名化为 DEL_<uuid>，释放唯一索引；软删除）。
     */
    public void deleteUser(Long userId) {
        User user = getById(userId);
        User update = new User();
        update.setId(userId);
        // 匿名化：破坏 openid 唯一性，保留行供审计追溯，但不泄露原始 openid
        update.setOpenid("DEL_" + UUID.randomUUID().toString().replace("-", ""));
        update.setStatus(1);
        userMapper.updateById(update);
        // 逻辑删除（deleted=1）
        userMapper.deleteById(userId);
    }
}