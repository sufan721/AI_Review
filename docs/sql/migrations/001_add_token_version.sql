-- 既有实例升级：补充会话版本号列
-- schema.sql 已包含该列，全新库无需执行本脚本。
-- 未执行时，登录会报 Unknown column 'token_version' in 'field list' → 500。
ALTER TABLE `user`
  ADD COLUMN `token_version` INT NOT NULL DEFAULT 0
  COMMENT '会话版本号，改密码/重置时+1，旧 JWT 失效'
  AFTER `avatar_url`;
