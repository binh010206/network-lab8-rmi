package server;

public final class RMIServerApp {
  public static void main(String[] args) {
    ui.Desktop.serverMode = true;
    javafx.application.Application.launch(ui.Desktop.class, args);
  }
}
