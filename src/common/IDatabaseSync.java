package common;

import model.StudentRecord;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Remote Interface định nghĩa các phương thức gọi hàm từ xa qua RMI:
 * - Thao tác CRUD từ Client
 * - Thao tác Replication giữa các Server trong cụm phân tán
 */
public interface IDatabaseSync extends Remote {
    String getServerId() throws RemoteException;
    boolean ping() throws RemoteException;
    List<StudentRecord> getAll() throws RemoteException;
    boolean insert(StudentRecord record) throws RemoteException;
    boolean update(StudentRecord record) throws RemoteException;
    boolean delete(String id) throws RemoteException;
    boolean replicate(String action, StudentRecord record, String sender) throws RemoteException;
}
