import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public class RMIServerApp extends JFrame {
    private String serverId;
    private int port;
    private final List<DatabaseSyncServiceImpl.PeerNode> peers = new ArrayList<>();
    private DatabaseSyncServiceImpl serviceImpl;
    private Registry registry;

    private JLabel lblTitle;
    private JLabel lblLanIp;
    private JTable tableData;
    private DefaultTableModel tableModel;
    private JTextArea txtLogs;
    private JPanel peerStatusPanel;
    private final Map<String, JLabel> peerStatusLabels = new HashMap<>();

    public RMIServerApp(int serverNum) {
        setupClusterConfig(serverNum);
        initComponents();
        startRmiService();
        startHeartbeatMonitor();
    }

    private void setupClusterConfig(int serverNum) {
        if (serverNum <= 1) {
            this.serverId = "SERVER_1";
            this.port = 1099;
            peers.add(new DatabaseSyncServiceImpl.PeerNode("SERVER_2", "127.0.0.1", 1098));
            peers.add(new DatabaseSyncServiceImpl.PeerNode("SERVER_3", "127.0.0.1", 1097));
        } else if (serverNum == 2) {
            this.serverId = "SERVER_2";
            this.port = 1098;
            peers.add(new DatabaseSyncServiceImpl.PeerNode("SERVER_1", "127.0.0.1", 1099));
            peers.add(new DatabaseSyncServiceImpl.PeerNode("SERVER_3", "127.0.0.1", 1097));
        } else {
            this.serverId = "SERVER_3";
            this.port = 1097;
            peers.add(new DatabaseSyncServiceImpl.PeerNode("SERVER_1", "127.0.0.1", 1099));
            peers.add(new DatabaseSyncServiceImpl.PeerNode("SERVER_2", "127.0.0.1", 1098));
        }
    }

    private void initComponents() {
        setTitle("VKU RMI Distributed Database Sync - [" + serverId + " (Port " + port + ")]");
        setSize(950, 680);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // 1. Top Panel
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        topPanel.setBackground(new Color(235, 245, 255));

        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 4, 4));
        titlePanel.setOpaque(false);

        lblTitle = new JLabel("🌐 MÁY CHỦ PHÂN TÁN RMI: " + serverId + " (CỔNG: " + port + ")");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblTitle.setForeground(new Color(0, 102, 204));

        String lanIp = getLanIpAddress();
        lblLanIp = new JLabel("Địa chỉ IP: " + lanIp + " | RMI URL: rmi://" + lanIp + ":" + port + "/SyncService");
        lblLanIp.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblLanIp.setForeground(Color.DARK_GRAY);

        titlePanel.add(lblTitle);
        titlePanel.add(lblLanIp);
        topPanel.add(titlePanel, BorderLayout.WEST);

        // Peer cluster status
        peerStatusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 5));
        peerStatusPanel.setOpaque(false);
        peerStatusPanel.setBorder(BorderFactory.createTitledBorder("Cụm Cluster Peers"));
        for (DatabaseSyncServiceImpl.PeerNode peer : peers) {
            JLabel lbl = new JLabel("● " + peer.id + ":" + peer.port + " [Offline]");
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            lbl.setForeground(Color.RED);
            peerStatusLabels.put(peer.id, lbl);
            peerStatusPanel.add(lbl);
        }
        JButton btnSyncNow = new JButton("🔄 Kéo dữ liệu (Full Sync)");
        btnSyncNow.addActionListener(e -> {
            if (serviceImpl != null) {
                serviceImpl.triggerFullSyncFromPeers();
            }
        });
        peerStatusPanel.add(btnSyncNow);
        topPanel.add(peerStatusPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // 2. Center Data Table
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createTitledBorder("📊 Cơ sở Dữ liệu Bản ghi Sinh viên Cục bộ của " + serverId));

        tableModel = new DefaultTableModel(new String[]{"Mã SV", "Họ và Tên", "Email", "Chuyên ngành", "GPA", "Server tạo/sửa", "Cập nhật lúc"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tableData = new JTable(tableModel);
        tableData.setRowHeight(24);
        tableData.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        centerPanel.add(new JScrollPane(tableData), BorderLayout.CENTER);

        // 3. Bottom Logs Panel
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setPreferredSize(new Dimension(800, 220));
        bottomPanel.setBorder(BorderFactory.createTitledBorder("📜 Nhật ký Gọi hàm Từ xa RMI & Đồng bộ Replication"));

        txtLogs = new JTextArea();
        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLogs.setBackground(new Color(25, 25, 25));
        txtLogs.setForeground(new Color(220, 220, 220));
        bottomPanel.add(new JScrollPane(txtLogs), BorderLayout.CENTER);

        centerPanel.add(bottomPanel, BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);
    }

    private void startRmiService() {
        try {
            String myIp = getLanIpAddress();
            System.setProperty("java.rmi.server.hostname", myIp);
            try {
                registry = LocateRegistry.createRegistry(port);
                log("[RMI REGISTRY] Đã tạo mới RMI Registry tại cổng " + port);
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(port);
                log("[RMI REGISTRY] Kết nối tới RMI Registry sẵn có tại cổng " + port);
            }

            serviceImpl = new DatabaseSyncServiceImpl(serverId, port, peers, this);
            registry.rebind("SyncService", serviceImpl);
            log("[RMI BIND] Dịch vụ 'SyncService' đã sẵn sàng phục vụ các Client và Peer!");

            refreshTable();
            // Đồng bộ dữ liệu từ các server peer khác nếu có
            serviceImpl.triggerFullSyncFromPeers();
        } catch (Exception e) {
            log("[LỖI KHỞI ĐỘNG RMI] " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Lỗi RMI: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            txtLogs.append("[" + time + "] " + msg + "\n");
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        });
    }

    public void refreshTable() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            if (serviceImpl != null) {
                try {
                    List<StudentRecord> list = serviceImpl.getAllRecords();
                    for (StudentRecord r : list) {
                        tableModel.addRow(new Object[]{
                                r.getId(),
                                r.getFullName(),
                                r.getEmail(),
                                r.getMajor(),
                                String.format("%.2f", r.getGpa()),
                                r.getOriginServer(),
                                r.getFormattedTime()
                        });
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    private void startHeartbeatMonitor() {
        javax.swing.Timer timer = new javax.swing.Timer(2500, e -> {
            for (DatabaseSyncServiceImpl.PeerNode peer : peers) {
                new Thread(() -> {
                    boolean online = false;
                    try {
                        IDatabaseSyncService stub = (IDatabaseSyncService) Naming.lookup(peer.getRmiUrl());
                        online = stub.ping();
                    } catch (Exception ignored) {}

                    final boolean isOnline = online;
                    SwingUtilities.invokeLater(() -> {
                        JLabel lbl = peerStatusLabels.get(peer.id);
                        if (lbl != null) {
                            if (isOnline) {
                                lbl.setText("● " + peer.id + ":" + peer.port + " [Online]");
                                lbl.setForeground(new Color(40, 167, 69));
                            } else {
                                lbl.setText("● " + peer.id + ":" + peer.port + " [Offline]");
                                lbl.setForeground(Color.RED);
                            }
                        }
                    });
                }).start();
            }
        });
        timer.start();
    }

    private String getLanIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface iface : Collections.list(interfaces)) {
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                for (InetAddress addr : Collections.list(addresses)) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    public static void main(String[] args) {
        int serverNum = 1;
        if (args.length > 0) {
            try {
                serverNum = Integer.parseInt(args[0]);
            } catch (Exception ignored) {}
        }
        final int num = serverNum;
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> {
            new RMIServerApp(num).setVisible(true);
        });
    }
}
