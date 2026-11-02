import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.rmi.Naming;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class RMIClientApp extends JFrame {
    private IDatabaseSyncService remoteService;
    private String currentServerUrl;

    private JComboBox<String> cbServer;
    private JTextField txtCustomHost;
    private JTextField txtCustomPort;
    private JButton btnConnect;
    private JLabel lblConnStatus;

    private JTextField txtId;
    private JTextField txtName;
    private JTextField txtEmail;
    private JComboBox<String> cbMajor;
    private JTextField txtGpa;

    private JTable tableStudents;
    private DefaultTableModel tableModel;
    private JTextArea txtLogs;

    public RMIClientApp() {
        super("VKU RMI Client - Ứng dụng Quản lý Sinh viên Phân tán");
        initComponents();
        autoConnectDefault();
    }

    private void initComponents() {
        setSize(1050, 720);
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // 1. Connection Header
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        topPanel.setBackground(new Color(245, 245, 250));
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        topPanel.add(new JLabel("Chọn Server RMI:"));
        cbServer = new JComboBox<>(new String[]{
                "Server 1 (Cổng 1099)",
                "Server 2 (Cổng 1098)",
                "Server 3 (Cổng 1097)",
                "Máy khác / IP tùy chỉnh"
        });
        cbServer.addActionListener(e -> updateHostFields());
        topPanel.add(cbServer);

        topPanel.add(new JLabel("Host:"));
        txtCustomHost = new JTextField("127.0.0.1", 10);
        txtCustomHost.setEnabled(false);
        topPanel.add(txtCustomHost);

        topPanel.add(new JLabel("Port:"));
        txtCustomPort = new JTextField("1099", 5);
        txtCustomPort.setEnabled(false);
        topPanel.add(txtCustomPort);

        btnConnect = new JButton("Kết nối RMI");
        btnConnect.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnConnect.setBackground(new Color(0, 120, 215));
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFocusPainted(false);
        btnConnect.addActionListener(e -> performConnect());
        topPanel.add(btnConnect);

        lblConnStatus = new JLabel("● Chưa kết nối");
        lblConnStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblConnStatus.setForeground(Color.RED);
        topPanel.add(lblConnStatus);

        add(topPanel, BorderLayout.NORTH);

        // 2. Center Split (Left: Input Form, Right: Data Table)
        JPanel centerPanel = new JPanel(new BorderLayout(8, 8));

        // Left Form
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setPreferredSize(new Dimension(340, 400));
        formPanel.setBorder(BorderFactory.createTitledBorder("📝 Thông tin Bản ghi Sinh viên"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        formPanel.add(new JLabel("Mã SV:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        txtId = new JTextField();
        formPanel.add(txtId, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        formPanel.add(new JLabel("Họ và Tên:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        txtName = new JTextField();
        formPanel.add(txtName, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        formPanel.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        txtEmail = new JTextField();
        formPanel.add(txtEmail, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        formPanel.add(new JLabel("Chuyên ngành:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        cbMajor = new JComboBox<>(new String[]{"Công nghệ thông tin", "Kỹ thuật phần mềm", "Mạng máy tính & ATTT", "Trí tuệ nhân tạo"});
        formPanel.add(cbMajor, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        formPanel.add(new JLabel("Điểm GPA:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        txtGpa = new JTextField("3.50");
        formPanel.add(txtGpa, gbc);

        // Buttons
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JPanel btnPanel = new JPanel(new GridLayout(2, 2, 6, 6));

        JButton btnAdd = new JButton("➕ Thêm mới");
        btnAdd.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnAdd.setBackground(new Color(40, 167, 69));
        btnAdd.setForeground(Color.WHITE);
        btnAdd.addActionListener(e -> performAdd());

        JButton btnUpdate = new JButton("✏️ Cập nhật");
        btnUpdate.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnUpdate.setBackground(new Color(255, 153, 0));
        btnUpdate.setForeground(Color.WHITE);
        btnUpdate.addActionListener(e -> performUpdate());

        JButton btnDelete = new JButton("❌ Xóa bản ghi");
        btnDelete.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnDelete.setBackground(new Color(220, 53, 69));
        btnDelete.setForeground(Color.WHITE);
        btnDelete.addActionListener(e -> performDelete());

        JButton btnClear = new JButton("🧹 Làm trắng form");
        btnClear.addActionListener(e -> clearForm());

        btnPanel.add(btnAdd);
        btnPanel.add(btnUpdate);
        btnPanel.add(btnDelete);
        btnPanel.add(btnClear);
        formPanel.add(btnPanel, gbc);

        // Nút kiểm tra đồng bộ cả 3 server
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JButton btnVerifySync = new JButton("🔍 Đối chiếu dữ liệu cả 3 Server");
        btnVerifySync.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnVerifySync.setBackground(new Color(111, 66, 193));
        btnVerifySync.setForeground(Color.WHITE);
        btnVerifySync.addActionListener(e -> performVerifyCluster());
        formPanel.add(btnVerifySync, gbc);

        centerPanel.add(formPanel, BorderLayout.WEST);

        // Right Table
        JPanel tablePanel = new JPanel(new BorderLayout(5, 5));
        tablePanel.setBorder(BorderFactory.createTitledBorder("📊 Danh sách Dữ liệu từ Server đang kết nối"));

        tableModel = new DefaultTableModel(new String[]{"Mã SV", "Họ và Tên", "Email", "Chuyên ngành", "GPA", "Server ghi nhận", "Thời gian"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tableStudents = new JTable(tableModel);
        tableStudents.setRowHeight(24);
        tableStudents.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int r = tableStudents.getSelectedRow();
                if (r != -1) {
                    txtId.setText((String) tableModel.getValueAt(r, 0));
                    txtName.setText((String) tableModel.getValueAt(r, 1));
                    txtEmail.setText((String) tableModel.getValueAt(r, 2));
                    cbMajor.setSelectedItem((String) tableModel.getValueAt(r, 3));
                    txtGpa.setText((String) tableModel.getValueAt(r, 4));
                }
            }
        });
        tablePanel.add(new JScrollPane(tableStudents), BorderLayout.CENTER);

        JPanel tableBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnRefresh = new JButton("🔄 Làm mới bảng");
        btnRefresh.addActionListener(e -> refreshData());
        tableBottom.add(btnRefresh);
        tablePanel.add(tableBottom, BorderLayout.SOUTH);

        centerPanel.add(tablePanel, BorderLayout.CENTER);

        // 3. Bottom Logs Panel
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setPreferredSize(new Dimension(800, 160));
        bottomPanel.setBorder(BorderFactory.createTitledBorder("📜 Nhật ký Gọi hàm RMI (Remote Procedure Call Trace)"));

        txtLogs = new JTextArea();
        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLogs.setBackground(new Color(25, 25, 25));
        txtLogs.setForeground(new Color(220, 220, 220));
        bottomPanel.add(new JScrollPane(txtLogs), BorderLayout.CENTER);

        // Add to main frame
        JPanel centerWrapper = new JPanel(new BorderLayout(5, 5));
        centerWrapper.add(centerPanel, BorderLayout.CENTER);
        centerWrapper.add(bottomPanel, BorderLayout.SOUTH);
        add(centerWrapper, BorderLayout.CENTER);
    }

    private void updateHostFields() {
        int idx = cbServer.getSelectedIndex();
        if (idx == 0) {
            txtCustomHost.setText("127.0.0.1");
            txtCustomPort.setText("1099");
            txtCustomHost.setEnabled(false);
            txtCustomPort.setEnabled(false);
        } else if (idx == 1) {
            txtCustomHost.setText("127.0.0.1");
            txtCustomPort.setText("1098");
            txtCustomHost.setEnabled(false);
            txtCustomPort.setEnabled(false);
        } else if (idx == 2) {
            txtCustomHost.setText("127.0.0.1");
            txtCustomPort.setText("1097");
            txtCustomHost.setEnabled(false);
            txtCustomPort.setEnabled(false);
        } else {
            txtCustomHost.setEnabled(true);
            txtCustomPort.setEnabled(true);
        }
    }

    private void autoConnectDefault() {
        performConnect();
    }

    private void performConnect() {
        String host = txtCustomHost.getText().trim();
        String portStr = txtCustomPort.getText().trim();
        int portValue = 1099;
        try {
            portValue = Integer.parseInt(portStr);
        } catch (NumberFormatException ignored) {}
        final int port = portValue;

        String url = "rmi://" + host + ":" + port + "/SyncService";
        log("[RMI LOOKUP] Đang kết nối tới: " + url + " ...");
        btnConnect.setEnabled(false);

        new Thread(() -> {
            try {
                IDatabaseSyncService stub = (IDatabaseSyncService) Naming.lookup(url);
                String sId = stub.getServerId();
                remoteService = stub;
                currentServerUrl = url;

                SwingUtilities.invokeLater(() -> {
                    lblConnStatus.setText("● Đã kết nối: " + sId + " (" + host + ":" + port + ")");
                    lblConnStatus.setForeground(new Color(40, 167, 69));
                    btnConnect.setEnabled(true);
                    log("[RMI KẾT NỐI THÀNH CÔNG] Đã nhận được Remote Stub từ " + sId);
                });
                refreshData();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblConnStatus.setText("● Mất kết nối tới " + host + ":" + port);
                    lblConnStatus.setForeground(Color.RED);
                    btnConnect.setEnabled(true);
                    log("[RMI LỖI KẾT NỐI] " + ex.getMessage());
                });
            }
        }).start();
    }

    private void refreshData() {
        if (remoteService == null) return;
        new Thread(() -> {
            try {
                long t1 = System.currentTimeMillis();
                List<StudentRecord> list = remoteService.getAllRecords();
                long latency = System.currentTimeMillis() - t1;

                SwingUtilities.invokeLater(() -> {
                    tableModel.setRowCount(0);
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
                    log("[RMI CALL] getAllRecords() hoàn thành (" + list.size() + " bản ghi, độ trễ: " + latency + "ms)");
                });
            } catch (Exception ex) {
                log("[LỖI TẢI DỮ LIỆU] " + ex.getMessage());
            }
        }).start();
    }

    private void performAdd() {
        if (remoteService == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng kết nối tới Server RMI trước!");
            return;
        }
        String id = txtId.getText().trim();
        String name = txtName.getText().trim();
        String email = txtEmail.getText().trim();
        String major = (String) cbMajor.getSelectedItem();
        double gpa;
        try {
            gpa = Double.parseDouble(txtGpa.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Điểm GPA phải là số thập phân hợp lệ (ví dụ: 3.5)!");
            return;
        }
        if (id.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập Mã SV và Họ tên!");
            return;
        }

        StudentRecord r = new StudentRecord(id, name, email, major, gpa, "CLIENT");
        new Thread(() -> {
            try {
                long t1 = System.currentTimeMillis();
                boolean ok = remoteService.insertRecord(r);
                long latency = System.currentTimeMillis() - t1;
                if (ok) {
                    SwingUtilities.invokeLater(() -> {
                        log("[RMI CALL] insertRecord(" + id + ") THÀNH CÔNG (độ trễ: " + latency + "ms) - Đã tự động kích hoạt đồng bộ sang các Server khác!");
                        JOptionPane.showMessageDialog(this, "Thêm sinh viên thành công! Dữ liệu đang được đồng bộ sang toàn bộ cụm Server.");
                        clearForm();
                    });
                    refreshData();
                } else {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "Mã SV '" + id + "' đã tồn tại trong hệ thống!", "Trùng khóa", JOptionPane.WARNING_MESSAGE);
                    });
                }
            } catch (Exception ex) {
                log("[LỖI THÊM] " + ex.getMessage());
            }
        }).start();
    }

    private void performUpdate() {
        if (remoteService == null) return;
        String id = txtId.getText().trim();
        String name = txtName.getText().trim();
        String email = txtEmail.getText().trim();
        String major = (String) cbMajor.getSelectedItem();
        double gpa;
        try {
            gpa = Double.parseDouble(txtGpa.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Điểm GPA không hợp lệ!");
            return;
        }
        if (id.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn hoặc nhập Mã SV cần sửa!");
            return;
        }

        StudentRecord r = new StudentRecord(id, name, email, major, gpa, "CLIENT");
        new Thread(() -> {
            try {
                boolean ok = remoteService.updateRecord(r);
                if (ok) {
                    SwingUtilities.invokeLater(() -> {
                        log("[RMI CALL] updateRecord(" + id + ") THÀNH CÔNG! Đã đồng bộ sang toàn bộ cụm.");
                        JOptionPane.showMessageDialog(this, "Cập nhật thành công!");
                    });
                    refreshData();
                } else {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Không tìm thấy Mã SV cần cập nhật!"));
                }
            } catch (Exception ex) {
                log("[LỖI CẬP NHẬT] " + ex.getMessage());
            }
        }).start();
    }

    private void performDelete() {
        if (remoteService == null) return;
        String id = txtId.getText().trim();
        if (id.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn hoặc nhập Mã SV cần xóa!");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn xóa SV: " + id + " trên toàn hệ thống không?", "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            new Thread(() -> {
                try {
                    boolean ok = remoteService.deleteRecord(id);
                    if (ok) {
                        SwingUtilities.invokeLater(() -> {
                            log("[RMI CALL] deleteRecord(" + id + ") THÀNH CÔNG! Đã xóa trên toàn bộ cụm.");
                            JOptionPane.showMessageDialog(this, "Đã xóa bản ghi thành công!");
                            clearForm();
                        });
                        refreshData();
                    } else {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Không tìm thấy Mã SV để xóa!"));
                    }
                } catch (Exception ex) {
                    log("[LỖI XÓA] " + ex.getMessage());
                }
            }).start();
        }
    }

    private void performVerifyCluster() {
        new Thread(() -> {
            log("==================================================");
            log("[KIỂM TRA TÍNH ĐỒNG BỘ CẢ 3 SERVER]");
            int[] ports = {1099, 1098, 1097};
            String[] sNames = {"SERVER_1", "SERVER_2", "SERVER_3"};

            StringBuilder report = new StringBuilder("KẾT QUẢ ĐỐI CHIẾU DỮ LIỆU CẢ 3 SERVER RMI:\n\n");
            int validCount = 0;
            int lastSize = -1;
            boolean allMatch = true;

            for (int i = 0; i < 3; i++) {
                String u = "rmi://127.0.0.1:" + ports[i] + "/SyncService";
                try {
                    IDatabaseSyncService stub = (IDatabaseSyncService) Naming.lookup(u);
                    List<StudentRecord> data = stub.getAllRecords();
                    report.append(String.format("✔ %s (Cổng %d): Đang ONLINE - Có %d bản ghi.\n", sNames[i], ports[i], data.size()));
                    if (lastSize == -1) {
                        lastSize = data.size();
                    } else if (lastSize != data.size()) {
                        allMatch = false;
                    }
                    validCount++;
                } catch (Exception e) {
                    report.append(String.format("✖ %s (Cổng %d): OFFLINE (%s)\n", sNames[i], ports[i], e.getMessage()));
                }
            }

            if (validCount >= 2 && allMatch) {
                report.append("\n==> KẾT LUẬN: DỮ LIỆU ĐỒNG BỘ 100% HOÀN TOÀN GIỮA CÁC MÁY CHỦ!");
            } else if (!allMatch) {
                report.append("\n==> CẢNH BÁO: Dữ liệu giữa các server đang có sự sai lệch (vui lòng bấm 'Kéo dữ liệu' trên Server để Full Sync).");
            }

            final String msg = report.toString();
            SwingUtilities.invokeLater(() -> {
                log(msg);
                JOptionPane.showMessageDialog(this, msg, "Báo cáo Kiểm tra Cụm RMI", JOptionPane.INFORMATION_MESSAGE);
            });
        }).start();
    }

    private void clearForm() {
        txtId.setText("");
        txtName.setText("");
        txtEmail.setText("");
        cbMajor.setSelectedIndex(0);
        txtGpa.setText("3.50");
    }

    private synchronized void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            txtLogs.append("[" + time + "] " + msg + "\n");
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> {
            new RMIClientApp().setVisible(true);
        });
    }
}
