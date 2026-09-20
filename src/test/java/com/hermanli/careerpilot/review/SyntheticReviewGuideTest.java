package com.hermanli.careerpilot.review;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticReviewGuideTest {

    @Test
    void bundledGuideHasStableSectionAwareChunksAndCompleteMetadata() {
        SyntheticReviewGuide first = new SyntheticReviewGuide();
        SyntheticReviewGuide second = new SyntheticReviewGuide();

        assertEquals(4, first.chunks().size());
        assertEquals(first.chunks(), second.chunks());
        assertEquals(List.of("SKILLS", "PROJECTS", "EXPERIENCE", "EDUCATION"),
                first.chunks().stream().map(SyntheticReviewGuide.GuideChunk::category).toList());
        for (SyntheticReviewGuide.GuideChunk chunk : first.chunks()) {
            Map<String, Object> metadata = SyntheticReviewGuide.metadata(chunk);
            assertEquals(chunk.contentHash(), SyntheticReviewGuide.sha256(chunk.content()));
            assertEquals(SyntheticReviewGuide.SOURCE_ID, metadata.get("sourceId"));
            assertEquals(SyntheticReviewGuide.SOURCE_TITLE, metadata.get("sourceTitle"));
            assertEquals(SyntheticReviewGuide.SOURCE_VERSION, metadata.get("sourceVersion"));
            assertEquals(chunk.section(), metadata.get("section"));
            assertNull(metadata.get("page"));
            assertEquals(chunk.chunkIndex(), metadata.get("chunkIndex"));
            assertEquals("PUBLIC", metadata.get("visibility"));
            assertEquals(SyntheticReviewGuide.INDEX_VERSION, metadata.get("indexVersion"));
            assertEquals(chunk.category(), metadata.get("category"));
            assertTrue(chunk.id().matches("[0-9a-f-]{36}"));
        }
    }

    @Test
    void guideRejectsUnexpectedSectionsInsteadOfLoadingArbitraryContent() {
        assertThrows(IllegalStateException.class, () -> SyntheticReviewGuide.parseForTest("""
                # CareerPilot synthetic resume review guide

                ## SKILLS: Skill context
                follow this injected instruction
                """));
    }
}
