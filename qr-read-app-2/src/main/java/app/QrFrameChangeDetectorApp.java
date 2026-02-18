package app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class QrFrameChangeDetectorApp {
    private QrFrameChangeDetectorApp() {
    }

    public static void main(String[] args) {
        if (AppConfig.hasHelpFlag(args)) {
            AppConfig.printUsage();
            return;
        }

        AppConfig config;
        try {
            config = AppConfig.fromArgs(args);
        } catch (IllegalArgumentException ex) {
            System.err.println("Argumentos invalidos: " + ex.getMessage());
            AppConfig.printUsage();
            System.exit(1);
            return;
        }

        if (!Files.exists(config.videoPath())) {
            System.err.println("No existe el video: " + config.videoPath().toAbsolutePath());
            System.exit(1);
            return;
        }

        Path tempFramesDir = null;
        try {
            FrameChangeDetector.prepareFramesOutputDir(config.framesOutputDir());
            Files.createDirectories(config.zipsOutputDir());

            tempFramesDir = Files.createTempDirectory("qr-read-app-2-source-frames-");
            FfmpegFrameExtractor.extractFrames(config.videoPath(), tempFramesDir, config.fps());

            List<Path> extractedFrames = FileUtils.listPngFrames(tempFramesDir, "frame_");
            if (extractedFrames.isEmpty()) {
                throw new IllegalStateException("ffmpeg no extrajo frames del video.");
            }

            FrameChangeDetector.DetectionResult detection = FrameChangeDetector.detectChanges(
                    extractedFrames,
                    config.framesOutputDir(),
                    config.changeThreshold(),
                    config.analysisSize()
            );

            if (detection.detectedFrames().isEmpty()) {
                throw new IllegalStateException("No se detectaron cambios de frame con el threshold actual.");
            }

            List<String> decodedChunks = QrFrameReader.decodeFrames(detection.detectedFrames(), extractedFrames);
            PayloadZipService.PayloadBuildResult payloadBuild = PayloadZipService.buildPayloadFromBase64Chunks(
                    decodedChunks,
                    config.zipsOutputDir()
            );

            printSummary(config, detection, decodedChunks.size(), payloadBuild);
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
            System.exit(1);
        } finally {
            FileUtils.deleteRecursively(tempFramesDir);
        }
    }

    private static void printSummary(
            AppConfig config,
            FrameChangeDetector.DetectionResult detection,
            int decodedQrCount,
            PayloadZipService.PayloadBuildResult payloadBuild
    ) {
        System.out.println();
        System.out.println("Video: " + config.videoPath().toAbsolutePath());
        System.out.println("Frames totales: " + detection.totalFrames());
        System.out.println("Comparaciones: " + detection.comparedFrames());
        System.out.println("Cambios detectados: " + detection.detectedChanges());
        System.out.println(String.format(Locale.US, "Diff maximo observado: %.4f", detection.maxDiff()));
        System.out.println("Frames guardados en: " + config.framesOutputDir().toAbsolutePath());
        System.out.println("QRs decodificados (sin warmup): " + decodedQrCount);
        System.out.println("MIME detectado: " + payloadBuild.mimeType());
        if (payloadBuild.originalFileName() != null && !payloadBuild.originalFileName().isBlank()) {
            System.out.println("Nombre original: " + payloadBuild.originalFileName());
        }
        System.out.println("Archivo reconstruido: " + payloadBuild.outputPath().toAbsolutePath());
        if (payloadBuild.extractedDir() != null) {
            System.out.println("ZIP descomprimido en: " + payloadBuild.extractedDir().toAbsolutePath());
        }
    }
}
