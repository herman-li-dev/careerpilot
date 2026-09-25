package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.documents.ResumeUploadException;
import com.hermanli.careerpilot.documents.ResumeUploadTextExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResumeUploadTextExtractorTest {

    private static final String PDF_MIME = "application/pdf";
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final ResumeUploadTextExtractor extractor = new ResumeUploadTextExtractor();

    @Test
    void extractsTextFromValidPdfAndDocx() throws Exception {
        assertEquals("Synthetic PDF resume\nJava", extractor.extract(pdf("Synthetic PDF resume\nJava")));
        assertEquals("Synthetic DOCX resume\nSpring", extractor.extract(docx("Synthetic DOCX resume\nSpring")));
    }

    @Test
    void preservesVisualWordGapsFromPositionedPdfChunksWithoutBreakingTechnicalTokens() throws Exception {
        assertEquals("Cloud native Java", extractor.extract(positionedChunkPdf()));
        assertEquals(
                "https://example.test Node.js C++",
                extractor.extract(pdf("https://example.test Node.js C++"))
        );
    }

    @Test
    void rejectsUntrustedOrUnsupportedFilesWithStableSafeCodes() throws Exception {
        assertCode(new MockMultipartFile("file", "resume.txt", "text/plain", "not a resume".getBytes()),
                "UNSUPPORTED_FILE_TYPE");
        assertCode(new MockMultipartFile("file", "resume.pdf", DOCX_MIME, pdfBytes("valid body")),
                "FILE_TYPE_MISMATCH");
        assertCode(new MockMultipartFile("file", "resume.pdf", PDF_MIME, new byte[0]), "EMPTY_FILE");
        assertCode(new MockMultipartFile("file", "resume.pdf", PDF_MIME, "%PDF-broken".getBytes()),
                "INVALID_DOCUMENT");
        assertCode(new MockMultipartFile("file", "resume.docx", DOCX_MIME, "PK\u0003\u0004bad".getBytes()),
                "INVALID_DOCUMENT");
        assertCode(pdf(""), "NO_EXTRACTABLE_TEXT");
        assertCode(macroDocx(), "UNSAFE_DOCUMENT");
        assertCode(pathTraversalDocx(), "UNSAFE_DOCUMENT");
        assertCode(expandingDocx(), "UNSAFE_DOCUMENT");
        assertCode(new MockMultipartFile(
                "file", "resume.docx", DOCX_MIME,
                new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}
        ), "ENCRYPTED_DOCUMENT");
        assertCode(encryptedPdf(), "ENCRYPTED_DOCUMENT");
        assertCode(new MockMultipartFile("file", "resume.pdf", PDF_MIME, new byte[5 * 1024 * 1024 + 1]),
                "FILE_TOO_LARGE");
    }

    @Test
    void rejectsExtractedTextAboveTheLimit() throws Exception {
        assertCode(pdf("x".repeat(100_001)), "EXTRACTED_TEXT_TOO_LARGE");
    }

    @Test
    void rejectsPdfDocumentsAboveThePageLimit() throws Exception {
        assertCode(pdfWithPages(51), "INVALID_DOCUMENT");
    }

    private void assertCode(MockMultipartFile file, String expectedCode) {
        ResumeUploadException exception = assertThrows(ResumeUploadException.class, () -> extractor.extract(file));
        assertEquals(expectedCode, exception.code());
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

    private MockMultipartFile positionedChunkPdf() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("Cloud");
                content.newLineAtOffset(40, 0);
                content.showText("native");
                content.newLineAtOffset(40, 0);
                content.showText("Java");
                content.endText();
            }
            document.save(output);
        }
        return new MockMultipartFile("file", "resume.pdf", PDF_MIME, output.toByteArray());
    }

    private MockMultipartFile encryptedPdf() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy("synthetic-owner", "synthetic-user", new AccessPermission()));
            document.save(output);
        }
        return new MockMultipartFile("file", "resume.pdf", PDF_MIME, output.toByteArray());
    }

    private MockMultipartFile pdfWithPages(int pageCount) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            for (int page = 0; page < pageCount; page++) {
                document.addPage(new PDPage());
            }
            document.save(output);
        }
        return new MockMultipartFile("file", "resume.pdf", PDF_MIME, output.toByteArray());
    }

    private MockMultipartFile macroDocx() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addEntry(zip, "[Content_Types].xml", "<Types/>".getBytes());
            addEntry(zip, "word/document.xml", "<document/>".getBytes());
            addEntry(zip, "word/vbaProject.bin", new byte[]{1});
        }
        return new MockMultipartFile("file", "resume.docx", DOCX_MIME, output.toByteArray());
    }

    private MockMultipartFile pathTraversalDocx() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addEntry(zip, "[Content_Types].xml", "<Types/>".getBytes());
            addEntry(zip, "word/document.xml", "<document/>".getBytes());
            addEntry(zip, "../outside.xml", "unsafe".getBytes());
        }
        return new MockMultipartFile("file", "resume.docx", DOCX_MIME, output.toByteArray());
    }

    private MockMultipartFile expandingDocx() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addEntry(zip, "[Content_Types].xml", "<Types/>".getBytes());
            addEntry(zip, "word/document.xml", new byte[10 * 1024 * 1024 + 1]);
        }
        return new MockMultipartFile("file", "resume.docx", DOCX_MIME, output.toByteArray());
    }

    private void addEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }
}
