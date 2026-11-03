package server;

import common.*;
import dao.StudentDAO;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import model.*;

public final class DatabaseSyncImpl extends UnicastRemoteObject
    implements IDatabaseSync, AutoCloseable {
  private final StudentDAO dao;
  private final String id, database, peerHost;
  private final int peerPort;
  private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
  private final Deque<String> logs = new ArrayDeque<>();
  private volatile boolean online;
  private volatile long ackMillis = -1;

  public DatabaseSyncImpl(Config c, StudentDAO dao) throws RemoteException {
    super(c.number("object.port"));
    this.dao = dao;
    id = c.get("server.id");
    database = c.get("mongo.database");
    peerHost = c.get("peer.host");
    peerPort = c.number("peer.registry.port");
  }

  public void start() {
    worker.scheduleWithFixedDelay(this::sync, 0, 300, TimeUnit.MILLISECONDS);
  }

  private synchronized void log(String s) {
    logs.addFirst(java.time.LocalTime.now().withNano(0) + "  " + s);
    while (logs.size() > 100) logs.removeLast();
  }

  public static Map<String, Change> materialize(List<Change> events) {
    Map<String, Change> rows = new TreeMap<>();
    for (var e : events) rows.merge(e.student().id(), e, (a, b) -> a.compareTo(b) >= 0 ? a : b);
    return rows;
  }

  public synchronized Snapshot snapshot() throws RemoteException {
    try {
      return new Snapshot(
          id,
          database,
          materialize(dao.all()).values().stream().filter(e -> !e.deleted()).toList(),
          dao.countPending(id),
          dao.pending(id).stream().map(Change::eventId).toList(),
          online,
          ackMillis,
          List.copyOf(logs));
    } catch (RuntimeException e) {
      throw new RemoteException("Không đọc được MongoDB", e);
    }
  }

  public synchronized String mutate(String action, StudentRecord r) throws RemoteException {
    try {
      if (!Set.of("INSERT", "UPDATE", "DELETE").contains(action))
        throw new IllegalArgumentException("Thao tác không hợp lệ");
      if (r == null) throw new IllegalArgumentException("Thiếu dữ liệu");
      r.validate();
      var all = dao.all();
      var current = materialize(all).get(r.id());
      boolean exists = current != null && !current.deleted();
      if (action.equals("INSERT") && exists)
        throw new IllegalArgumentException("Mã sinh viên đã tồn tại");
      if (!action.equals("INSERT") && !exists)
        throw new IllegalArgumentException("Sinh viên không còn tồn tại");
      long version = all.stream().mapToLong(Change::version).max().orElse(0) + 1;
      var e =
          new Change(
              id + ":" + UUID.randomUUID(),
              version,
              id,
              System.currentTimeMillis(),
              action.equals("DELETE"),
              r);
      dao.save(e, false);
      log(action + " " + r.id() + " • Đã lưu cục bộ, chờ peer xác nhận");
      return e.eventId();
    } catch (RuntimeException e) {
      throw new RemoteException("Không thể lưu: " + e.getMessage(), e);
    }
  }

  public synchronized void receive(Change e) throws RemoteException {
    try {
      if (e == null
          || e.student() == null
          || e.eventId() == null
          || e.origin() == null
          || e.version() < 1) throw new IllegalArgumentException("Sự kiện không hợp lệ");
      e.student().validate();
      dao.save(e, true);
    } catch (RuntimeException ex) {
      throw new RemoteException("Không thể ghi sự kiện", ex);
    }
  }

  public synchronized List<Change> history() throws RemoteException {
    try {
      return dao.all();
    } catch (RuntimeException e) {
      throw new RemoteException("Không đọc được nhật ký", e);
    }
  }

  private void sync() {
    try {
      var peer =
          (IDatabaseSync) LocateRegistry.getRegistry(peerHost, peerPort).lookup("SyncService");
      // Reject accidental self-link and duplicate IDs before acknowledging any event.
      var remote = peer.snapshot();
      if (remote.serverId().equals(id))
        throw new IllegalStateException("Hai server phải có ID khác nhau");
      for (var e : dao.pending(id)) {
        long begin = System.nanoTime();
        peer.receive(e);
        dao.acknowledge(e.eventId());
        ackMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
        log("Đã xác nhận " + e.student().id() + " • " + ackMillis + " ms");
      }
      var known = new HashSet<String>();
      for (var e : dao.all()) known.add(e.eventId());
      int received = 0;
      for (var e : peer.history())
        if (!known.contains(e.eventId())) {
          receive(e);
          received++;
        }
      if (received > 0) log("Nhận " + received + " thay đổi từ " + remote.serverId());
      if (!online) log("Peer đã kết nối");
      online = true;
    } catch (Exception e) {
      if (online) log("Peer mất kết nối hoặc đồng bộ lỗi: " + e.getClass().getSimpleName());
      online = false;
    }
  }

  public void close() {
    worker.shutdownNow();
    try {
      UnicastRemoteObject.unexportObject(this, true);
    } catch (Exception ignored) {
    }
    dao.close();
  }
}
