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
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.nio.file.InvalidPathException;
import java.util.stream.Stream;
import java.util.prefs.Preferences;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class QrGeneratorApp {
    private static final int CHUNK_SIZE = 2000;
    private static final int SLIDE_DELAY_MS = 1250;
    private static final int MAX_FILE_LABEL_CHARS = 70;
    private static final String WARMUP_QR_PAYLOAD = "__WARMUP__";
    private static final String FILE_PAYLOAD_PREFIX = "QRFILE1|";
    private static final String DEFAULT_FILE_NAME = "payload.bin";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String PREF_LAST_CHOOSER_DIR = "lastChooserDirectory";
    private static final Preferences APP_PREFS = Preferences.userNodeForPackage(QrGeneratorApp.class);
    private static QrSlideshow currentSlideshow;
    private static Path selectedFile;
    private static Path lastChooserDirectory = loadLastChooserDirectory();
    private static String selectedFilePayload;

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
        JRadioButton sourceFile = new JRadioButton("Archivo (Base64+Meta)");
        sourceText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sourceFile.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        ButtonGroup sourceGroup = new ButtonGroup();
        sourceGroup.add(sourceText);
        sourceGroup.add(sourceFile);

        JButton chooseFileButton = new JButton("Seleccionar archivo");
        chooseFileButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chooseFileButton.setFocusPainted(false);
        JLabel fileLabel = new JLabel();
        fileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        setSelectedFileLabel(fileLabel, selectedFile);

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

        JPanel fileRow = new JPanel(new BorderLayout(8, 0));
        fileRow.add(fileLabel, BorderLayout.CENTER);
        fileRow.add(generateButton, BorderLayout.EAST);
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
            if (selectedFilePayload != null) {
                raw = selectedFilePayload;
            } else {
                try {
                    byte[] bytes = Files.readAllBytes(selectedFile);
                    raw = buildFilePayload(selectedFile, bytes);
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
                        currentSlideshow = new QrSlideshow(bounds, preRenderedSlides);
                        currentSlideshow.start();
                    } catch (RuntimeException ex) {
                        deleteRecursively(preRenderedSlides.tempDir);
                        throw ex;
                    }
                    System.out.println(String.format(Locale.US,
                            "[generador] Slides listos. Esperando 'Continuar'. Slides=%d, intervalo=%dms",
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
        JFileChooser chooser;
        if (lastChooserDirectory != null && Files.isDirectory(lastChooserDirectory)) {
            chooser = new JFileChooser(lastChooserDirectory.toFile());
        } else {
            chooser = new JFileChooser();
        }
        chooser.setDialogTitle("Seleccionar archivo");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(true);
        int result = chooser.showOpenDialog(parent);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile().toPath();
            Path parentDir = selectedFile.getParent();
            if (parentDir != null) {
                lastChooserDirectory = parentDir;
                APP_PREFS.put(PREF_LAST_CHOOSER_DIR, parentDir.toString());
            }
            setSelectedFileLabel(fileLabel, selectedFile);
            sourceFile.setSelected(true);
            try {
                byte[] bytes = Files.readAllBytes(selectedFile);
                selectedFilePayload = buildFilePayload(selectedFile, bytes);
            } catch (IOException ex) {
                selectedFilePayload = null;
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
            count = countChunks(selectedFilePayload);
        }
        label.setText("Total QRs: " + count);
    }

    private static String buildFilePayload(Path file, byte[] bytes) throws IOException {
        String mimeType = resolveContentType(file);
        Path fileNamePath = file == null ? null : file.getFileName();
        String fileName = fileNamePath == null ? DEFAULT_FILE_NAME : fileNamePath.toString();
        String encodedFileName = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(fileName.getBytes(StandardCharsets.UTF_8));
        String encodedData = Base64.getEncoder().encodeToString(bytes);
        return FILE_PAYLOAD_PREFIX + mimeType + "|" + encodedFileName + "|" + encodedData;
    }

    private static String resolveContentType(Path file) throws IOException {
        String probed = file == null ? null : Files.probeContentType(file);
        if (probed != null && !probed.isBlank()) {
            return probed.toLowerCase(Locale.ROOT);
        }

        String extension = extensionOf(file);
        switch (extension) {
            case ".zip":
                return "application/zip";
            case ".pdf":
                return "application/pdf";
            case ".txt":
                return "text/plain";
            case ".json":
                return "application/json";
            case ".csv":
                return "text/csv";
            case ".xml":
                return "application/xml";
            case ".png":
                return "image/png";
            case ".jpg":
            case ".jpeg":
                return "image/jpeg";
            default:
                return DEFAULT_CONTENT_TYPE;
        }
    }

    private static String extensionOf(Path file) {
        if (file == null || file.getFileName() == null) {
            return "";
        }
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static void setSelectedFileLabel(JLabel label, Path path) {
        if (path == null) {
            label.setText("Ningun archivo seleccionado");
            label.setToolTipText(null);
            return;
        }
        String fullPath = path.toString();
        label.setText(ellipsizeMiddle(fullPath, MAX_FILE_LABEL_CHARS));
        label.setToolTipText(fullPath);
    }

    private static String ellipsizeMiddle(String value, int maxLength) {
        if (value == null || value.length() <= maxLength || maxLength < 7) {
            return value;
        }
        int startLength = (maxLength - 3) / 2;
        int endLength = maxLength - 3 - startLength;
        return value.substring(0, startLength) + "..." + value.substring(value.length() - endLength);
    }

    private static int squareSizeFor(Rectangle bounds) {
        return Math.max(1, Math.min(bounds.width, bounds.height));
    }

    private static Rectangle centeredSquareBounds(Rectangle bounds) {
        int side = squareSizeFor(bounds);
        int x = bounds.x + ((bounds.width - side) / 2);
        int y = bounds.y + ((bounds.height - side) / 2);
        return new Rectangle(x, y, side, side);
    }

    private static Path loadLastChooserDirectory() {
        String raw = APP_PREFS.get(PREF_LAST_CHOOSER_DIR, null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Path stored = Path.of(raw);
            return Files.isDirectory(stored) ? stored : null;
        } catch (InvalidPathException ex) {
            return null;
        }
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
        private final JButton continueButton;
        private final Path tempSlidesDir;
        private final List<SlideAsset> slides;
        private final Timer timer;
        private int index;
        private BufferedImage currentImage;
        private boolean slidesDeleted;
        private boolean playbackStarted;

        private QrSlideshow(Rectangle bounds, PreRenderedSlides preRenderedSlides) {
            this.index = 0;
            this.slidesDeleted = false;
            this.playbackStarted = false;
            this.tempSlidesDir = preRenderedSlides.tempDir;
            this.slides = preRenderedSlides.slides;

            frame = new JFrame();
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            label = new JLabel();
            label.setHorizontalAlignment(JLabel.CENTER);
            label.setVerticalAlignment(JLabel.CENTER);
            frame.add(label, BorderLayout.CENTER);

            continueButton = new JButton("Continuar");
            continueButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
            continueButton.setFocusPainted(false);
            continueButton.addActionListener(e -> startPlayback());

            JPanel controlsPanel = new JPanel();
            controlsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            controlsPanel.add(continueButton);
            frame.add(controlsPanel, BorderLayout.SOUTH);

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

            frame.setBounds(centeredSquareBounds(bounds));
        }

        private void start() {
            frame.setVisible(true);
        }

        private void startPlayback() {
            if (playbackStarted) {
                return;
            }
            playbackStarted = true;
            continueButton.setEnabled(false);
            frame.repaint();

            System.out.println(String.format(Locale.US,
                    "[generador] Reproduccion iniciada. Slides=%d, intervalo=%dms",
                    slides.size(), SLIDE_DELAY_MS));

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
            return Integer.toString(Math.max(1, realIndex + 1));
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
            int width = Math.max(1, label.getWidth());
            int height = Math.max(1, label.getHeight());
            int side = Math.max(1, Math.min(width, height));
            Image scaled = currentImage.getScaledInstance(side, side, Image.SCALE_SMOOTH);
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
            int slideSize = squareSizeFor(bounds);
            Path tempDir = Files.createTempDirectory("qr-generator-slides-");
            List<SlideAsset> renderedSlides = new ArrayList<>(chunks.size());
            try {
                System.out.println("[generador] Carpeta temporal: " + tempDir.toAbsolutePath());
                if (reporter != null) {
                    reporter.onProgress(0, chunks.size(), "Generando QR 0/" + chunks.size());
                }
                for (int i = 0; i < chunks.size(); i++) {
                    BufferedImage qr = generateQr(chunks.get(i), slideSize, slideSize);
                    drawQrOverlay(qr, badgeTextFor(hasWarmup, i));
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

    private static void drawQrOverlay(BufferedImage image, String badgeText) {
        Graphics2D g2d = image.createGraphics();
        try {
            int minDim = Math.min(image.getWidth(), image.getHeight());
            int borderThickness = Math.max(4, minDim / 90);
            String text = badgeText;

            Rectangle darkBounds = findDarkPixelBounds(image);
            int outerMargin = borderThickness + Math.max(2, minDim / 320);
            int safeGap = Math.max(2, minDim / 320);
            int availableWidth = Math.max(1, darkBounds.x - safeGap - outerMargin);
            int availableHeight = Math.max(1, darkBounds.y - safeGap - outerMargin);
            int fontSize = Math.max(10, Math.min(24, minDim / 42));
            int minFontSize = 8;
            int basePadX = Math.max(3, minDim / 260);
            int basePadY = Math.max(3, minDim / 300);

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics metrics = null;
            int textWidth = 0;
            int textHeight = 0;
            int padX = basePadX;
            int padY = basePadY;
            int boxWidth = 0;
            int boxHeight = 0;
            while (true) {
                g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
                metrics = g2d.getFontMetrics();
                textWidth = metrics.stringWidth(text);
                textHeight = metrics.getAscent() + metrics.getDescent();
                int maxPadX = Math.max(2, (availableWidth - textWidth) / 2);
                int maxPadY = Math.max(2, (availableHeight - textHeight) / 2);
                padX = Math.max(2, Math.min(basePadX, maxPadX));
                padY = Math.max(2, Math.min(basePadY, maxPadY));
                boxWidth = textWidth + padX * 2;
                boxHeight = textHeight + padY * 2;
                boolean fits = textWidth <= availableWidth
                        && textHeight <= availableHeight
                        && boxWidth <= availableWidth
                        && boxHeight <= availableHeight;
                if (fits || fontSize <= minFontSize) {
                    break;
                }
                fontSize--;
            }

            int x = outerMargin;
            int y = outerMargin;

            g2d.setColor(Color.BLACK);
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            g2d.fillRect(0, 0, imageWidth, borderThickness);
            g2d.fillRect(0, Math.max(0, imageHeight - borderThickness), imageWidth, borderThickness);
            g2d.fillRect(0, 0, borderThickness, imageHeight);
            g2d.fillRect(Math.max(0, imageWidth - borderThickness), 0, borderThickness, imageHeight);

            g2d.setColor(Color.WHITE);
            int badgeCorner = Math.max(8, Math.min(boxWidth, boxHeight) / 5);
            g2d.fillRoundRect(x, y, boxWidth, boxHeight, badgeCorner, badgeCorner);
            g2d.setColor(Color.BLACK);
            g2d.drawRoundRect(x, y, boxWidth, boxHeight, badgeCorner, badgeCorner);
            int textX = x + ((boxWidth - textWidth) / 2);
            int textY = y + ((boxHeight - textHeight) / 2) + metrics.getAscent();
            g2d.drawString(text, textX, textY);
        } finally {
            g2d.dispose();
        }
    }

    private static Rectangle findDarkPixelBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y) & 0x00FFFFFF;
                if (rgb != 0x00FFFFFF) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return new Rectangle(image.getWidth(), image.getHeight(), 0, 0);
        }
        return new Rectangle(minX, minY, (maxX - minX) + 1, (maxY - minY) + 1);
    }
}
