package dao;

import model.StudentRecord;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/**
 * Data Access Object (DAO) hỗ trợ kết nối Cơ sở dữ liệu thật trên XAMPP MySQL (vku_network_db)
 * và tự động fallback sang file CSDL vật lý (database_server_X.csv) nếu MySQL chưa bật.
 */
public class StudentDAO {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/vku_network_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";

    private final File dbFile;
    private final String serverId;
    private boolean isMySqlConnected = false;

    public StudentDAO(String serverId) {
        this.serverId = serverId;
        this.dbFile = new File("database_" + serverId.toLowerCase() + ".csv");
        checkMySqlConnection();
    }

    private void checkMySqlConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                if (conn != null && !conn.isClosed()) {
                    isMySqlConnected = true;
                    initMySqlTable(conn);
                }
            }
        } catch (Throwable e) {
            isMySqlConnected = false;
        }
    }

    private void initMySqlTable(Connection conn) {
        String sql = "CREATE TABLE IF NOT EXISTS student_records ("
                + "id VARCHAR(50) PRIMARY KEY, "
                + "full_name VARCHAR(100), "
                + "email VARCHAR(100), "
                + "major VARCHAR(100), "
                + "gpa DOUBLE, "
                + "origin_server VARCHAR(50), "
                + "timestamp BIGINT"
                + ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException ignored) {}
    }

    public boolean isMySqlActive() {
        return isMySqlConnected;
    }

    public File getFile() {
        return dbFile;
    }

    /**
     * Nạp toàn bộ dữ liệu: Ưu tiên nạp từ XAMPP MySQL, nếu lỗi nạp từ file CSV.
     */
    public synchronized Map<String, StudentRecord> loadAll() {
        Map<String, StudentRecord> map = new LinkedHashMap<>();

        if (isMySqlConnected) {
            String query = "SELECT id, full_name, email, major, gpa, origin_server, timestamp FROM student_records";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                while (rs.next()) {
                    StudentRecord r = new StudentRecord(
                            rs.getString("id"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("major"),
                            rs.getDouble("gpa"),
                            rs.getString("origin_server")
                    );
                    r.setTimestamp(rs.getLong("timestamp"));
                    map.put(r.getId(), r);
                }
                if (!map.isEmpty()) {
                    // Đồng bộ lưu một bản vào file CSV
                    saveToFileOnly(map.values());
                    return map;
                }
            } catch (SQLException e) {
                isMySqlConnected = false;
            }
        }

        // Fallback đọc file CSV
        return loadFromFile();
    }

    /**
     * Lưu toàn bộ bản ghi vào cả MySQL XAMPP và file CSV.
     */
    public synchronized boolean saveAll(Collection<StudentRecord> records) {
        boolean fileSuccess = saveToFileOnly(records);

        if (isMySqlConnected) {
            String upsert = "INSERT INTO student_records (id, full_name, email, major, gpa, origin_server, timestamp) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE full_name=VALUES(full_name), email=VALUES(email), "
                    + "major=VALUES(major), gpa=VALUES(gpa), origin_server=VALUES(origin_server), timestamp=VALUES(timestamp)";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement ps = conn.prepareStatement(upsert)) {

                for (StudentRecord r : records) {
                    ps.setString(1, r.getId());
                    ps.setString(2, r.getName());
                    ps.setString(3, r.getEmail());
                    ps.setString(4, r.getMajor());
                    ps.setDouble(5, r.getGpa());
                    ps.setString(6, r.getOriginServer());
                    ps.setLong(7, r.getTimestamp());
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                isMySqlConnected = false;
            }
        }

        return fileSuccess;
    }

    public synchronized void deleteRecord(String id) {
        if (isMySqlConnected) {
            String sql = "DELETE FROM student_records WHERE id = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        }
    }

    private synchronized Map<String, StudentRecord> loadFromFile() {
        Map<String, StudentRecord> map = new LinkedHashMap<>();
        if (!dbFile.exists()) {
            initDefaultData(map);
            saveToFileOnly(map.values());
            return map;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(dbFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                StudentRecord record = StudentRecord.fromCsvRow(line);
                if (record != null) {
                    map.put(record.getId(), record);
                }
            }
        } catch (Exception e) {
            initDefaultData(map);
        }
        return map;
    }

    private synchronized boolean saveToFileOnly(Collection<StudentRecord> records) {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(dbFile), StandardCharsets.UTF_8))) {
            pw.println("# MaSV,HoTen,Email,Nganh,GPA,ServerGhiNhan");
            for (StudentRecord r : records) {
                pw.println(r.toCsvRow());
            }
            pw.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void initDefaultData(Map<String, StudentRecord> map) {
        if ("SERVER_1".equalsIgnoreCase(serverId)) {
            StudentRecord s1 = new StudentRecord("24IT001", "Trần Quang Bình", "binhtq.24it@vku.udn.vn", "CNTT", 3.85, serverId);
            StudentRecord s2 = new StudentRecord("24IT002", "Lê Nhuận Phát", "phatln.24it@vku.udn.vn", "KTPM", 3.75, serverId);
            map.put(s1.getId(), s1);
            map.put(s2.getId(), s2);
        }
    }
}
