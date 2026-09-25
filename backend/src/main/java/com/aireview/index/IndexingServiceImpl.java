package com.aireview.index;

import com.aireview.entity.DocumentChunk;
import com.aireview.mapper.DocumentChunkMapper;
import com.aireview.util.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 索引流水线：读取资料当前内容与版本 → 清洗切片 → 持久化切片 → 批量嵌入 →
 * 整体替换向量 → 标记已索引；任一步失败则标记 FAILED 并记录原因，重试幂等。
 */
@Service
public class IndexingServiceImpl implements IndexingService {
    private static final Logger log = LoggerFactory.getLogger(IndexingServiceImpl.class);
    private static final String INDEXED = "INDEXED";

    private final IndexResourceRepository repository;
    private final DocumentChunkMapper chunkMapper;
    private final MarkdownChunker chunker;
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;
    private final IndexTaskSink taskSink;

    public IndexingServiceImpl(IndexResourceRepository repository, DocumentChunkMapper chunkMapper,
        MarkdownChunker chunker, EmbeddingClient embeddingClient, VectorStore vectorStore,
        IndexTaskSink taskSink) {
        this.repository = repository;
        this.chunkMapper = chunkMapper;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.taskSink = taskSink;
    }

    @Override
    public void schedule(ResourceType type, long resourceId) {
        long userId = UserContext.require();
        taskSink.submit(() -> indexNow(type, resourceId, userId));
    }

    @Override
    public void indexNow(ResourceType type, long resourceId, long userId) {
        IndexResource resource = repository.load(type, resourceId, userId);
        if (resource == null || INDEXED.equals(resource.indexStatus())) {
            return;
        }
        try {
            List<Chunk> chunks = chunker.chunk(resource.content());
            List<float[]> vectors = chunks.isEmpty() ? List.of() : embeddingClient.embed(chunks.stream().map(Chunk::text).toList());

            // 先删后插，保证当前版本切片唯一；插入后拿回自增主键作为向量主键
            chunkMapper.delete(Wrappers.<DocumentChunk>lambdaQuery()
                .eq(DocumentChunk::getResourceType, type.name())
                .eq(DocumentChunk::getResourceId, resourceId));
            List<VectorRecord> records = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                Chunk chunk = chunks.get(i);
                DocumentChunk entity = new DocumentChunk();
                entity.setUserId(userId);
                entity.setResourceType(type.name());
                entity.setResourceId(resourceId);
                entity.setContentVersion(resource.contentVersion());
                entity.setChunkIndex(chunk.index());
                entity.setContent(chunk.text());
                entity.setPositionStart(chunk.start());
                entity.setPositionEnd(chunk.end());
                entity.setContentHash(sha256(chunk.text()));
                chunkMapper.insert(entity);
                records.add(new VectorRecord(userId, type, resourceId, entity.getId(), chunk.index(),
                    resource.contentVersion(), chunk.text(), vectors.get(i)));
            }

            if (records.isEmpty()) {
                vectorStore.delete(type, resourceId);
            } else {
                vectorStore.replace(type, resourceId, records);
            }
            repository.markIndexed(type, resourceId);
        } catch (Exception exception) {
            String reason = exception instanceof IndexingException
                ? exception.getMessage() : "索引失败: " + exception.getMessage();
            log.warn("资料索引失败 type={} id={} version={}: {}", type, resourceId,
                resource.contentVersion(), reason);
            repository.markFailed(type, resourceId, reason);
        }
    }

    @Override
    public void deleteResource(ResourceType type, long resourceId) {
        try {
            chunkMapper.delete(Wrappers.<DocumentChunk>lambdaQuery()
                .eq(DocumentChunk::getResourceType, type.name())
                .eq(DocumentChunk::getResourceId, resourceId));
        } catch (Exception exception) {
            log.warn("删除切片失败 type={} id={}: {}", type, resourceId, exception.getMessage());
        }
        try {
            vectorStore.delete(type, resourceId);
        } catch (Exception exception) {
            log.warn("删除向量失败 type={} id={}: {}", type, resourceId, exception.getMessage());
        }
    }

    @Override
    public void reindex(ResourceType type, long resourceId) {
        repository.markPending(type, resourceId);
        schedule(type, resourceId);
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
