package com.aireview.service;

import com.aireview.dto.AttachmentStorageStatusVO;
import com.aireview.dto.AttachmentVO;
import com.aireview.entity.Attachment;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/** 附件业务：上传、列表、下载、删除与存储概览。归属校验在学习归属服务内完成。 */
public interface AttachmentService {

    /** 上传附件，字节按当前配置的 storage_mode 落盘。返回元数据（不含二进制）。 */
    AttachmentVO upload(MultipartFile file);

    /** 当前用户的附件列表。 */
    List<AttachmentVO> list();

    /** 按 id 载入本人附件（管理员可越权）。 */
    Attachment loadOwned(Long id);

    /** 读取附件字节；按该附件自己的 storage_mode 分派 FS / DB。 */
    byte[] readBytes(Attachment attachment);

    /** 删除附件（元数据 + 对应字节），本人或管理员。 */
    void delete(Long id);

    /** 存储概览，仅管理员使用。 */
    AttachmentStorageStatusVO status();

    /** 构造下载用的 Resource（FS 走 FileSystemResource，DB 走 ByteArrayResource）。 */
    Resource downloadResource(Attachment attachment);

    /** 下载文件名。 */
    String downloadName(Attachment attachment);
}
