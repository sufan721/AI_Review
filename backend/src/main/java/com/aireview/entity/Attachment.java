package com.aireview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 附件元数据。字节本身按 storageMode 分流：LOCAL_FS 落在本地目录，DATABASE 落在 attachment_blob。
 * storageMode 逐条记录，读取时按行分派，因此切换配置不影响已存附件。
 */
@Data
@TableName("attachment")
public class Attachment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 原始文件名（安全化后），仅用于展示与下载命名。 */
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    /** 文件字节的 SHA-256。 */
    private String sha256;
    /** LOCAL_FS / DATABASE，写入时逐条固定。 */
    private String storageMode;
    /** FS=相对路径；DATABASE=空串（blob 以 attachment_id 关联）。 */
    private String storageKey;
    private LocalDateTime createdAt;
}
