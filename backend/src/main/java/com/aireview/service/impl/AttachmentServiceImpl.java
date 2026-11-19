package com.aireview.service.impl;

import com.aireview.common.BusinessException;
import com.aireview.common.ResultCode;
import com.aireview.dto.AttachmentStorageSetting;
import com.aireview.dto.AttachmentStorageStatusVO;
import com.aireview.dto.AttachmentVO;
import com.aireview.entity.Attachment;
import com.aireview.entity.AttachmentBlob;
import com.aireview.mapper.AttachmentBlobMapper;
import com.aireview.mapper.AttachmentMapper;
import com.aireview.security.OwnershipService;
import com.aireview.service.AttachmentService;
import com.aireview.service.InstanceSettingService;
import com.aireview.util.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 附件服务。字节落点由「写入当时的配置」决定并逐条固化在 attachment.storage_mode，
 * 读取/删除一律按行内的模式分派，不读当前配置——切换存储方式后老附件仍可正常读写。
 */
@Service
public class AttachmentServiceImpl implements AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentServiceImpl.class);
    private static final String MODE_FS = AttachmentStorageSetting.Mode.LOCAL_FS.name();
    private static final String MODE_DB = AttachmentStorageSetting.Mode.DATABASE.name();
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM");

    private final AttachmentMapper attachmentMapper;
    private final AttachmentBlobMapper attachmentBlobMapper;
    private final InstanceSettingService instanceSettingService;
    private final OwnershipService ownershipService;

    public AttachmentServiceImpl(AttachmentMapper attachmentMapper,
        AttachmentBlobMapper attachmentBlobMapper,
        InstanceSettingService instanceSettingService,
        OwnershipService ownershipService) {
        this.attachmentMapper = attachmentMapper;
        this.attachmentBlobMapper = attachmentBlobMapper;
        this.instanceSettingService = instanceSettingService;
        this.ownershipService = ownershipService;
    }

    @Override
    @Transactional
    public AttachmentVO upload(MultipartFile file) {
        AttachmentStorageSetting setting = instanceSettingService.getAttachmentStorage();
        long maxBytes = (long) setting.maxSizeMb() * 1024 * 1024;
        byte[] bytes = validateAndRead(file, maxBytes);
        String safeName = safeFileName(file.getOriginalFilename());
        String sha256 = sha256(bytes);

        Attachment attachment = new Attachment();
        attachment.setUserId(UserContext.require());
        attachment.setOriginalName(safeName);
        attachment.setContentType(defaultContentType(file.getContentType()));
        attachment.setSizeBytes((long) bytes.length);
        attachment.setSha256(sha256);
        attachment.setStorageMode(setting.mode().name());

        Path written = null;
        try {
            if (setting.mode() == AttachmentStorageSetting.Mode.LOCAL_FS) {
                Path root = Path.of(setting.localDir()).toAbsolutePath().normalize();
                Files.createDirectories(root);
                String relative = LocalDate.now().format(DATE_DIR) + "/"
                    + UUID.randomUUID().toString().replace("-", "") + extensionOf(safeName);
                written = root.resolve(relative).normalize();
                assertInside(root, written);
                // 文件落在 root/yyyy/MM/ 下，必须先建到父目录，否则 Files.write 抛 IOException
                Files.createDirectories(written.getParent());
                Files.write(written, bytes);
                attachment.setStorageKey(relative);
                attachmentMapper.insert(attachment);
            } else {
                attachment.setStorageKey("");
                attachmentMapper.insert(attachment);
                AttachmentBlob blob = new AttachmentBlob();
                blob.setAttachmentId(attachment.getId());
                blob.setContent(bytes);
                // 元数据与 blob 同一 @Transactional：任一步失败整体回滚
                attachmentBlobMapper.insertBlob(blob);
            }
        } catch (BusinessException exception) {
            rollbackFile(written);
            throw exception;
        } catch (IOException exception) {
            rollbackFile(written);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "附件写入失败");
        } catch (RuntimeException exception) {
            rollbackFile(written);
            throw exception;
        }
        return toVO(attachmentMapper.selectById(attachment.getId()));
    }

    @Override
    public List<AttachmentVO> list() {
        return attachmentMapper.selectList(Wrappers.<Attachment>lambdaQuery()
                .eq(Attachment::getUserId, UserContext.require())
                .orderByDesc(Attachment::getId))
            .stream().map(AttachmentServiceImpl::toVO).toList();
    }

    @Override
    public Attachment loadOwned(Long id) {
        Long currentUserId = UserContext.require();
        Attachment attachment = attachmentMapper.selectOne(Wrappers.<Attachment>lambdaQuery()
            .eq(Attachment::getId, id)
            .eq(Attachment::getUserId, currentUserId));
        if (attachment != null) {
            return attachment;
        }
        if (ownershipService.isAdmin(currentUserId)) {
            Attachment any = attachmentMapper.selectById(id);
            if (any == null) {
                throw new BusinessException(ResultCode.NOT_FOUND);
            }
            return any;
        }
        throw new BusinessException(ResultCode.FORBIDDEN);
    }

    @Override
    public byte[] readBytes(Attachment attachment) {
        if (MODE_DB.equals(attachment.getStorageMode())) {
            AttachmentBlob blob = attachmentBlobMapper.selectById(attachment.getId());
            if (blob == null || blob.getContent() == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "附件内容不存在");
            }
            return blob.getContent();
        }
        Path file = fsPath(attachment);
        if (!Files.isRegularFile(file)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "附件文件不存在");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "附件读取失败");
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Attachment attachment = loadOwned(id);
        // 按行内的模式删字节，不读当前配置，避免切换后残留孤儿文件/行
        if (MODE_DB.equals(attachment.getStorageMode())) {
            attachmentBlobMapper.deleteById(attachment.getId());
        } else {
            Path file = fsPath(attachment);
            try {
                Files.deleteIfExists(file);
            } catch (IOException exception) {
                log.warn("删除附件文件失败: {}", file, exception);
            }
        }
        attachmentMapper.deleteById(attachment.getId());
    }

    @Override
    public AttachmentStorageStatusVO status() {
        AttachmentStorageSetting setting = instanceSettingService.getAttachmentStorage();
        long fsCount = 0;
        long fsBytes = 0;
        long dbCount = 0;
        long dbBytes = 0;
        for (Map<String, Object> row : attachmentMapper.selectUsageByMode()) {
            Object mode = row.get("storageMode");
            long count = ((Number) row.getOrDefault("itemCount", 0L)).longValue();
            long bytes = ((Number) row.getOrDefault("totalBytes", 0L)).longValue();
            if (MODE_FS.equals(mode)) {
                fsCount = count;
                fsBytes = bytes;
            } else if (MODE_DB.equals(mode)) {
                dbCount = count;
                dbBytes = bytes;
            }
        }
        boolean writable = setting.mode() == AttachmentStorageSetting.Mode.DATABASE
            || isWritable(Path.of(setting.localDir()).toAbsolutePath().normalize());
        return new AttachmentStorageStatusVO(setting.mode().name(), setting.localDir(),
            writable, fsCount, fsBytes, dbCount, dbBytes);
    }

    @Override
    public Resource downloadResource(Attachment attachment) {
        if (MODE_DB.equals(attachment.getStorageMode())) {
            return new ByteArrayResource(readBytes(attachment));
        }
        return new FileSystemResource(fsPath(attachment));
    }

    @Override
    public String downloadName(Attachment attachment) {
        return attachment.getOriginalName() == null ? "attachment.bin" : attachment.getOriginalName();
    }

    /** FS 路径：root + 存量的相对 key，normalize 后断言仍在 root 内，挡 ../ 穿越。 */
    private Path fsPath(Attachment attachment) {
        AttachmentStorageSetting setting = instanceSettingService.getAttachmentStorage();
        Path root = Path.of(setting.localDir()).toAbsolutePath().normalize();
        String key = attachment.getStorageKey() == null ? "" : attachment.getStorageKey();
        Path file = root.resolve(key).normalize();
        assertInside(root, file);
        return file;
    }

    private static void assertInside(Path root, Path target) {
        if (!target.startsWith(root)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "附件路径不合法");
        }
    }

    private static void rollbackFile(Path written) {
        if (written == null) {
            return;
        }
        try {
            Files.deleteIfExists(written);
        } catch (IOException ignored) {
            // 补偿失败仅记录，主异常继续抛出
        }
    }

    private static boolean isWritable(Path directory) {
        try {
            Files.createDirectories(directory);
            Path probe = Files.createTempFile(directory, "write-probe-", ".tmp");
            Files.deleteIfExists(probe);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static byte[] validateAndRead(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择非空附件");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "附件大小超过上限");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无法读取附件");
        }
    }

    /** 去掉路径分隔符与控制字符，限长 191；空名回退为 attachment。 */
    private static String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "attachment";
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        StringBuilder cleaned = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isISOControl(c) || c == '/' || c == '\\' || c == 0) {
                continue;
            }
            cleaned.append(c);
        }
        String result = cleaned.toString().trim();
        if (result.isEmpty() || ".".equals(result) || "..".equals(result)) {
            return "attachment";
        }
        return result.length() > 191 ? result.substring(result.length() - 191) : result;
    }

    private static String extensionOf(String safeName) {
        int dot = safeName.lastIndexOf('.');
        if (dot < 0 || dot == safeName.length() - 1) {
            return "";
        }
        String ext = safeName.substring(dot).toLowerCase(Locale.ROOT);
        return ext.matches("\\.[a-z0-9]{1,16}") ? ext : "";
    }

    private static String defaultContentType(String contentType) {
        return contentType == null || contentType.isBlank()
            ? "application/octet-stream" : contentType;
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static AttachmentVO toVO(Attachment attachment) {
        return new AttachmentVO(attachment.getId(), attachment.getOriginalName(),
            attachment.getContentType(), attachment.getSizeBytes() == null ? 0 : attachment.getSizeBytes(),
            attachment.getSha256(), attachment.getStorageMode(), attachment.getCreatedAt());
    }
}
