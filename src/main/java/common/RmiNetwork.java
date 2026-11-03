package common;

import java.io.IOException;
import java.net.*;
import java.rmi.server.RMISocketFactory;

/** Bounds actual TCP connection time as well as RMI response time after a network change. */
public final class RmiNetwork {
  private RmiNetwork() {}

  public static synchronized void configure() throws IOException {
    System.setProperty("sun.rmi.transport.tcp.responseTimeout", "2500");
    System.setProperty("sun.rmi.transport.tcp.handshakeTimeout", "2500");
    if (RMISocketFactory.getSocketFactory() != null) return;
    RMISocketFactory.setSocketFactory(
        new RMISocketFactory() {
          @Override
          public Socket createSocket(String host, int port) throws IOException {
            Socket socket = new Socket();
            try {
              socket.connect(new InetSocketAddress(host, port), 2500);
              socket.setSoTimeout(2500);
              return socket;
            } catch (IOException e) {
              socket.close();
              throw e;
            }
          }

          @Override
          public ServerSocket createServerSocket(int port) throws IOException {
            return new ServerSocket(port);
          }
        });
  }
}
