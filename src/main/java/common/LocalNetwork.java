package common;

import java.net.*;
import java.util.*;

/** Reads current interface addresses each time; never treats a saved IP as the machine's IP. */
public record LocalNetwork(List<String> addresses, String preferred) {
  public static LocalNetwork scan() throws SocketException {
    Set<String> addresses = new LinkedHashSet<>();
    for (var nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
      if (!nic.isUp() || nic.isLoopback() || nic.isPointToPoint()) continue;
      for (var address : Collections.list(nic.getInetAddresses())) {
        if (address instanceof Inet4Address && !address.isLinkLocalAddress()) {
          addresses.add(address.getHostAddress());
        }
      }
    }
    String routed = "";
    // UDP connect asks the OS which interface owns the default route; no packet is sent.
    try (var socket = new DatagramSocket()) {
      socket.connect(InetAddress.getByAddress(new byte[] {1, 1, 1, 1}), 9);
      routed = socket.getLocalAddress().getHostAddress();
    } catch (Exception ignored) {
    }
    List<String> result = new ArrayList<>(addresses);
    result.sort(String::compareTo);
    // Loopback remains available for a demo on one computer without a network.
    result.add("127.0.0.1");
    return new LocalNetwork(
        List.copyOf(result), addresses.contains(routed) ? routed : result.getFirst());
  }

  public String resolve(String selection) {
    if (selection.equals("auto")) return preferred;
    if (!addresses.contains(selection))
      throw new IllegalArgumentException(
          "IP " + selection + " không còn trên máy. Chọn Tự động rồi Áp dụng.");
    return selection;
  }
}
