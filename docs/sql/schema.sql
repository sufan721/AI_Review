-- 备忘录与个人知识库问答系统 · 建表脚本（唯一建表来源）
-- 用法：mysql -uroot -p < docs/sql/schema.sql
-- 约定：库名 memo，字符集 utf8mb4；表名与字段名一律 snake_case。

CREATE DATABASE IF NOT EXISTS `memo` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;

USE `memo`;

-- 用户
CREATE TABLE IF NOT EXISTS `user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `username`      VARCHAR(64)  NOT NULL COMMENT '登录名',
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
  `nickname`      VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
  `phone`         VARCHAR(20)  DEFAULT NULL COMMENT '手机号，用于短信登录',
  `email`         VARCHAR(128) DEFAULT NULL COMMENT '邮箱，用于邮件登录/找回密码',
  `real_name`     VARCHAR(64)  DEFAULT NULL COMMENT '真实姓名',
  `description`   VARCHAR(255) DEFAULT NULL COMMENT '个人描述（memos 语义）',
  `avatar_url`    VARCHAR(512) DEFAULT NULL COMMENT '头像 URL（memos 语义）',
  `token_version` INT          NOT NULL DEFAULT 0 COMMENT '会话版本号，改密码/重置时+1，旧 JWT 失效',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  `fail_count`    INT          NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
  `lock_until`    DATETIME     DEFAULT NULL COMMENT '锁定截止时间',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- 角色
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id`        BIGINT      NOT NULL AUTO_INCREMENT,
  `role_code` VARCHAR(64) NOT NULL COMMENT 'ROLE_ADMIN / ROLE_USER',
  `role_name` VARCHAR(64) DEFAULT NULL COMMENT '角色名',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

-- 权限
CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id`        BIGINT      NOT NULL AUTO_INCREMENT,
  `perm_code` VARCHAR(64) NOT NULL COMMENT 'user:manage 等',
  `perm_name` VARCHAR(64) DEFAULT NULL COMMENT '权限名',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限';

-- 用户-角色关联
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

-- 角色-权限关联
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `role_id` BIGINT NOT NULL,
  `perm_id` BIGINT NOT NULL,
  PRIMARY KEY (`role_id`, `perm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联';

-- SSO 绑定
CREATE TABLE IF NOT EXISTS `sys_user_sso_binding` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT       NOT NULL COMMENT '本地用户 ID',
  `sso_provider` VARCHAR(32)  NOT NULL COMMENT '如 OIDC',
  `sso_subject`  VARCHAR(255) NOT NULL COMMENT 'IdP 返回的唯一标识',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_subject` (`sso_provider`, `sso_subject`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO 绑定';

-- 登录审计日志
CREATE TABLE IF NOT EXISTS `sys_login_log` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `username`   VARCHAR(64)  DEFAULT NULL COMMENT '登录名',
  `login_type` VARCHAR(32)  DEFAULT NULL COMMENT 'PASSWORD/SMS/EMAIL/SSO',
  `success`    TINYINT      NOT NULL DEFAULT 0 COMMENT '1成功 0失败',
  `client_ip`  VARCHAR(64)  DEFAULT NULL,
  `user_agent` VARCHAR(512) DEFAULT NULL,
  `message`    VARCHAR(255) DEFAULT NULL COMMENT '结果描述',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_username_created` (`username`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

-- 初始化角色与权限
INSERT IGNORE INTO `sys_role` (`role_code`, `role_name`) VALUES
  ('ROLE_ADMIN', '管理员'),
  ('ROLE_USER', '普通用户');

INSERT IGNORE INTO `sys_permission` (`perm_code`, `perm_name`) VALUES
  ('user:manage', '管理用户'),
  ('note:view', '查看笔记');

-- 管理员拥有全部权限，普通用户默认仅 note:view
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `perm_id`)
  SELECT r.id, p.id FROM `sys_role` r JOIN `sys_permission` p
  WHERE r.role_code = 'ROLE_ADMIN';

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `perm_id`)
  SELECT r.id, p.id FROM `sys_role` r JOIN `sys_permission` p
  WHERE r.role_code = 'ROLE_USER' AND p.perm_code = 'note:view';

-- 笔记
CREATE TABLE IF NOT EXISTS `note` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT       NOT NULL COMMENT '所属用户',
  `title`           VARCHAR(255) NOT NULL DEFAULT '' COMMENT '标题',
  `content`         LONGTEXT     COMMENT 'Markdown 正文',
  `is_pinned`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶 0/1',
  `is_archived`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否归档 0/1',
  `content_version` INT          NOT NULL DEFAULT 1 COMMENT '内容版本，正文变更时递增',
  `index_status`    VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '索引状态 PENDING/INDEXED/FAILED',
  `index_error`     VARCHAR(500) DEFAULT NULL COMMENT '最近一次索引失败原因',
  `retry_count`     INT          NOT NULL DEFAULT 0 COMMENT '索引重试次数',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_archived_pinned` (`user_id`, `is_archived`, `is_pinned`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记';

-- Markdown 文件
CREATE TABLE IF NOT EXISTS `document` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT       NOT NULL COMMENT '所属用户',
  `file_name`       VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `content`         LONGTEXT     NOT NULL COMMENT 'Markdown 文本',
  `file_size`       BIGINT       NOT NULL COMMENT '字节数',
  `content_version` INT          NOT NULL DEFAULT 1 COMMENT '内容版本',
  `index_status`    VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '索引状态',
  `index_error`     VARCHAR(500) DEFAULT NULL COMMENT '最近一次索引失败原因',
  `retry_count`     INT          NOT NULL DEFAULT 0 COMMENT '索引重试次数',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_document_user_updated` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Markdown 文件';

-- 资料切片（笔记与 Markdown 文件共用）
CREATE TABLE IF NOT EXISTS `document_chunk` (
  `id`              BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT      NOT NULL COMMENT '所属用户（冗余，便于校验与清理）',
  `resource_type`   VARCHAR(16) NOT NULL COMMENT '资料类型 note/document',
  `resource_id`     BIGINT      NOT NULL COMMENT '资料 ID',
  `content_version` INT         NOT NULL COMMENT '内容版本',
  `chunk_index`     INT         NOT NULL COMMENT '切片序号，从 0 开始',
  `content`         TEXT        NOT NULL COMMENT '切片文本',
  `position_start`  INT         NOT NULL COMMENT '切片在清洗后正文中的起始字符位置',
  `position_end`    INT         NOT NULL COMMENT '切片在清洗后正文中的结束字符位置（开区间）',
  `content_hash`    CHAR(64)    NOT NULL COMMENT '切片内容 SHA-256，用于幂等比对',
  `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_version_chunk` (`resource_type`, `resource_id`, `content_version`, `chunk_index`),
  KEY `idx_resource` (`resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资料切片';

-- 知识库
CREATE TABLE IF NOT EXISTS `knowledge_base` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `owner_id`    BIGINT       NOT NULL COMMENT '拥有者用户 ID',
  `name`        VARCHAR(128) NOT NULL COMMENT '知识库名称',
  `description` VARCHAR(512) DEFAULT NULL COMMENT '描述',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库';

-- 知识库成员
CREATE TABLE IF NOT EXISTS `knowledge_base_member` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT,
  `knowledge_base_id` BIGINT      NOT NULL COMMENT '知识库 ID',
  `user_id`           BIGINT      NOT NULL COMMENT '成员用户 ID',
  `member_role`       VARCHAR(16) NOT NULL COMMENT 'OWNER/EDITOR/VIEWER',
  `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_user` (`knowledge_base_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库成员';

-- 知识库资料关联
CREATE TABLE IF NOT EXISTS `knowledge_base_resource` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT,
  `knowledge_base_id` BIGINT      NOT NULL COMMENT '知识库 ID',
  `resource_type`     VARCHAR(16) NOT NULL COMMENT 'NOTE/DOCUMENT',
  `resource_id`       BIGINT      NOT NULL COMMENT '资料 ID（note.id 或 document.id）',
  `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_resource` (`knowledge_base_id`, `resource_type`, `resource_id`),
  KEY `idx_resource` (`resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库资料关联';

-- 用户设置 / 访问令牌 / 身份源 / 实例设置（memos 迁移中保留下来的部分）

CREATE TABLE IF NOT EXISTS `user_setting` (
  `id`      BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT      NOT NULL,
  `key`     VARCHAR(32) NOT NULL COMMENT 'GENERAL/TAGS/EDITOR/WEBHOOKS',
  `value`   JSON        DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_setting` (`user_id`, `key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设置';

CREATE TABLE IF NOT EXISTS `personal_access_token` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `uid`        VARCHAR(36)  NOT NULL,
  `user_id`    BIGINT       NOT NULL,
  `token_hash` VARCHAR(64)  NOT NULL COMMENT 'SHA-256 摘要',
  `description` VARCHAR(255) DEFAULT NULL,
  `created_at` BIGINT       NOT NULL COMMENT 'epoch 毫秒',
  `expires_ts` BIGINT       DEFAULT NULL COMMENT 'epoch 毫秒',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pat_uid` (`uid`),
  KEY `idx_pat_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人访问令牌';

CREATE TABLE IF NOT EXISTS `user_identity` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT       NOT NULL,
  `provider`   VARCHAR(64)  NOT NULL COMMENT '身份源 uid',
  `extern_uid` VARCHAR(255) NOT NULL COMMENT '外部唯一标识',
  `created_at` BIGINT       NOT NULL COMMENT 'epoch 毫秒',
  `updated_at` BIGINT       NOT NULL COMMENT 'epoch 毫秒',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_identity` (`provider`, `extern_uid`),
  KEY `idx_identity_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户 SSO 身份';

CREATE TABLE IF NOT EXISTS `identity_provider` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `uid`               VARCHAR(36)  NOT NULL,
  `name`              VARCHAR(128) NOT NULL,
  `type`              VARCHAR(32)  NOT NULL COMMENT 'OAUTH2/OIDC',
  `identifier_filter` VARCHAR(255) DEFAULT NULL,
  `config`            JSON         DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idp_uid` (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='身份源';

CREATE TABLE IF NOT EXISTS `instance_setting` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(64)  NOT NULL COMMENT 'BASIC/GENERAL/STORAGE/TAGS/NOTIFICATION/ACCESS/USAGE_LIMITS',
  `value`       JSON         DEFAULT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_instance_setting_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实例设置';

-- 数据快照记录：每次备份/恢复前快照都留一行，含失败原因
CREATE TABLE IF NOT EXISTS `backup_record` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `file_name`     VARCHAR(191) NOT NULL COMMENT '归档文件名，不含目录',
  `trigger_type`  VARCHAR(16)  NOT NULL COMMENT 'MANUAL / AUTO / PRE_RESTORE',
  `status`        VARCHAR(16)  NOT NULL COMMENT 'RUNNING / SUCCESS / FAILED',
  `size_bytes`    BIGINT       NOT NULL DEFAULT 0,
  `checksum`      CHAR(64)     NOT NULL DEFAULT '' COMMENT 'zip 字节 SHA-256',
  `table_count`   INT          NOT NULL DEFAULT 0,
  `row_count`     BIGINT       NOT NULL DEFAULT 0,
  `keep_forever`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1=不参与保留期清理',
  `error_message` VARCHAR(512) DEFAULT NULL,
  `started_at`    DATETIME     NOT NULL,
  `finished_at`   DATETIME     DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_backup_file` (`file_name`),
  KEY `idx_backup_status_started` (`status`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据快照记录';

-- 附件存储：元数据（字节按 storage_mode 落 FS 或 attachment_blob）
CREATE TABLE IF NOT EXISTS `attachment` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT       NOT NULL COMMENT '上传者',
  `original_name` VARCHAR(191) NOT NULL COMMENT '原始文件名，仅展示',
  `content_type`  VARCHAR(128) NOT NULL DEFAULT 'application/octet-stream',
  `size_bytes`    BIGINT       NOT NULL DEFAULT 0,
  `sha256`        CHAR(64)     NOT NULL COMMENT '文件字节 SHA-256',
  `storage_mode`  VARCHAR(16)  NOT NULL COMMENT 'LOCAL_FS / DATABASE，写入时逐条固化',
  `storage_key`   VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'FS=相对路径；DATABASE=空串',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_attachment_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件元数据';

CREATE TABLE IF NOT EXISTS `attachment_blob` (
  `attachment_id` BIGINT    NOT NULL COMMENT '对应 attachment.id（1:1）',
  `content`       LONGBLOB  NOT NULL COMMENT '附件二进制，仅 DATABASE 模式写入',
  PRIMARY KEY (`attachment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件二进制（数据库存储）';
