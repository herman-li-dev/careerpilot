package com.hermanli.careerpilot.review;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SyntheticVectorRagConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SyntheticVectorRagConfiguration.class)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(EmbeddingModel.class, () -> mock(EmbeddingModel.class))
            .withBean(SyntheticReviewGuide.class, SyntheticReviewGuide::new)
            .withBean(ReviewRagProperties.class, ReviewRagProperties::new)
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));

    @Test
    void createsTheStoreAndRagOnlyWhenAiAndRagAreEnabled() {
        contextRunner.withPropertyValues(
                        "careerpilot.ai.enabled=true",
                        "careerpilot.review.rag.enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasBean("resumeReviewVectorStore");
                    assertThat(context).hasSingleBean(VectorStore.class);
                    assertThat(context).hasSingleBean(SyntheticVectorRag.class);
                });
    }

    @Test
    void doesNotCreateVectorBeansWhenAiIsDisabled() {
        contextRunner.withPropertyValues(
                        "careerpilot.ai.enabled=false",
                        "careerpilot.review.rag.enabled=true"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean("resumeReviewVectorStore");
                    assertThat(context).doesNotHaveBean(SyntheticVectorRag.class);
                });
    }

    @Test
    void doesNotCreateVectorBeansWhenRagIsDisabled() {
        contextRunner.withPropertyValues(
                        "careerpilot.ai.enabled=true",
                        "careerpilot.review.rag.enabled=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean("resumeReviewVectorStore");
                    assertThat(context).doesNotHaveBean(SyntheticVectorRag.class);
                });
    }
}
