package model;

import java.io.Serializable;
import java.util.List;

public record Snapshot(
    String serverId,
    String database,
    List<Change> rows,
    long pending,
    List<String> pendingEventIds,
    boolean peerOnline,
    long ackMillis,
    List<String> logs)
    implements Serializable {}
