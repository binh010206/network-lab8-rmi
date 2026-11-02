import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.rmi.Naming;
import java.util.List;

public class RMIClient extends JFrame {
    private IDatabaseSync remote;
    private JComboBox<String> cbServer;
    private JTextField txtId, txtName, txtEmail, txtMajor, txtGpa;
    private DefaultTableModel model;
    private JLabel lblStatus;

    public RMIClient() {
        setTitle("RMI Client - Quản lý & Kiểm tra Đồng bộ CSDL");
        setSize(920, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // Top bar
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        top.setBackground(new Color(245, 245, 245));
        top.add(new JLabel("Chọn Server kết nối:"));
        cbServer = new JComboBox<>(new String[]{"Server 1 (Cổng 1099)", "Server 2 (Cổng 1098)", "Server 3 (Cổng 1097)"});
        top.add(cbServer);
        JButton btnConnect = new JButton("Kết nối RMI");
        btnConnect.setBackground(new Color(0, 102, 204));
        btnConnect.setForeground(Color.WHITE);
        btnConnect.addActionListener(e -> connect());
        top.add(btnConnect);
        lblStatus = new JLabel("● Chưa kết nối");
        lblStatus.setForeground(Color.RED);
        top.add(lblStatus);
        add(top, BorderLayout.NORTH);

        // Center split: Form bên trái, Bảng bên phải
        JPanel form = new JPanel(new GridLayout(6, 2, 6, 8));
        form.setBorder(BorderFactory.createTitledBorder("Nhập thông tin Sinh viên"));
        form.setPreferredSize(new Dimension(300, 300));
        txtId = new JTextField();
        txtName = new JTextField();
        txtEmail = new JTextField();
        txtMajor = new JTextField("CNTT");
        txtGpa = new JTextField("3.50");

        form.add(new JLabel("Mã SV:")); form.add(txtId);
        form.add(new JLabel("Họ Tên:")); form.add(txtName);
        form.add(new JLabel("Email:")); form.add(txtEmail);
        form.add(new JLabel("Ngành:")); form.add(txtMajor);
        form.add(new JLabel("GPA:")); form.add(txtGpa);

        JPanel btnPanel = new JPanel(new GridLayout(1, 3, 4, 4));
        JButton btnAdd = new JButton("Thêm");
        btnAdd.setBackground(new Color(40, 167, 69)); btnAdd.setForeground(Color.WHITE);
        btnAdd.addActionListener(e -> addStudent());

        JButton btnUpdate = new JButton("Sửa");
        btnUpdate.setBackground(new Color(255, 153, 0)); btnUpdate.setForeground(Color.WHITE);
        btnUpdate.addActionListener(e -> updateStudent());

        JButton btnDelete = new JButton("Xóa");
        btnDelete.setBackground(new Color(220, 53, 69)); btnDelete.setForeground(Color.WHITE);
        btnDelete.addActionListener(e -> deleteStudent());

        btnPanel.add(btnAdd); btnPanel.add(btnUpdate); btnPanel.add(btnDelete);
        form.add(new JLabel("Thao tác:")); form.add(btnPanel);

        // Table
        model = new DefaultTableModel(new String[]{"Mã SV", "Họ Tên", "Email", "Ngành", "GPA", "Server ghi nhận"}, 0);
        JTable table = new JTable(model);
        table.setRowHeight(22);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
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
        JPanel pTable = new JPanel(new BorderLayout());
        pTable.setBorder(BorderFactory.createTitledBorder("Dữ liệu từ Server đang kết nối"));
        pTable.add(new JScrollPane(table), BorderLayout.CENTER);

        // Bottom: Kiểm tra đồng bộ
        JPanel bot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        JButton btnVerify = new JButton("🔍 Đối chiếu dữ liệu cả 3 Server");
        btnVerify.setBackground(new Color(111, 66, 193));
        btnVerify.setForeground(Color.WHITE);
        btnVerify.addActionListener(e -> verifySync());
        bot.add(btnVerify);
        pTable.add(bot, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, form, pTable);
        split.setDividerLocation(310);
        add(split, BorderLayout.CENTER);

        connect(); // Tự động kết nối mặc định
    }

    private void connect() {
        int idx = cbServer.getSelectedIndex();
        int port = (idx == 0) ? 1099 : ((idx == 1) ? 1098 : 1097);
        new Thread(() -> {
            try {
                remote = (IDatabaseSync) Naming.lookup("rmi://127.0.0.1:" + port + "/SyncService");
                String sId = remote.getServerId();
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("● Đã kết nối: " + sId + " (Port " + port + ")");
                    lblStatus.setForeground(new Color(40, 167, 69));
                });
                refresh();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("● Lỗi kết nối Port " + port);
                    lblStatus.setForeground(Color.RED);
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
                        model.addRow(new Object[]{r.id, r.name, r.email, r.major, r.gpa, r.originServer});
                    }
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private void addStudent() {
        if (remote == null) return;
        try {
            double gpa = Double.parseDouble(txtGpa.getText().trim());
            StudentRecord r = new StudentRecord(txtId.getText().trim(), txtName.getText().trim(),
                    txtEmail.getText().trim(), txtMajor.getText().trim(), gpa, "CLIENT");
            new Thread(() -> {
                try {
                    if (remote.insert(r)) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Đã thêm thành công! Đang tự động đồng bộ sang các Server khác."));
                        refresh();
                    } else {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Mã SV đã tồn tại!"));
                    }
                } catch (Exception ignored) {}
            }).start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "GPA không hợp lệ!");
        }
    }

    private void updateStudent() {
        if (remote == null) return;
        try {
            double gpa = Double.parseDouble(txtGpa.getText().trim());
            StudentRecord r = new StudentRecord(txtId.getText().trim(), txtName.getText().trim(),
                    txtEmail.getText().trim(), txtMajor.getText().trim(), gpa, "CLIENT");
            new Thread(() -> {
                try {
                    if (remote.update(r)) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Cập nhật thành công!"));
                        refresh();
                    }
                } catch (Exception ignored) {}
            }).start();
        } catch (Exception ignored) {}
    }

    private void deleteStudent() {
        if (remote == null) return;
        String id = txtId.getText().trim();
        new Thread(() -> {
            try {
                if (remote.delete(id)) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Đã xóa!"));
                    refresh();
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void verifySync() {
        new Thread(() -> {
            int[] ports = {1099, 1098, 1097};
            String[] names = {"Server 1", "Server 2", "Server 3"};
            StringBuilder sb = new StringBuilder("KẾT QUẢ ĐỐI CHIẾU DỮ LIỆU CẢ 3 SERVER RMI:\n\n");
            int matchCount = 0;
            int prevSize = -1;
            boolean allMatch = true;

            for (int i = 0; i < 3; i++) {
                try {
                    IDatabaseSync s = (IDatabaseSync) Naming.lookup("rmi://127.0.0.1:" + ports[i] + "/SyncService");
                    int size = s.getAll().size();
                    sb.append("✔ ").append(names[i]).append(" (Cổng ").append(ports[i]).append("): ONLINE - ").append(size).append(" bản ghi\n");
                    if (prevSize == -1) prevSize = size;
                    else if (prevSize != size) allMatch = false;
                    matchCount++;
                } catch (Exception e) {
                    sb.append("✖ ").append(names[i]).append(" (Cổng ").append(ports[i]).append("): OFFLINE\n");
                }
            }
            if (matchCount >= 2 && allMatch) {
                sb.append("\n==> KẾT LUẬN: CÁC MÁY CHỦ ĐỒNG BỘ 100% HOÀN TOÀN!");
            }
            final String res = sb.toString();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, res, "Đối chiếu Cụm Server", JOptionPane.INFORMATION_MESSAGE));
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RMIClient().setVisible(true));
    }
}
