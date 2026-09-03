package com.treatbord.module.auth.dto;

import com.treatbord.module.user.entity.User;
import lombok.Builder;
import lombok.Data;

/**
 * 登录响应（含 token 与用户摘要）。
 */
@Data
@Builder
public class LoginResponse {

    private String token;
    private long expiresIn;
    private UserView user;

    /** 用户摘要（不发 openid 等敏感字段） */
    @Data
    @Builder
    public static class UserView {
        private Long id;
        private String nickname;
        private String avatar;
        private Integer creditScore;
        private Integer role;
        private Integer status;
        private String registerTime;

        public static UserView from(User u) {
            return UserView.builder()
                    .id(u.getId())
                    .nickname(u.getNickname())
                    .avatar(u.getAvatar())
                    .creditScore(u.getCreditScore())
                    .role(u.getRole())
                    .status(u.getStatus())
                    .registerTime(u.getRegisterTime() == null ? null : u.getRegisterTime().toString())
                    .build();
        }
    }
}