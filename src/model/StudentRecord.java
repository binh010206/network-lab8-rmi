package model;

import java.io.Serializable;

/**
 * Lớp thực thể đại diện cho bản ghi sinh viên trong hệ thống phân tán.
 */
public class StudentRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String email;
    private String major;
    private double gpa;
    private String originServer;
    private long timestamp;

    public StudentRecord() {
        this.timestamp = System.currentTimeMillis();
    }

    public StudentRecord(String id, String name, String email, String major, double gpa, String originServer) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.major = major;
        this.gpa = gpa;
        this.originServer = originServer;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }

    public double getGpa() { return gpa; }
    public void setGpa(double gpa) { this.gpa = gpa; }

    public String getOriginServer() { return originServer; }
    public void setOriginServer(String originServer) { this.originServer = originServer; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    /**
     * Xuất chuỗi định dạng CSV để lưu file vật lý.
     */
    public String toCsvRow() {
        return String.format("%s,%s,%s,%s,%.2f,%s", id, name, email, major, gpa, originServer);
    }

    /**
     * Đọc từ chuỗi dòng CSV.
     */
    public static StudentRecord fromCsvRow(String line) {
        if (line == null || line.trim().isEmpty() || line.startsWith("#")) return null;
        String[] parts = line.split(",", -1);
        if (parts.length >= 6) {
            double gpa = 0.0;
            try { gpa = Double.parseDouble(parts[4].trim()); } catch (Exception ignored) {}
            return new StudentRecord(parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim(), gpa, parts[5].trim());
        }
        return null;
    }

    @Override
    public String toString() {
        return "StudentRecord{" + "id='" + id + '\'' + ", name='" + name + '\'' + ", gpa=" + gpa + '}';
    }
}
