package dao;

import model.StudentRecord;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Data Access Object (DAO) chịu trách nhiệm lưu trữ và truy xuất CSDL cục bộ
 * ra file vật lý định dạng CSV cho từng Server.
 */
public class StudentDAO {
    private final File dbFile;
    private final String serverId;

    public StudentDAO(String serverId) {
        this.serverId = serverId;
        this.dbFile = new File("database_" + serverId.toLowerCase() + ".csv");
    }

    public File getFile() {
        return dbFile;
    }

    /**
     * Nạp toàn bộ bản ghi từ file CSDL vật lý.
     */
    public synchronized Map<String, StudentRecord> loadAll() {
        Map<String, StudentRecord> map = new LinkedHashMap<>();
        if (!dbFile.exists()) {
            initDefaultData(map);
            saveAll(map.values());
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
            System.err.println("[DAO Error] Không thể đọc file " + dbFile.getName() + ": " + e.getMessage());
            initDefaultData(map);
        }
        return map;
    }

    /**
     * Lưu toàn bộ bản ghi xuống file CSDL vật lý.
     */
    public synchronized boolean saveAll(Collection<StudentRecord> records) {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(dbFile), StandardCharsets.UTF_8))) {
            pw.println("# MaSV,HoTen,Email,Nganh,GPA,ServerGhiNhan");
            for (StudentRecord r : records) {
                pw.println(r.toCsvRow());
            }
            pw.flush();
            return true;
        } catch (Exception e) {
            System.err.println("[DAO Error] Không thể lưu file " + dbFile.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Khởi tạo dữ liệu mẫu nếu Server 1 khởi động lần đầu tiên.
     */
    private void initDefaultData(Map<String, StudentRecord> map) {
        if ("SERVER_1".equalsIgnoreCase(serverId)) {
            StudentRecord s1 = new StudentRecord("24IT001", "Trần Quang Bình", "binhtq.24it@vku.udn.vn", "CNTT", 3.85, serverId);
            StudentRecord s2 = new StudentRecord("24IT002", "Lê Nhuận Phát", "phatln.24it@vku.udn.vn", "KTPM", 3.75, serverId);
            map.put(s1.getId(), s1);
            map.put(s2.getId(), s2);
        }
    }
}
