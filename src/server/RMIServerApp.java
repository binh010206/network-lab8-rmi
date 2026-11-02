package server;

import common.IDatabaseSync;
import dao.StudentDAO;

import javax.swing.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Lớp khởi động chính (Main Entry) cho RMIServer.
 */
public class RMIServerApp {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        int serverIndex = 1;

        if (args != null && args.length > 0) {
            try {
                serverIndex = Integer.parseInt(args[0].trim());
            } catch (Exception ignored) {}
        } else {
            // Hiển thị hộp thoại chọn Server nếu chạy trực tiếp bằng nút Run
            String[] options = {
                    "Server 1 (Cổng 1099)",
                    "Server 2 (Cổng 1098)",
                    "Server 3 (Cổng 1097)"
            };
            int choice = JOptionPane.showOptionDialog(
                    null,
                    "Chọn máy chủ phân tán cần khởi động:",
                    "Khởi động VKU RMI Server",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]
            );
            if (choice == 1) serverIndex = 2;
            else if (choice == 2) serverIndex = 3;
            else if (choice == -1) return; // Người dùng đóng hộp thoại
        }

        final int idx = serverIndex;
        SwingUtilities.invokeLater(() -> startServer(idx));
    }

    private static void startServer(int serverIndex) {
        String serverId;
        int port;
        int[] peerPorts;

        if (serverIndex == 1) {
            serverId = "SERVER_1";
            port = 1099;
            peerPorts = new int[]{1098, 1097};
        } else if (serverIndex == 2) {
            serverId = "SERVER_2";
            port = 1098;
            peerPorts = new int[]{1099, 1097};
        } else {
            serverId = "SERVER_3";
            port = 1097;
            peerPorts = new int[]{1099, 1098};
        }

        try {
            System.setProperty("java.rmi.server.hostname", "127.0.0.1");

            StudentDAO dao = new StudentDAO(serverId);
            DatabaseSyncImpl service = new DatabaseSyncImpl(serverId, port, peerPorts, dao);

            ServerUI ui = new ServerUI(serverId, port, peerPorts, dao, service::pullSyncFromPeers);
            service.setUI(ui);

            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(port);
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(port);
            }

            registry.rebind("SyncService", service);

            ui.appendLog("[KHỞI TẠO] Dịch vụ 'SyncService' đã đăng ký thành công vào RMI Registry tại cổng " + port);
            ui.setVisible(true);

            // Kéo dữ liệu đồng bộ ban đầu từ các peer đang online
            service.pullSyncFromPeers();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Lỗi khởi động Server: " + e.getMessage(), "Lỗi RMI", JOptionPane.ERROR_MESSAGE);
        }
    }
}
