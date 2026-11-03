package model;

import java.io.Serializable;

public record Change(
    String eventId,
    long version,
    String origin,
    long createdAt,
    boolean deleted,
    StudentRecord student)
    implements Serializable, Comparable<Change> {
  public int compareTo(Change other) {
    int c = Long.compare(version, other.version);
    return c != 0 ? c : eventId.compareTo(other.eventId);
  }
}
