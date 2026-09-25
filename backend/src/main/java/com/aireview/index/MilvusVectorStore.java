package com.aireview.index;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq.CollectionSchema;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp.SearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Milvus 向量库实现。集合在首次写入时惰性创建，地址、集合名、维度与超时均由配置注入。
 *
 * <p>标量字段：{@code chunk_id}(主键)、{@code user_id}、{@code resource_type}、
 * {@code resource_id}、{@code content_version}、{@code chunk_index}、{@code content}，
 * 以及稠密向量字段 {@code embedding}。
 */
public class MilvusVectorStore implements VectorStore {
    private static final String FIELD_CHUNK_ID = "chunk_id";
    private static final String FIELD_USER_ID = "user_id";
    private static final String FIELD_RESOURCE_TYPE = "resource_type";
    private static final String FIELD_RESOURCE_ID = "resource_id";
    private static final String FIELD_CONTENT_VERSION = "content_version";
    private static final String FIELD_CHUNK_INDEX = "chunk_index";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_EMBEDDING = "embedding";
    private static final int CONTENT_MAX_LENGTH = 65535;

    private final String collection;
    private final int dimension;
    private final MilvusClientV2 client;
    private final Gson gson = new Gson();

    private volatile boolean initialized = false;

    public MilvusVectorStore(String host, int port, String collection, int dimension, long timeoutMs) {
        this.collection = collection;
        this.dimension = dimension;
        this.client = new MilvusClientV2(ConnectConfig.builder()
            .uri("http://" + host + ":" + port)
            .connectTimeoutMs(timeoutMs)
            .rpcDeadlineMs(timeoutMs)
            .build());
    }

    @Override
    public void replace(ResourceType resourceType, long resourceId, List<VectorRecord> records) {
        ensureCollection();
        delete(resourceType, resourceId);
        if (records.isEmpty()) {
            return;
        }
        List<JsonObject> rows = records.stream().map(this::toRow).toList();
        client.upsert(UpsertReq.builder().collectionName(collection).data(rows).build());
    }

    @Override
    public void delete(ResourceType resourceType, long resourceId) {
        if (!initialized) {
            return;
        }
        client.delete(DeleteReq.builder()
            .collectionName(collection)
            .filter(resourceFilter(resourceType, resourceId))
            .build());
    }

    @Override
    public List<VectorHit> search(VectorSearchFilter filter, float[] queryVector) {
        ensureCollection();
        String expression = "user_id == " + filter.userId()
            + " && " + resourceFilter(filter.resourceType(), filter.resourceId())
            + " && content_version == " + filter.contentVersion();
        var response = client.search(SearchReq.builder()
            .collectionName(collection)
            .topK(filter.topK())
            .annsField(FIELD_EMBEDDING)
            .metricType(IndexParam.MetricType.COSINE)
            .filter(expression)
            .outputFields(List.of(FIELD_CHUNK_ID, FIELD_RESOURCE_ID, FIELD_CONTENT_VERSION, FIELD_CONTENT))
            .data(List.of(new FloatVec(queryVector)))
            .build());

        List<VectorHit> hits = new ArrayList<>();
        List<SearchResult> results = response.getSearchResults().isEmpty()
            ? List.of() : response.getSearchResults().get(0);
        for (SearchResult result : results) {
            Map<String, Object> entity = result.getEntity();
            hits.add(new VectorHit(
                toLong(entity.get(FIELD_CHUNK_ID)),
                toLong(entity.get(FIELD_RESOURCE_ID)),
                (int) toLong(entity.get(FIELD_CONTENT_VERSION)),
                String.valueOf(entity.getOrDefault(FIELD_CONTENT, "")),
                result.getScore() == null ? 0f : result.getScore()));
        }
        return hits;
    }

    private JsonObject toRow(VectorRecord record) {
        JsonObject row = new JsonObject();
        row.addProperty(FIELD_CHUNK_ID, record.chunkId());
        row.addProperty(FIELD_USER_ID, record.userId());
        row.addProperty(FIELD_RESOURCE_TYPE, record.resourceType().name());
        row.addProperty(FIELD_RESOURCE_ID, record.resourceId());
        row.addProperty(FIELD_CONTENT_VERSION, record.contentVersion());
        row.addProperty(FIELD_CHUNK_INDEX, record.chunkIndex());
        row.addProperty(FIELD_CONTENT, record.content());
        row.add(FIELD_EMBEDDING, gson.toJsonTree(record.vector()));
        return row;
    }

    private String resourceFilter(ResourceType resourceType, long resourceId) {
        return "resource_type == \"" + resourceType.name() + "\" && resource_id == " + resourceId;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(value.toString());
    }

    private synchronized void ensureCollection() {
        if (initialized) {
            return;
        }
        boolean exists = Boolean.TRUE.equals(client.hasCollection(
            HasCollectionReq.builder().collectionName(collection).build()));
        if (!exists) {
            CollectionSchema schema = CollectionSchema.builder().build();
            schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_CHUNK_ID).dataType(DataType.Int64).isPrimaryKey(true).autoID(false).build());
            schema.addField(AddFieldReq.builder().fieldName(FIELD_USER_ID).dataType(DataType.Int64).build());
            schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_RESOURCE_TYPE).dataType(DataType.VarChar).maxLength(16).build());
            schema.addField(AddFieldReq.builder().fieldName(FIELD_RESOURCE_ID).dataType(DataType.Int64).build());
            schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_CONTENT_VERSION).dataType(DataType.Int64).build());
            schema.addField(AddFieldReq.builder().fieldName(FIELD_CHUNK_INDEX).dataType(DataType.Int64).build());
            schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_CONTENT).dataType(DataType.VarChar).maxLength(CONTENT_MAX_LENGTH).build());
            schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_EMBEDDING).dataType(DataType.FloatVector).dimension(dimension).build());

            IndexParam indexParam = IndexParam.builder()
                .fieldName(FIELD_EMBEDDING)
                .metricType(IndexParam.MetricType.COSINE)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .build();

            client.createCollection(CreateCollectionReq.builder()
                .collectionName(collection)
                .collectionSchema(schema)
                .indexParams(List.of(indexParam))
                .build());
        }
        client.loadCollection(LoadCollectionReq.builder().collectionName(collection).build());
        initialized = true;
    }
}
