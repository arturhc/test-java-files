package app;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JFileChooser;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.awt.GraphicsEnvironment;
import java.util.stream.Stream;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class QrGeneratorApp {
    private static final int CHUNK_SIZE = 2000;
    private static final int SLIDE_DELAY_MS = 500;
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
        generateButton.addActionListener(event -> onGenerate(frame, generateButton, textArea.getText(), sourceText.isSelected()));

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

    private static void onGenerate(JFrame parent, JButton generateButton, String text, boolean useTextSource) {
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

        List<String> chunks = buildSlideshowChunks(raw, CHUNK_SIZE);
        if (chunks.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "No hay contenido para generar QR.", "Sin contenido",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int dataQrCount = Math.max(0, chunks.size() - 1);
        System.out.println(String.format(Locale.US,
                "[generador] Inicio. QRs a generar: %d (datos=%d, warmup=%d), delay=%dms",
                chunks.size(), dataQrCount, chunks.size() - dataQrCount, SLIDE_DELAY_MS));

        GenerationProgressDialog progressDialog = new GenerationProgressDialog(parent, chunks.size());
        progressDialog.update(0, chunks.size(), "Preparando...");
        generateButton.setEnabled(false);

        SwingWorker<PreRenderedSlides, GenerationUpdate> worker = new SwingWorker<>() {
            @Override
            protected PreRenderedSlides doInBackground() throws Exception {
                return QrSlideshow.preRenderSlides(bounds, chunks, (done, total, message) -> {
                    publish(new GenerationUpdate(done, total, message));
                });
            }

            @Override
            protected void process(List<GenerationUpdate> updates) {
                if (updates.isEmpty()) {
                    return;
                }
                GenerationUpdate last = updates.get(updates.size() - 1);
                progressDialog.update(last.done, last.total, last.message);
            }

            @Override
            protected void done() {
                generateButton.setEnabled(true);
                try {
                    PreRenderedSlides preRenderedSlides = get();
                    progressDialog.close();
                    try {
                        currentSlideshow = new QrSlideshow(parent, bounds, chunks, preRenderedSlides);
                        currentSlideshow.start();
                    } catch (RuntimeException ex) {
                        deleteRecursively(preRenderedSlides.tempDir);
                        throw ex;
                    }
                    System.out.println(String.format(Locale.US,
                            "[generador] Reproduccion iniciada. Slides=%d, intervalo=%dms",
                            chunks.size(), SLIDE_DELAY_MS));
                } catch (Exception ex) {
                    progressDialog.close();
                    String message = extractErrorMessage(ex);
                    System.out.println("[generador] Error: " + message);
                    JOptionPane.showMessageDialog(parent, "Error generando QR: " + message, "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        progressDialog.showDialog();
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

    private static String extractErrorMessage(Throwable throwable) {
        Throwable root = throwable;
        if (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    @FunctionalInterface
    private interface ProgressReporter {
        void onProgress(int done, int total, String message);
    }

    private static final class GenerationUpdate {
        private final int done;
        private final int total;
        private final String message;

        private GenerationUpdate(int done, int total, String message) {
            this.done = done;
            this.total = total;
            this.message = message;
        }
    }

    private static final class GenerationProgressDialog {
        private final JDialog dialog;
        private final JLabel statusLabel;
        private final JProgressBar progressBar;

        private GenerationProgressDialog(JFrame parent, int total) {
            dialog = new JDialog(parent, "Generando QRs", false);
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            dialog.setLayout(new BorderLayout(8, 8));

            statusLabel = new JLabel("Preparando...");
            statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));

            progressBar = new JProgressBar(0, Math.max(1, total));
            progressBar.setValue(0);
            progressBar.setStringPainted(true);
            progressBar.setBorder(BorderFactory.createEmptyBorder(0, 12, 10, 12));

            dialog.add(statusLabel, BorderLayout.NORTH);
            dialog.add(progressBar, BorderLayout.CENTER);
            dialog.setSize(new Dimension(420, 110));
            dialog.setResizable(false);
            dialog.setLocationRelativeTo(parent);
        }

        private void update(int done, int total, String message) {
            int max = Math.max(1, total);
            int value = Math.max(0, Math.min(done, max));
            progressBar.setMaximum(max);
            progressBar.setValue(value);
            progressBar.setString(value + " / " + max);
            statusLabel.setText(message);
        }

        private void showDialog() {
            dialog.setVisible(true);
        }

        private void close() {
            if (dialog.isDisplayable()) {
                dialog.dispose();
            }
        }
    }

    private static final class QrSlideshow {
        private final JFrame frame;
        private final JLabel label;
        private final Path tempSlidesDir;
        private final List<SlideAsset> slides;
        private final Timer timer;
        private int index;
        private BufferedImage currentImage;
        private boolean slidesDeleted;

        private QrSlideshow(JFrame parent, Rectangle bounds, List<String> chunks, PreRenderedSlides preRenderedSlides) {
            this.index = 0;
            this.slidesDeleted = false;
            this.tempSlidesDir = preRenderedSlides.tempDir;
            this.slides = preRenderedSlides.slides;

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

            timer = new Timer(SLIDE_DELAY_MS, e -> advance());
            timer.setInitialDelay(SLIDE_DELAY_MS);

            updateImage();

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
            if (slides.size() > 1) {
                timer.start();
            }
        }

        private void stop() {
            if (timer.isRunning()) {
                timer.stop();
            }
            cleanupSlides();
            if (frame.isDisplayable()) {
                frame.dispose();
            }
        }

        private void advance() {
            index++;
            if (index >= slides.size()) {
                timer.stop();
                System.out.println("[generador] Reproduccion finalizada.");
                cleanupSlides();
                return;
            }
            updateImage();
        }

        private void updateImage() {
            try {
                SlideAsset slide = slides.get(index);
                currentImage = ImageIO.read(slide.imagePath.toFile());
                if (currentImage == null) {
                    throw new IOException("No se pudo leer slide: " + slide.imagePath);
                }
                updateScaledIcon();
                frame.setTitle(slide.title);
            } catch (IOException ex) {
                timer.stop();
                cleanupSlides();
                JOptionPane.showMessageDialog(frame, "Error mostrando QR: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        private static String badgeTextFor(boolean hasWarmup, int slideIndex) {
            if (hasWarmup && slideIndex == 0) {
                return "W";
            }
            int realIndex = hasWarmup ? slideIndex - 1 : slideIndex;
            return Integer.toString(Math.max(0, realIndex));
        }

        private static String titleFor(boolean hasWarmup, int realChunkCount, int slideIndex) {
            if (hasWarmup && slideIndex == 0) {
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

        private static PreRenderedSlides preRenderSlides(
                Rectangle bounds,
                List<String> chunks,
                ProgressReporter reporter
        ) throws WriterException, IOException {
            boolean hasWarmup = !chunks.isEmpty() && WARMUP_QR_PAYLOAD.equals(chunks.get(0));
            int realChunkCount = Math.max(0, chunks.size() - (hasWarmup ? 1 : 0));
            Path tempDir = Files.createTempDirectory("qr-generator-slides-");
            List<SlideAsset> renderedSlides = new ArrayList<>(chunks.size());
            try {
                System.out.println("[generador] Carpeta temporal: " + tempDir.toAbsolutePath());
                if (reporter != null) {
                    reporter.onProgress(0, chunks.size(), "Generando QR 0/" + chunks.size());
                }
                for (int i = 0; i < chunks.size(); i++) {
                    BufferedImage qr = generateQr(chunks.get(i), bounds.width, bounds.height);
                    drawIndexBadge(qr, badgeTextFor(hasWarmup, i));
                    Path output = tempDir.resolve(String.format("slide_%04d.png", i));
                    if (!ImageIO.write(qr, "png", output.toFile())) {
                        throw new IOException("No se pudo escribir slide PNG: " + output.getFileName());
                    }
                    renderedSlides.add(new SlideAsset(output, titleFor(hasWarmup, realChunkCount, i)));
                    int done = i + 1;
                    String msg = String.format(Locale.US, "Generando QR %d/%d", done, chunks.size());
                    if (reporter != null) {
                        reporter.onProgress(done, chunks.size(), msg);
                    }
                    System.out.println(String.format(Locale.US, "[generador] %s", msg));
                }
                System.out.println("[generador] Pre-generacion completada.");
                return new PreRenderedSlides(tempDir, renderedSlides);
            } catch (WriterException | IOException ex) {
                deleteRecursively(tempDir);
                throw ex;
            }
        }

        private void cleanupSlides() {
            if (slidesDeleted) {
                return;
            }
            deleteRecursively(tempSlidesDir);
            slidesDeleted = true;
            System.out.println("[generador] Temporales eliminados.");
        }
    }

    private static final class PreRenderedSlides {
        private final Path tempDir;
        private final List<SlideAsset> slides;

        private PreRenderedSlides(Path tempDir, List<SlideAsset> slides) {
            this.tempDir = tempDir;
            this.slides = slides;
        }
    }

    private static final class SlideAsset {
        private final Path imagePath;
        private final String title;

        private SlideAsset(Path imagePath, String title) {
            this.imagePath = imagePath;
            this.title = title;
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
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
