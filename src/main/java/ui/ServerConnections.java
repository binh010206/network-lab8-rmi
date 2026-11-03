package ui;

import common.*;
import java.util.*;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;

/** Connection controls only. Network probing and server restarts run on Desktop's worker. */
final class ServerConnections extends VBox {
  private static final String AUTO = "Tự động";
  private final ComboBox<String> interfaceChoice = new ComboBox<>();
  private final TextField localAddress = new TextField(), localPort = new TextField();
  private final TextField peerAddress = new TextField(), peerPort = new TextField();
  private final Button apply = new Button("Áp dụng");
  private Config config;
  private LocalNetwork network;

  ServerConnections(
      Config config, LocalNetwork network, Consumer<Config> onApply, Consumer<String> onError) {
    super(8);
    this.config = config;
    this.network = network;
    getStyleClass().add("address-panel");
    localAddress.setEditable(false);
    localAddress.setPrefWidth(170);
    localPort.setText(config.get("registry.port"));
    localPort.setEditable(false);
    localPort.setPrefWidth(75);
    peerAddress.setText(config.get("peer.host"));
    peerAddress.setPromptText("IP mới của " + peerName(config.get("server.id")));
    peerAddress.setPrefWidth(170);
    peerPort.setText(config.get("peer.registry.port"));
    peerPort.setPrefWidth(75);
    interfaceChoice.setPrefWidth(160);
    interfaceChoice.setValue(
        config.getOrDefault("network.interface", "auto").equals("auto")
            ? AUTO
            : config.get("network.interface"));
    Button copy = new Button("Sao chép IP");
    copy.setOnAction(
        e -> {
          var content = new ClipboardContent();
          content.putString(localAddress.getText());
          Clipboard.getSystemClipboard().setContent(content);
        });
    apply.setOnAction(
        e -> {
          try {
            String selection =
                AUTO.equals(interfaceChoice.getValue()) ? "auto" : interfaceChoice.getValue();
            String local = network.resolve(selection);
            String peer = peerAddress.getText().trim();
            if (!validIpv4(peer))
              throw new IllegalArgumentException(
                  "Nhập IPv4 hợp lệ của " + peerName(config.get("server.id")) + ".");
            int own = port(localPort.getText()), other = port(peerPort.getText());
            if (own == config.number("object.port"))
              throw new IllegalArgumentException(
                  "Cổng này đang được server sử dụng. Chọn cổng khác.");
            if (network.addresses().contains(peer) && own == other)
              throw new IllegalArgumentException(
                  "Hai server không được dùng cùng một địa chỉ và cổng.");
            onApply.accept(
                this.config.with(
                    Map.of(
                        "network.interface",
                        selection,
                        "advertise.host",
                        local,
                        "registry.port",
                        Integer.toString(own),
                        "peer.host",
                        peer,
                        "peer.registry.port",
                        Integer.toString(other))));
          } catch (IllegalArgumentException ex) {
            onError.accept(ex.getMessage());
          }
        });
    Label hint =
        new Label(
            "IP máy này tự cập nhật theo mạng. Khi máy kia đổi IP, nhập địa chỉ mới rồi Áp dụng.");
    hint.setWrapText(true);
    getChildren()
        .addAll(
            hint,
            row(name(config.get("server.id")), localAddress, localPort, interfaceChoice, copy),
            row(peerName(config.get("server.id")), peerAddress, peerPort, apply));
    update(network, config);
  }

  void update(LocalNetwork network, Config active) {
    this.network = network;
    String choice = interfaceChoice.getValue();
    String previousMode = config.getOrDefault("network.interface", "auto");
    String activeMode = active.getOrDefault("network.interface", "auto");
    if (Objects.equals(choice, previousMode.equals("auto") ? AUTO : previousMode))
      choice = activeMode.equals("auto") ? AUTO : activeMode;
    this.config = active;
    var options = new ArrayList<String>();
    options.add(AUTO);
    options.addAll(network.addresses());
    if (choice != null && !options.contains(choice)) options.add(choice);
    if (!interfaceChoice.getItems().equals(options)) {
      interfaceChoice.getItems().setAll(options);
    }
    if (!Objects.equals(interfaceChoice.getValue(), choice)) interfaceChoice.setValue(choice);
    localAddress.setText(active.get("advertise.host"));
  }

  void setBusy(boolean busy) {
    apply.setDisable(busy);
  }

  private HBox row(String caption, TextField ip, TextField port, javafx.scene.Node... extra) {
    Label label = new Label(caption);
    label.setPrefWidth(80);
    HBox row = new HBox(10, label, new Label("IP"), ip, new Label("Cổng"), port);
    row.getChildren().addAll(extra);
    row.setAlignment(Pos.CENTER_LEFT);
    return row;
  }

  static String name(String id) {
    return id.startsWith("SERVER_") ? "Server " + id.substring(7) : id;
  }

  static String peerName(String id) {
    return "SERVER_1".equals(id) ? "Server 2" : "Server 1";
  }

  private static int port(String text) {
    try {
      int port = Integer.parseInt(text.trim());
      if (port > 0 && port <= 65535) return port;
    } catch (NumberFormatException ignored) {
    }
    throw new IllegalArgumentException("Cổng phải là số từ 1 đến 65535.");
  }

  private static boolean validIpv4(String text) {
    String[] parts = text.split("\\.", -1);
    if (parts.length != 4) return false;
    for (String part : parts) {
      if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255) return false;
    }
    return !text.equals("0.0.0.0")
        && !text.equals("255.255.255.255")
        && Integer.parseInt(parts[0]) < 224;
  }
}
