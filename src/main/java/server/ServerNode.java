package server;

import common.Config;
import common.RmiNetwork;
import dao.StudentDAO;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;

public final class ServerNode implements AutoCloseable {
  public final DatabaseSyncImpl service;
  private final Registry registry;

  public ServerNode(Config c) throws Exception {
    System.setProperty("java.rmi.server.hostname", c.get("advertise.host"));
    RmiNetwork.configure();
    var dao = new StudentDAO(c.get("mongo.uri"), c.get("mongo.database"));
    DatabaseSyncImpl candidate = null;
    Registry reg = null;
    try {
      candidate = new DatabaseSyncImpl(c, dao);
      reg = LocateRegistry.createRegistry(c.number("registry.port"));
      reg.rebind("SyncService", candidate);
    } catch (Exception e) {
      if (candidate != null) candidate.close();
      else dao.close();
      if (reg != null) UnicastRemoteObject.unexportObject(reg, true);
      throw e;
    }
    service = candidate;
    registry = reg;
    service.start();
  }

  public void close() {
    service.close();
    try {
      UnicastRemoteObject.unexportObject(registry, true);
    } catch (Exception ignored) {
    }
  }

  public static void main(String[] args) throws Exception {
    var node = new ServerNode(new Config(args[0]));
    Runtime.getRuntime().addShutdownHook(new Thread(node::close));
    System.out.println("READY " + args[0]);
    new java.util.concurrent.CountDownLatch(1).await();
  }
}
