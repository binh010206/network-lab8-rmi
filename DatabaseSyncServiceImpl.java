import java.io.*;
import java.nio.charset.StandardCharsets;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseSyncServiceImpl extends UnicastRemoteObject implements IDatabaseSyncService {
    private static final long serialVersionUID = 1L;

    public static class PeerNode {
        public final String id;
        public final String host;
        public final int port;

        public PeerNode(String id, String host, int port) {
            this.id = id;
            this.host = host;
            this.port = port;
        }

        public String getRmiUrl() {
            return "rmi://" + host + ":" + port + "/SyncService";
        }
    }

    private final String serverId;
    private final int port;
    private final List<PeerNode> peers = new ArrayList<>();
    private final Map<String, StudentRecord> dataStore = new ConcurrentHashMap<>();
    private final RMIServerApp gui;
    private final File storageFile;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public DatabaseSyncServiceImpl(String serverId, int port, List<PeerNode> peers, RMIServerApp gui) throws RemoteException {
        super(port);
        this.serverId = serverId;
        this.port = port;
        this.peers.addAll(peers);
        this.gui = gui;
        this.storageFile = new File("db_" + serverId.toLowerCase() + ".csv");

        loadFromDisk();
    }

    @Override
    public String getServerId() throws RemoteException {
        return serverId;
    }

    @Override
    public boolean ping() throws RemoteException {
        return true;
    }

    @Override
    public List<StudentRecord> getAllRecords() throws RemoteException {
        return new ArrayList<>(dataStore.values());
    }

    @Override
    public StudentRecord getRecordById(String id) throws RemoteException {
        return dataStore.get(id);
    }

    @Override
    public synchronized boolean insertRecord(StudentRecord record) throws RemoteException {
        if (dataStore.containsKey(record.getId())) {
            return false;
        }
        record.setOriginServer(serverId);
        record.setTimestamp(System.currentTimeMillis());
        dataStore.put(record.getId(), record);
        saveToDisk();

        gui.log("[CLIENT CALL] Thêm mới bản ghi: " + record.getId() + " - " + record.getFullName());
        gui.refreshTable();

        broadcastReplication(SyncAction.INSERT, record);
        return true;
    }

    @Override
    public synchronized boolean updateRecord(StudentRecord record) throws RemoteException {
        if (!dataStore.containsKey(record.getId())) {
            return false;
        }
        record.setOriginServer(serverId);
        record.setTimestamp(System.currentTimeMillis());
        dataStore.put(record.getId(), record);
        saveToDisk();

        gui.log("[CLIENT CALL] Cập nhật bản ghi: " + record.getId() + " - " + record.getFullName());
        gui.refreshTable();

        broadcastReplication(SyncAction.UPDATE, record);
        return true;
    }

    @Override
    public synchronized boolean deleteRecord(String id) throws RemoteException {
        StudentRecord removed = dataStore.remove(id);
        if (removed != null) {
            saveToDisk();
            gui.log("[CLIENT CALL] Xóa bản ghi: " + id);
            gui.refreshTable();

            StudentRecord stub = new StudentRecord(id, "", "", "", 0, serverId);
            broadcastReplication(SyncAction.DELETE, stub);
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean replicate(SyncAction action, StudentRecord record, String senderServerId) throws RemoteException {
        gui.log("[RMI REPLICATION] << Nhận lệnh " + action + " từ " + senderServerId + " đối với ID: " + record.getId());
        switch (action) {
            case INSERT:
            case UPDATE:
                dataStore.put(record.getId(), record);
                saveToDisk();
                break;
            case DELETE:
                dataStore.remove(record.getId());
                saveToDisk();
                break;
        }
        gui.refreshTable();
        return true;
    }

    @Override
    public synchronized Map<String, StudentRecord> requestFullSync() throws RemoteException {
        return new HashMap<>(dataStore);
    }

    public void triggerFullSyncFromPeers() {
        executor.submit(() -> {
            for (PeerNode peer : peers) {
                try {
                    String url = peer.getRmiUrl();
                    IDatabaseSyncService peerStub = (IDatabaseSyncService) Naming.lookup(url);
                    Map<String, StudentRecord> remoteData = peerStub.requestFullSync();
                    if (remoteData != null && !remoteData.isEmpty()) {
                        int added = 0;
                        for (Map.Entry<String, StudentRecord> entry : remoteData.entrySet()) {
                            if (!dataStore.containsKey(entry.getKey()) ||
                                    entry.getValue().getTimestamp() > dataStore.get(entry.getKey()).getTimestamp()) {
                                dataStore.put(entry.getKey(), entry.getValue());
                                added++;
                            }
                        }
                        if (added > 0) {
                            saveToDisk();
                            gui.log("[FULL SYNC] Đã đồng bộ " + added + " bản ghi mới nhất từ " + peer.id);
                            gui.refreshTable();
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    private void broadcastReplication(SyncAction action, StudentRecord record) {
        for (PeerNode peer : peers) {
            executor.submit(() -> {
                try {
                    String url = peer.getRmiUrl();
                    IDatabaseSyncService peerStub = (IDatabaseSyncService) Naming.lookup(url);
                    peerStub.replicate(action, record, serverId);
                    gui.log("[RMI REPLICATION] >> Đã đồng bộ " + action + " (ID: " + record.getId() + ") sang " + peer.id + " (" + url + ")");
                } catch (Exception e) {
                    gui.log("[RMI CẢNH BÁO] Không thể kết nối tới " + peer.id + " (" + peer.getRmiUrl() + "): " + e.getMessage());
                }
            });
        }
    }

    private synchronized void saveToDisk() {
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(storageFile), StandardCharsets.UTF_8))) {
            writer.println("id,fullName,email,major,gpa,originServer,timestamp");
            for (StudentRecord r : dataStore.values()) {
                writer.printf("%s,%s,%s,%s,%.2f,%s,%d%n",
                        escape(r.getId()),
                        escape(r.getFullName()),
                        escape(r.getEmail()),
                        escape(r.getMajor()),
                        r.getGpa(),
                        escape(r.getOriginServer()),
                        r.getTimestamp());
            }
        } catch (IOException e) {
            gui.log("[LỖI LƯU DISK] " + e.getMessage());
        }
    }

    private synchronized void loadFromDisk() {
        if (!storageFile.exists()) {
            initSampleData();
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(storageFile), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // Header
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length >= 7) {
                    String id = parts[0].trim();
                    String name = parts[1].trim();
                    String email = parts[2].trim();
                    String major = parts[3].trim();
                    double gpa = Double.parseDouble(parts[4].trim());
                    String origin = parts[5].trim();
                    long time = Long.parseLong(parts[6].trim());
                    dataStore.put(id, new StudentRecord(id, name, email, major, gpa, origin, time));
                }
            }
            gui.log("[DISK] Đã tải " + dataStore.size() + " bản ghi từ " + storageFile.getName());
        } catch (Exception e) {
            initSampleData();
        }
    }

    private void initSampleData() {
        if ("SERVER_1".equalsIgnoreCase(serverId)) {
            dataStore.put("24IT001", new StudentRecord("24IT001", "Trần Quang Bình", "binhtq.24it@vku.udn.vn", "CNTT", 3.85, serverId));
            dataStore.put("24IT002", new StudentRecord("24IT002", "Lê Nhuận Phát", "phatln.24it@vku.udn.vn", "KTPM", 3.75, serverId));
            saveToDisk();
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace(",", " ");
    }
}
