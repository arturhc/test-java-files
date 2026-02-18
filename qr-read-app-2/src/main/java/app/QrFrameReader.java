package app;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class QrFrameReader {
    private static final String WARMUP_PAYLOAD = "__WARMUP__";
    private static final int[] NEIGHBOR_OFFSETS = {1, -1, 2, -2, 3, -3, 4, -4};
    private static final Pattern FRAME_NUMBER_PATTERN = Pattern.compile("frame_(\\d+)\\.png$", Pattern.CASE_INSENSITIVE);

    private QrFrameReader() {
    }

    static List<String> decodeFrames(List<Path> frames) throws IOException {
        return decodeFrames(frames, List.of());
    }

    static List<String> decodeFrames(List<Path> frames, List<Path> sourceFrames) throws IOException {
        Map<Integer, Path> sourceFramesByNumber = indexFramesByNumber(sourceFrames);
        List<String> decodedChunks = new ArrayList<>();
        for (Path framePath : frames) {
            BufferedImage image = ImageIO.read(framePath.toFile());
            if (image == null) {
                System.out.println("[decode] Frame invalido, se omite: " + framePath.getFileName());
                continue;
            }

            String text = decodeQrRobust(image);
            Path decodedFrom = framePath;
            if (text == null) {
                FallbackDecode fallback = decodeFromNeighborFrames(framePath, sourceFramesByNumber);
                if (fallback != null) {
                    text = fallback.decodedText;
                    decodedFrom = fallback.sourceFrame;
                    System.out.println(String.format(Locale.US,
                            "[decode] Recuperado con frame vecino (%+d): %s -> %s",
                            fallback.offset,
                            framePath.getFileName(),
                            decodedFrom.getFileName()));
                }
            }
            if (text == null) {
                System.out.println("[decode] Sin QR detectable en: " + framePath.getFileName());
                continue;
            }

            String normalized = text.trim();
            if (isWarmupPayload(normalized)) {
                System.out.println(String.format(Locale.US,
                        "[decode] Omitiendo __WARMUP__ en %s",
                        framePath.getFileName()));
                continue;
            }

            decodedChunks.add(normalized);
            System.out.println(String.format(Locale.US,
                    "[decode] QR %03d leido en %s (len=%d)",
                    decodedChunks.size(),
                    decodedFrom.getFileName(),
                    normalized.length()));
        }
        return decodedChunks;
    }

    private static Map<Integer, Path> indexFramesByNumber(List<Path> sourceFrames) {
        Map<Integer, Path> byNumber = new HashMap<>();
        if (sourceFrames == null) {
            return byNumber;
        }
        for (Path path : sourceFrames) {
            int frameNumber = extractFrameNumber(path);
            if (frameNumber >= 0) {
                byNumber.putIfAbsent(frameNumber, path);
            }
        }
        return byNumber;
    }

    private static FallbackDecode decodeFromNeighborFrames(Path framePath, Map<Integer, Path> sourceFramesByNumber)
            throws IOException {
        if (sourceFramesByNumber.isEmpty()) {
            return null;
        }

        int frameNumber = extractFrameNumber(framePath);
        if (frameNumber < 0) {
            return null;
        }

        for (int offset : NEIGHBOR_OFFSETS) {
            Path neighbor = sourceFramesByNumber.get(frameNumber + offset);
            if (neighbor == null) {
                continue;
            }
            BufferedImage neighborImage = ImageIO.read(neighbor.toFile());
            if (neighborImage == null) {
                continue;
            }
            String decoded = decodeQrRobust(neighborImage);
            if (decoded != null) {
                return new FallbackDecode(decoded, neighbor, offset);
            }
        }
        return null;
    }

    private static int extractFrameNumber(Path path) {
        if (path == null || path.getFileName() == null) {
            return -1;
        }
        String fileName = path.getFileName().toString();
        Matcher matcher = FRAME_NUMBER_PATTERN.matcher(fileName);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return -1;
        }
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

    private static Result tryDecode(MultiFormatReader reader, BinaryBitmap bitmap, Map<DecodeHintType, Object> hints) {
        try {
            return reader.decode(bitmap, hints);
        } catch (NotFoundException ex) {
            return null;
        } finally {
            reader.reset();
        }
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

    private static boolean isWarmupPayload(String text) {
        if (text == null) {
            return false;
        }
        return WARMUP_PAYLOAD.equals(text.trim());
    }

    private static final class FallbackDecode {
        private final String decodedText;
        private final Path sourceFrame;
        private final int offset;

        private FallbackDecode(String decodedText, Path sourceFrame, int offset) {
            this.decodedText = decodedText;
            this.sourceFrame = sourceFrame;
            this.offset = offset;
        }
    }
}
