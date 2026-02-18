package app;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class QrVideoReaderApp {
    private static final Path DEFAULT_VIDEO = Path.of("video", "ola7.mp4");
    private static final Path DEFAULT_OUTPUT_DIR = Path.of("out");
    private static final String WARMUP_PAYLOAD = "__WARMUP__";
    private static final int DEFAULT_FPS = 6;
    private static final double DEFAULT_CHANGE_THRESHOLD = 0.10;
    private static final int DEFAULT_ANALYSIS_SIZE = 64;
    private static final int DEFAULT_DECODE_WINDOW = 4;

    private QrVideoReaderApp() {
    }

    public static void main(String[] args) {
        Config config;
        try {
            config = Config.fromArgs(args);
        } catch (IllegalArgumentException ex) {
            System.err.println("Argumentos invalidos: " + ex.getMessage());
            printUsage();
            System.exit(1);
            return;
        }

        if (!Files.exists(config.videoPath)) {
            System.err.println("No existe el video: " + config.videoPath.toAbsolutePath());
            System.exit(1);
            return;
        }

        Path tempFramesDir = null;
        try {
            tempFramesDir = Files.createTempDirectory("qr-reader-frames-");
            extractFrames(config.videoPath, tempFramesDir, config.fps);
            List<Path> frames = listFrames(tempFramesDir);
            if (frames.isEmpty()) {
                throw new IOException("ffmpeg no extrajo frames del video.");
            }

            DecodeSummary summary = decodeFromFrames(frames, config.changeThreshold, config.analysisSize, config.decodeWindow);
            writeOutput(config.outputDir, summary.decodedChunks);
            printSummary(config, summary);
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
            System.exit(1);
        } finally {
            if (tempFramesDir != null) {
                deleteRecursively(tempFramesDir);
            }
        }
    }

    private static void printUsage() {
        System.out.println("Uso:");
        System.out.println("  java app.QrVideoReaderApp [video.mp4] [outputDir]");
        System.out.println("  java app.QrVideoReaderApp --video video\\ola7.mp4 --out out --fps 4 --threshold 0.10 --decode-window 4");
    }

    private static void extractFrames(Path videoPath, Path tempFramesDir, int fps) throws IOException, InterruptedException {
        String fpsFilter = "fps=" + fps;
        Path outputPattern = tempFramesDir.resolve("frame_%08d.png");
        List<String> command = List.of(
                "ffmpeg",
                "-hide_banner",
                "-loglevel", "error",
                "-y",
                "-i", videoPath.toString(),
                "-vf", fpsFilter,
                outputPattern.toString()
        );
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("ffmpeg devolvio codigo " + exitCode + ". Salida: " + output);
        }
    }

    private static List<Path> listFrames(Path tempFramesDir) throws IOException {
        try (Stream<Path> stream = Files.list(tempFramesDir)) {
            return stream
                    .filter(path -> path.getFileName().toString().startsWith("frame_"))
                    .filter(path -> path.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .collect(Collectors.toList());
        }
    }

    private static DecodeSummary decodeFromFrames(
            List<Path> frames,
            double threshold,
            int analysisSize,
            int decodeWindow
    ) throws IOException {
        List<String> decoded = new ArrayList<>();
        String previousDecoded = null;
        FrameSignature previousSignature = null;
        boolean firstQrDetected = false;

        int totalFrames = 0;
        int decodeAttempts = 0;
        int radicalChanges = 0;

        List<SegmentFrame> segmentFrames = new ArrayList<>();

        for (Path framePath : frames) {
            totalFrames++;
            BufferedImage image = ImageIO.read(framePath.toFile());
            if (image == null) {
                continue;
            }

            BufferedImage analysisRegion = centerSquareCrop(image, 0.85);
            if (analysisRegion == null) {
                analysisRegion = image;
            }

            FrameSignature signature = FrameSignature.from(analysisRegion, analysisSize);

            if (!firstQrDetected) {
                decodeAttempts++;
                String firstText = decodeQrRobust(image);
                if (firstText != null) {
                    previousDecoded = firstText;
                    firstQrDetected = true;
                    segmentFrames.clear();
                    segmentFrames.add(new SegmentFrame(copyImage(image), framePath.getFileName().toString()));
                    if (isWarmupPayload(firstText)) {
                        System.out.println(String.format(Locale.US, "[---] Warmup detectado en %s, ignorado",
                                framePath.getFileName()));
                    } else {
                        decoded.add(firstText);
                        System.out.println(String.format(Locale.US, "[%03d] QR detectado en %s (len=%d)",
                                decoded.size(), framePath.getFileName(), firstText.length()));
                    }
                }
                previousSignature = signature;
                continue;
            }

            boolean radicalChange = false;
            if (previousSignature != null) {
                double diff = signature.distance(previousSignature);
                if (diff >= threshold) {
                    radicalChange = true;
                    radicalChanges++;
                }
            }

            if (radicalChange) {
                SegmentDecodeResult segmentResult = decodeSegment(segmentFrames, decodeWindow, previousDecoded);
                decodeAttempts += segmentResult.attempts;
                if (segmentResult.text != null && !segmentResult.text.equals(previousDecoded)) {
                    previousDecoded = segmentResult.text;
                    if (isWarmupPayload(segmentResult.text)) {
                        System.out.println(String.format(Locale.US, "[---] Warmup detectado en %s, ignorado",
                                segmentResult.sourceLabel));
                    } else {
                        decoded.add(segmentResult.text);
                        System.out.println(String.format(Locale.US, "[%03d] QR detectado en %s (len=%d)",
                                decoded.size(), segmentResult.sourceLabel, segmentResult.text.length()));
                    }
                }
                segmentFrames.clear();
            }

            segmentFrames.add(new SegmentFrame(copyImage(image), framePath.getFileName().toString()));
            previousSignature = signature;
        }

        SegmentDecodeResult lastSegmentResult = decodeSegment(segmentFrames, decodeWindow, previousDecoded);
        decodeAttempts += lastSegmentResult.attempts;
        if (lastSegmentResult.text != null && !lastSegmentResult.text.equals(previousDecoded)) {
            if (isWarmupPayload(lastSegmentResult.text)) {
                System.out.println(String.format(Locale.US, "[---] Warmup detectado en %s, ignorado",
                        lastSegmentResult.sourceLabel));
            } else {
                decoded.add(lastSegmentResult.text);
                System.out.println(String.format(Locale.US, "[%03d] QR detectado en %s (len=%d)",
                        decoded.size(), lastSegmentResult.sourceLabel, lastSegmentResult.text.length()));
            }
        }

        return new DecodeSummary(decoded, totalFrames, decodeAttempts, radicalChanges);
    }

    private static SegmentDecodeResult decodeSegment(List<SegmentFrame> frames, int decodeWindow, String previousDecoded) {
        if (frames.isEmpty()) {
            return SegmentDecodeResult.empty();
        }

        int attempts = 0;
        String fallbackSameAsPrevious = null;
        String fallbackLabel = null;
        List<Integer> sampleIndexes = buildSampleIndexes(frames.size(), decodeWindow);
        for (int index : sampleIndexes) {
            SegmentFrame frame = frames.get(index);
            attempts++;
            String text = decodeQrRobust(frame.image);
            if (text != null) {
                if (previousDecoded == null || !text.equals(previousDecoded)) {
                    return new SegmentDecodeResult(text, frame.label, attempts);
                }
                if (fallbackSameAsPrevious == null) {
                    fallbackSameAsPrevious = text;
                    fallbackLabel = frame.label;
                }
            }
        }

        if (frames.size() >= 2) {
            BufferedImage avg = averageFrames(frames);
            attempts++;
            String text = decodeQrRobust(avg);
            if (text != null) {
                if (previousDecoded == null || !text.equals(previousDecoded)) {
                    return new SegmentDecodeResult(text, "promedio_segmento", attempts);
                }
                if (fallbackSameAsPrevious == null) {
                    fallbackSameAsPrevious = text;
                    fallbackLabel = "promedio_segmento";
                }
            }
        }

        if (fallbackSameAsPrevious != null) {
            return new SegmentDecodeResult(fallbackSameAsPrevious, fallbackLabel, attempts);
        }
        return new SegmentDecodeResult(null, "", attempts);
    }

    private static List<Integer> buildSampleIndexes(int frameCount, int decodeWindow) {
        if (frameCount <= 0) {
            return List.of();
        }

        int targetSamples = decodeWindow <= 0 ? 4 : decodeWindow;
        targetSamples = Math.max(3, targetSamples);
        targetSamples = Math.min(targetSamples, frameCount);

        LinkedHashSet<Integer> indexes = new LinkedHashSet<>();
        indexes.add(frameCount / 2);
        for (int i = 0; i < targetSamples; i++) {
            double pos = (i + 0.5) / targetSamples;
            int idx = (int) Math.round(pos * (frameCount - 1));
            indexes.add(Math.max(0, Math.min(frameCount - 1, idx)));
        }
        indexes.add(0);
        indexes.add(frameCount - 1);

        List<Integer> ordered = new ArrayList<>(indexes);
        if (ordered.size() > targetSamples) {
            return new ArrayList<>(ordered.subList(0, targetSamples));
        }
        return ordered;
    }

    private static String decodeQrRobust(BufferedImage image) {
        List<BufferedImage> candidates = buildDecodeCandidates(image);
        for (BufferedImage candidate : candidates) {
            String decoded = decodeQr(candidate);
            if (decoded != null) {
                return decoded;
            }
        }
        return null;
    }

    private static String decodeQr(BufferedImage image) {
        List<BufferedImage> variants = buildVariants(image);
        for (BufferedImage variant : variants) {
            String decoded = decodeVariant(variant);
            if (decoded != null) {
                return decoded;
            }
        }
        return null;
    }

    private static String decodeVariant(BufferedImage image) {
        MultiFormatReader reader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");

        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap hybrid = new BinaryBitmap(new HybridBinarizer(source));
        Result result = tryDecode(reader, hybrid, hints);
        if (result != null) {
            return result.getText();
        }

        BinaryBitmap global = new BinaryBitmap(new GlobalHistogramBinarizer(source));
        result = tryDecode(reader, global, hints);
        if (result != null) {
            return result.getText();
        }

        BinaryBitmap inverted = new BinaryBitmap(new HybridBinarizer(source.invert()));
        result = tryDecode(reader, inverted, hints);
        return result == null ? null : result.getText();
    }

    private static List<BufferedImage> buildDecodeCandidates(BufferedImage image) {
        List<BufferedImage> candidates = new ArrayList<>();
        candidates.add(image);

        double[] ratios = {0.95, 0.90, 0.85, 0.80, 0.70};
        for (double ratio : ratios) {
            BufferedImage crop = centerSquareCrop(image, ratio);
            if (crop != null) {
                candidates.add(crop);
            }
        }
        return candidates;
    }

    private static List<BufferedImage> buildVariants(BufferedImage image) {
        List<BufferedImage> variants = new ArrayList<>();
        variants.add(image);
        variants.add(scaleImage(image, 1.5));
        variants.add(scaleImage(image, 2.0));
        return variants;
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

    private static BufferedImage scaleImage(BufferedImage image, double factor) {
        int targetWidth = Math.max(1, (int) Math.round(image.getWidth() * factor));
        int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * factor));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaled.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g2d.dispose();
        }
        return scaled;
    }

    private static BufferedImage copyImage(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = copy.createGraphics();
        try {
            g2d.drawImage(source, 0, 0, null);
        } finally {
            g2d.dispose();
        }
        return copy;
    }

    private static BufferedImage averageFrames(List<SegmentFrame> frames) {
        BufferedImage first = frames.get(0).image;
        int width = first.getWidth();
        int height = first.getHeight();
        int pixelCount = width * height;

        int[] rs = new int[pixelCount];
        int[] gs = new int[pixelCount];
        int[] bs = new int[pixelCount];

        for (SegmentFrame frame : frames) {
            BufferedImage img = frame.image;
            int idx = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = img.getRGB(x, y);
                    rs[idx] += (rgb >> 16) & 0xFF;
                    gs[idx] += (rgb >> 8) & 0xFF;
                    bs[idx] += rgb & 0xFF;
                    idx++;
                }
            }
        }

        int total = frames.size();
        BufferedImage averaged = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = rs[idx] / total;
                int g = gs[idx] / total;
                int b = bs[idx] / total;
                int rgb = (r << 16) | (g << 8) | b;
                averaged.setRGB(x, y, rgb);
                idx++;
            }
        }
        return averaged;
    }

    private static Result tryDecode(MultiFormatReader reader, BinaryBitmap bitmap, Map<DecodeHintType, Object> hints) {
        try {
            return reader.decode(bitmap, hints);
        } catch (NotFoundException ex) {
            return null;
        } finally {
            reader.reset();
        }
    }

    private static boolean isWarmupPayload(String text) {
        if (text == null) {
            return false;
        }
        return WARMUP_PAYLOAD.equals(text.trim());
    }

    private static void writeOutput(Path outputDir, List<String> chunks) throws IOException {
        List<String> filteredChunks = chunks.stream()
                .filter(chunk -> !isWarmupPayload(chunk))
                .collect(Collectors.toList());

        Files.createDirectories(outputDir);
        Path chunksFile = outputDir.resolve("decoded_chunks.txt");
        Path combinedFile = outputDir.resolve("combined_payload.txt");

        StringBuilder chunkText = new StringBuilder();
        for (int i = 0; i < filteredChunks.size(); i++) {
            chunkText.append("----- QR ").append(i + 1).append(" -----").append(System.lineSeparator());
            chunkText.append(filteredChunks.get(i)).append(System.lineSeparator()).append(System.lineSeparator());
        }
        Files.writeString(chunksFile, chunkText.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        String combined = String.join("", filteredChunks);
        Files.writeString(combinedFile, combined, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        byte[] decodedBinary = tryDecodeBase64(combined);
        if (decodedBinary != null) {
            Path binaryFile = outputDir.resolve("combined_payload.bin");
            Files.write(binaryFile, decodedBinary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            System.out.println("Payload binario reconstruido en: " + binaryFile.toAbsolutePath());
        }
    }

    private static byte[] tryDecodeBase64(String text) {
        if (text == null) {
            return null;
        }
        String compact = text.replaceAll("\\s+", "");
        if (compact.isEmpty() || compact.length() % 4 != 0) {
            return null;
        }
        if (!compact.matches("^[A-Za-z0-9+/=]+$")) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(compact);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static void printSummary(Config config, DecodeSummary summary) {
        System.out.println();
        System.out.println("Video: " + config.videoPath.toAbsolutePath());
        System.out.println("Frames procesados: " + summary.totalFrames);
        System.out.println("Cambios radicales: " + summary.radicalChanges);
        System.out.println("Intentos de decode: " + summary.decodeAttempts);
        System.out.println("QRs unicos detectados: " + summary.decodedChunks.size());
        System.out.println("Salida: " + config.outputDir.toAbsolutePath());
    }

    private static void deleteRecursively(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static final class SegmentFrame {
        private final BufferedImage image;
        private final String label;

        private SegmentFrame(BufferedImage image, String label) {
            this.image = image;
            this.label = label;
        }
    }

    private static final class SegmentDecodeResult {
        private final String text;
        private final String sourceLabel;
        private final int attempts;

        private SegmentDecodeResult(String text, String sourceLabel, int attempts) {
            this.text = text;
            this.sourceLabel = sourceLabel;
            this.attempts = attempts;
        }

        private static SegmentDecodeResult empty() {
            return new SegmentDecodeResult(null, "", 0);
        }
    }

    private static final class Config {
        private final Path videoPath;
        private final Path outputDir;
        private final int fps;
        private final double changeThreshold;
        private final int analysisSize;
        private final int decodeWindow;

        private Config(Path videoPath, Path outputDir, int fps, double changeThreshold, int analysisSize, int decodeWindow) {
            this.videoPath = videoPath;
            this.outputDir = outputDir;
            this.fps = fps;
            this.changeThreshold = changeThreshold;
            this.analysisSize = analysisSize;
            this.decodeWindow = decodeWindow;
        }

        private static Config fromArgs(String[] args) {
            Path video = DEFAULT_VIDEO;
            Path out = DEFAULT_OUTPUT_DIR;
            int fps = DEFAULT_FPS;
            double threshold = DEFAULT_CHANGE_THRESHOLD;
            int analysisSize = DEFAULT_ANALYSIS_SIZE;
            int decodeWindow = DEFAULT_DECODE_WINDOW;

            List<String> positional = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--video":
                        video = Path.of(requireValue(args, ++i, "--video"));
                        break;
                    case "--out":
                        out = Path.of(requireValue(args, ++i, "--out"));
                        break;
                    case "--fps":
                        fps = Integer.parseInt(requireValue(args, ++i, "--fps"));
                        break;
                    case "--threshold":
                        threshold = Double.parseDouble(requireValue(args, ++i, "--threshold"));
                        break;
                    case "--analysis-size":
                        analysisSize = Integer.parseInt(requireValue(args, ++i, "--analysis-size"));
                        break;
                    case "--decode-window":
                        decodeWindow = Integer.parseInt(requireValue(args, ++i, "--decode-window"));
                        break;
                    case "--help":
                        printUsage();
                        System.exit(0);
                        break;
                    default:
                        if (arg.startsWith("--")) {
                            throw new IllegalArgumentException("Bandera no soportada: " + arg);
                        }
                        positional.add(arg);
                        break;
                }
            }

            if (!positional.isEmpty()) {
                video = Path.of(positional.get(0));
            }
            if (positional.size() > 1) {
                out = Path.of(positional.get(1));
            }
            if (positional.size() > 2) {
                throw new IllegalArgumentException("Demasiados argumentos posicionales.");
            }

            if (fps <= 0) {
                throw new IllegalArgumentException("--fps debe ser > 0");
            }
            if (analysisSize < 8) {
                throw new IllegalArgumentException("--analysis-size debe ser >= 8");
            }
            if (threshold < 0.0 || threshold > 1.0) {
                throw new IllegalArgumentException("--threshold debe estar entre 0 y 1");
            }
            if (decodeWindow < 0) {
                throw new IllegalArgumentException("--decode-window debe ser >= 0");
            }

            return new Config(video, out, fps, threshold, analysisSize, decodeWindow);
        }

        private static String requireValue(String[] args, int index, String name) {
            if (index >= args.length) {
                throw new IllegalArgumentException("Falta valor para " + name);
            }
            return args[index];
        }
    }

    private static final class FrameSignature {
        private final double[] values;

        private FrameSignature(double[] values) {
            this.values = values;
        }

        private static FrameSignature from(BufferedImage image, int size) {
            int width = image.getWidth();
            int height = image.getHeight();
            double[] values = new double[size * size];
            int idx = 0;

            for (int y = 0; y < size; y++) {
                int sy = sampleCoord(y, size, height);
                for (int x = 0; x < size; x++) {
                    int sx = sampleCoord(x, size, width);
                    int rgb = image.getRGB(sx, sy);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    int gray = (r * 299 + g * 587 + b * 114) / 1000;
                    values[idx++] = gray / 255.0;
                }
            }
            return new FrameSignature(values);
        }

        private double distance(FrameSignature other) {
            if (other == null || other.values.length != values.length) {
                return 1.0;
            }
            double sum = 0.0;
            for (int i = 0; i < values.length; i++) {
                sum += Math.abs(values[i] - other.values[i]);
            }
            return sum / values.length;
        }

        private static int sampleCoord(int n, int size, int max) {
            if (max <= 1 || size <= 1) {
                return 0;
            }
            return (int) Math.round((n * (max - 1.0)) / (size - 1.0));
        }
    }

    private static final class DecodeSummary {
        private final List<String> decodedChunks;
        private final int totalFrames;
        private final int decodeAttempts;
        private final int radicalChanges;

        private DecodeSummary(List<String> decodedChunks, int totalFrames, int decodeAttempts, int radicalChanges) {
            this.decodedChunks = decodedChunks;
            this.totalFrames = totalFrames;
            this.decodeAttempts = decodeAttempts;
            this.radicalChanges = radicalChanges;
        }
    }
}
