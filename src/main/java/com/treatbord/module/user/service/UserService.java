package com.treatbord.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.user.entity.User;
import com.treatbord.module.user.mapper.UserMapper;
import com.treatbord.security.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 用户服务：注册（首登建号）、查询、账密设置/校验、注销（openid 匿名化 - 方案 A）。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    /** 避免易混淆字符（0/O、1/l） */
    private static final String USERNAME_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkm23456789";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /** 按 openid 查用户（未删除） */
    public User findByOpenid(String openid) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getOpenid, openid));
    }

    /** 按登录账号查用户（未删除） */
    public User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
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
     * 登录时获取用户：不存在则创建（自动生成唯一账号、信用分 100、角色 USER）。
     */
    public User getOrCreate(String openid) {
        User user = findByOpenid(openid);
        if (user != null) {
            return user;
        }
        User nu = new User();
        nu.setOpenid(openid);
        nu.setUsername(generateUsername());
        nu.setNickname("微信用户");
        nu.setCreditScore(100);
        nu.setRole(0);
        nu.setStatus(0);
        nu.setRegisterTime(LocalDateTime.now());
        userMapper.insert(nu);
        return nu;
    }

    /**
     * 设置/修改账密（账号唯一校验 + 密码加盐哈希）。
     *
     * @param username 允许空：不改账号
     * @param password 允许空：不改密码
     */
    public void setCredentials(Long userId, String username, String password) {
        User me = getById(userId);
        if (username != null && !username.isBlank() && !username.equals(me.getUsername())) {
            if (username.length() < 4 || username.length() > 32) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "账号长度需 4-32 位");
            }
            if (!username.matches("^[a-zA-Z0-9_]+$")) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "账号仅支持字母/数字/下划线");
            }
            User exist = findByUsername(username);
            if (exist != null && !exist.getId().equals(userId)) {
                throw new BusinessException(ResultCode.CONFLICT, "该账号已被占用");
            }
            me.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            if (password.length() < 6 || password.length() > 64) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "密码需 6-64 位");
            }
            me.setPasswordHash(passwordEncoder.encode(password));
        }
        userMapper.updateById(me);
    }

    /** 账密登录校验：返回匹配用户；账号不存在 / 未设密码 / 密码错误分别抛错。 */
    public User verifyAccount(String username, String password) {
        User user = findByUsername(username);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "账号不存在");
        }
        if (user.getPasswordHash() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该账号未设置密码，请用微信登录后设置");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码错误");
        }
        // 旧哈希（SHA-256）自动升级为 BCrypt：渐进迁移，用户无感（P0-4）
        if (passwordEncoder.needsUpgrade(user.getPasswordHash())) {
            String upgraded = passwordEncoder.encode(password);
            User up = new User();
            up.setId(user.getId());
            up.setPasswordHash(upgraded);
            userMapper.updateById(up);
            user.setPasswordHash(upgraded);
        }
        return user;
    }

    /** 生成唯一用户名（随机 7 位 + 前缀 u），冲突时用 UUID 兜底。 */
    private String generateUsername() {
        for (int i = 0; i < 20; i++) {
            StringBuilder sb = new StringBuilder("u");
            for (int j = 0; j < 7; j++) {
                sb.append(USERNAME_CHARS.charAt(ThreadLocalRandom.current().nextInt(USERNAME_CHARS.length())));
            }
            String candidate = sb.toString();
            if (findByUsername(candidate) == null) {
                return candidate;
            }
        }
        return "u" + UUID.randomUUID().toString().substring(0, 8).replace("-", "");
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
