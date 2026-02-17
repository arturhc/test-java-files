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
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.Timer;
import javax.swing.ButtonGroup;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.awt.GraphicsEnvironment;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class QrGeneratorApp {
    private static final int CHUNK_SIZE = 2000;
    private static final int SLIDE_DELAY_MS = 1250;
    private static final String WARMUP_QR_PAYLOAD = "__WARMUP__";
    private static QrSlideshow currentSlideshow;
    private static Path selectedFile;
    private static String selectedFileBase64;

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
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

        JRadioButton sourceText = new JRadioButton("Texto", true);
        JRadioButton sourceFile = new JRadioButton("Archivo (Base64)");
        sourceText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sourceFile.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        ButtonGroup sourceGroup = new ButtonGroup();
        sourceGroup.add(sourceText);
        sourceGroup.add(sourceFile);

        JButton chooseFileButton = new JButton("Seleccionar archivo");
        chooseFileButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chooseFileButton.setFocusPainted(false);
        JLabel fileLabel = new JLabel("Ningun archivo seleccionado");
        fileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JButton generateButton = new JButton("Generar QR");
        generateButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        generateButton.setFocusPainted(false);
        generateButton.addActionListener(event -> onGenerate(frame, textArea.getText(), sourceText.isSelected()));

        JLabel qrCountLabel = new JLabel("Total QRs: 0");
        qrCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        qrCountLabel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        chooseFileButton.addActionListener(event -> {
            chooseFile(frame, fileLabel, sourceFile);
            updateQrCountLabel(qrCountLabel, textArea, sourceText.isSelected());
        });

        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateQrCountLabel(qrCountLabel, textArea, sourceText.isSelected());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateQrCountLabel(qrCountLabel, textArea, sourceText.isSelected());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateQrCountLabel(qrCountLabel, textArea, sourceText.isSelected());
            }
        });

        sourceText.addActionListener(e -> updateQrCountLabel(qrCountLabel, textArea, true));
        sourceFile.addActionListener(e -> updateQrCountLabel(qrCountLabel, textArea, false));

        JPanel qrCountRow = new JPanel();
        qrCountRow.setLayout(new BoxLayout(qrCountRow, BoxLayout.X_AXIS));
        qrCountRow.add(qrCountLabel);
        qrCountRow.add(Box.createHorizontalGlue());

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        centerPanel.add(qrCountRow, BorderLayout.SOUTH);

        frame.add(centerPanel, BorderLayout.CENTER);

        JPanel sourceRow = new JPanel();
        sourceRow.setLayout(new BoxLayout(sourceRow, BoxLayout.X_AXIS));
        sourceRow.add(sourceText);
        sourceRow.add(Box.createHorizontalStrut(12));
        sourceRow.add(sourceFile);
        sourceRow.add(Box.createHorizontalGlue());
        sourceRow.add(chooseFileButton);
        sourceRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel fileRow = new JPanel();
        fileRow.setLayout(new BoxLayout(fileRow, BoxLayout.X_AXIS));
        fileRow.add(fileLabel);
        fileRow.add(Box.createHorizontalGlue());
        fileRow.add(generateButton);
        fileRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
        controls.add(sourceRow);
        controls.add(Box.createVerticalStrut(8));
        controls.add(fileRow);

        frame.add(controls, BorderLayout.SOUTH);

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

    private static void onGenerate(JFrame parent, String text, boolean useTextSource) {
        String raw;
        if (useTextSource) {
            raw = text == null ? "" : text;
            if (raw.trim().isEmpty()) {
                JOptionPane.showMessageDialog(parent, "Pega un texto para generar el QR.", "Falta texto",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
        } else {
            if (selectedFile == null) {
                JOptionPane.showMessageDialog(parent, "Selecciona un archivo primero.", "Falta archivo",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (selectedFileBase64 != null) {
                raw = selectedFileBase64;
            } else {
                try {
                    byte[] bytes = Files.readAllBytes(selectedFile);
                    raw = Base64.getEncoder().encodeToString(bytes);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(parent, "No se pudo leer el archivo.", "Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
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

    private static void chooseFile(JFrame parent, JLabel fileLabel, JRadioButton sourceFile) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccionar archivo");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(true);
        int result = chooser.showOpenDialog(parent);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile().toPath();
            fileLabel.setText(selectedFile.toString());
            sourceFile.setSelected(true);
            try {
                byte[] bytes = Files.readAllBytes(selectedFile);
                selectedFileBase64 = Base64.getEncoder().encodeToString(bytes);
            } catch (IOException ex) {
                selectedFileBase64 = null;
                JOptionPane.showMessageDialog(parent, "No se pudo leer el archivo.", "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void updateQrCountLabel(JLabel label, JTextArea textArea, boolean useTextSource) {
        int count;
        if (useTextSource) {
            String text = textArea.getText();
            count = countChunks(text);
        } else {
            count = countChunks(selectedFileBase64);
        }
        label.setText("Total QRs: " + count);
    }

    private static QrSlideshow showQrWindow(JFrame parent, String text) throws WriterException {
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        List<String> chunks = buildSlideshowChunks(text, CHUNK_SIZE);
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

    private static List<String> buildSlideshowChunks(String text, int maxCodePoints) {
        List<String> dataChunks = splitIntoChunks(text, maxCodePoints);
        List<String> slideshowChunks = new ArrayList<>(dataChunks.size() + 1);
        slideshowChunks.add(WARMUP_QR_PAYLOAD);
        slideshowChunks.addAll(dataChunks);
        return slideshowChunks;
    }

    private static int countChunks(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int codePoints = text.codePointCount(0, text.length());
        return (codePoints + CHUNK_SIZE - 1) / CHUNK_SIZE;
    }

    private static final class QrSlideshow {
        private final JFrame frame;
        private final JLabel label;
        private final Rectangle bounds;
        private final List<String> chunks;
        private final boolean hasWarmup;
        private final int realChunkCount;
        private final Timer timer;
        private int index;
        private BufferedImage currentImage;

        private QrSlideshow(JFrame parent, Rectangle bounds, List<String> chunks) throws WriterException {
            this.bounds = bounds;
            this.chunks = chunks;
            this.hasWarmup = !chunks.isEmpty() && WARMUP_QR_PAYLOAD.equals(chunks.get(0));
            this.realChunkCount = Math.max(0, chunks.size() - (hasWarmup ? 1 : 0));
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

            frame.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    updateScaledIcon();
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
                currentImage = generateQr(chunks.get(index), bounds.width, bounds.height);
                drawIndexBadge(currentImage, badgeTextFor(index));
                updateScaledIcon();
                frame.setTitle(titleFor(index));
            } catch (WriterException ex) {
                timer.stop();
                JOptionPane.showMessageDialog(frame, "Error generando QR: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        private boolean isWarmupFrame(int slideIndex) {
            return hasWarmup && slideIndex == 0;
        }

        private String badgeTextFor(int slideIndex) {
            if (isWarmupFrame(slideIndex)) {
                return "W";
            }
            int realIndex = hasWarmup ? slideIndex - 1 : slideIndex;
            return Integer.toString(Math.max(0, realIndex));
        }

        private String titleFor(int slideIndex) {
            if (isWarmupFrame(slideIndex)) {
                return "QR Warmup";
            }
            int realIndex = hasWarmup ? slideIndex - 1 : slideIndex;
            if (realChunkCount > 0) {
                return "QR " + (realIndex + 1) + "/" + realChunkCount;
            }
            return "QR";
        }

        private void updateScaledIcon() {
            if (currentImage == null) {
                return;
            }
            int width = Math.max(1, frame.getContentPane().getWidth());
            int height = Math.max(1, frame.getContentPane().getHeight());
            Image scaled = currentImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            label.setIcon(new ImageIcon(scaled));
            label.revalidate();
            label.repaint();
        }
    }

    private static void drawIndexBadge(BufferedImage image, String badgeText) {
        Graphics2D g2d = image.createGraphics();
        try {
            int minDim = Math.min(image.getWidth(), image.getHeight());
            int padding = Math.max(12, minDim / 50);
            int fontSize = Math.max(18, minDim / 20);
            String text = badgeText;

            g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            FontMetrics metrics = g2d.getFontMetrics();
            int textWidth = metrics.stringWidth(text);
            int textHeight = metrics.getAscent();

            int boxWidth = textWidth + padding * 2;
            int boxHeight = textHeight + padding;

            int x = padding;
            int y = padding;

            g2d.setColor(Color.WHITE);
            g2d.fillRoundRect(x, y, boxWidth, boxHeight, padding, padding);
            g2d.setColor(Color.BLACK);
            g2d.drawRoundRect(x, y, boxWidth, boxHeight, padding, padding);
            g2d.drawString(text, x + padding, y + padding / 2 + textHeight);
        } finally {
            g2d.dispose();
        }
    }
}
