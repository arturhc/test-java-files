package app;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.awt.GraphicsEnvironment;

public final class QrGeneratorApp {
    private static final int CHUNK_SIZE = 2000;
    private static final int SLIDE_DELAY_MS = 2000;
    private static QrSlideshow currentSlideshow;

    private QrGeneratorApp() {
    }

    public static void main(String[] args) {
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> showUi(shutdownLatch));
        waitUntilClosed(shutdownLatch);
    }

    private static BufferedImage generateQr(String text, int width, int height) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height, hints);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    private static void showUi(CountDownLatch shutdownLatch) {
        JFrame frame = new JFrame("QR Generator");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JTextArea textArea = new JTextArea(8, 40);
        JScrollPane scrollPane = new JScrollPane(textArea);

        JButton generateButton = new JButton("Generar QR");
        generateButton.addActionListener(event -> onGenerate(frame, textArea.getText()));

        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(generateButton, BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                shutdownLatch.countDown();
            }
        });
    }

    private static void onGenerate(JFrame parent, String text) {
        String raw = text == null ? "" : text;
        if (raw.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Pega un texto para generar el QR.", "Falta texto",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (currentSlideshow != null) {
            currentSlideshow.stop();
            currentSlideshow = null;
        }

        try {
            currentSlideshow = showQrWindow(parent, raw);
        } catch (WriterException ex) {
            JOptionPane.showMessageDialog(parent, "Error generando QR: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static QrSlideshow showQrWindow(JFrame parent, String text) throws WriterException {
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        List<String> chunks = splitIntoChunks(text, CHUNK_SIZE);
        QrSlideshow slideshow = new QrSlideshow(parent, bounds, chunks);
        slideshow.start();
        return slideshow;
    }

    private static void waitUntilClosed(CountDownLatch shutdownLatch) {
        try {
            shutdownLatch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<String> splitIntoChunks(String text, int maxCodePoints) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        int index = 0;
        while (index < length) {
            int remaining = text.codePointCount(index, length);
            int count = Math.min(maxCodePoints, remaining);
            int end = text.offsetByCodePoints(index, count);
            chunks.add(text.substring(index, end));
            index = end;
        }
        return chunks;
    }

    private static final class QrSlideshow {
        private final JFrame frame;
        private final JLabel label;
        private final Rectangle bounds;
        private final List<String> chunks;
        private final Timer timer;
        private int index;

        private QrSlideshow(JFrame parent, Rectangle bounds, List<String> chunks) throws WriterException {
            this.bounds = bounds;
            this.chunks = chunks;
            this.index = 0;

            frame = new JFrame();
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            label = new JLabel();
            frame.add(label, BorderLayout.CENTER);

            // Cerrar con ESC.
            frame.getRootPane().registerKeyboardAction(
                    e -> frame.dispose(),
                    javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
            );

            updateImage();

            timer = new Timer(SLIDE_DELAY_MS, e -> advance());
            timer.setInitialDelay(SLIDE_DELAY_MS);

            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    stop();
                }
            });

            frame.setBounds(bounds);
            frame.setLocationRelativeTo(parent);
        }

        private void start() {
            frame.setVisible(true);
            if (chunks.size() > 1) {
                timer.start();
            }
        }

        private void stop() {
            if (timer.isRunning()) {
                timer.stop();
            }
            if (frame.isDisplayable()) {
                frame.dispose();
            }
        }

        private void advance() {
            index++;
            if (index >= chunks.size()) {
                timer.stop();
                return;
            }
            updateImage();
        }

        private void updateImage() {
            try {
                BufferedImage image = generateQr(chunks.get(index), bounds.width, bounds.height);
                Image scaled = image.getScaledInstance(bounds.width, bounds.height, Image.SCALE_SMOOTH);
                label.setIcon(new ImageIcon(scaled));
                frame.setTitle("QR " + (index + 1) + "/" + chunks.size());
            } catch (WriterException ex) {
                timer.stop();
                JOptionPane.showMessageDialog(frame, "Error generando QR: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
