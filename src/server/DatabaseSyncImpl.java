package server;

import common.IDatabaseSync;
import model.StudentRecord;
import dao.StudentDAO;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Triển khai Remote Object IDatabaseSync.
 * Đảm nhiệm:
 * 1. Xử lý yêu cầu CRUD từ Client
 * 2. Lưu trữ bền vững xuống CSDL file CSV qua StudentDAO
 * 3. Đồng bộ hóa phân tán đa máy chủ (Multi-Master Replication) qua RMI
 */
public class DatabaseSyncImpl extends UnicastRemoteObject implements IDatabaseSync {
    private static final long serialVersionUID = 1L;

    private final String serverId;
    private final int port;
    private final int[] peerPorts;
    private final Map<String, StudentRecord> database = new ConcurrentHashMap<>();
    private final StudentDAO dao;
    private ServerUI ui;

    public DatabaseSyncImpl(String serverId, int port, int[] peerPorts, StudentDAO dao) throws RemoteException {
        super();
        this.serverId = serverId;
        this.port = port;
        this.peerPorts = peerPorts;
        this.dao = dao;

        // Nạp dữ liệu ban đầu từ file CSDL
        Map<String, StudentRecord> loaded = dao.loadAll();
        database.putAll(loaded);
    }

    public void setUI(ServerUI ui) {
        this.ui = ui;
        if (ui != null) {
            ui.updateTable(database.values());
            log("[CSDL FILE] Đã nạp " + database.size() + " bản ghi từ " + dao.getFile().getName());
        }
    }

    @Override
    public String getServerId() {
        return serverId;
    }

    @Override
    public boolean ping() {
        return true;
    }

    @Override
    public List<StudentRecord> getAll() {
        return new ArrayList<>(database.values());
    }

    @Override
    public synchronized boolean insert(StudentRecord r) {
        if (database.containsKey(r.getId())) return false;
        r.setOriginServer(serverId);
        database.put(r.getId(), r);
        dao.saveAll(database.values());
        refresh();
        log("[CLIENT THÊM] ID: " + r.getId() + " | Tên: " + r.getName() + " | Ngành: " + r.getMajor() + " | GPA: " + r.getGpa());
        syncToPeers("INSERT", r);
        return true;
    }

    @Override
    public synchronized boolean update(StudentRecord r) {
        if (!database.containsKey(r.getId())) return false;
        r.setOriginServer(serverId);
        database.put(r.getId(), r);
        dao.saveAll(database.values());
        refresh();
        log("[CLIENT SỬA] ID: " + r.getId() + " | Tên: " + r.getName() + " | GPA: " + r.getGpa());
        syncToPeers("UPDATE", r);
        return true;
    }

    @Override
    public synchronized boolean delete(String id) {
        if (database.remove(id) != null) {
            dao.deleteRecord(id);
            dao.saveAll(database.values());
            refresh();
            log("[CLIENT XÓA] Mã SV: " + id);
            StudentRecord dummy = new StudentRecord(id, "", "", "", 0, serverId);
            syncToPeers("DELETE", dummy);
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean replicate(String action, StudentRecord r, String sender) {
        log("[REPLICATION NHẬN] << Lệnh: " + action + " từ " + sender + " đối với Mã: " + r.getId());
        if ("DELETE".equalsIgnoreCase(action)) {
            database.remove(r.getId());
            dao.deleteRecord(r.getId());
        } else {
            database.put(r.getId(), r);
        }
        dao.saveAll(database.values());
        refresh();
        return true;
    }

    /**
     * Phát tán thay đổi dữ liệu sang tất cả các Server ngang hàng trong cụm.
     */
    private void syncToPeers(String action, StudentRecord r) {
        Executors.newCachedThreadPool().submit(() -> {
            for (int p : peerPorts) {
                try {
                    IDatabaseSync peer = (IDatabaseSync) Naming.lookup("rmi://127.0.0.1:" + p + "/SyncService");
                    peer.replicate(action, r, serverId);
                    log("[REPLICATION GỬI] >> Đã đồng bộ " + action + " (Mã: " + r.getId() + ") sang Server cổng " + p);
                } catch (Exception e) {
                    log("[CẢNH BÁO] Không thể kết nối Server cổng " + p + " (Server này đang offline)");
                }
            }
        });
    }

    /**
     * Kéo toàn bộ dữ liệu mới nhất từ các máy chủ online khi vừa khởi động lại.
     */
    public synchronized void pullSyncFromPeers() {
        Executors.newCachedThreadPool().submit(() -> {
            for (int p : peerPorts) {
                try {
                    IDatabaseSync peer = (IDatabaseSync) Naming.lookup("rmi://127.0.0.1:" + p + "/SyncService");
                    List<StudentRecord> peerList = peer.getAll();
                    if (peerList != null && !peerList.isEmpty()) {
                        int added = 0;
                        for (StudentRecord r : peerList) {
                            if (!database.containsKey(r.getId())) {
                                database.put(r.getId(), r);
                                added++;
                            }
                        }
                        if (added > 0) {
                            dao.saveAll(database.values());
                            refresh();
                            log("[TỰ ĐỘNG ĐỒNG BỘ] Đã kéo " + added + " bản ghi mới từ Server cổng " + p);
                        }
                        break;
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    private void refresh() {
        if (ui != null) {
            ui.updateTable(database.values());
        }
    }

    private void log(String msg) {
        if (ui != null) {
            ui.appendLog(msg);
        } else {
            System.out.println(msg);
        }
    }
}
