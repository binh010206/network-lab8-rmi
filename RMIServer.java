import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
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

// 1. Đối tượng dữ liệu sinh viên
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

// 2. Remote Interface cho RMI
interface IDatabaseSync extends Remote {
    String getServerId() throws RemoteException;
    boolean ping() throws RemoteException;
    List<StudentRecord> getAll() throws RemoteException;
    boolean insert(StudentRecord r) throws RemoteException;
    boolean update(StudentRecord r) throws RemoteException;
    boolean delete(String id) throws RemoteException;
    boolean replicate(String action, StudentRecord r, String sender) throws RemoteException;
}

// 3. Máy chủ RMI Server
public class RMIServer extends JFrame implements IDatabaseSync {
    private static final long serialVersionUID = 1L;

    private final String serverId;
    private final int port;
    private final int[] peerPorts;
    private final Map<String, StudentRecord> db = new ConcurrentHashMap<>();

    private DefaultTableModel tableModel;
    private JTextArea txtLog;

    public RMIServer(int serverIndex) throws RemoteException {
        // Cấu hình cụm 3 server: Port 1099, 1098, 1097
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

        initSampleData();
        initUI();
        startService();
    }

    private void initSampleData() {
        if ("SERVER_1".equals(serverId)) {
            db.put("24IT001", new StudentRecord("24IT001", "Trần Quang Bình", "binhtq.24it@vku.udn.vn", "CNTT", 3.85, serverId));
            db.put("24IT002", new StudentRecord("24IT002", "Lê Nhuận Phát", "phatln.24it@vku.udn.vn", "KTPM", 3.75, serverId));
        }
    }

    private void initUI() {
        setTitle("RMI Server - " + serverId + " (Cổng " + port + ")");
        setSize(850, 560);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // Header
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(new Color(230, 242, 255));
        top.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        JLabel lbl = new JLabel("🌐 " + serverId + " - CỔNG RMI: " + port + " (rmi://localhost:" + port + "/SyncService)");
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lbl.setForeground(new Color(0, 102, 204));
        top.add(lbl, BorderLayout.WEST);
        add(top, BorderLayout.NORTH);

        // Center: Bảng CSDL cục bộ
        tableModel = new DefaultTableModel(new String[]{"Mã SV", "Họ Tên", "Email", "Ngành", "GPA", "Server ghi nhận"}, 0);
        JTable table = new JTable(tableModel);
        table.setRowHeight(22);
        JPanel pCenter = new JPanel(new BorderLayout());
        pCenter.setBorder(BorderFactory.createTitledBorder("📊 Cơ sở Dữ liệu của " + serverId));
        pCenter.add(new JScrollPane(table), BorderLayout.CENTER);

        // Bottom: Log
        txtLog = new JTextArea(8, 60);
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLog.setBackground(new Color(25, 25, 25));
        txtLog.setForeground(new Color(220, 220, 220));
        JPanel pLog = new JPanel(new BorderLayout());
        pLog.setBorder(BorderFactory.createTitledBorder("📜 Nhật ký RMI Replication"));
        pLog.add(new JScrollPane(txtLog), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pCenter, pLog);
        split.setDividerLocation(260);
        add(split, BorderLayout.CENTER);
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
            log("[KHỞI ĐỘNG] " + serverId + " đã sẵn sàng tại cổng " + port);
        } catch (Exception e) {
            log("[LỖI] " + e.getMessage());
        }
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
        refreshTable();
        log("[CLIENT CALL] Thêm mới: " + r.id + " - " + r.name);
        syncToPeers("INSERT", r);
        return true;
    }

    @Override
    public synchronized boolean update(StudentRecord r) {
        if (!db.containsKey(r.id)) return false;
        r.originServer = serverId;
        db.put(r.id, r);
        refreshTable();
        log("[CLIENT CALL] Cập nhật: " + r.id);
        syncToPeers("UPDATE", r);
        return true;
    }

    @Override
    public synchronized boolean delete(String id) {
        if (db.remove(id) != null) {
            refreshTable();
            log("[CLIENT CALL] Xóa: " + id);
            syncToPeers("DELETE", new StudentRecord(id, "", "", "", 0, serverId));
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean replicate(String action, StudentRecord r, String sender) {
        log("[RMI SYNC] << Nhận " + action + " từ " + sender + " (ID: " + r.id + ")");
        if ("DELETE".equals(action)) db.remove(r.id);
        else db.put(r.id, r);
        refreshTable();
        return true;
    }

    private void syncToPeers(String action, StudentRecord r) {
        Executors.newCachedThreadPool().submit(() -> {
            for (int p : peerPorts) {
                try {
                    IDatabaseSync peer = (IDatabaseSync) Naming.lookup("rmi://127.0.0.1:" + p + "/SyncService");
                    peer.replicate(action, r, serverId);
                    log("[RMI SYNC] >> Đã đồng bộ " + action + " sang cổng " + p);
                } catch (Exception ignored) {}
            }
        });
    }

    private void refreshTable() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            for (StudentRecord r : db.values()) {
                tableModel.addRow(new Object[]{r.id, r.name, r.email, r.major, r.gpa, r.originServer});
            }
        });
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            txtLog.append("[" + time + "] " + msg + "\n");
        });
    }

    public static void main(String[] args) {
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
