import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class TyperUi {
    private static final int START_DELAY_MS = 5000;

    private final JFrame frame;
    private final JTextArea textArea;
    private final JButton startButton;
    private final JButton chooseFileButton;
    private final JLabel fileLabel;
    private final JRadioButton sourceText;
    private final JRadioButton sourceFile;
    private final JProgressBar progressBar;

    private Path selectedFile;

    public TyperUi() {
        frame = new JFrame("Typer App");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(12, 12));

        textArea = new JTextArea();
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        textArea.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

        sourceText = new JRadioButton("Texto", true);
        sourceFile = new JRadioButton("Archivo (Base64)");
        sourceText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sourceFile.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        ButtonGroup group = new ButtonGroup();
        group.add(sourceText);
        group.add(sourceFile);

        chooseFileButton = new JButton("Seleccionar archivo");
        chooseFileButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        fileLabel = new JLabel("Ningún archivo seleccionado");
        fileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        startButton = new JButton("Escribir (delay 5s)");
        startButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        startButton.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);

        JPanel controlsTop = new JPanel(new BorderLayout(8, 8));
        JPanel sourcePanel = new JPanel();
        sourcePanel.add(sourceText);
        sourcePanel.add(sourceFile);
        controlsTop.add(sourcePanel, BorderLayout.WEST);
        controlsTop.add(chooseFileButton, BorderLayout.EAST);

        JPanel controlsBottom = new JPanel(new BorderLayout(8, 8));
        controlsBottom.add(fileLabel, BorderLayout.CENTER);
        controlsBottom.add(startButton, BorderLayout.EAST);

        JPanel controls = new JPanel(new BorderLayout(8, 8));
        controls.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        controls.add(controlsTop, BorderLayout.NORTH);
        controls.add(controlsBottom, BorderLayout.CENTER);
        controls.add(progressBar, BorderLayout.SOUTH);

        chooseFileButton.addActionListener(e -> chooseFile());
        startButton.addActionListener(e -> startTyping());

        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(controls, BorderLayout.SOUTH);

        frame.setPreferredSize(new Dimension(560, 360));
        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    public void show() {
        frame.setVisible(true);
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccionar archivo");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(true);
        int result = chooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile().toPath();
            fileLabel.setText(selectedFile.toString());
            sourceFile.setSelected(true);
        }
    }

    private void startTyping() {
        String payload;
        if (sourceFile.isSelected()) {
            if (selectedFile == null) {
                showMessage("Selecciona un archivo primero.");
                return;
            }
            try {
                byte[] bytes = Files.readAllBytes(selectedFile);
                payload = Base64.getEncoder().encodeToString(bytes);
            } catch (IOException ex) {
                showMessage("No se pudo leer el archivo.");
                return;
            }
        } else {
            payload = textArea.getText();
            if (payload == null || payload.isEmpty()) {
                showMessage("El área de texto está vacía.");
                return;
            }
        }

        setUiBusy(true);
        new Thread(() -> {
            try {
                Thread.sleep(START_DELAY_MS);
                TypingEngine.type(payload);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                SwingUtilities.invokeLater(() -> setUiBusy(false));
            }
        }, "typer-thread").start();
    }

    private void setUiBusy(boolean busy) {
        startButton.setEnabled(!busy);
        chooseFileButton.setEnabled(!busy);
        textArea.setEnabled(!busy);
        sourceText.setEnabled(!busy);
        sourceFile.setEnabled(!busy);
        progressBar.setVisible(busy);
        frame.revalidate();
        frame.repaint();
    }

    private void showMessage(String message) {
        UIManager.put("OptionPane.messageFont", new Font("Segoe UI", Font.PLAIN, 12));
        JOptionPane.showMessageDialog(frame, message);
    }
}
