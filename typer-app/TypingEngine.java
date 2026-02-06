import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;

public final class TypingEngine {
    private static final int CHAR_DELAY_MS = 2;

    private TypingEngine() {
    }

    public static void type(String text) {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(CHAR_DELAY_MS);
            typeWithRobot(robot, text);
        } catch (AWTException ex) {
            ex.printStackTrace();
        }
    }

    private static void typeWithRobot(Robot robot, String text) {
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);

            if (codePoint == '\n') {
                robot.keyPress(KeyEvent.VK_ENTER);
                robot.keyRelease(KeyEvent.VK_ENTER);
                continue;
            }
            if (codePoint == '\r') {
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
                    typeKey(robot, ch, keyCode);
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

    private static void typeKey(Robot robot, char ch, int keyCode) {
        boolean needsShift = Character.isUpperCase(ch);
        if (needsShift) {
            robot.keyPress(KeyEvent.VK_SHIFT);
        }
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
        if (needsShift) {
            robot.keyRelease(KeyEvent.VK_SHIFT);
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
