import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.io.*;
import java.rmi.Naming;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

// ==================== MODEL DỮ LIỆU SINH VIÊN ====================
class StudentRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    public String id, name, email, major, originServer;
    public double gpa;
    public long timestamp;

    public StudentRecord(String id, String name, String email, String major, double gpa, String originServer) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.major = major;
        this.gpa = gpa;
        this.originServer = originServer;
        this.timestamp = System.currentTimeMillis();
    }
}

// ==================== REMOTE INTERFACE ====================
interface IDatabaseSync extends Remote {
    String getServerId() throws RemoteException;
    boolean ping() throws RemoteException;
    List<StudentRecord> getAll() throws RemoteException;
    boolean insert(StudentRecord r) throws RemoteException;
    boolean update(StudentRecord r) throws RemoteException;
    boolean delete(String id) throws RemoteException;
    boolean replicate(String action, StudentRecord r, String sender) throws RemoteException;
}

// ==================== RMI SERVER VỚI CƠ SỞ DỮ LIỆU ĐỒNG BỘ ====================
public class RMIServer extends JFrame implements IDatabaseSync {
    private static final long serialVersionUID = 1L;

    private final String serverId;
    private final int port;
    private final int[] peerPorts;
    private final Map<String, StudentRecord> db = new ConcurrentHashMap<>();
    private final File dbFile;

    private DefaultTableModel tableModel;
    private JTextArea txtLog;
    private JLabel lblStatusBadge;

    public RMIServer(int serverIndex) throws RemoteException {
        if (serverIndex == 1) {
            this.serverId = "SERVER_1";
            this.port = 1099;
            this.peerPorts = new int[]{1098, 1097};
        } else if (serverIndex == 2) {
            this.serverId = "SERVER_2";
            this.port = 1098;
            this.peerPorts = new int[]{1099, 1097};
        } else {
            this.serverId = "SERVER_3";
            this.port = 1097;
            this.peerPorts = new int[]{1099, 1098};
        }

        this.dbFile = new File("database_" + serverId.toLowerCase() + ".csv");

        loadFromDbFile();
        initModernUI();
        startService();
        pullSyncFromPeers();
    }

    private synchronized void loadFromDbFile() {
        if (!dbFile.exists()) {
            initSampleData();
            saveToDbFile();
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(dbFile), "UTF-8"))) {
            String line;
            db.clear();
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split(",", -1);
                if (p.length >= 6) {
                    double gpa = 0.0;
                    try { gpa = Double.parseDouble(p[4].trim()); } catch (Exception ignored) {}
                    StudentRecord r = new StudentRecord(p[0].trim(), p[1].trim(), p[2].trim(), p[3].trim(), gpa, p[5].trim());
                    db.put(r.id, r);
                }
            }
        } catch (Exception e) {
            initSampleData();
        }
    }

    private synchronized void saveToDbFile() {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(dbFile), "UTF-8"))) {
            pw.println("# MaSV,HoTen,Email,Nganh,GPA,ServerGhiNhan");
            for (StudentRecord r : db.values()) {
                pw.printf("%s,%s,%s,%s,%.2f,%s\n", r.id, r.name, r.email, r.major, r.gpa, r.originServer);
            }
            pw.flush();
        } catch (Exception e) {
            log("[LỖI CSDL] Không thể ghi file: " + e.getMessage());
        }
    }

    private void initSampleData() {
        if ("SERVER_1".equals(serverId)) {
            db.put("24IT001", new StudentRecord("24IT001", "Trần Quang Bình", "binhtq.24it@vku.udn.vn", "CNTT", 3.85, serverId));
            db.put("24IT002", new StudentRecord("24IT002", "Lê Nhuận Phát", "phatln.24it@vku.udn.vn", "KTPM", 3.75, serverId));
        }
    }

    private void initModernUI() {
        setTitle("VKU RMI Distributed Server - " + serverId + " [Cổng " + port + "]");
        setSize(980, 650);
        setMinimumSize(new Dimension(820, 520));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(248, 250, 252));
        setLayout(new BorderLayout(10, 10));

        // 1. Header Card
        JPanel headerCard = new JPanel(new BorderLayout(15, 0));
        headerCard.setBackground(Color.WHITE);
        headerCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1),
                new EmptyBorder(14, 20, 14, 20)
        ));

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBox.setOpaque(false);
        JLabel lblTitle = new JLabel("MÁY CHỦ PHÂN TÁN RMI: " + serverId);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblTitle.setForeground(new Color(15, 23, 42));

        JLabel lblSub = new JLabel("Địa chỉ: rmi://127.0.0.1:" + port + "/SyncService  |  Cụm Peer: " + Arrays.toString(peerPorts) + "  |  CSDL: " + dbFile.getName());
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(100, 116, 139));
        titleBox.add(lblTitle);
        titleBox.add(lblSub);

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightHeader.setOpaque(false);

        JButton btnOpenFile = new JButton("📂 Xem file CSDL");
        btnOpenFile.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnOpenFile.setFocusPainted(false);
        btnOpenFile.setBackground(new Color(241, 245, 249));
        btnOpenFile.addActionListener(e -> {
            try {
                if (dbFile.exists()) {
                    Desktop.getDesktop().open(dbFile);
                } else {
                    JOptionPane.showMessageDialog(this, "File CSDL chưa được khởi tạo!");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Đường dẫn file CSDL: " + dbFile.getAbsolutePath());
            }
        });
        rightHeader.add(btnOpenFile);

        JButton btnPull = new JButton("🔄 Kéo dữ liệu từ Peer");
        btnPull.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnPull.setFocusPainted(false);
        btnPull.setBackground(new Color(241, 245, 249));
        btnPull.addActionListener(e -> pullSyncFromPeers());
        rightHeader.add(btnPull);

        lblStatusBadge = new JLabel("  ● ONLINE (Port " + port + ")  ");
        lblStatusBadge.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblStatusBadge.setOpaque(true);
        lblStatusBadge.setBackground(new Color(220, 252, 231));
        lblStatusBadge.setForeground(new Color(22, 101, 52));
        lblStatusBadge.setBorder(new LineBorder(new Color(134, 239, 172), 1, true));
        rightHeader.add(lblStatusBadge);

        headerCard.add(titleBox, BorderLayout.WEST);
        headerCard.add(rightHeader, BorderLayout.EAST);
        add(headerCard, BorderLayout.NORTH);

        // 2. Center: Table & Log Split
        JPanel bodyPanel = new JPanel(new BorderLayout(10, 10));
        bodyPanel.setOpaque(false);
        bodyPanel.setBorder(new EmptyBorder(0, 15, 12, 15));

        // --- Table Panel ---
        JPanel tableCard = new JPanel(new BorderLayout(0, 8));
        tableCard.setBackground(Color.WHITE);
        tableCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JLabel lblTableTitle = new JLabel("CƠ SỞ DỮ LIỆU CỤC BỘ HIỆN TẠI (" + serverId + ") - ĐƯỢC LƯU TRỰC TIẾP VÀO: " + dbFile.getName());
        lblTableTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblTableTitle.setForeground(new Color(30, 41, 59));
        tableCard.add(lblTableTitle, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new String[]{"Mã SV", "Họ và Tên", "Email", "Chuyên ngành", "GPA", "Server ghi nhận"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setGridColor(new Color(241, 245, 249));
        table.setSelectionBackground(new Color(224, 242, 254));
        table.setSelectionForeground(new Color(15, 23, 42));

        JTableHeader th = table.getTableHeader();
        th.setFont(new Font("Segoe UI", Font.BOLD, 12));
        th.setBackground(new Color(241, 245, 249));
        th.setForeground(new Color(71, 85, 105));
        th.setPreferredSize(new Dimension(0, 32));

        DefaultTableCellRenderer centerRender = new DefaultTableCellRenderer();
        centerRender.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(centerRender);
        table.getColumnModel().getColumn(4).setCellRenderer(centerRender);
        table.getColumnModel().getColumn(5).setCellRenderer(centerRender);

        tableCard.add(new JScrollPane(table), BorderLayout.CENTER);

        // --- Log Panel ---
        JPanel logCard = new JPanel(new BorderLayout(0, 8));
        logCard.setBackground(Color.WHITE);
        logCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JPanel logHeader = new JPanel(new BorderLayout());
        logHeader.setOpaque(false);
        JLabel lblLogTitle = new JLabel("NHẬT KÝ GỌI HÀM TỪ XA & ĐỒNG BỘ REPLICATION");
        lblLogTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblLogTitle.setForeground(new Color(71, 85, 105));

        JButton btnClear = new JButton("Xóa log");
        btnClear.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnClear.setFocusPainted(false);
        btnClear.addActionListener(e -> txtLog.setText(""));
        logHeader.add(lblLogTitle, BorderLayout.WEST);
        logHeader.add(btnClear, BorderLayout.EAST);
        logCard.add(logHeader, BorderLayout.NORTH);

        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLog.setBackground(new Color(15, 23, 42)); // Slate 900
        txtLog.setForeground(new Color(226, 232, 240));
        txtLog.setMargin(new Insets(8, 10, 8, 10));
        logCard.add(new JScrollPane(txtLog), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableCard, logCard);
        split.setDividerLocation(310);
        split.setResizeWeight(0.6);
        split.setBorder(null);

        bodyPanel.add(split, BorderLayout.CENTER);
        add(bodyPanel, BorderLayout.CENTER);

        refreshTable();
    }

    private void startService() {
        try {
            System.setProperty("java.rmi.server.hostname", "127.0.0.1");
            Registry reg;
            try {
                reg = LocateRegistry.createRegistry(port);
            } catch (Exception e) {
                reg = LocateRegistry.getRegistry(port);
            }
            IDatabaseSync stub = (IDatabaseSync) UnicastRemoteObject.exportObject(this, 0);
            reg.rebind("SyncService", stub);
            log("[KHỞI TẠO] Dịch vụ 'SyncService' đã đăng ký thành công vào RMI Registry tại cổng " + port);
            log("[CSDL FILE] Dữ liệu CSDL được tự động đồng bộ và lưu vào file: " + dbFile.getName());
        } catch (Exception e) {
            log("[LỖI KHỞI ĐỘNG] " + e.getMessage());
        }
    }

    public synchronized void pullSyncFromPeers() {
        Executors.newCachedThreadPool().submit(() -> {
            for (int p : peerPorts) {
                try {
                    IDatabaseSync peer = (IDatabaseSync) Naming.lookup("rmi://127.0.0.1:" + p + "/SyncService");
                    List<StudentRecord> peerList = peer.getAll();
                    if (peerList != null && !peerList.isEmpty()) {
                        int count = 0;
                        for (StudentRecord r : peerList) {
                            if (!db.containsKey(r.id)) {
                                db.put(r.id, r);
                                count++;
                            }
                        }
                        if (count > 0) {
                            saveToDbFile();
                            refreshTable();
                            log("[TỰ ĐỘNG ĐỒNG BỘ] Đã kéo " + count + " bản ghi mới từ Server cổng " + p);
                        }
                        break;
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    public String getServerId() { return serverId; }

    @Override
    public boolean ping() { return true; }

    @Override
    public List<StudentRecord> getAll() {
        return new ArrayList<>(db.values());
    }

    @Override
    public synchronized boolean insert(StudentRecord r) {
        if (db.containsKey(r.id)) return false;
        r.originServer = serverId;
        db.put(r.id, r);
        saveToDbFile();
        refreshTable();
        log("[CLIENT THÊM] ID: " + r.id + " | Tên: " + r.name + " | Ngành: " + r.major + " | GPA: " + r.gpa);
        syncToPeers("INSERT", r);
        return true;
    }

    @Override
    public synchronized boolean update(StudentRecord r) {
        if (!db.containsKey(r.id)) return false;
        r.originServer = serverId;
        db.put(r.id, r);
        saveToDbFile();
        refreshTable();
        log("[CLIENT SỬA] ID: " + r.id + " | Tên: " + r.name + " | GPA: " + r.gpa);
        syncToPeers("UPDATE", r);
        return true;
    }

    @Override
    public synchronized boolean delete(String id) {
        if (db.remove(id) != null) {
            saveToDbFile();
            refreshTable();
            log("[CLIENT XÓA] Mã SV: " + id);
            syncToPeers("DELETE", new StudentRecord(id, "", "", "", 0, serverId));
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean replicate(String action, StudentRecord r, String sender) {
        log("[REPLICATION NHẬN] << Lệnh: " + action + " từ " + sender + " đối với Mã: " + r.id);
        if ("DELETE".equals(action)) db.remove(r.id);
        else db.put(r.id, r);
        saveToDbFile();
        refreshTable();
        return true;
    }

    private void syncToPeers(String action, StudentRecord r) {
        Executors.newCachedThreadPool().submit(() -> {
            for (int p : peerPorts) {
                try {
                    IDatabaseSync peer = (IDatabaseSync) Naming.lookup("rmi://127.0.0.1:" + p + "/SyncService");
                    peer.replicate(action, r, serverId);
                    log("[REPLICATION GỬI] >> Đã đồng bộ " + action + " (Mã: " + r.id + ") sang Server cổng " + p);
                } catch (Exception e) {
                    log("[CẢNH BÁO] Không thể kết nối Server cổng " + p + " (Server này đang offline)");
                }
            }
        });
    }

    private void refreshTable() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            for (StudentRecord r : db.values()) {
                tableModel.addRow(new Object[]{r.id, r.name, r.email, r.major, String.format("%.2f", r.gpa), r.originServer});
            }
        });
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            txtLog.append("[" + time + "] " + msg + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        int idx = 1;
        if (args.length > 0) {
            try { idx = Integer.parseInt(args[0]); } catch (Exception ignored) {}
        }
        final int serverIdx = idx;
        SwingUtilities.invokeLater(() -> {
            try {
                new RMIServer(serverIdx).setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
