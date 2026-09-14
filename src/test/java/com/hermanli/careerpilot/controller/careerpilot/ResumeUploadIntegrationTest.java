package com.hermanli.careerpilot.controller.careerpilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermanli.careerpilot.documents.DocumentParser;
import com.hermanli.careerpilot.identity.RegisteredUser;
import com.hermanli.careerpilot.identity.SessionCookieService;
import com.hermanli.careerpilot.identity.SessionTokenService;
import com.hermanli.careerpilot.identity.UserAccountService;
import jakarta.servlet.http.Cookie;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=true")
@AutoConfigureMockMvc
@Import(ResumeUploadIntegrationTest.FakeDocumentParserConfiguration.class)
@Tag("external")
@Transactional
class ResumeUploadIntegrationTest {

    private static final String PDF_MIME = "application/pdf";
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String VALID_RESUME_JSON = """
            {"skills":["Java"],"education":[],"projects":[],"workExperience":[],"certifications":[]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private SessionTokenService sessionTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakeDocumentParser fakeDocumentParser;

    @Test
    void uploadsPdfWithDefaultTitleAndCanUseTheExistingParsingFlow() throws Exception {
        Cookie cookie = sessionCookie(createAccount());
        long resumeId = upload(cookie, pdf("Synthetic PDF resume\nJava and Spring"), null);

        mockMvc.perform(get("/resumes/{resumeId}", resumeId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Uploaded resume"))
                .andExpect(jsonPath("$.data.rawText").value("Synthetic PDF resume\nJava and Spring"))
                .andExpect(jsonPath("$.data.parseStatus").value("NOT_STARTED"));
        mockMvc.perform(post("/resumes/{resumeId}/parse", resumeId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("COMPLETED"));
        assertEquals("Synthetic PDF resume\nJava and Spring", fakeDocumentParser.lastResumeInput);
    }

    @Test
    void uploadsDocxAsTextOnlyAndKeepsUsersIsolated() throws Exception {
        long ownerId = createAccount();
        long otherId = createAccount();
        Cookie ownerCookie = sessionCookie(ownerId);
        Cookie otherCookie = sessionCookie(otherId);
        long resumeId = upload(ownerCookie, docx("Synthetic DOCX resume\nKotlin"), "Backend resume");

        mockMvc.perform(get("/resumes/{resumeId}", resumeId).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Backend resume"))
                .andExpect(jsonPath("$.data.rawText").value("Synthetic DOCX resume\nKotlin"));
        mockMvc.perform(get("/resumes/{resumeId}", resumeId).cookie(otherCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/resumes/{resumeId}/parse", resumeId).cookie(otherCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertEquals(1, resumeCount(ownerId));
        assertEquals(0, resumeCount(otherId));
    }

    @Test
    void rejectsUnsupportedMismatchedEmptyAndUnreadableFilesWithoutPersistingTextOrDetails() throws Exception {
        Cookie cookie = sessionCookie(createAccount());
        int before = totalResumeCount();
        String canary = "CANARY_RESUME_TEXT_MUST_NOT_APPEAR";

        assertUploadFailure(cookie, new MockMultipartFile("file", "resume.txt", "text/plain", canary.getBytes()),
                "UNSUPPORTED_FILE_TYPE", canary);
        assertUploadFailure(cookie, new MockMultipartFile("file", "resume.pdf", DOCX_MIME, pdfBytes("safe")),
                "FILE_TYPE_MISMATCH", canary);
        assertUploadFailure(cookie, new MockMultipartFile("file", "resume.pdf", PDF_MIME, new byte[0]),
                "EMPTY_FILE", canary);
        assertUploadFailure(cookie, new MockMultipartFile("file", "resume.pdf", PDF_MIME, "%PDF-broken".getBytes()),
                "INVALID_DOCUMENT", canary);
        assertUploadFailure(cookie, new MockMultipartFile("file", "resume.docx", DOCX_MIME, "PK\u0003\u0004broken".getBytes()),
                "INVALID_DOCUMENT", canary);

        assertEquals(before, totalResumeCount());
    }

    @Test
    void rejectsScannedMacroAndOversizedDocumentsBeforeWritingAnything() throws Exception {
        Cookie cookie = sessionCookie(createAccount());
        int before = totalResumeCount();

        assertUploadFailure(cookie, pdf(""), "NO_EXTRACTABLE_TEXT", "server");
        assertUploadFailure(cookie, macroDocx(), "UNSAFE_DOCUMENT", "server");
        assertUploadFailureWithStatus(cookie, new MockMultipartFile(
                "file", "resume.pdf", PDF_MIME, new byte[5 * 1024 * 1024 + 1]
        ), "FILE_TOO_LARGE", "server", 413);

        assertEquals(before, totalResumeCount());
    }

    @Test
    void rejectsExtractedTextOverTheLimitWithoutPersistingAResume() throws Exception {
        Cookie cookie = sessionCookie(createAccount());
        int before = totalResumeCount();
        assertUploadFailure(cookie, pdf("x".repeat(100_001)), "EXTRACTED_TEXT_TOO_LARGE", "server");
        assertEquals(before, totalResumeCount());
    }

    private long upload(Cookie cookie, MockMultipartFile file, String title) throws Exception {
        var request = multipart("/resumes/upload").file(file).cookie(cookie);
        if (title != null) {
            request.param("title", title);
        }
        String response = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parseStatus").value("NOT_STARTED"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private void assertUploadFailure(Cookie cookie, MockMultipartFile file, String expectedCode, String forbiddenText)
            throws Exception {
        assertUploadFailureWithStatus(cookie, file, expectedCode, forbiddenText, 400);
    }

    private void assertUploadFailureWithStatus(
            Cookie cookie,
            MockMultipartFile file,
            String expectedCode,
            String forbiddenText,
            int expectedStatus
    ) throws Exception {
        String response = mockMvc.perform(multipart("/resumes/upload").file(file).cookie(cookie))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.error.code").value(expectedCode))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains(forbiddenText));
    }

    private MockMultipartFile pdf(String text) throws IOException {
        return new MockMultipartFile("file", "resume.pdf", PDF_MIME, pdfBytes(text));
    }

    private byte[] pdfBytes(String text) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (!text.isEmpty()) {
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 720);
                    for (String line : text.split("\\R", -1)) {
                        if (!line.isEmpty()) {
                            content.showText(line);
                        }
                        content.newLineAtOffset(0, -16);
                    }
                    content.endText();
                }
            }
            document.save(output);
        }
        return output.toByteArray();
    }

    private MockMultipartFile docx(String text) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
        }
        return new MockMultipartFile("file", "resume.docx", DOCX_MIME, output.toByteArray());
    }

    private MockMultipartFile macroDocx() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addZipEntry(zip, "[Content_Types].xml", "<Types/>".getBytes());
            addZipEntry(zip, "word/document.xml", "<document/>".getBytes());
            addZipEntry(zip, "word/vbaProject.bin", new byte[]{1});
        }
        return new MockMultipartFile("file", "resume.docx", DOCX_MIME, output.toByteArray());
    }

    private void addZipEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private long createAccount() {
        RegisteredUser user = userAccountService.register(
                "u01-" + UUID.randomUUID() + "@example.com",
                UUID.randomUUID().toString()
        );
        return user.id();
    }

    private Cookie sessionCookie(long userId) {
        Cookie cookie = new Cookie(SessionCookieService.COOKIE_NAME, sessionTokenService.issue(userId));
        cookie.setHttpOnly(true);
        cookie.setPath("/api");
        return cookie;
    }

    private int resumeCount(long userId) {
        return jdbcTemplate.queryForObject("select count(*) from resume where user_id = ?", Integer.class, userId);
    }

    private int totalResumeCount() {
        return jdbcTemplate.queryForObject("select count(*) from resume", Integer.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeDocumentParserConfiguration {

        @Bean
        @Primary
        FakeDocumentParser documentParser() {
            return new FakeDocumentParser();
        }
    }

    static class FakeDocumentParser implements DocumentParser {
        private String lastResumeInput;

        @Override
        public String parseResume(String rawText) {
            lastResumeInput = rawText;
            return VALID_RESUME_JSON;
        }

        @Override
        public String parseJobDescription(String rawText) {
            return "{}";
        }
    }
}
