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
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

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
