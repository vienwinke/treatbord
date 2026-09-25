package com.treatbord.module.user.dto;

import com.treatbord.module.user.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户视图（对外输出）。
 * 严禁包含：openid、unionid、passwordHash、deleted 等敏感/内部字段。
 */
@Data
@Builder
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private Integer creditScore;
    /** 0=USER 1=ADMIN */
    private Integer role;
    /** 0=正常 1=封禁 */
    private Integer status;
    private LocalDateTime registerTime;
    private LocalDateTime createTime;

    public static UserVO from(User u) {
        if (u == null) {
            return null;
        }
        return UserVO.builder()
                .id(u.getId())
                .username(u.getUsername())
                .nickname(u.getNickname())
                .avatar(u.getAvatar())
                .creditScore(u.getCreditScore())
                .role(u.getRole())
                .status(u.getStatus())
                .registerTime(u.getRegisterTime())
                .createTime(u.getCreateTime())
                .build();
    }
}