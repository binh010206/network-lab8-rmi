# Lab 8 — Java RMI / JavaFX / MongoDB

Ứng dụng quản lý sinh viên, đồng bộ hai chiều giữa **hai server và hai MongoDB độc lập**. Client truy cập qua Java RMI. Giao diện JavaFX tiếng Việt cho cả server và client.

## Yêu cầu và chạy nhanh trên Windows

- JDK 21 (kiểm tra `java -version`), Internet ở lần chạy đầu để tải Maven và thư viện.
- MongoDB Community 8.0.x chạy riêng trên từng máy; MongoDB Compass để kiểm tra dữ liệu.
- Bộ `mvnw.cmd` tải Maven 3.9.9 vào `.tools/` của dự án. Không cần cài Maven toàn hệ thống.

Thử trên một máy: chạy hai MongoDB riêng trên cổng 27017 và 27018. Nếu có Docker Desktop, dùng `docker compose up -d` tại thư mục dự án. Hai container dùng hai volume độc lập.

Không dùng Docker: sau khi cài MongoDB và đưa `mongod` vào PATH, mở `run-mongo1.cmd` và `run-mongo2.cmd`, giữ hai cửa sổ này hoạt động. Trên máy đang phát triển, script cũng nhận bộ MongoDB portable đã tải trong `.tools/`; bộ này không đưa lên GitHub. Trên hai máy thật chỉ chạy một MongoDB 27017 ở mỗi máy.

Mở lần lượt `run-server1.cmd`, `run-server2.cmd`, `run-client.cmd`. Có thể mở client thứ hai bằng cách chạy lại `run-client.cmd`, rồi nhập cổng 1098 để kết nối Server 2. Server 1 mặc định cổng 1099.

Cách chạy bằng PowerShell:

```powershell
.\mvnw.cmd test
.\run.ps1 server1
.\run.ps1 server2
.\run.ps1 client
```

Không đóng cửa sổ lệnh khi đang sử dụng ứng dụng. Dừng server bằng đóng cửa sổ JavaFX. Nếu MongoDB không hoạt động, server báo lỗi và không chuyển sang CSV.

## Demo chính thức: hai máy thật và các máy ảo

Ví dụ máy A là `192.168.1.10`, máy B là `192.168.1.20`. Thay bằng IPv4 LAN thực tế, có thể xem bằng `ipconfig`.

| Cấu hình | Máy A / Server 1 | Máy B / Server 2 |
|---|---|---|
| File | config/server1.properties | config/server2.properties |
| server.id | SERVER_1 | SERVER_2 |
| advertise.host | 192.168.1.10 | 192.168.1.20 |
| registry.port | 1099 | 1098 |
| object.port | 1101 | 1102 |
| peer.host | 192.168.1.20 | 192.168.1.10 |
| peer.registry.port | 1098 | 1099 |
| mongo.uri | mongodb://127.0.0.1:27017/?serverSelectionTimeoutMS=2000&connectTimeoutMS=2000&socketTimeoutMS=3000 | mongodb://127.0.0.1:27017/?serverSelectionTimeoutMS=2000&connectTimeoutMS=2000&socketTimeoutMS=3000 |
| mongo.database | lab8_server1 | lab8_server2 |

Lưu ý: trên **hai máy thật**, mỗi máy có MongoDB cổng 27017 riêng. Cổng 27018 trong file mẫu Server 2 chỉ dành cho thử hai instance trên một máy.

Có thể sao chép thành `config/server1.local.properties` rồi chạy:

```powershell
.\run.ps1 server1 -Config config/server1.local.properties
```

File `*.local.properties` được Git bỏ qua, dùng cho cấu hình cá nhân và URI có mật khẩu. Khi đường dẫn cấu hình có khoảng trắng, đặt toàn bộ đường dẫn trong dấu nháy kép ở tham số `-Config`; ưu tiên giữ file trong `config/` với tên không có khoảng trắng.

Máy ảo đặt mạng **Bridged Adapter / External Switch** để truy cập được cả hai IP LAN. Client 1 nhập IP máy A, cổng 1099; Client 2 nhập IP máy B, cổng 1098. Client không cần MongoDB.

Chỉ mở các cổng RMI cần thiết trên mạng Private. Chạy PowerShell quản trị trên máy A:

```powershell
New-NetFirewallRule -DisplayName "Lab8 RMI Server1" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 1099,1101 -Profile Private -RemoteAddress LocalSubnet
```

Máy B tương tự với `-LocalPort 1098,1102`. Không cần mở MongoDB cho máy ảo hoặc máy peer, vì mỗi server dùng MongoDB cục bộ. RMI Registry trả về địa chỉ remote object, do đó `advertise.host` phải là IP có thể truy cập từ client và peer, không dùng localhost khi demo LAN.

## Kịch bản demo 5–7 phút

1. Mở Server 1, Server 2 và hai client. Đợi trạng thái Peer ONLINE trên hai server.
2. Client 1 thêm `24IT027`, tên, email, ngành và GPA 3.5. Quan sát Server 2 và Client 2 tự hiển thị, không bấm tải lại.
3. Client 2 chọn sinh viên, sửa GPA thành 3.9. Quan sát phía Server 1.
4. Xóa sinh viên và xác nhận. Kiểm tra cả hai phía không còn bản ghi.
5. Thêm lại sinh viên. Tắt Server 2, đợi Peer OFFLINE; tại Client 1 sửa dữ liệu, thêm sinh viên khác và xóa một bản ghi. Số sự kiện chờ gửi tăng lên.
6. Bật lại Server 2. Quan sát tự đồng bộ bù cả thêm/sửa/xóa, số chờ gửi về 0. Sau đó đóng mở cả hai server để chứng minh dữ liệu tồn tại.
7. Mở Compass trên từng máy, kết nối MongoDB cục bộ. Vào `lab8_server1` và `lab8_server2`: view `students` thể hiện trạng thái hiện tại; collection `events` chứa sự kiện, `origin`, `version`, `deleted`, `acked`. Làm mới Compass để kiểm chứng riêng, độc lập với giao diện ứng dụng.

## Cách đồng bộ và giải thích khi vấn đáp

Luồng: **Client 1 → RMI Server 1 → MongoDB 1 → RMI Server 2 → MongoDB 2 → Client 2 tự tải trạng thái**. Chiều ngược lại giống hệt.

Một sự kiện bất biến có UUID, server nguồn, bộ đếm Lamport, thời điểm tạo, nội dung sinh viên và cờ xóa. Ghi một document MongoDB là thao tác lưu bền vững duy nhất, đồng thời đóng vai trò outbox. Ghi dùng journaled write concern. Không có khoảng trống giữa lưu dữ liệu và lưu hàng đợi. View `students` dựng dữ liệu hiện tại từ các sự kiện; không duy trì thêm một bảng có thể lệch trạng thái.

Worker mỗi server chạy cách nhau 300 ms: gửi sự kiện local chưa ACK, peer ghi bền vững rồi mới trả lời; nguồn mới đánh dấu ACK. Nếu phản hồi bị mất, gửi lại cùng event ID và upsert `$setOnInsert` không ghi trùng. Mỗi vòng còn trao đổi lịch sử để bù sự kiện bị thiếu. Sự kiện nhận từ peer không được phát sinh thành sự kiện mới nên không gây vòng lặp.

Xóa là **tombstone** được giữ trong lịch sử. Khi nhập lại lịch sử cũ, tombstone có phiên bản cao hơn vẫn thắng. Có thể thêm lại cùng mã bằng một sự kiện mới có phiên bản cao hơn.

Quy tắc xung đột: phiên bản Lamport cao hơn thắng; nếu bằng nhau, so sánh event ID theo thứ tự chuỗi. Các bên có cùng tập sự kiện luôn dựng ra cùng kết quả, bất kể thứ tự nhận. Đây là lựa chọn xác định để hội tụ, **không bảo đảm thay đổi xảy ra sau theo đồng hồ thực sẽ thắng** khi hai server cùng mất kết nối. Một thay đổi có thể bị thay thế khi hòa giải xung đột; hệ thống không cung cấp giao dịch phân tán hoặc tính nhất quán mạnh.

Client/server JavaFX tải snapshot mỗi 400 ms. Tác vụ mạng và MongoDB chạy nền. Mục tiêu quan sát trên LAN bình thường khoảng một giây, phụ thuộc mạng và tải máy. ACK hiển thị là thời gian từ lúc gọi peer đến khi ghi xong và nhận xác nhận, **không phải toàn bộ độ trễ đến màn hình client**. Màu xanh trong bảng đánh dấu vòng cập nhật vừa nhận. Server 1 và Server 2 có màu tiêu đề riêng.

Giới hạn dành cho phạm vi bài lab: trao đổi toàn bộ lịch sử mỗi vòng, chưa phân trang hay dọn lịch sử; phù hợp dữ liệu demo nhỏ. Hai server phải có ID riêng và database riêng. Không sửa trực tiếp `students` hoặc `events` trong Compass để tạo thay đổi: thao tác đồng bộ phải đi qua ứng dụng. Mạng demo được tin cậy; chưa có đăng nhập, TLS và phân quyền cho RMI.

## Kiểm thử

```powershell
.\mvnw.cmd test
# Cần hai MongoDB độc lập đang chạy ở 27017 và 27018:
.\mvnw.cmd test -Dlab8.integration=true
```

Test tích hợp gọi qua stub RMI thực, ghi MongoDB thực: thêm/sửa hai chiều, sự kiện trùng, server offline, xóa, khởi động lại cả hai server, gửi bù và thêm lại sau xóa. Database kiểm thử có tên UUID riêng và được xóa sau bài kiểm tra, không dùng database demo. Test mặc định bỏ qua tích hợp nếu chưa bật cờ. Kết quả xác minh của đợt nâng cấp nằm trong `VALIDATION.md`.

## Cấu trúc

- `src/main/java/common`: hợp đồng RMI, đọc cấu hình.
- `model`: dữ liệu trao đổi và snapshot.
- `dao`: MongoDB event store và view sinh viên.
- `server`: xử lý ghi, worker đồng bộ, vòng đời RMI.
- `client`, `ui`: khởi chạy client và JavaFX dùng chung.
- `src/test/java`: kiểm thử thuật toán và tích hợp.

Dự án thay thế mã Swing/MySQL/CSV trước đây bằng Maven + JavaFX + MongoDB. Chưa tự di chuyển dữ liệu CSV/MySQL cũ; chúng không được tải vào database mới.

Tài liệu nền tảng: [MongoDB Java driver](https://www.mongodb.com/docs/drivers/java/sync/current/get-started/), [JavaFX](https://openjfx.io/openjfx-docs/).
