package com.aireview.index;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aireview.entity.DocumentChunk;
import com.aireview.mapper.DocumentChunkMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexingServiceImplTest {
    @Mock private IndexResourceRepository repository;
    @Mock private DocumentChunkMapper chunkMapper;
    @Mock private EmbeddingClient embeddingClient;
    @Mock private VectorStore vectorStore;
    @Mock private IndexTaskSink taskSink;

    private IndexingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new IndexingServiceImpl(repository, chunkMapper, new MarkdownChunker(800, 100),
            embeddingClient, vectorStore, taskSink);
    }

    @Test
    void skipsAlreadyIndexedResource() {
        when(repository.load(ResourceType.NOTE, 1, 42))
            .thenReturn(new IndexResource(1, 42, "内容", 1, "INDEXED"));

        service.indexNow(ResourceType.NOTE, 1, 42);

        verifyNoInteractions(embeddingClient, chunkMapper, vectorStore);
        verify(repository, never()).markIndexed(any(), anyLong());
    }

    @Test
    void skipsMissingResource() {
        when(repository.load(ResourceType.NOTE, 1, 42)).thenReturn(null);

        service.indexNow(ResourceType.NOTE, 1, 42);

        verifyNoInteractions(embeddingClient, chunkMapper, vectorStore);
    }

    @Test
    void indexesAndMarksIndexed() {
        when(repository.load(ResourceType.NOTE, 1, 42))
            .thenReturn(new IndexResource(1, 42, "# A\n# B", 1, "PENDING"));
        when(embeddingClient.embed(any())).thenReturn(List.of(new float[] {1f}, new float[] {2f}));
        doAnswer(invocation -> {
            DocumentChunk chunk = invocation.getArgument(0);
            chunk.setId(100L + chunk.getChunkIndex());
            return 1;
        }).when(chunkMapper).insert(any(DocumentChunk.class));

        service.indexNow(ResourceType.NOTE, 1, 42);

        verify(chunkMapper).delete(any());
        verify(chunkMapper, times(2)).insert(any(DocumentChunk.class));
        verify(vectorStore).replace(eq(ResourceType.NOTE), eq(1L), any());
        verify(repository).markIndexed(ResourceType.NOTE, 1);
    }

    @Test
    void emptyContentDeletesVectorsAndMarksIndexed() {
        when(repository.load(ResourceType.NOTE, 1, 42))
            .thenReturn(new IndexResource(1, 42, "", 1, "PENDING"));

        service.indexNow(ResourceType.NOTE, 1, 42);

        verify(vectorStore).delete(ResourceType.NOTE, 1);
        verify(vectorStore, never()).replace(any(), anyLong(), any());
        verify(repository).markIndexed(ResourceType.NOTE, 1);
    }

    @Test
    void embeddingFailureMarksFailedWithReason() {
        when(repository.load(ResourceType.NOTE, 1, 42))
            .thenReturn(new IndexResource(1, 42, "正文", 1, "PENDING"));
        when(embeddingClient.embed(any())).thenThrow(new IndexingException("嵌入服务不可用"));

        service.indexNow(ResourceType.NOTE, 1, 42);

        verify(repository).markFailed(ResourceType.NOTE, 1, "嵌入服务不可用");
        verify(repository, never()).markIndexed(any(), anyLong());
    }

    @Test
    void deleteResourceClearsChunksAndVectors() {
        service.deleteResource(ResourceType.NOTE, 1);

        verify(chunkMapper).delete(any());
        verify(vectorStore).delete(ResourceType.NOTE, 1);
    }
}
