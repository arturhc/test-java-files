import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class TyperApp {
    private static final int START_DELAY_MS = 5000;
    private static final int CHAR_DELAY_MS = 2;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TyperApp::createAndShowUi);
    }

    private static void createAndShowUi() {
        JFrame frame = new JFrame("Typer App");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(12, 12));

        JTextArea textArea = new JTextArea();
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        textArea.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

        JButton startButton = new JButton("Escribir (delay 5s)");
        startButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        startButton.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        startButton.addActionListener(e -> {
            String text = textArea.getText();
            if (text == null || text.isEmpty()) {
                return;
            }
            startButton.setEnabled(false);
            new Thread(() -> {
                try {
                    Thread.sleep(START_DELAY_MS);
                    Robot robot = new Robot();
                    robot.setAutoDelay(CHAR_DELAY_MS);
                    typeText(robot, text);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (AWTException ex) {
                    ex.printStackTrace();
                } finally {
                    SwingUtilities.invokeLater(() -> startButton.setEnabled(true));
                }
            }, "typer-thread").start();
        });

        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(startButton, BorderLayout.SOUTH);

        frame.setPreferredSize(new Dimension(520, 320));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void typeText(Robot robot, String text) {
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);

            if (codePoint == '\n') {
                robot.keyPress(KeyEvent.VK_ENTER);
                robot.keyRelease(KeyEvent.VK_ENTER);
                continue;
            }
            if (codePoint == '\t') {
                robot.keyPress(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_TAB);
                continue;
            }

            if (isSafeDirectCodePoint(codePoint) && codePoint <= Character.MAX_VALUE) {
                char ch = (char) codePoint;
                int keyCode = KeyEvent.getExtendedKeyCodeForChar(ch);
                if (canTypeDirectly(keyCode)) {
                    tryTypeKey(robot, ch, keyCode);
                    continue;
                }
            }

            if (codePoint >= 0 && codePoint <= 255) {
                typeAltCode(robot, codePoint);
            }
        }
    }

    private static boolean canTypeDirectly(int keyCode) {
        if (keyCode == KeyEvent.VK_UNDEFINED) {
            return false;
        }
        if (keyCode < KeyEvent.VK_UNDEFINED || keyCode > KeyEvent.KEY_LAST) {
            return false;
        }
        return !isDeadKey(keyCode);
    }

    private static boolean isDeadKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_DEAD_GRAVE:
            case KeyEvent.VK_DEAD_ACUTE:
            case KeyEvent.VK_DEAD_CIRCUMFLEX:
            case KeyEvent.VK_DEAD_TILDE:
            case KeyEvent.VK_DEAD_MACRON:
            case KeyEvent.VK_DEAD_BREVE:
            case KeyEvent.VK_DEAD_ABOVEDOT:
            case KeyEvent.VK_DEAD_DIAERESIS:
            case KeyEvent.VK_DEAD_ABOVERING:
            case KeyEvent.VK_DEAD_DOUBLEACUTE:
            case KeyEvent.VK_DEAD_CARON:
            case KeyEvent.VK_DEAD_CEDILLA:
            case KeyEvent.VK_DEAD_OGONEK:
            case KeyEvent.VK_DEAD_IOTA:
            case KeyEvent.VK_DEAD_VOICED_SOUND:
            case KeyEvent.VK_DEAD_SEMIVOICED_SOUND:
                return true;
            default:
                return false;
        }
    }

    private static boolean isSafeDirectCodePoint(int codePoint) {
        if (codePoint == ' ') {
            return true;
        }
        if (codePoint >= '0' && codePoint <= '9') {
            return true;
        }
        if (codePoint >= 'a' && codePoint <= 'z') {
            return true;
        }
        if (codePoint >= 'A' && codePoint <= 'Z') {
            return true;
        }
        return false;
    }

    private static void tryTypeKey(Robot robot, char ch, int keyCode) {
        boolean needsShift = Character.isUpperCase(ch);
        try {
            if (needsShift) {
                robot.keyPress(KeyEvent.VK_SHIFT);
            }
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
        } finally {
            if (needsShift) {
                try {
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private static void typeAltCode(Robot robot, int codePoint) {
        String code = String.format("%04d", codePoint);
        robot.keyPress(KeyEvent.VK_ALT);
        for (int i = 0; i < code.length(); i++) {
            int digit = code.charAt(i) - '0';
            int keyCode = KeyEvent.VK_NUMPAD0 + digit;
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
        }
        robot.keyRelease(KeyEvent.VK_ALT);
    }
}
