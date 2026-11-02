import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface IDatabaseSyncService extends Remote {
    String getServerId() throws RemoteException;
    boolean ping() throws RemoteException;

    List<StudentRecord> getAllRecords() throws RemoteException;
    StudentRecord getRecordById(String id) throws RemoteException;

    boolean insertRecord(StudentRecord record) throws RemoteException;
    boolean updateRecord(StudentRecord record) throws RemoteException;
    boolean deleteRecord(String id) throws RemoteException;

    // Hàm gọi nội bộ giữa các Server để Replication đồng bộ hóa
    boolean replicate(SyncAction action, StudentRecord record, String senderServerId) throws RemoteException;

    // Đồng bộ toàn bộ dữ liệu khi một server mới online trở lại (Full Catch-up)
    Map<String, StudentRecord> requestFullSync() throws RemoteException;
}
