package app;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class PayloadZipService {
    private static final String FILE_PAYLOAD_TAG = "QRFILE1";
    private static final char FILE_PAYLOAD_SEPARATOR = '|';
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
    private static final ZoneId MEXICO_CITY_ZONE = ZoneId.of("America/Mexico_City");
    private static final Locale MEXICO_LOCALE = Locale.forLanguageTag("es-MX");
    private static final DateTimeFormatter ZIP_NAME_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss", MEXICO_LOCALE);

    private PayloadZipService() {
    }

    static PayloadBuildResult buildPayloadFromBase64Chunks(List<String> chunks, Path outputDir) throws IOException {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalStateException("No se encontraron QRs utiles para reconstruir un archivo.");
        }

        String combined = String.join("", chunks);
        DecodedPayload payload = decodePayload(combined);
        String outputName = resolveOutputFileName(payload);

        Files.createDirectories(outputDir);
        Path payloadPath = resolveUniquePath(outputDir, outputName);
        Files.write(payloadPath, payload.bytes(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        System.out.println("Archivo reconstruido en: " + payloadPath.toAbsolutePath());

        Path extractedDir = null;
        if (isZipPayload(payload, payloadPath)) {
            String extractDirName = stripExtension(payloadPath.getFileName().toString());
            extractedDir = outputDir.resolve(extractDirName);
            unzip(payloadPath, extractedDir);
            System.out.println("ZIP descomprimido en: " + extractedDir.toAbsolutePath());
        }

        return new PayloadBuildResult(
                payloadPath,
                extractedDir,
                chunks.size(),
                payload.bytes().length,
                payload.mimeType(),
                payload.originalFileName()
        );
    }

    private static DecodedPayload decodePayload(String combined) {
        String compact = combined == null ? "" : combined.replaceAll("\\s+", "");
        if (compact.isEmpty()) {
            throw new IllegalStateException("El payload combinado esta vacio.");
        }

        String taggedPrefix = FILE_PAYLOAD_TAG + FILE_PAYLOAD_SEPARATOR;
        if (compact.startsWith(taggedPrefix)) {
            return decodeTaggedPayload(compact, taggedPrefix.length());
        }

        byte[] bytes = decodeBase64Payload(compact);
        String detectedMime = detectMimeTypeFromBytes(bytes);
        return new DecodedPayload(bytes, detectedMime, null);
    }

    private static DecodedPayload decodeTaggedPayload(String compact, int prefixLength) {
        int mimeEnd = compact.indexOf(FILE_PAYLOAD_SEPARATOR, prefixLength);
        if (mimeEnd <= prefixLength) {
            throw new IllegalStateException("Payload QRFILE1 invalido: falta mimeType.");
        }

        int fileNameEnd = compact.indexOf(FILE_PAYLOAD_SEPARATOR, mimeEnd + 1);
        if (fileNameEnd <= mimeEnd + 1) {
            throw new IllegalStateException("Payload QRFILE1 invalido: falta nombre de archivo.");
        }

        String mimeType = compact.substring(prefixLength, mimeEnd).trim();
        String fileNameToken = compact.substring(mimeEnd + 1, fileNameEnd);
        String base64Data = compact.substring(fileNameEnd + 1);
        byte[] bytes = decodeBase64Payload(base64Data);

        String decodedFileName = decodeFileNameToken(fileNameToken);
        String normalizedMimeType = normalizeMimeType(mimeType, bytes);
        return new DecodedPayload(bytes, normalizedMimeType, decodedFileName);
    }

    private static byte[] decodeBase64Payload(String compact) {
        try {
            return Base64.getDecoder().decode(compact);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("El payload combinado no es Base64 valido.");
        }
    }

    private static String decodeFileNameToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(token);
        } catch (IllegalArgumentException ex) {
            try {
                bytes = Base64.getDecoder().decode(token);
            } catch (IllegalArgumentException innerEx) {
                throw new IllegalStateException("Payload QRFILE1 invalido: nombre de archivo no es Base64 valido.");
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String normalizeMimeType(String mimeType, byte[] bytes) {
        String raw = mimeType == null ? "" : mimeType.trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty() || DEFAULT_MIME_TYPE.equals(raw)) {
            return detectMimeTypeFromBytes(bytes);
        }
        return raw;
    }

    private static String detectMimeTypeFromBytes(byte[] bytes) {
        if (isZipBytes(bytes)) {
            return "application/zip";
        }
        if (isPdfBytes(bytes)) {
            return "application/pdf";
        }
        if (isPngBytes(bytes)) {
            return "image/png";
        }
        if (isJpegBytes(bytes)) {
            return "image/jpeg";
        }
        return DEFAULT_MIME_TYPE;
    }

    private static String resolveOutputFileName(DecodedPayload payload) {
        String safeFileName = sanitizeFileName(payload.originalFileName());
        String extension = extensionForMime(payload.mimeType());
        if (safeFileName == null) {
            String timestamp = currentMexTimestamp();
            return timestamp + extension;
        }

        if (!hasExtension(safeFileName) && !extension.isEmpty()) {
            return safeFileName + extension;
        }
        return safeFileName;
    }

    private static Path resolveUniquePath(Path outputDir, String fileName) {
        Path candidate = outputDir.resolve(fileName);
        if (!Files.exists(candidate)) {
            return candidate;
        }

        String baseName = fileName;
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0 && dot < fileName.length() - 1) {
            baseName = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }

        int suffix = 1;
        while (true) {
            Path next = outputDir.resolve(baseName + "_" + suffix + extension);
            if (!Files.exists(next)) {
                return next;
            }
            suffix++;
        }
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        String normalized = fileName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < normalized.length()) {
            normalized = normalized.substring(slash + 1);
        }

        normalized = normalized.replaceAll("[\\\\/:*?\"<>|]", "_");
        normalized = normalized.replaceAll("[\\p{Cntrl}]", "");
        normalized = normalized.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }

    private static String extensionForMime(String mimeType) {
        if (mimeType == null) {
            return ".bin";
        }
        switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "application/zip":
                return ".zip";
            case "application/pdf":
                return ".pdf";
            case "image/png":
                return ".png";
            case "image/jpeg":
                return ".jpg";
            case "text/plain":
                return ".txt";
            case "application/json":
                return ".json";
            default:
                return ".bin";
        }
    }

    private static boolean hasExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 && dot < fileName.length() - 1;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName;
        }
        return fileName.substring(0, dot);
    }

    private static String currentMexTimestamp() {
        return ZonedDateTime.now(MEXICO_CITY_ZONE).format(ZIP_NAME_FORMAT);
    }

    private static boolean isZipPayload(DecodedPayload payload, Path path) {
        if (payload == null) {
            return false;
        }
        if ("application/zip".equals(payload.mimeType())) {
            return true;
        }
        String fileName = path == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".zip")) {
            return true;
        }
        return isZipBytes(payload.bytes());
    }

    private static boolean isZipBytes(byte[] bytes) {
        return bytes != null
                && bytes.length >= 4
                && (bytes[0] & 0xFF) == 0x50
                && (bytes[1] & 0xFF) == 0x4B
                && (bytes[2] & 0xFF) == 0x03
                && (bytes[3] & 0xFF) == 0x04;
    }

    private static boolean isPdfBytes(byte[] bytes) {
        return bytes != null
                && bytes.length >= 5
                && (bytes[0] & 0xFF) == 0x25
                && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x44
                && (bytes[3] & 0xFF) == 0x46
                && (bytes[4] & 0xFF) == 0x2D;
    }

    private static boolean isPngBytes(byte[] bytes) {
        return bytes != null
                && bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x4E
                && (bytes[3] & 0xFF) == 0x47
                && (bytes[4] & 0xFF) == 0x0D
                && (bytes[5] & 0xFF) == 0x0A
                && (bytes[6] & 0xFF) == 0x1A
                && (bytes[7] & 0xFF) == 0x0A;
    }

    private static boolean isJpegBytes(byte[] bytes) {
        return bytes != null
                && bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private static void unzip(Path zipPath, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path resolvedPath = targetDir.resolve(entry.getName()).normalize();
                if (!resolvedPath.startsWith(targetDir)) {
                    throw new IOException("Entrada ZIP insegura: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolvedPath);
                } else {
                    Path parent = resolvedPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    try (OutputStream output = Files.newOutputStream(
                            resolvedPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)) {
                        zis.transferTo(output);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    static final class PayloadBuildResult {
        private final Path outputPath;
        private final Path extractedDir;
        private final int chunkCount;
        private final int payloadBytes;
        private final String mimeType;
        private final String originalFileName;

        private PayloadBuildResult(
                Path outputPath,
                Path extractedDir,
                int chunkCount,
                int payloadBytes,
                String mimeType,
                String originalFileName
        ) {
            this.outputPath = outputPath;
            this.extractedDir = extractedDir;
            this.chunkCount = chunkCount;
            this.payloadBytes = payloadBytes;
            this.mimeType = mimeType;
            this.originalFileName = originalFileName;
        }

        Path outputPath() {
            return outputPath;
        }

        Path extractedDir() {
            return extractedDir;
        }

        int chunkCount() {
            return chunkCount;
        }

        int payloadBytes() {
            return payloadBytes;
        }

        String mimeType() {
            return mimeType;
        }

        String originalFileName() {
            return originalFileName;
        }
    }

    private static final class DecodedPayload {
        private final byte[] bytes;
        private final String mimeType;
        private final String originalFileName;

        private DecodedPayload(byte[] bytes, String mimeType, String originalFileName) {
            this.bytes = bytes;
            this.mimeType = mimeType == null || mimeType.isBlank() ? DEFAULT_MIME_TYPE : mimeType;
            this.originalFileName = originalFileName;
        }

        byte[] bytes() {
            return bytes;
        }

        String mimeType() {
            return mimeType;
        }

        String originalFileName() {
            return originalFileName;
        }
    }
}
