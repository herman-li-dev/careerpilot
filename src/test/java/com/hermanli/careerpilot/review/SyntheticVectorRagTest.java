package com.hermanli.careerpilot.review;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyntheticVectorRagTest {

    @Test
    void retrievalUsesTopKThresholdAndPublicSourceFilterThenRejectsTamperedOrDuplicateChunks() {
        SyntheticReviewGuide guide = new SyntheticReviewGuide();
        VectorStore vectorStore = mock(VectorStore.class);
        SyntheticReviewGuide.GuideChunk skills = guide.chunks().getFirst();
        SyntheticReviewGuide.GuideChunk projects = guide.chunks().get(1);
        Document validSkills = document(skills);
        Document duplicateSkills = document(skills);
        Document tampered = new Document(projects.id(), "injected content", SyntheticReviewGuide.metadata(projects));
        Map<String, Object> wrongSourceMetadata = new java.util.LinkedHashMap<>(SyntheticReviewGuide.metadata(projects));
        wrongSourceMetadata.put("sourceTitle", "untrusted title");
        Document wrongSource = new Document(projects.id(), projects.content(), wrongSourceMetadata);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(validSkills, duplicateSkills, tampered, wrongSource, document(projects)));

        List<SyntheticVectorRag.RetrievedGuidance> retrieved = new SyntheticVectorRag(vectorStore, guide).retrieve("Java");

        assertEquals(List.of("SKILLS", "PROJECTS"),
                retrieved.stream().map(item -> item.chunk().category()).toList());
        assertEquals(skills.content().substring(0, Math.min(240, skills.content().length())),
                retrieved.getFirst().excerpt());
        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        assertEquals(8, request.getValue().getTopK());
        assertEquals(0.50, new ReviewRagProperties().getSimilarityThreshold());
        assertEquals(0.50, request.getValue().getSimilarityThreshold());
        assertTrue(request.getValue().toString().contains(SyntheticReviewGuide.SOURCE_ID));
        assertTrue(request.getValue().toString().contains(SyntheticReviewGuide.SOURCE_VERSION));
        assertTrue(request.getValue().toString().contains("PUBLIC"));
    }

    @Test
    void failedIndexWriteRollsBackAndLeavesRetrievalNotReady() {
        SyntheticReviewGuide guide = new SyntheticReviewGuide();
        VectorStore vectorStore = mock(VectorStore.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        org.mockito.Mockito.doThrow(new IllegalStateException("EMBEDDING_SECRET"))
                .when(vectorStore).add(any());
        ReviewRagProperties properties = new ReviewRagProperties();
        SyntheticVectorRag rag = new SyntheticVectorRag(vectorStore, guide, properties, transactionManager);

        rag.indexBundledGuide();

        verify(vectorStore).delete(anyString());
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
        assertTrue(rag.retrieve("RESUME_SECRET").isEmpty());
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void retrievalCapsContextAndFailureDoesNotExposeQueryOrProviderDetail() {
        SyntheticReviewGuide guide = new SyntheticReviewGuide();
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(guide.chunks().stream()
                .map(this::document).toList());

        int onlyFirstChunk = guide.chunks().getFirst().content().length();
        List<SyntheticVectorRag.RetrievedGuidance> bounded = new SyntheticVectorRag(vectorStore, guide, onlyFirstChunk)
                .retrieve("Java");
        assertEquals(1, bounded.size());
        assertEquals("SKILLS", bounded.getFirst().chunk().category());

        VectorStore unavailable = mock(VectorStore.class);
        when(unavailable.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new IllegalStateException("VECTOR_PROVIDER_SECRET"));
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(SyntheticVectorRag.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThrows(SyntheticVectorRag.VectorRetrievalUnavailable.class,
                    () -> new SyntheticVectorRag(unavailable, guide).retrieve("RESUME_SECRET"));
        } finally {
            logger.detachAppender(appender);
        }
        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining(" "));
        assertFalse(logs.contains("VECTOR_PROVIDER_SECRET"));
        assertFalse(logs.contains("RESUME_SECRET"));
    }

    private Document document(SyntheticReviewGuide.GuideChunk chunk) {
        return new Document(chunk.id(), chunk.content(), SyntheticReviewGuide.metadata(chunk));
    }
}
