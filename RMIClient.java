import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.rmi.Naming;
import java.util.List;

public class RMIClient extends JFrame {
    private IDatabaseSync remote;
    private JComboBox<String> cbServer;
    private JButton btnConnect;
    private JLabel lblStatusBadge;

    private JTextField txtId, txtName, txtEmail, txtMajor, txtGpa;
    private DefaultTableModel model;
    private JTable table;

    public RMIClient() {
        initModernUI();
        connect();
    }

    private void initModernUI() {
        setTitle("VKU RMI Client - Quản Trị Dữ Liệu Sinh Viên & Đồng Bộ Cụm Server");
        setSize(1020, 680);
        setMinimumSize(new Dimension(880, 560));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(248, 250, 252));
        setLayout(new BorderLayout(12, 12));

        // 1. Top Header Card
        JPanel topCard = new JPanel(new BorderLayout(15, 0));
        topCard.setBackground(Color.WHITE);
        topCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1),
                new EmptyBorder(12, 18, 12, 18)
        ));

        JPanel leftControl = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftControl.setOpaque(false);

        JLabel lblServer = new JLabel("Chọn Máy Chủ:");
        lblServer.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblServer.setForeground(new Color(51, 65, 85));
        leftControl.add(lblServer);

        cbServer = new JComboBox<>(new String[]{
                "Server 1 (Cổng 1099)",
                "Server 2 (Cổng 1098)",
                "Server 3 (Cổng 1097)"
        });
        cbServer.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cbServer.setPreferredSize(new Dimension(190, 32));
        cbServer.addActionListener(e -> connect());
        leftControl.add(cbServer);

        btnConnect = new JButton("Kết nối RMI");
        btnConnect.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnConnect.setBackground(new Color(37, 99, 235)); // Blue 600
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFocusPainted(false);
        btnConnect.setPreferredSize(new Dimension(115, 32));
        btnConnect.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnConnect.addActionListener(e -> connect());
        leftControl.add(btnConnect);

        lblStatusBadge = new JLabel("  ● Chưa kết nối  ");
        lblStatusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatusBadge.setOpaque(true);
        lblStatusBadge.setBackground(new Color(254, 226, 226)); // Red 100
        lblStatusBadge.setForeground(new Color(185, 28, 28)); // Red 700
        lblStatusBadge.setBorder(new LineBorder(new Color(252, 165, 165), 1, true));
        lblStatusBadge.setPreferredSize(new Dimension(180, 30));
        lblStatusBadge.setHorizontalAlignment(SwingConstants.CENTER);
        leftControl.add(lblStatusBadge);

        topCard.add(leftControl, BorderLayout.WEST);

        JButton btnVerify = new JButton("🔍 Đối chiếu dữ liệu 3 Server");
        btnVerify.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnVerify.setBackground(new Color(124, 58, 237)); // Purple 600
        btnVerify.setForeground(Color.WHITE);
        btnVerify.setFocusPainted(false);
        btnVerify.setPreferredSize(new Dimension(210, 32));
        btnVerify.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnVerify.addActionListener(e -> verifySync());
        topCard.add(btnVerify, BorderLayout.EAST);

        add(topCard, BorderLayout.NORTH);

        // 2. Main Content (Left: Input Form Card, Right: Table Card)
        JPanel contentPanel = new JPanel(new BorderLayout(12, 0));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(0, 15, 15, 15));

        // --- Left Form Card ---
        JPanel formCard = new JPanel(new BorderLayout(0, 12));
        formCard.setBackground(Color.WHITE);
        formCard.setPreferredSize(new Dimension(330, 0));
        formCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1),
                new EmptyBorder(16, 18, 16, 18)
        ));

        JLabel lblFormTitle = new JLabel("THÔNG TIN SINH VIÊN");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblFormTitle.setForeground(new Color(30, 41, 59));
        formCard.add(lblFormTitle, BorderLayout.NORTH);

        // Grid inputs (proper height, no ugly vertical stretching)
        JPanel inputGrid = new JPanel(new GridBagLayout());
        inputGrid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 0, 6, 0);
        gbc.weightx = 1.0;

        int row = 0;
        txtId = createStyledTextField();
        txtName = createStyledTextField();
        txtEmail = createStyledTextField();
        txtMajor = createStyledTextField();
        txtMajor.setText("Công nghệ thông tin");
        txtGpa = createStyledTextField();
        txtGpa.setText("3.50");

        addFormField(inputGrid, gbc, row++, "Mã Sinh Viên:", txtId);
        addFormField(inputGrid, gbc, row++, "Họ và Tên:", txtName);
        addFormField(inputGrid, gbc, row++, "Email:", txtEmail);
        addFormField(inputGrid, gbc, row++, "Chuyên Ngành:", txtMajor);
        addFormField(inputGrid, gbc, row++, "Điểm GPA (Hệ 4):", txtGpa);

        formCard.add(inputGrid, BorderLayout.CENTER);

        // Form action buttons
        JPanel actionBox = new JPanel(new GridLayout(2, 2, 8, 8));
        actionBox.setOpaque(false);

        JButton btnAdd = createButton("➕ Thêm Mới", new Color(22, 163, 74)); // Green 600
        btnAdd.addActionListener(e -> addStudent());

        JButton btnUpdate = createButton("✏️ Cập Nhật", new Color(217, 119, 6)); // Amber 600
        btnUpdate.addActionListener(e -> updateStudent());

        JButton btnDelete = createButton("❌ Xóa Bản Ghi", new Color(220, 38, 38)); // Red 600
        btnDelete.addActionListener(e -> deleteStudent());

        JButton btnClear = createButton("🧹 Làm Trắng", new Color(100, 116, 139)); // Slate 500
        btnClear.addActionListener(e -> clearForm());

        actionBox.add(btnAdd);
        actionBox.add(btnUpdate);
        actionBox.add(btnDelete);
        actionBox.add(btnClear);
        formCard.add(actionBox, BorderLayout.SOUTH);

        contentPanel.add(formCard, BorderLayout.WEST);

        // --- Right Table Card ---
        JPanel tableCard = new JPanel(new BorderLayout(0, 10));
        tableCard.setBackground(Color.WHITE);
        tableCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1),
                new EmptyBorder(14, 16, 14, 16)
        ));

        JPanel tableHeaderBox = new JPanel(new BorderLayout());
        tableHeaderBox.setOpaque(false);
        JLabel lblTableTitle = new JLabel("DỮ LIỆU TỪ SERVER ĐANG KẾT NỐI");
        lblTableTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTableTitle.setForeground(new Color(30, 41, 59));

        JButton btnRefresh = new JButton("🔄 Làm mới bảng");
        btnRefresh.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnRefresh.setFocusPainted(false);
        btnRefresh.addActionListener(e -> refresh());
        tableHeaderBox.add(lblTableTitle, BorderLayout.WEST);
        tableHeaderBox.add(btnRefresh, BorderLayout.EAST);
        tableCard.add(tableHeaderBox, BorderLayout.NORTH);

        model = new DefaultTableModel(new String[]{"Mã SV", "Họ và Tên", "Email", "Chuyên Ngành", "GPA", "Server ghi nhận"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
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

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int r = table.getSelectedRow();
                if (r != -1) {
                    txtId.setText((String) model.getValueAt(r, 0));
                    txtName.setText((String) model.getValueAt(r, 1));
                    txtEmail.setText((String) model.getValueAt(r, 2));
                    txtMajor.setText((String) model.getValueAt(r, 3));
                    txtGpa.setText(String.valueOf(model.getValueAt(r, 4)));
                }
            }
        });

        tableCard.add(new JScrollPane(table), BorderLayout.CENTER);
        contentPanel.add(tableCard, BorderLayout.CENTER);

        add(contentPanel, BorderLayout.CENTER);
    }

    private void addFormField(JPanel p, GridBagConstraints gbc, int row, String label, JTextField tf) {
        gbc.gridy = row * 2;
        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(new Color(71, 85, 105));
        p.add(l, gbc);

        gbc.gridy = row * 2 + 1;
        p.add(tf, gbc);
    }

    private JTextField createStyledTextField() {
        JTextField tf = new JTextField();
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tf.setPreferredSize(new Dimension(0, 32));
        tf.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));
        return tf;
    }

    private JButton createButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(0, 32));
        return btn;
    }

    private void connect() {
        int idx = cbServer.getSelectedIndex();
        int port = (idx == 0) ? 1099 : ((idx == 1) ? 1098 : 1097);
        btnConnect.setEnabled(false);
        lblStatusBadge.setText("  ● Đang kết nối...  ");
        lblStatusBadge.setBackground(new Color(254, 243, 199)); // Amber 100
        lblStatusBadge.setForeground(new Color(180, 83, 9));

        new Thread(() -> {
            try {
                remote = (IDatabaseSync) Naming.lookup("rmi://127.0.0.1:" + port + "/SyncService");
                String sId = remote.getServerId();
                SwingUtilities.invokeLater(() -> {
                    lblStatusBadge.setText("  ● ĐÃ KẾT NỐI: " + sId + "  ");
                    lblStatusBadge.setBackground(new Color(220, 252, 231)); // Green 100
                    lblStatusBadge.setForeground(new Color(22, 101, 52));
                    lblStatusBadge.setBorder(new LineBorder(new Color(134, 239, 172), 1, true));
                    btnConnect.setEnabled(true);
                });
                refresh();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblStatusBadge.setText("  ● LỖI KẾT NỐI PORT " + port + "  ");
                    lblStatusBadge.setBackground(new Color(254, 226, 226)); // Red 100
                    lblStatusBadge.setForeground(new Color(185, 28, 28));
                    lblStatusBadge.setBorder(new LineBorder(new Color(252, 165, 165), 1, true));
                    btnConnect.setEnabled(true);
                });
            }
        }).start();
    }

    private void refresh() {
        if (remote == null) return;
        new Thread(() -> {
            try {
                List<StudentRecord> list = remote.getAll();
                SwingUtilities.invokeLater(() -> {
                    model.setRowCount(0);
                    for (StudentRecord r : list) {
                        model.addRow(new Object[]{r.id, r.name, r.email, r.major, String.format("%.2f", r.gpa), r.originServer});
                    }
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private void addStudent() {
        if (remote == null) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối Server RMI!");
            return;
        }
        String id = txtId.getText().trim();
        String name = txtName.getText().trim();
        String email = txtEmail.getText().trim();
        String major = txtMajor.getText().trim();
        if (id.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập Mã SV và Họ Tên!");
            return;
        }

        double gpa;
        try {
            gpa = Double.parseDouble(txtGpa.getText().trim());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Điểm GPA phải là số (VD: 3.50)!");
            return;
        }

        StudentRecord r = new StudentRecord(id, name, email, major, gpa, "CLIENT");
        new Thread(() -> {
            try {
                if (remote.insert(r)) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "Thêm sinh viên thành công! Đang tự động Replication sang các Server khác.");
                        clearForm();
                    });
                    refresh();
                } else {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Mã SV '" + id + "' đã tồn tại!"));
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Lỗi RMI: " + ex.getMessage()));
            }
        }).start();
    }

    private void updateStudent() {
        if (remote == null) return;
        String id = txtId.getText().trim();
        if (id.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn hoặc nhập Mã SV cần sửa!");
            return;
        }
        try {
            double gpa = Double.parseDouble(txtGpa.getText().trim());
            StudentRecord r = new StudentRecord(id, txtName.getText().trim(), txtEmail.getText().trim(), txtMajor.getText().trim(), gpa, "CLIENT");
            new Thread(() -> {
                try {
                    if (remote.update(r)) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Cập nhật thành công!"));
                        refresh();
                    } else {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Không tìm thấy Mã SV!"));
                    }
                } catch (Exception ignored) {}
            }).start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "GPA không hợp lệ!");
        }
    }

    private void deleteStudent() {
        if (remote == null) return;
        String id = txtId.getText().trim();
        if (id.isEmpty()) return;

        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa sinh viên " + id + " trên toàn hệ thống?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            new Thread(() -> {
                try {
                    if (remote.delete(id)) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this, "Đã xóa sinh viên!");
                            clearForm();
                        });
                        refresh();
                    }
                } catch (Exception ignored) {}
            }).start();
        }
    }

    private void clearForm() {
        txtId.setText("");
        txtName.setText("");
        txtEmail.setText("");
        txtMajor.setText("Công nghệ thông tin");
        txtGpa.setText("3.50");
    }

    private void verifySync() {
        new Thread(() -> {
            int[] ports = {1099, 1098, 1097};
            String[] names = {"Server 1", "Server 2", "Server 3"};
            StringBuilder sb = new StringBuilder("=== KẾT QUẢ ĐỐI CHIẾU DỮ LIỆU CỤM 3 SERVER RMI ===\n\n");
            int matchCount = 0;
            java.util.Map<String, java.util.Set<String>> serverRecords = new java.util.LinkedHashMap<>();

            for (int i = 0; i < 3; i++) {
                try {
                    IDatabaseSync s = (IDatabaseSync) Naming.lookup("rmi://127.0.0.1:" + ports[i] + "/SyncService");
                    List<StudentRecord> list = s.getAll();
                    java.util.Set<String> ids = new java.util.TreeSet<>();
                    for (StudentRecord r : list) ids.add(r.id);
                    serverRecords.put(names[i], ids);
                    sb.append(String.format("✔ %-10s (Cổng %d) : ONLINE  - %d bản ghi %s\n", names[i], ports[i], list.size(), ids));
                    matchCount++;
                } catch (Exception e) {
                    sb.append(String.format("✖ %-10s (Cổng %d) : OFFLINE\n", names[i], ports[i]));
                }
            }

            if (matchCount >= 2) {
                java.util.Set<String> base = null;
                boolean matched = true;
                for (java.util.Set<String> ids : serverRecords.values()) {
                    if (base == null) base = ids;
                    else if (!base.equals(ids)) { matched = false; break; }
                }
                if (matched) {
                    sb.append("\n==> KẾT LUẬN: TẤT CẢ MÁY CHỦ ONLINE ĐỒNG BỘ 100% CẢ VỀ SỐ LƯỢNG VÀ MÃ BẢN GHI!");
                } else {
                    sb.append("\n==> CẢNH BÁO: Dữ liệu giữa các máy chủ chưa khớp!");
                }
            } else if (matchCount == 1) {
                sb.append("\n==> CHÚ Ý: Chỉ có 1 máy chủ đang bật, hãy khởi động thêm Server 2/Server 3 để thấy đồng bộ cụm!");
            } else {
                sb.append("\n==> CẢNH BÁO: Chưa có máy chủ RMI nào đang hoạt động!");
            }

            final String res = sb.toString();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, res, "Báo cáo Đối chiếu Dữ liệu", JOptionPane.INFORMATION_MESSAGE));
        }).start();
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new RMIClient().setVisible(true));
    }
}
