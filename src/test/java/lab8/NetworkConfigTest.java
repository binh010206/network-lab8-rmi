package lab8;

import static org.junit.jupiter.api.Assertions.*;

import common.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NetworkConfigTest {
  @TempDir Path dir;

  @Test
  void autoUsesNewNetworkAndManualAddressMustStillExist() {
    var before = new LocalNetwork(List.of("192.0.2.10", "127.0.0.1"), "192.0.2.10");
    var after = new LocalNetwork(List.of("198.51.100.20", "127.0.0.1"), "198.51.100.20");
    assertEquals("192.0.2.10", before.resolve("auto"));
    assertEquals("198.51.100.20", after.resolve("auto"));
    assertThrows(IllegalArgumentException.class, () -> after.resolve("192.0.2.10"));
    assertEquals("127.0.0.1", after.resolve("127.0.0.1"));
  }

  @Test
  void savesNetworkChangesWithoutModifyingTemplateOrDatabase() throws Exception {
    Path template = dir.resolve("server1.properties"),
        local = dir.resolve("server1.local.properties");
    String original =
        "server.id=SERVER_1\n"
            + "advertise.host=192.0.2.10\n"
            + "peer.host=192.0.2.20\n"
            + "mongo.uri=mongodb://127.0.0.1:27017/?connectTimeoutMS=2000\n"
            + "mongo.database=lab8_server1\n";
    Files.writeString(template, original);
    Config base = new Config(template.toString());
    Config edited =
        base.with(
            Map.of(
                "advertise.host",
                "198.51.100.10",
                "peer.host",
                "198.51.100.20",
                "network.interface",
                "auto"));
    edited.save(local);
    Config restored = new Config(local.toString());
    assertEquals("198.51.100.10", restored.get("advertise.host"));
    assertEquals("198.51.100.20", restored.get("peer.host"));
    assertEquals(base.get("mongo.uri"), restored.get("mongo.uri"));
    assertEquals(base.get("mongo.database"), restored.get("mongo.database"));
    assertEquals("192.0.2.10", base.get("advertise.host"));
    assertEquals(original, Files.readString(template));
    edited.with(Map.of("peer.host", "198.51.100.21")).save(local);
    assertEquals("198.51.100.21", new Config(local.toString()).get("peer.host"));
  }
}
