package app;

import java.awt.image.BufferedImage;

final class FrameSignature {
    private final double[] values;

    private FrameSignature(double[] values) {
        this.values = values;
    }

    static FrameSignature from(BufferedImage image, int size) {
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

    double distance(FrameSignature other) {
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
