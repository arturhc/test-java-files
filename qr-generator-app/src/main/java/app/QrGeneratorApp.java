package app;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;

public final class QrGeneratorApp {
    private static final int DEFAULT_SIZE = 300;

    private QrGeneratorApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(QrGeneratorApp::showUi);
    }

    private static BufferedImage generateQr(String text, int size) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    private static void showUi() {
        JFrame frame = new JFrame("QR Generator");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
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
    }

    private static void onGenerate(JFrame parent, String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Pega un texto para generar el QR.", "Falta texto",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            BufferedImage image = generateQr(trimmed, DEFAULT_SIZE);
            showQrWindow(parent, image);
        } catch (WriterException ex) {
            JOptionPane.showMessageDialog(parent, "Error generando QR: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void showQrWindow(JFrame parent, Image image) {
        JFrame qrFrame = new JFrame("QR");
        qrFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        qrFrame.add(new JLabel(new javax.swing.ImageIcon(image)));
        qrFrame.pack();
        qrFrame.setLocationRelativeTo(parent);
        qrFrame.setVisible(true);
    }
}
