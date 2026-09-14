package com.hermanli.careerpilot.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermanli.careerpilot.ai.AiAvailability;
import com.hermanli.careerpilot.ai.AiUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentParsingServiceTest {

    @Test
    void rejectsOfflineResumeParsingWithoutChangingParseState() {
        ResumeRepository resumes = mock(ResumeRepository.class);
        JobDescriptionRepository jobs = mock(JobDescriptionRepository.class);
        DocumentParser parser = mock(DocumentParser.class);
        when(resumes.findByIdAndUserId(11L, 7L)).thenReturn(Optional.of(
                new Resume(11L, "Synthetic resume", "Synthetic text", "NOT_STARTED", null,
                        Instant.EPOCH, Instant.EPOCH)
        ));
        DocumentParsingService service = new DocumentParsingService(
                resumes, jobs, parser, new ObjectMapper(), new AiAvailability(false)
        );

        assertThrows(AiUnavailableException.class, () -> service.parseResume(11L, 7L));

        verify(resumes, never()).markRunning(11L, 7L);
        verify(resumes, never()).markFailed(11L, 7L, "The document could not be parsed. Please try again.");
        verify(parser, never()).parseResume("Synthetic text");
    }

    @Test
    void rejectsOfflineJobParsingWithoutChangingParseState() {
        ResumeRepository resumes = mock(ResumeRepository.class);
        JobDescriptionRepository jobs = mock(JobDescriptionRepository.class);
        DocumentParser parser = mock(DocumentParser.class);
        when(jobs.findByIdAndUserId(13L, 7L)).thenReturn(Optional.of(
                new JobDescription(13L, "Synthetic job", null, null, "Synthetic text", "NOT_STARTED", null,
                        Instant.EPOCH, Instant.EPOCH)
        ));
        DocumentParsingService service = new DocumentParsingService(
                resumes, jobs, parser, new ObjectMapper(), new AiAvailability(false)
        );

        assertThrows(AiUnavailableException.class, () -> service.parseJobDescription(13L, 7L));

        verify(jobs, never()).markRunning(13L, 7L);
        verify(jobs, never()).markFailed(13L, 7L, "The document could not be parsed. Please try again.");
        verify(parser, never()).parseJobDescription("Synthetic text");
    }
}
