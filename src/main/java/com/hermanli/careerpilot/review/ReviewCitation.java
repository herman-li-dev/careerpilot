package com.hermanli.careerpilot.review;

/**
 * A bounded reference to public synthetic guidance returned by the current retrieval.
 */
public record ReviewCitation(
        String sourceId,
        String sourceTitle,
        String sourceVersion,
        String section,
        Integer page,
        int chunkIndex,
        String excerpt
) {
}
