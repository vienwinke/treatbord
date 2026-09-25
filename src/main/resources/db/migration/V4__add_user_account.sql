-- 账密登录支持：user 表增加 username（唯一、可空）与 password_hash（可空）
ALTER TABLE `user`
  ADD COLUMN `username` varchar(40) NULL COMMENT '登录账号（唯一；微信登录自动生成或用户自填）' AFTER `openid`;

ALTER TABLE `user`
  ADD UNIQUE KEY `uk_user_username` (`username`);

ALTER TABLE `user`
  ADD COLUMN `password_hash` varchar(128) NULL COMMENT '密码哈希（SHA-256 + 随机盐 + pepper）' AFTER `nickname`;
