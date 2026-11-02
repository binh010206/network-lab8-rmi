package client;

import javax.swing.*;

/**
 * Lớp khởi động chính (Main Entry) cho ứng dụng RMI Client.
 */
public class RMIClientApp {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            ClientUI client = new ClientUI();
            client.setVisible(true);
        });
    }
}
