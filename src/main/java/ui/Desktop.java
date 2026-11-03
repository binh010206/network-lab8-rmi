package ui;

import common.*;
import java.rmi.registry.LocateRegistry;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import model.*;
import server.*;

public final class Desktop extends Application {
  public static boolean serverMode;
  private final ScheduledExecutorService io =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            var t = new Thread(r, "network-worker");
            t.setDaemon(true);
            return t;
          });
  private volatile IDatabaseSync service;
  private ServerNode node;
  private final Label connection = new Label("Chưa kết nối"),
      stats = new Label("—"),
      feedback = new Label("Sẵn sàng"),
      title = new Label();
  private final TextArea logs = new TextArea();
  private final TableView<Change> table = new TableView<>();
  private final ObservableList<Change> data = FXCollections.observableArrayList();
  private final TextField host = new TextField("127.0.0.1"),
      port = new TextField("1099"),
      search = new TextField();
  private final TextField id = new TextField(),
      name = new TextField(),
      email = new TextField(),
      major = new TextField(),
      gpa = new TextField();
  private final List<Button> actions = new ArrayList<>();
  private final Map<String, String> previous = new HashMap<>();
  private final Set<String> changed = new HashSet<>();
  private boolean connecting, mutating;
  private String lastEvent;
  private long generation;

  public void start(Stage stage) {
    System.setProperty("sun.rmi.transport.tcp.responseTimeout", "2500");
    System.setProperty("sun.rmi.transport.tcp.handshakeTimeout", "2500");
    System.setProperty("sun.rmi.transport.connectionTimeout", "2500");
    title.setText(serverMode ? "Trung tâm đồng bộ" : "Quản lý sinh viên");
    title.getStyleClass().add("title");
    Label brand = new Label("VKU  /  NETWORK LAB 08");
    brand.getStyleClass().add("brand");
    VBox heading =
        new VBox(8, brand, title, new Label("JAVA RMI  •  HAI MÁY CHỦ  •  HAI DATABASE ĐỘC LẬP"));
    Button connect = new Button("Kết nối");
    connect.setOnAction(e -> connect());
    host.setPromptText("IP máy chủ");
    port.setPrefWidth(90);
    host.setPrefWidth(180);
    HBox connectBar = new HBox(10, new Label("Địa chỉ"), host, new Label("Cổng"), port, connect);
    connectBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    connection.getStyleClass().add("badge");
    stats.getStyleClass().add("stats");
    feedback.setWrapText(true);
    search.setPromptText("Tìm theo mã, tên, email hoặc ngành…");
    search.textProperty().addListener((o, a, b) -> filter());
    column("Mã SV", c -> c.student().id());
    column("Họ và tên", c -> c.student().name());
    column("Email", c -> c.student().email());
    column("Ngành", c -> c.student().major());
    column("GPA", c -> String.format(java.util.Locale.ROOT, "%.2f", c.student().gpa()));
    column("Nguồn / phiên bản", c -> c.origin() + " / " + c.version());
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    table.setPlaceholder(new Label("Chưa có dữ liệu • Thêm sinh viên để bắt đầu demo"));
    table.setRowFactory(
        t ->
            new TableRow<>() {
              protected void updateItem(Change c, boolean empty) {
                super.updateItem(c, empty);
                setStyle(
                    !empty && c != null && changed.contains(c.student().id())
                        ? "-fx-background-color: #d9f7eb;"
                        : "");
              }
            });
    table
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (o, a, b) -> {
              if (b != null) {
                var s = b.student();
                id.setText(s.id());
                name.setText(s.name());
                email.setText(s.email());
                major.setText(s.major());
                gpa.setText(Double.toString(s.gpa()));
              }
            });
    VBox center = new VBox(12, search, table);
    VBox.setVgrow(table, Priority.ALWAYS);
    if (!serverMode) {
      GridPane form = new GridPane();
      form.setHgap(10);
      form.setVgap(6);
      TextField[] fields = {id, name, email, major, gpa};
      String[] labels = {"Mã sinh viên", "Họ tên", "Email", "Ngành", "GPA (0–4)"};
      for (int i = 0; i < fields.length; i++) {
        form.add(new Label(labels[i]), i, 0);
        form.add(fields[i], i, 1);
        fields[i].setPrefWidth(i == 2 ? 220 : 150);
      }
      HBox buttons = new HBox(10);
      String[] captions = {"Thêm sinh viên", "Lưu chỉnh sửa", "Xóa sinh viên"},
          verbs = {"INSERT", "UPDATE", "DELETE"};
      for (int i = 0; i < 3; i++) {
        String action = verbs[i];
        Button b = new Button(captions[i]);
        b.setDisable(true);
        b.setOnAction(e -> mutate(action));
        actions.add(b);
        buttons.getChildren().add(b);
      }
      Button clear = new Button("Nhập mới");
      clear.setOnAction(
          e -> {
            table.getSelectionModel().clearSelection();
            for (var f : fields) f.clear();
            id.requestFocus();
          });
      buttons.getChildren().add(clear);
      center.getChildren().addAll(form, buttons);
    }
    logs.setEditable(false);
    logs.setPrefRowCount(5);
    logs.setWrapText(true);
    VBox root =
        new VBox(
            16,
            heading,
            serverMode
                ? new Label("Chế độ máy chủ • dữ liệu được lưu trực tiếp vào MongoDB")
                : connectBar,
            connection,
            stats,
            center,
            feedback,
            new Label("NHẬT KÝ ĐỒNG BỘ"),
            logs);
    root.setPadding(new javafx.geometry.Insets(26));
    VBox.setVgrow(center, Priority.ALWAYS);
    Scene scene = new Scene(root, 1180, 850);
    scene
        .getStylesheets()
        .add(Objects.requireNonNull(getClass().getResource("/theme.css")).toExternalForm());
    stage.setScene(scene);
    stage.setTitle("Lab 8 • " + title.getText());
    stage.setMinWidth(950);
    stage.setMinHeight(720);
    stage.show();
    if (serverMode) {
      String selection;
      if (getParameters().getRaw().isEmpty()) {
        ChoiceDialog<String> chooser = new ChoiceDialog<>("Server 1", "Server 1", "Server 2");
        chooser.setTitle("Chọn máy chủ");
        chooser.setHeaderText("Máy này chạy Server nào?");
        chooser.setContentText("Máy chủ:");
        var selected = chooser.showAndWait();
        if (selected.isEmpty()) {
          Platform.exit();
          return;
        }
        selection = selected.get().endsWith("2") ? "2" : "1";
      } else selection = getParameters().getRaw().getFirst();
      final String path;
      if (selection.equals("1") || selection.equals("2")) {
        String local = "config/server" + selection + ".local.properties";
        path =
            java.nio.file.Files.exists(java.nio.file.Path.of(local))
                ? local
                : "config/server" + selection + ".properties";
      } else path = selection;
      io.execute(
          () -> {
            try {
              node = new ServerNode(new Config(path));
              service = node.service;
            } catch (Exception e) {
              error(e);
            }
          });
    } else connect();
    io.scheduleWithFixedDelay(this::poll, 0, 400, TimeUnit.MILLISECONDS);
  }

  private void column(String title, java.util.function.Function<Change, String> value) {
    var c = new TableColumn<Change, String>(title);
    c.setCellValueFactory(v -> new ReadOnlyStringWrapper(value.apply(v.getValue())));
    table.getColumns().add(c);
  }

  private void filter() {
    String q = search.getText().toLowerCase(java.util.Locale.ROOT).trim();
    table.setItems(
        data.filtered(
            c ->
                (c.student().id()
                        + " "
                        + c.student().name()
                        + " "
                        + c.student().email()
                        + " "
                        + c.student().major())
                    .toLowerCase(java.util.Locale.ROOT)
                    .contains(q)));
  }

  private void connect() {
    if (connecting) return;
    final String address = host.getText().trim();
    final int number;
    try {
      number = Integer.parseInt(port.getText().trim());
      if (number < 1 || number > 65535) throw new NumberFormatException();
    } catch (Exception e) {
      feedback.setText("Cổng phải từ 1 đến 65535");
      return;
    }
    connecting = true;
    service = null;
    ++generation;
    actions.forEach(b -> b.setDisable(true));
    connection.setText("Đang kết nối…");
    io.execute(
        () -> {
          try {
            var candidate =
                (IDatabaseSync) LocateRegistry.getRegistry(address, number).lookup("SyncService");
            candidate.snapshot();
            service = candidate;
            Platform.runLater(
                () -> {
                  previous.clear();
                  feedback.setText("Đã kết nối " + address + ":" + number);
                });
          } catch (Exception e) {
            error(e);
          } finally {
            Platform.runLater(() -> connecting = false);
          }
        });
  }

  private void poll() {
    var remote = service;
    if (remote == null) return;
    long request = generation;
    try {
      var snap = remote.snapshot();
      Platform.runLater(
          () -> {
            if (remote != service || request != generation) return;
            if (serverMode) {
              title.setText("Trung tâm đồng bộ • " + snap.serverId());
              title.setStyle(
                  snap.serverId().endsWith("2")
                      ? "-fx-text-fill: #7449aa;"
                      : "-fx-text-fill: #127e75;");
            }
            connection.setText(
                snap.serverId()
                    + "  •  MongoDB: "
                    + snap.database()
                    + "  •  Peer "
                    + (snap.peerOnline() ? "ONLINE" : "OFFLINE"));
            stats.setText(
                snap.rows().size()
                    + " sinh viên     |     "
                    + snap.pending()
                    + " thay đổi chờ gửi     |     ACK gần nhất: "
                    + (snap.ackMillis() < 0 ? "—" : snap.ackMillis() + " ms"));
            if (lastEvent != null && lastEvent.startsWith(snap.serverId() + ":")) {
              feedback.setText(
                  snap.pendingEventIds().contains(lastEvent)
                      ? "Đã lưu tại server • Đang chờ server còn lại xác nhận"
                      : "Đã lưu tại server • Server còn lại đã xác nhận đồng bộ");
            }
            changed.clear();
            for (var c : snap.rows()) {
              String old = previous.put(c.student().id(), c.eventId());
              if (old == null || !old.equals(c.eventId())) changed.add(c.student().id());
            }
            Change selected = table.getSelectionModel().getSelectedItem();
            if (!data.equals(snap.rows())) {
              data.setAll(snap.rows());
              filter();
              if (selected != null)
                for (var c : table.getItems())
                  if (c.student().id().equals(selected.student().id()))
                    table.getSelectionModel().select(c);
            }
            table.refresh();
            logs.setText(String.join("\n", snap.logs()));
            actions.forEach(b -> b.setDisable(mutating));
          });
    } catch (Exception e) {
      Platform.runLater(
          () -> {
            if (remote != service || request != generation) return;
            connection.setText("Mất kết nối server / MongoDB • Đang thử lại");
            actions.forEach(b -> b.setDisable(true));
          });
    }
  }

  private void mutate(String action) {
    if (mutating || service == null) return;
    final StudentRecord s;
    try {
      s =
          new StudentRecord(
              id.getText().trim(),
              name.getText().trim(),
              email.getText().trim(),
              major.getText().trim(),
              Double.parseDouble(gpa.getText().trim()));
      s.validate();
    } catch (Exception e) {
      feedback.setText("Kiểm tra mã SV, họ tên, email, ngành và GPA từ 0 đến 4.");
      return;
    }
    if (!action.equals("INSERT")) {
      var selected = table.getSelectionModel().getSelectedItem();
      if (selected == null || !selected.student().id().equals(s.id())) {
        feedback.setText("Chọn một bản ghi trong bảng; không đổi mã khi sửa/xóa.");
        return;
      }
    }
    if (action.equals("DELETE")) {
      var confirm =
          new Alert(
              Alert.AlertType.CONFIRMATION,
              "Xóa sinh viên " + s.id() + " trên cả hai server?",
              ButtonType.OK,
              ButtonType.CANCEL);
      if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
    }
    mutating = true;
    actions.forEach(b -> b.setDisable(true));
    var remote = service;
    io.execute(
        () -> {
          try {
            String event = remote.mutate(action, s);
            Platform.runLater(
                () -> {
                  lastEvent = event;
                  feedback.setText("Đã lưu tại server • chờ đồng bộ • " + event);
                });
          } catch (Exception e) {
            error(e);
          } finally {
            Platform.runLater(() -> mutating = false);
          }
        });
  }

  private void error(Exception e) {
    Platform.runLater(
        () -> {
          String message = e.getMessage();
          feedback.setText("Lỗi: " + (message == null ? e.getClass().getSimpleName() : message));
          connection.setText("Kiểm tra cấu hình, kết nối MongoDB và máy chủ");
        });
  }

  public void stop() {
    io.shutdownNow();
    if (node != null) node.close();
  }
}
