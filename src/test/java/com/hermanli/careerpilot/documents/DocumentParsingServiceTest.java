package com.hermanli.careerpilot.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermanli.careerpilot.ai.AiAvailability;
import com.hermanli.careerpilot.ai.AiUnavailableException;
import com.hermanli.careerpilot.publicrag.PublicRagGuardProperties;
import com.hermanli.careerpilot.publicrag.PublicRagGuardService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
                resumes, jobs, parser, new ObjectMapper(), new AiAvailability(false),
                emptyGuardProvider(), guardProperties(), false
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
                resumes, jobs, parser, new ObjectMapper(), new AiAvailability(false),
                emptyGuardProvider(), guardProperties(), false
        );

        assertThrows(AiUnavailableException.class, () -> service.parseJobDescription(13L, 7L));

        verify(jobs, never()).markRunning(13L, 7L);
        verify(jobs, never()).markFailed(13L, 7L, "The document could not be parsed. Please try again.");
        verify(parser, never()).parseJobDescription("Synthetic text");
    }

    @Test
    void publicResumeParsingVerifiesOwnershipBeforeGuardOrProviderWork() {
        ResumeRepository resumes = mock(ResumeRepository.class);
        DocumentParser parser = mock(DocumentParser.class);
        PublicRagGuardService guard = mock(PublicRagGuardService.class);
        DocumentParsingService service = service(resumes, parser, guard, true);

        assertThrows(
                com.hermanli.careerpilot.api.ResourceNotFoundException.class,
                () -> service.parseResume(11L, 7L, "subject-a")
        );

        verifyNoInteractions(guard, parser);
        verify(resumes, never()).markRunning(11L, 7L);
    }

    @Test
    void publicResumeParsingDoesNotBypassGuardRejection() {
        ResumeRepository resumes = mock(ResumeRepository.class);
        DocumentParser parser = mock(DocumentParser.class);
        PublicRagGuardService guard = mock(PublicRagGuardService.class);
        Resume resume = resume("NOT_STARTED", null);
        when(resumes.findByIdAndUserId(11L, 7L)).thenReturn(Optional.of(resume));
        when(guard.acquire("subject-a", List.of("Synthetic text"), 800))
                .thenThrow(new IllegalStateException("synthetic guard rejection"));
        DocumentParsingService service = service(resumes, parser, guard, true);

        assertThrows(IllegalStateException.class, () -> service.parseResume(11L, 7L, "subject-a"));

        verify(resumes, never()).markRunning(11L, 7L);
        verifyNoInteractions(parser);
    }

    @Test
    void publicResumeParsingAlwaysReleasesPermitAfterProviderFailure() {
        ResumeRepository resumes = mock(ResumeRepository.class);
        DocumentParser parser = mock(DocumentParser.class);
        PublicRagGuardService guard = mock(PublicRagGuardService.class);
        PublicRagGuardService.GuardPermit permit = mock(PublicRagGuardService.GuardPermit.class);
        Resume original = resume("NOT_STARTED", null);
        Resume failed = resume("FAILED", "The document could not be parsed. Please try again.");
        when(resumes.findByIdAndUserId(11L, 7L))
                .thenReturn(Optional.of(original), Optional.of(failed));
        when(resumes.markRunning(11L, 7L)).thenReturn(true);
        when(guard.acquire("subject-a", List.of("Synthetic text"), 800)).thenReturn(permit);
        when(parser.parseResume("Synthetic text")).thenThrow(new IllegalStateException("synthetic provider failure"));
        DocumentParsingService service = service(resumes, parser, guard, true);

        service.parseResume(11L, 7L, "subject-a");

        InOrder order = inOrder(resumes, guard, parser, permit);
        order.verify(resumes).findByIdAndUserId(11L, 7L);
        order.verify(guard).acquire("subject-a", List.of("Synthetic text"), 800);
        order.verify(resumes).markRunning(11L, 7L);
        order.verify(parser).parseResume("Synthetic text");
        order.verify(resumes).markFailed(11L, 7L, "The document could not be parsed. Please try again.");
        order.verify(resumes).findByIdAndUserId(11L, 7L);
        order.verify(permit).close();
    }

    @Test
    void publicApplicationBlocksJobParsingBeforeProviderWork() {
        ResumeRepository resumes = mock(ResumeRepository.class);
        JobDescriptionRepository jobs = mock(JobDescriptionRepository.class);
        DocumentParser parser = mock(DocumentParser.class);
        when(jobs.findByIdAndUserId(13L, 7L)).thenReturn(Optional.of(
                new JobDescription(13L, "Synthetic job", null, null, "Synthetic text", "NOT_STARTED", null,
                        Instant.EPOCH, Instant.EPOCH)
        ));
        DocumentParsingService service = new DocumentParsingService(
                resumes, jobs, parser, new ObjectMapper(), new AiAvailability(true, true),
                emptyGuardProvider(), guardProperties(), true
        );

        assertThrows(AiUnavailableException.class, () -> service.parseJobDescription(13L, 7L));

        verify(jobs, never()).markRunning(13L, 7L);
        verifyNoInteractions(parser);
    }

    private DocumentParsingService service(
            ResumeRepository resumes,
            DocumentParser parser,
            PublicRagGuardService guard,
            boolean clerkApplicationAuthenticationEnabled
    ) {
        @SuppressWarnings("unchecked")
        ObjectProvider<PublicRagGuardService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(guard);
        return new DocumentParsingService(
                resumes,
                mock(JobDescriptionRepository.class),
                parser,
                new ObjectMapper(),
                new AiAvailability(true, clerkApplicationAuthenticationEnabled),
                provider,
                guardProperties(),
                clerkApplicationAuthenticationEnabled
        );
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<PublicRagGuardService> emptyGuardProvider() {
        return mock(ObjectProvider.class);
    }

    private PublicRagGuardProperties guardProperties() {
        return new PublicRagGuardProperties();
    }

    private Resume resume(String status, String error) {
        return new Resume(11L, "Synthetic resume", "Synthetic text", status, error,
                Instant.EPOCH, Instant.EPOCH);
    }
}
