package com.hermanli.careerpilot.publicrag;

import com.hermanli.careerpilot.api.CareerPilotExceptionHandler;
import com.hermanli.careerpilot.controller.careerpilot.PublicRagResumeUploadController;
import com.hermanli.careerpilot.documents.ResumeUploadTextExtractor;
import com.hermanli.careerpilot.identity.UserAccountService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicRagResumeUploadControllerTest {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String TOKEN = "valid-token";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JwtDecoder jwtDecoder = mock(JwtDecoder.class);
        UserAccountService userAccountService = mock(UserAccountService.class);
        when(jwtDecoder.decode(TOKEN)).thenReturn(Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .issuer("https://example.clerk.accounts.dev")
                .subject("user_123")
                .issuedAt(Instant.parse("2026-09-20T00:00:00Z"))
                .expiresAt(Instant.parse("2026-09-20T01:00:00Z"))
                .build());
        when(userAccountService.resolveOrCreateClerkUser(
                "https://example.clerk.accounts.dev",
                "user_123"
        )).thenReturn(42L);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PublicRagResumeUploadController(new ResumeUploadTextExtractor())
                )
                .setControllerAdvice(new CareerPilotExceptionHandler())
                .addInterceptors(
                        new PublicRagSecurityAuditInterceptor(),
                        new PublicRagAuthenticationInterceptor(jwtDecoder, userAccountService)
                )
                .build();
    }

    @Test
    void requiresClerkAuthentication() throws Exception {
        mockMvc.perform(multipart("/rag/resume/validate").file(docx("Synthetic resume")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void validatesDocxWithoutReturningResumeTextOrIdentity() throws Exception {
        String canary = "PRIVATE_RESUME_CANARY";
        String response = mockMvc.perform(multipart("/rag/resume/validate")
                        .file(docx(canary))
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true))
                .andExpect(jsonPath("$.data.documentType").value("DOCX"))
                .andExpect(jsonPath("$.data.extractedCharacterCount").value(canary.length()))
                .andExpect(jsonPath("$.data.text").doesNotExist())
                .andExpect(jsonPath("$.data.fileName").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(canary, "resume.docx", "user_123");
    }

    @Test
    void rejectsUnsupportedAndOversizedFilesWithSafeErrors() throws Exception {
        String canary = "PRIVATE_RESUME_CANARY";
        String unsupportedResponse = mockMvc.perform(multipart("/rag/resume/validate")
                        .file(new MockMultipartFile("file", "resume.txt", "text/plain", canary.getBytes()))
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FILE_TYPE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(unsupportedResponse).doesNotContain(canary);

        mockMvc.perform(multipart("/rag/resume/validate")
                        .file(new MockMultipartFile(
                                "file",
                                "resume.pdf",
                                "application/pdf",
                                new byte[5 * 1024 * 1024 + 1]
                        ))
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"));
    }

    private MockMultipartFile docx(String text) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
        }
        return new MockMultipartFile("file", "resume.docx", DOCX_MIME, output.toByteArray());
    }
}
