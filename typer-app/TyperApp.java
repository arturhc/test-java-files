import javax.swing.SwingUtilities;

public class TyperApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TyperUi().show());
    }
}
