package dao;

import static com.mongodb.client.model.Filters.*;

import com.mongodb.client.*;
import com.mongodb.client.model.*;
import java.util.*;
import model.*;
import org.bson.Document;

/** One atomic event insert is both the durable data and the outbox. No CSV fallback. */
public final class StudentDAO implements AutoCloseable {
  private final MongoClient client;
  private final MongoCollection<Document> events;

  public StudentDAO(String uri, String database) {
    client = MongoClients.create(uri);
    try {
      var db = client.getDatabase(database);
      db.runCommand(new Document("ping", 1));
      events = db.getCollection("events").withWriteConcern(com.mongodb.WriteConcern.JOURNALED);
      events.createIndex(Indexes.ascending("origin", "acked"));
      if (!db.listCollectionNames().into(new ArrayList<>()).contains("students")) {
        db.createView(
            "students",
            "events",
            List.of(
                Document.parse("{\"$sort\":{\"version\":-1,\"_id\":-1}}"),
                Document.parse(
                    "{\"$group\":{\"_id\":\"$student.id\",\"latest\":{\"$first\":\"$$ROOT\"}}}"),
                Document.parse("{\"$match\":{\"latest.deleted\":false}}"),
                Document.parse(
                    "{\"$replaceRoot\":{\"newRoot\":{\"$mergeObjects\":[\"$latest.student\",{\"origin\":\"$latest.origin\",\"version\":\"$latest.version\",\"eventId\":\"$latest._id\"}]}}}")));
      }
    } catch (RuntimeException e) {
      client.close();
      throw e;
    }
  }

  public void save(Change e, boolean acked) {
    var r = e.student();
    var d =
        new Document("version", e.version())
            .append("origin", e.origin())
            .append("createdAt", e.createdAt())
            .append("deleted", e.deleted())
            .append(
                "student",
                new Document("id", r.id())
                    .append("name", r.name())
                    .append("email", r.email())
                    .append("major", r.major())
                    .append("gpa", r.gpa()))
            .append("acked", acked);
    events.updateOne(
        eq("_id", e.eventId()), new Document("$setOnInsert", d), new UpdateOptions().upsert(true));
  }

  public void acknowledge(String id) {
    events.updateOne(eq("_id", id), Updates.set("acked", true));
  }

  public List<Change> all() {
    return read(new Document());
  }

  public List<Change> pending(String origin) {
    return read(and(eq("origin", origin), eq("acked", false)));
  }

  private List<Change> read(org.bson.conversions.Bson filter) {
    var result = new ArrayList<Change>();
    for (var d : events.find(filter)) {
      var s = d.get("student", Document.class);
      result.add(
          new Change(
              d.getString("_id"),
              ((Number) d.get("version")).longValue(),
              d.getString("origin"),
              ((Number) d.get("createdAt")).longValue(),
              d.getBoolean("deleted"),
              new StudentRecord(
                  s.getString("id"),
                  s.getString("name"),
                  s.getString("email"),
                  s.getString("major"),
                  ((Number) s.get("gpa")).doubleValue())));
    }
    return result;
  }

  public long countPending(String origin) {
    return events.countDocuments(and(eq("origin", origin), eq("acked", false)));
  }

  public void close() {
    client.close();
  }
}
