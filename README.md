# BÁO CÁO & HƯỚNG DẪN DEMO LAB 5: RMI ĐỒNG BỘ HÓA DỮ LIỆU ĐA SERVER (LAB 8 BÀI 2)
**Học phần**: Lập trình mạng (VKU)  
**Tiêu chuẩn**: Kiến trúc Gọi hàm từ xa Java RMI (Remote Method Invocation), Đồng bộ hóa thời gian thực (Replication) giữa 2 hoặc 3 Server, Client quản lý phân tán.

---

## 📌 1. CẤU TRÚC DỰ ÁN
```
Lab5_RMISync/
├── src/
│   ├── common/
│   │   ├── IDatabaseSyncService.java   (Interface RMI Remote định nghĩa các hàm: insert, update, delete, replicate, fullSync)
│   │   ├── StudentRecord.java          (Mô hình dữ liệu Sinh viên phân tán, implements Serializable)
│   │   └── SyncAction.java             (Enum hành động đồng bộ: INSERT, UPDATE, DELETE)
│   ├── server/
│   │   ├── DatabaseSyncServiceImpl.java (Lớp triển khai RMI, lưu trữ CSDL cục bộ và phát Replication sang các Server khác)
│   │   └── RMIServerApp.java            (Giao diện máy chủ, hiển thị trạng thái Heartbeat của các máy chủ khác trong cụm)
│   └── client/
│       └── RMIClientApp.java           (Giao diện Client: Thêm/Sửa/Xóa dữ liệu, chọn kết nối Server bất kỳ, đối chiếu dữ liệu)
├── compile.bat                         (Lệnh biên dịch toàn bộ bằng javac)
├── run_server1.bat                     (Chạy Server 1 trên cổng 1099)
├── run_server2.bat                     (Chạy Server 2 trên cổng 1098)
├── run_server3.bat                     (Chạy Server 3 trên cổng 1097)
├── run_all_servers.bat                 (Tự động mở cùng lúc cả 3 Server để demo tiện lợi)
├── run_client.bat                      (Khởi chạy Client RMI)
└── README.md
```

---

## 🚀 2. KỊCH BẢN DEMO ĂN ĐIỂM TUYỆT ĐỐI VỚI THẦY

### Bước 1: Khởi động Cụm Server (2 hoặc 3 Server)
- Cách 1 (Nhanh nhất trên 1 máy tính): Chạy `run_all_servers.bat` để mở cả 3 Server RMI:
  - **Server 1** chạy tại cổng `1099`.
  - **Server 2** chạy tại cổng `1098`.
  - **Server 3** chạy tại cổng `1097`.
- Cách 2 (Chạy trên 2 máy thực hoặc máy ảo như đề bài):
  - Máy A chạy `run_server1.bat` (Server 1).
  - Máy B chạy `run_server2.bat` (Server 2, chỉ cần chỉnh IP của máy A).
- Trên giao diện của mỗi Server: Đèn báo Heartbeat của các Server bạn bè sẽ chuyển sang màu **Xanh [Online]**.

### Bước 2: Khởi động Client và Kiểm thử Đồng bộ
1. Chạy `run_client.bat`.
2. Mặc định Client kết nối tới **Server 1 (Cổng 1099)**.
3. Nhập form thông tin sinh viên mới:
   - Mã SV: `24IT999`
   - Họ và tên: `Nguyễn Văn An`
   - Chuyên ngành: `Công nghệ thông tin`
   - Điểm GPA: `3.90`
   - Bấm **"➕ Thêm mới"**.
4. Quan sát:
   - Client hiển thị bản ghi vừa thêm.
   - Server 1 in log: `[CLIENT CALL] Thêm mới -> Đã đồng bộ sang Server 2, Server 3`.
   - Server 2 và Server 3 lập tức in log: `[RMI REPLICATION] << Nhận lệnh INSERT từ SERVER_1...` và bảng dữ liệu của Server 2, 3 tự động nhảy thêm sinh viên đó ngay lập tức!

### Bước 3: Đổi Client sang Server khác để kiểm chứng
1. Trên Client, ở ô dropdown "Chọn Server RMI", chọn **Server 2 (Cổng 1098)** -> Bấm **"Kết nối RMI"**.
2. Thấy toàn bộ dữ liệu ở Server 2 y hệt Server 1!
3. Thực hiện sửa điểm GPA hoặc xóa một sinh viên trên Server 2 -> Lập tức Server 1 và Server 3 cũng được đồng bộ hóa theo.

### Bước 4: Bấm nút "🔍 Đối chiếu dữ liệu cả 3 Server"
- Client sẽ đồng thời gọi hàm RMI tới cả 3 server, đếm số bản ghi và so sánh.
- Màn hình bật thông báo: `KẾT LUẬN: DỮ LIỆU ĐỒNG BỘ 100% HOÀN TOÀN GIỮA CÁC MÁY CHỦ!`.

---

## ❓ 3. CÂU HỎI VẤN ĐÁP CỦA THẦY VỀ RMI

**Câu 1: Cơ chế RMI (Remote Method Invocation) là gì và khác gì Socket thông thường?**
- **Trả lời**: RMI là cơ chế gọi thủ tục từ xa ở tầng ứng dụng cấp cao trong Java:
  - Thay vì lập trình viên phải tự mở Socket TCP, tuần tự hóa chuỗi ký tự thủ công và phân tích cú pháp (parse string), RMI cho phép Client gọi trực tiếp một phương thức của đối tượng nằm trên máy chủ từ xa giống hệt như đang gọi hàm cục bộ.
  - RMI sử dụng cơ chế **Stub (phía Client)** và **Skeleton (phía Server)** để tự động đóng gói (Marshalling) và giải nén (Unmarshalling) tham số qua mạng.

**Câu 2: Hệ thống xử lý đồng bộ đa server như thế nào để tránh lặp vô tận (Loop replication)?**
- **Trả lời**:
  - Khi Server A nhận lời gọi từ Client, nó gắn nhãn nguồn gốc `originServer = SERVER_A`.
  - Khi Server A phát Replication sang Server B, nó truyền kèm ID người gửi (`senderServerId = SERVER_A`).
  - Server B khi nhận lệnh `replicate()`, nó chỉ cập nhật vào CSDL cục bộ của mình mà **không** phát ngược lại cho Server A, giúp ngăn chặn triệt để hiện tượng vòng lặp đồng bộ vô hạn.
