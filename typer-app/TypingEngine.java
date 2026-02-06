import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;

public final class TypingEngine {
    private static final int CHAR_DELAY_MS = 1;

    private TypingEngine() {
    }

    public static void type(String text) {
        type(text, null);
    }

    public static void type(String text, ProgressListener listener) {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(CHAR_DELAY_MS);
            typeWithRobot(robot, text, listener);
        } catch (AWTException ex) {
            ex.printStackTrace();
        }
    }

    private static void typeWithRobot(Robot robot, String text, ProgressListener listener) {
        int total = text.codePointCount(0, text.length());
        long start = System.nanoTime();
        ProgressState progressState = new ProgressState(start);
        int typed = 0;

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);

            if (codePoint == '\n') {
                robot.keyPress(KeyEvent.VK_ENTER);
                robot.keyRelease(KeyEvent.VK_ENTER);
                typed++;
                updateProgress(listener, typed, total, start, progressState);
                continue;
            }
            if (codePoint == '\r') {
                continue;
            }
            if (codePoint == '\t') {
                robot.keyPress(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_TAB);
                typed++;
                updateProgress(listener, typed, total, start, progressState);
                continue;
            }

            if (isSafeDirectCodePoint(codePoint) && codePoint <= Character.MAX_VALUE) {
                char ch = (char) codePoint;
                int keyCode = KeyEvent.getExtendedKeyCodeForChar(ch);
                if (canTypeDirectly(keyCode)) {
                    typeKey(robot, ch, keyCode);
                    typed++;
                    updateProgress(listener, typed, total, start, progressState);
                    continue;
                }
            }

            if (codePoint >= 0 && codePoint <= 255) {
                typeAltCode(robot, codePoint);
                typed++;
                updateProgress(listener, typed, total, start, progressState);
            }
        }

        if (listener != null) {
            listener.onProgress(total, total, System.nanoTime() - start);
        }
    }

    private static void updateProgress(ProgressListener listener, int typed, int total, long start,
                                       ProgressState state) {
        if (listener == null) {
            return;
        }
        long now = System.nanoTime();
        boolean shouldUpdate = typed >= total
                || typed - state.lastUpdateCount >= 50
                || now - state.lastUpdateNanos >= 50_000_000L;
        if (!shouldUpdate) {
            return;
        }
        listener.onProgress(typed, total, now - start);
        state.lastUpdateCount = typed;
        state.lastUpdateNanos = now;
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

    public interface ProgressListener {
        void onProgress(int typed, int total, long elapsedNanos);
    }

    private static final class ProgressState {
        private int lastUpdateCount;
        private long lastUpdateNanos;

        private ProgressState(long startNanos) {
            this.lastUpdateCount = 0;
            this.lastUpdateNanos = startNanos;
        }
    }
}
