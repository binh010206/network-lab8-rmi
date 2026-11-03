package common;

import java.rmi.*;
import java.util.List;
import model.*;

public interface IDatabaseSync extends Remote {
  Snapshot snapshot() throws RemoteException;

  String mutate(String action, StudentRecord student) throws RemoteException;

  void receive(Change event) throws RemoteException;

  List<Change> history() throws RemoteException;
}
