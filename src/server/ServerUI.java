package server;

import model.StudentRecord;
import dao.StudentDAO;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;

/**
 * Giao diện người dùng cho Máy chủ phân tán RMI Server.
 */
public class ServerUI extends JFrame {
    private final String serverId;
    private final int port;
    private final int[] peerPorts;
    private final StudentDAO dao;
    private final Runnable onPullRequested;

    private DefaultTableModel tableModel;
    private JTextArea txtLog;

    public ServerUI(String serverId, int port, int[] peerPorts, StudentDAO dao, Runnable onPullRequested) {
        this.serverId = serverId;
        this.port = port;
        this.peerPorts = peerPorts;
        this.dao = dao;
        this.onPullRequested = onPullRequested;

        initUI();
    }

    private void initUI() {
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

        JLabel lblSub = new JLabel("Địa chỉ: rmi://127.0.0.1:" + port + "/SyncService  |  Cụm Peer: " + Arrays.toString(peerPorts) + "  |  CSDL: " + dao.getFile().getName());
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
                if (dao.getFile().exists()) {
                    Desktop.getDesktop().open(dao.getFile());
                } else {
                    JOptionPane.showMessageDialog(this, "File CSDL chưa được khởi tạo!");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Đường dẫn file: " + dao.getFile().getAbsolutePath());
            }
        });
        rightHeader.add(btnOpenFile);

        JButton btnPull = new JButton("🔄 Kéo dữ liệu từ Peer");
        btnPull.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnPull.setFocusPainted(false);
        btnPull.setBackground(new Color(241, 245, 249));
        btnPull.addActionListener(e -> {
            if (onPullRequested != null) onPullRequested.run();
        });
        rightHeader.add(btnPull);

        JLabel lblStatusBadge = new JLabel("  ● ONLINE (Port " + port + ")  ");
        lblStatusBadge.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblStatusBadge.setOpaque(true);
        lblStatusBadge.setBackground(new Color(220, 252, 231));
        lblStatusBadge.setForeground(new Color(22, 101, 52));
        lblStatusBadge.setBorder(new LineBorder(new Color(134, 239, 172), 1, true));
        rightHeader.add(lblStatusBadge);

        headerCard.add(titleBox, BorderLayout.WEST);
        headerCard.add(rightHeader, BorderLayout.EAST);
        add(headerCard, BorderLayout.NORTH);

        // 2. Body Panel (Table + Log)
        JPanel bodyPanel = new JPanel(new BorderLayout(10, 10));
        bodyPanel.setOpaque(false);
        bodyPanel.setBorder(new EmptyBorder(0, 15, 12, 15));

        // Table Card
        JPanel tableCard = new JPanel(new BorderLayout(0, 8));
        tableCard.setBackground(Color.WHITE);
        tableCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JLabel lblTableTitle = new JLabel("CƠ SỞ DỮ LIỆU CỤC BỘ (" + serverId + ") - FILE: " + dao.getFile().getName());
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

        // Log Card
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
        txtLog.setBackground(new Color(15, 23, 42));
        txtLog.setForeground(new Color(226, 232, 240));
        txtLog.setMargin(new Insets(8, 10, 8, 10));
        logCard.add(new JScrollPane(txtLog), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableCard, logCard);
        split.setDividerLocation(310);
        split.setResizeWeight(0.6);
        split.setBorder(null);

        bodyPanel.add(split, BorderLayout.CENTER);
        add(bodyPanel, BorderLayout.CENTER);
    }

    public void updateTable(Collection<StudentRecord> records) {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            for (StudentRecord r : records) {
                tableModel.addRow(new Object[]{
                        r.getId(),
                        r.getName(),
                        r.getEmail(),
                        r.getMajor(),
                        String.format("%.2f", r.getGpa()),
                        r.getOriginServer()
                });
            }
        });
    }

    public void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            txtLog.append("[" + time + "] " + msg + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }
}
