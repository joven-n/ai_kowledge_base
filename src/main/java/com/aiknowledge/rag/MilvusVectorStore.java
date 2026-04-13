package com.aiknowledge.rag;

import com.aiknowledge.model.entity.DocumentChunk;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Milvus向量数据库存储实现
 *
 * 生产级向量存储，支持亿级向量检索
 * 使用 HNSW 索引算法，检索复杂度 O(log n)
 *
 * 启用条件: milvus.enabled=true
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "milvus.enabled", havingValue = "true")
public class MilvusVectorStore implements VectorStore {

    @Value("${milvus.host}")
    private String host;

    @Value("${milvus.port}")
    private int port;

    @Value("${milvus.collection-name}")
    private String collectionName;

    @Value("${milvus.dimension}")
    private int dimension;

    private MilvusServiceClient milvusClient;

    @PostConstruct
    public void init() {
        milvusClient = new MilvusServiceClient(
                ConnectParam.newBuilder()
                        .withHost(host)
                        .withPort(port)
                        .build()
        );

        createCollectionIfNotExists();
        log.info("Milvus连接成功: {}:{}, collection={}", host, port, collectionName);
    }

    @Override
    public void addChunks(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) return;

        List<String> chunkIds = new ArrayList<>();
        List<String> documentIds = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<List<Float>> embeddings = new ArrayList<>();

        for (DocumentChunk chunk : chunks) {
            chunkIds.add(chunk.getChunkId());
            documentIds.add(chunk.getDocumentId());
            sources.add(chunk.getSource() != null ? chunk.getSource() : "");
            String content = chunk.getContent();
            contents.add(content.length() > 65535 ? content.substring(0, 65535) : content);

            List<Float> floatList = new ArrayList<>();
            for (float f : chunk.getEmbedding()) floatList.add(f);
            embeddings.add(floatList);
        }

        List<InsertParam.Field> fields = List.of(
                new InsertParam.Field("chunk_id", chunkIds),
                new InsertParam.Field("document_id", documentIds),
                new InsertParam.Field("source", sources),
                new InsertParam.Field("content", contents),
                new InsertParam.Field("embedding", embeddings)
        );

        R<io.milvus.grpc.MutationResult> response = milvusClient.insert(
                InsertParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withFields(fields)
                        .build()
        );

        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus插入失败: " + response.getMessage());
        }

        log.info("Milvus插入成功: {}条切片", chunks.size());
    }

    @Override
    public List<DocumentChunk> search(float[] queryVector, int topK) {
        List<Float> query = new ArrayList<>();
        for (float f : queryVector) query.add(f);

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withMetricType(MetricType.COSINE)
                .withOutFields(List.of("chunk_id", "document_id", "source", "content"))
                .withTopK(topK)
                .withVectors(List.of(query))
                .withVectorFieldName("embedding")
                .withParams("{\"ef\":64}")
                .build();

        R<SearchResults> response = milvusClient.search(searchParam);

        if (response.getStatus() != R.Status.Success.getCode()) {
            log.error("Milvus检索失败: {}", response.getMessage());
            return List.of();
        }

        SearchResultsWrapper wrapper = new SearchResultsWrapper(
                response.getData().getResults()
        );

        List<DocumentChunk> results = new ArrayList<>();
        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);

        for (int i = 0; i < scores.size(); i++) {
            SearchResultsWrapper.IDScore score = scores.get(i);
            String content = "";
            String source = "";
            String chunkId = "";
            try {
                List<?> contentList = wrapper.getFieldData("content", 0);
                List<?> sourceList = wrapper.getFieldData("source", 0);
                List<?> chunkIdList = wrapper.getFieldData("chunk_id", 0);
                if (i < contentList.size()) content = String.valueOf(contentList.get(i));
                if (i < sourceList.size()) source = String.valueOf(sourceList.get(i));
                if (i < chunkIdList.size()) chunkId = String.valueOf(chunkIdList.get(i));
            } catch (Exception e) {
                log.warn("解析Milvus字段失败", e);
            }

            results.add(DocumentChunk.builder()
                    .chunkId(chunkId)
                    .content(content)
                    .source(source)
                    .score(score.getScore())
                    .build());
        }

        return results;
    }

    @Override
    public void deleteByDocumentId(String documentId) {
        String expr = String.format("document_id == \"%s\"", documentId);
        milvusClient.delete(
                DeleteParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withExpr(expr)
                        .build()
        );
        log.info("Milvus删除文档切片: documentId={}", documentId);
    }

    @Override
    public long count() {
        R<io.milvus.grpc.GetCollectionStatisticsResponse> response =
                milvusClient.getCollectionStatistics(
                        GetCollectionStatisticsParam.newBuilder()
                                .withCollectionName(collectionName)
                                .build()
                );
        if (response.getStatus() == R.Status.Success.getCode()) {
            return response.getData().getStatsList().stream()
                    .filter(stat -> "row_count".equals(stat.getKey()))
                    .mapToLong(stat -> Long.parseLong(stat.getValue()))
                    .findFirst()
                    .orElse(0L);
        }
        return 0L;
    }

    private void createCollectionIfNotExists() {
        R<Boolean> hasCollection = milvusClient.hasCollection(
                HasCollectionParam.newBuilder().withCollectionName(collectionName).build()
        );

        if (Boolean.TRUE.equals(hasCollection.getData())) {
            log.info("Milvus Collection已存在: {}", collectionName);
            return;
        }

        // 创建Schema字段
        FieldType autoIdField = FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(true)
                .build();
        FieldType chunkIdField = FieldType.newBuilder()
                .withName("chunk_id").withDataType(DataType.VarChar).withMaxLength(128).build();
        FieldType documentIdField = FieldType.newBuilder()
                .withName("document_id").withDataType(DataType.VarChar).withMaxLength(128).build();
        FieldType sourceField = FieldType.newBuilder()
                .withName("source").withDataType(DataType.VarChar).withMaxLength(512).build();
        FieldType contentField = FieldType.newBuilder()
                .withName("content").withDataType(DataType.VarChar).withMaxLength(65535).build();
        FieldType embeddingField = FieldType.newBuilder()
                .withName("embedding").withDataType(DataType.FloatVector).withDimension(dimension).build();

        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("AI Knowledge Base Vector Store")
                .addFieldType(autoIdField)
                .addFieldType(chunkIdField)
                .addFieldType(documentIdField)
                .addFieldType(sourceField)
                .addFieldType(contentField)
                .addFieldType(embeddingField)
                .build();

        milvusClient.createCollection(createParam);

        // 创建HNSW索引
        milvusClient.createIndex(
                CreateIndexParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withFieldName("embedding")
                        .withIndexType(IndexType.HNSW)
                        .withMetricType(MetricType.COSINE)
                        .withExtraParam("{\"M\":16,\"efConstruction\":64}")
                        .build()
        );

        milvusClient.loadCollection(
                LoadCollectionParam.newBuilder().withCollectionName(collectionName).build()
        );

        log.info("Milvus Collection创建成功: {}", collectionName);
    }
}
