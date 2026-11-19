-- 数据持久化模块：新增快照记录表（也已同步到 docs/sql/schema.sql）
-- 用法：mysql -uroot -p memo < docs/sql/migrations/002_add_backup_record.sql
-- 幂等：重复执行不会报错，也不会改动已有行。

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
