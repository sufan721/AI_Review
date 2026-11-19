-- 附件存储模块：新增附件元数据表与二进制表（也已同步到 docs/sql/schema.sql）
-- 用法：mysql -uroot -p memo < docs/sql/migrations/003_add_attachment_storage.sql
-- 幂等：重复执行不会报错，也不会改动已有行。

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
