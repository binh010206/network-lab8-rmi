# Xác minh ngày 03/11/2026

Môi trường: Windows, Temurin JDK 21.0.8, JavaFX 21.0.2, MongoDB Java Driver 4.11.1, hai MongoDB 8.0.4 standalone (27017 / 27018).

- Biên dịch Maven thành công.
- Bộ kiểm tra gồm 3 bài: kiểm tra dữ liệu nhập, hội tụ xung đột/tombstone và tích hợp MongoDB + RMI.
- Chạy với `-Dlab8.integration=true`: 3 passed, 0 failures, 0 errors, 0 skipped.
- Tích hợp gọi qua RMI Registry và stub thật; hai database kiểm thử tách biệt trên hai tiến trình MongoDB.
- Đã kiểm tra thêm ở Server 1, sửa ở Server 2, nhận sự kiện trùng, sửa/thêm/xóa khi peer offline, khởi động lại hai server, gửi bù, thêm lại sau xóa và truy vấn view `students` trực tiếp trong MongoDB.
- Script `run.ps1 client` khởi chạy và giữ tiến trình JavaFX hoạt động, không ghi lỗi khởi động.
- Chưa xác minh trực quan giao diện: quyền xem cửa sổ qua công cụ computer-use hết thời gian chờ.
- Chưa chạy thử trên hai máy vật lý hoặc các máy ảo thật. Các bài tích hợp hiện chạy trên một máy qua TCP loopback. Cần thay IP và kiểm tra tường lửa theo README trước buổi demo LAN.
- Chưa đo độ trễ đầu cuối trên LAN; con số ACK trong giao diện chỉ đo vòng gửi sự kiện và nhận xác nhận.

CI GitHub được cấu hình chạy lại bộ test tích hợp bằng hai dịch vụ MongoDB trên mỗi push/PR; việc thêm cấu hình CI không đồng nghĩa CI từ xa đã chạy thành công.
