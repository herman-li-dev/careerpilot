package com.hermanli.careerpilot.documents;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import java.text.Normalizer;
import java.util.Enumeration;
import java.util.Locale;

@Component
public class ResumeUploadTextExtractor {

    static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    static final int MAX_TEXT_LENGTH = 100_000;
    private static final int MAX_PDF_PAGES = 50;
    private static final int MAX_DOCX_ENTRY_COUNT = 1_000;
    private static final long MAX_DOCX_ENTRY_SIZE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_DOCX_TOTAL_SIZE_BYTES = 20L * 1024 * 1024;
    private static final long PDF_MAX_MAIN_MEMORY_BYTES = 32L * 1024 * 1024;
    private static final MemoryUsageSetting PDF_MEMORY_USAGE =
            MemoryUsageSetting.setupMainMemoryOnly(PDF_MAX_MAIN_MEMORY_BYTES);
    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final String DOCX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP_SIGNATURE = {'P', 'K', 3, 4};
    private static final byte[] COMPOUND_SIGNATURE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    static {
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(10L * 1024 * 1024);
        ZipSecureFile.setMaxFileCount(1_000);
        ZipSecureFile.setMaxTextSize(2L * 1024 * 1024);
    }

    public String extract(MultipartFile file) {
        return extractWithMetadata(file).text();
    }

    public ExtractedResume extractWithMetadata(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResumeUploadException("EMPTY_FILE", "The uploaded resume file is empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw tooLarge();
        }

        UploadType type = determineType(file);
        byte[] content = readContent(file);
        if (content.length == 0) {
            throw new ResumeUploadException("EMPTY_FILE", "The uploaded resume file is empty.");
        }
        if (content.length > MAX_FILE_SIZE_BYTES) {
            throw tooLarge();
        }
        validateSignature(type, content);

        String text = switch (type) {
            case PDF -> extractPdf(content);
            case DOCX -> extractDocx(content);
        };
        String normalized = normalizeText(text);
        if (normalized.isEmpty()) {
            throw new ResumeUploadException(
                    "NO_EXTRACTABLE_TEXT",
                    "The uploaded resume file does not contain extractable text. Scanned PDFs are not supported."
            );
        }
        if (normalized.length() > MAX_TEXT_LENGTH) {
            throw new ResumeUploadException(
                    "EXTRACTED_TEXT_TOO_LARGE",
                    "The extracted resume text exceeds the 100,000 character limit."
            );
        }
        return new ExtractedResume(type.name(), normalized);
    }

    public record ExtractedResume(String documentType, String text) {
    }

    private UploadType determineType(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new ResumeUploadException("UNSUPPORTED_FILE_TYPE", "Only PDF and DOCX resume files are supported.");
        }
        String lowerFilename = filename.toLowerCase(Locale.ROOT);
        UploadType type;
        if (lowerFilename.endsWith(".pdf")) {
            type = UploadType.PDF;
        } else if (lowerFilename.endsWith(".docx")) {
            type = UploadType.DOCX;
        } else {
            throw new ResumeUploadException("UNSUPPORTED_FILE_TYPE", "Only PDF and DOCX resume files are supported.");
        }
        String contentType = file.getContentType();
        if (!type.mimeType.equals(contentType)) {
            throw new ResumeUploadException("FILE_TYPE_MISMATCH", "The file type does not match the uploaded resume.");
        }
        return type;
    }

    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ResumeUploadException("INVALID_DOCUMENT", "The uploaded resume file could not be read.");
        }
    }

    private void validateSignature(UploadType type, byte[] content) {
        if (type == UploadType.PDF && !hasPrefix(content, PDF_SIGNATURE)) {
            throw new ResumeUploadException("FILE_TYPE_MISMATCH", "The file type does not match the uploaded resume.");
        }
        if (type == UploadType.DOCX) {
            if (hasPrefix(content, COMPOUND_SIGNATURE)) {
                throw new ResumeUploadException("ENCRYPTED_DOCUMENT", "Encrypted resume files are not supported.");
            }
            if (!hasPrefix(content, ZIP_SIGNATURE)) {
                throw new ResumeUploadException("FILE_TYPE_MISMATCH", "The file type does not match the uploaded resume.");
            }
            validateDocxContainer(content);
        }
    }

    private void validateDocxContainer(byte[] content) {
        boolean hasContentTypes = false;
        boolean hasMainDocument = false;
        int entryCount = 0;
        long totalUncompressedBytes = 0;
        try (SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(content);
             ZipFile zip = ZipFile.builder().setSeekableByteChannel(channel).get()) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                entryCount++;
                long entrySize = entry.getSize();
                if (entryCount > MAX_DOCX_ENTRY_COUNT
                        || entrySize < 0
                        || entrySize > MAX_DOCX_ENTRY_SIZE_BYTES
                        || totalUncompressedBytes > MAX_DOCX_TOTAL_SIZE_BYTES - entrySize) {
                    throw unsafeDocument();
                }
                totalUncompressedBytes += entrySize;

                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.equals("..") || name.startsWith("../") || name.contains("/../")) {
                    throw unsafeDocument();
                }
                String lowerName = name.toLowerCase(Locale.ROOT);
                hasContentTypes |= lowerName.equals("[content_types].xml");
                hasMainDocument |= lowerName.equals("word/document.xml");
                if (lowerName.endsWith("vbaproject.bin")) {
                    throw new ResumeUploadException(
                            "UNSAFE_DOCUMENT",
                            "Macro-enabled resume files are not supported."
                    );
                }
                if (lowerName.endsWith("encryptedpackage") || lowerName.endsWith("encryptioninfo")) {
                    throw new ResumeUploadException(
                            "ENCRYPTED_DOCUMENT",
                            "Encrypted resume files are not supported."
                    );
                }
            }
        } catch (ResumeUploadException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ResumeUploadException("INVALID_DOCUMENT", "The uploaded resume file could not be parsed.");
        }
        if (!hasContentTypes || !hasMainDocument) {
            throw new ResumeUploadException("FILE_TYPE_MISMATCH", "The file type does not match the uploaded resume.");
        }
    }

    private String extractPdf(byte[] content) {
        try (PDDocument pdf = Loader.loadPDF(content, "", null, null, PDF_MEMORY_USAGE.streamCache)) {
            if (pdf.isEncrypted()) {
                throw new ResumeUploadException("ENCRYPTED_DOCUMENT", "Encrypted resume files are not supported.");
            }
            if (pdf.getNumberOfPages() > MAX_PDF_PAGES) {
                throw invalidDocument();
            }
            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setSortByPosition(true);
            textStripper.setSpacingTolerance(0.3f);
            textStripper.setStartPage(1);
            textStripper.setEndPage(MAX_PDF_PAGES);
            BoundedTextWriter text = new BoundedTextWriter();
            textStripper.writeText(pdf, text);
            return text.text();
        } catch (ResumeUploadException exception) {
            throw exception;
        } catch (InvalidPasswordException exception) {
            throw new ResumeUploadException("ENCRYPTED_DOCUMENT", "Encrypted resume files are not supported.");
        } catch (Exception exception) {
            throw invalidDocument();
        }
    }

    private String extractDocx(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            validateSafeDocxParts(document);
            extractor.setFetchHyperlinks(false);
            return extractor.getText();
        } catch (ResumeUploadException exception) {
            throw exception;
        } catch (POIXMLException exception) {
            throw new ResumeUploadException("INVALID_DOCUMENT", "The uploaded resume file could not be parsed.");
        } catch (IOException | RuntimeException exception) {
            throw new ResumeUploadException("INVALID_DOCUMENT", "The uploaded resume file could not be parsed.");
        }
    }

    private void validateSafeDocxParts(XWPFDocument document) {
        try {
            for (PackagePart part : document.getPackage().getParts()) {
                String lowerName = part.getPartName().getName().toLowerCase(Locale.ROOT);
                String lowerContentType = part.getContentType().toLowerCase(Locale.ROOT);
                if (lowerName.endsWith("vbaproject.bin") || lowerContentType.contains("macroenabled")) {
                    throw new ResumeUploadException(
                            "UNSAFE_DOCUMENT",
                            "Macro-enabled resume files are not supported."
                    );
                }
                if (lowerName.endsWith("encryptedpackage") || lowerName.endsWith("encryptioninfo")) {
                    throw new ResumeUploadException(
                            "ENCRYPTED_DOCUMENT",
                            "Encrypted resume files are not supported."
                    );
                }
            }
        } catch (ResumeUploadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResumeUploadException("INVALID_DOCUMENT", "The uploaded resume file could not be parsed.");
        }
    }

    private boolean hasPrefix(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private String normalizeText(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\p{Cc}&&[^\\n\\t]]", "")
                .replaceAll("[\\t ]+", " ")
                .replaceAll(" ?\\n ?", "\n")
                .trim();
    }

    private ResumeUploadException extractedTextTooLarge() {
        return new ResumeUploadException(
                "EXTRACTED_TEXT_TOO_LARGE",
                "The extracted resume text exceeds the 100,000 character limit."
        );
    }

    private ResumeUploadException invalidDocument() {
        return new ResumeUploadException("INVALID_DOCUMENT", "The uploaded resume file could not be parsed.");
    }

    private ResumeUploadException tooLarge() {
        return new ResumeUploadException("FILE_TOO_LARGE", "The uploaded resume file exceeds the 5 MiB limit.");
    }

    private ResumeUploadException unsafeDocument() {
        return new ResumeUploadException(
                "UNSAFE_DOCUMENT",
                "The uploaded resume document exceeds its safe processing limits."
        );
    }

    private enum UploadType {
        PDF(PDF_MIME_TYPE),
        DOCX(DOCX_MIME_TYPE);

        private final String mimeType;

        UploadType(String mimeType) {
            this.mimeType = mimeType;
        }
    }

    private static class BoundedTextWriter extends Writer {

        private final StringBuilder text = new StringBuilder();

        @Override
        public void write(char[] characters, int offset, int length) {
            if (length > MAX_TEXT_LENGTH - text.length()) {
                throw new ResumeUploadException(
                        "EXTRACTED_TEXT_TOO_LARGE",
                        "The extracted resume text exceeds the 100,000 character limit."
                );
            }
            text.append(characters, offset, length);
        }

        @Override
        public void write(String value, int offset, int length) {
            if (length > MAX_TEXT_LENGTH - text.length()) {
                throw new ResumeUploadException(
                        "EXTRACTED_TEXT_TOO_LARGE",
                        "The extracted resume text exceeds the 100,000 character limit."
                );
            }
            text.append(value, offset, offset + length);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        private String text() {
            return text.toString();
        }
    }
}
