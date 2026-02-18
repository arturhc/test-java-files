package app;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class FrameChangeDetector {
    private static final double ANALYSIS_CROP_RATIO = 0.85;

    private FrameChangeDetector() {
    }

    static void prepareFramesOutputDir(Path framesOutputDir) throws IOException {
        Files.createDirectories(framesOutputDir);
        try (Stream<Path> stream = Files.list(framesOutputDir)) {
            for (Path path : stream.collect(Collectors.toList())) {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.startsWith("change_") && name.endsWith(".png")) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    static DetectionResult detectChanges(
            List<Path> sourceFrames,
            Path framesOutputDir,
            double threshold,
            int analysisSize
    ) throws IOException {
        FrameSignature previousSignature = null;
        int totalFrames = 0;
        int comparedFrames = 0;
        int detectedChanges = 0;
        double maxDiff = 0.0;
        List<Path> detectedFrames = new ArrayList<>();

        for (Path framePath : sourceFrames) {
            totalFrames++;
            BufferedImage image = ImageIO.read(framePath.toFile());
            if (image == null) {
                continue;
            }

            BufferedImage analysisRegion = centerSquareCrop(image, ANALYSIS_CROP_RATIO);
            if (analysisRegion == null) {
                analysisRegion = image;
            }

            FrameSignature signature = FrameSignature.from(analysisRegion, analysisSize);
            if (previousSignature != null) {
                comparedFrames++;
                double diff = signature.distance(previousSignature);
                if (diff > maxDiff) {
                    maxDiff = diff;
                }

                if (diff >= threshold) {
                    detectedChanges++;
                    String targetName = String.format(Locale.US, "change_%04d_%s",
                            detectedChanges, framePath.getFileName());
                    Path targetPath = framesOutputDir.resolve(targetName);
                    Files.copy(framePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    detectedFrames.add(targetPath);

                    System.out.println(String.format(Locale.US,
                            "[%04d] Cambio detectado | diff=%.4f | frame=%s",
                            detectedChanges, diff, framePath.getFileName()));
                }
            }
            previousSignature = signature;
        }

        return new DetectionResult(totalFrames, comparedFrames, detectedChanges, maxDiff, detectedFrames);
    }

    private static BufferedImage centerSquareCrop(BufferedImage image, double sideRatio) {
        int width = image.getWidth();
        int height = image.getHeight();
        int side = (int) Math.round(Math.min(width, height) * sideRatio);
        if (side < 32) {
            return null;
        }
        int x = (width - side) / 2;
        int y = (height - side) / 2;
        return image.getSubimage(Math.max(0, x), Math.max(0, y), Math.min(side, width), Math.min(side, height));
    }

    static final class DetectionResult {
        private final int totalFrames;
        private final int comparedFrames;
        private final int detectedChanges;
        private final double maxDiff;
        private final List<Path> detectedFrames;

        private DetectionResult(
                int totalFrames,
                int comparedFrames,
                int detectedChanges,
                double maxDiff,
                List<Path> detectedFrames
        ) {
            this.totalFrames = totalFrames;
            this.comparedFrames = comparedFrames;
            this.detectedChanges = detectedChanges;
            this.maxDiff = maxDiff;
            this.detectedFrames = List.copyOf(detectedFrames);
        }

        int totalFrames() {
            return totalFrames;
        }

        int comparedFrames() {
            return comparedFrames;
        }

        int detectedChanges() {
            return detectedChanges;
        }

        double maxDiff() {
            return maxDiff;
        }

        List<Path> detectedFrames() {
            return detectedFrames;
        }
    }
}
