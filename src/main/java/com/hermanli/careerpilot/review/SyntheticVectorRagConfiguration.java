package com.hermanli.careerpilot.review;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Flyway owns the table. This builder only binds Spring AI to that pre-existing pgvector table.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "careerpilot.ai.enabled", havingValue = "true")
class SyntheticVectorRagConfiguration {

    @Bean(name = "resumeReviewVectorStore")
    @ConditionalOnProperty(name = "careerpilot.review.rag.enabled", havingValue = "true")
    VectorStore resumeReviewVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .schemaName("public")
                .vectorTableName("review_knowledge_chunk")
                .idType(PgVectorStore.PgIdType.UUID)
                .dimensions(1024)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .vectorTableValidationsEnabled(false)
                .initializeSchema(false)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "careerpilot.review.rag.enabled", havingValue = "true")
    SyntheticVectorRag syntheticVectorRag(
            @Qualifier("resumeReviewVectorStore") VectorStore resumeReviewVectorStore,
            SyntheticReviewGuide guide,
            ReviewRagProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        return new SyntheticVectorRag(
                resumeReviewVectorStore,
                guide,
                properties,
                transactionManager
        );
    }
}
