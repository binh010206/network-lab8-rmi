package server;

/** Điểm chạy riêng để VS Code không chọn nhầm cấu hình máy chủ. */
public final class Server1App {
  public static void main(String[] args) {
    RMIServerApp.main(new String[] {"1"});
  }
}
