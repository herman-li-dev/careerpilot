package com.hermanli.careerpilot.database;

import com.hermanli.careerpilot.identity.UserAccountRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "careerpilot.ai.enabled=false"
})
@Tag("external")
@Transactional
class ResumeReviewVectorSchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Test
    void resolvesClerkIdentityToOneStableInternalUser() {
        String issuer = "https://migration-test.clerk.accounts.dev";
        String subject = "user_migration_test";

        long firstId = userAccountRepository.resolveOrCreateClerkUser(issuer, subject);
        long secondId = userAccountRepository.resolveOrCreateClerkUser(issuer, subject);

        assertEquals(firstId, secondId);
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from app_user where clerk_issuer = ? and clerk_subject = ?",
                Integer.class,
                issuer,
                subject
        ));
    }

    @Test
    void installsTheVectorExtensionAndCreatesOnlyTheVectorStoreColumns() {
        assertEquals("vector", jdbcTemplate.queryForObject(
                "select extname from pg_extension where extname = 'vector'",
                String.class
        ));
        assertEquals(Set.of("id", "content", "metadata", "embedding"), tableColumns());
        assertEquals("vector(1024)", jdbcTemplate.queryForObject(
                """
                select format_type(attribute.atttypid, attribute.atttypmod)
                from pg_attribute attribute
                join pg_class relation on relation.oid = attribute.attrelid
                join pg_namespace namespace on namespace.oid = relation.relnamespace
                where namespace.nspname = 'public'
                  and relation.relname = 'review_knowledge_chunk'
                  and attribute.attname = 'embedding'
                """,
                String.class
        ));
    }

    @Test
    void createsCosineHnswAndMetadataFilterIndexes() {
        assertEquals(
                "CREATE INDEX ix_review_knowledge_chunk_embedding_cosine ON public.review_knowledge_chunk USING hnsw (embedding vector_cosine_ops)",
                indexDefinition("ix_review_knowledge_chunk_embedding_cosine")
        );
        String filterIndex = indexDefinition("ix_review_knowledge_chunk_metadata_filter");
        assertTrue(filterIndex.contains("((metadata ->> 'sourceId'::text))"));
        assertTrue(filterIndex.contains("((metadata ->> 'sourceVersion'::text))"));
        assertTrue(filterIndex.contains("((metadata ->> 'visibility'::text))"));
        assertTrue(filterIndex.contains("((metadata ->> 'indexVersion'::text))"));
    }

    @Test
    void keepsResumeStorageFreeOfVectorColumns() {
        Set<String> resumeColumns = tableColumns("resume");

        assertFalse(resumeColumns.contains("embedding"));
        assertFalse(resumeColumns.contains("metadata"));
        assertFalse(resumeColumns.contains("review_knowledge_chunk"));
    }

    @Test
    void pgVectorStoreAddsFiltersAndRetrievesWithAStubEmbeddingModel() {
        VectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, new StubEmbeddingModel())
                .schemaName("public")
                .vectorTableName("review_knowledge_chunk")
                .idType(PgVectorStore.PgIdType.UUID)
                .dimensions(1024)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .vectorTableValidationsEnabled(false)
                .initializeSchema(false)
                .build();
        Document allowed = new Document(
                "10000000-0000-0000-0000-000000000001",
                "Java skills evidence",
                Map.of("sourceId", "allowed-public-source", "section", "Skills", "indexVersion", "test-v1")
        );
        Document filtered = new Document(
                "10000000-0000-0000-0000-000000000002",
                "Java skills from another source",
                Map.of("sourceId", "other-source", "section", "Skills", "indexVersion", "test-v1")
        );
        Document unrelated = new Document(
                "10000000-0000-0000-0000-000000000003",
                "Deployment project evidence",
                Map.of("sourceId", "allowed-public-source", "section", "Projects", "indexVersion", "test-v1")
        );

        vectorStore.add(List.of(allowed, filtered, unrelated));
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query("Java skills")
                .topK(1)
                .similarityThreshold(0.95)
                .filterExpression("sourceId == 'allowed-public-source'")
                .build());

        assertEquals(1, results.size());
        assertEquals(allowed.getId(), results.getFirst().getId());
        assertEquals("allowed-public-source", results.getFirst().getMetadata().get("sourceId"));
        assertEquals("Skills", results.getFirst().getMetadata().get("section"));

        vectorStore.delete("sourceId == 'other-source' && indexVersion == 'test-v1'");
        List<Document> deletedSourceResults = vectorStore.similaritySearch(SearchRequest.builder()
                .query("Java skills")
                .topK(3)
                .similarityThreshold(0.95)
                .filterExpression("sourceId == 'other-source'")
                .build());
        assertTrue(deletedSourceResults.isEmpty());
    }

    private Set<String> tableColumns() {
        return tableColumns("review_knowledge_chunk");
    }

    private Set<String> tableColumns(String tableName) {
        List<String> columns = jdbcTemplate.queryForList(
                """
                select column_name
                from information_schema.columns
                where table_schema = 'public' and table_name = ?
                """,
                String.class,
                tableName
        );
        return new HashSet<>(columns);
    }

    private String indexDefinition(String indexName) {
        return jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where schemaname = 'public' and indexname = ?",
                String.class,
                indexName
        );
    }

    private static final class StubEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = java.util.stream.IntStream.range(0, request.getInstructions().size())
                    .mapToObj(index -> new Embedding(vector(request.getInstructions().get(index)), index))
                    .toList();
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        @Override
        public int dimensions() {
            return 1024;
        }

        private float[] vector(String text) {
            float[] vector = new float[1024];
            String normalized = text.toLowerCase(java.util.Locale.ROOT);
            if (normalized.contains("java") || normalized.contains("skills")) {
                vector[0] = 1.0f;
            } else if (normalized.contains("deployment") || normalized.contains("project")) {
                vector[1] = 1.0f;
            } else {
                vector[2] = 1.0f;
            }
            return vector;
        }
    }
}
