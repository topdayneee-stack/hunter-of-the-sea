# HUNTER OF THE SEA - HƯỚNG DẪN KIỂM THỬ TỔNG THỂ (PHASE 1 - 5)

---

## 🌊 TỔNG QUAN HỆ THỐNG
Dự án **Hunter of the Sea** (Cá Lớn Nuốt Cá Bé Web & PvP Online) đã hoàn thiện trọn vẹn qua 5 giai đoạn kiến trúc:
- **Phase 1:** Hệ thống Tài khoản, Sảnh chờ, Sinh mã phòng 4 số ngẫu nhiên không trùng lặp và Vòng đời phòng đấu.
- **Phase 2:** Cơ chế điều khiển chuột (Mouse-Aim), Kênh truyền thời gian thực hai chiều (WebSocket + BroadcastChannel độ trễ <1ms), Va chạm ăn nuốt & húc nhau dội ngược.
- **Phase 3:** Thanh Mana (0 - 5.0) phát sáng khi đầy, phím [Q] kích hoạt kỹ năng Thủy Thần, 2 ô kỹ năng nhặt được [W] và [E] (quy tắc W $\rightarrow$ E $\rightarrow$ ghi đè E).
- **Phase 4:** Chuyên sâu 3 chiêu thức đặc thù: Tôm (Dịch chuyển định hướng chống kẹt biên), Cua (Phóng càng sắt găm kéo mục tiêu), Cá Ép (Lao bám ký sinh mượn sức mạnh & chủ động tách ra).
- **Phase 5:** Xử lý toàn diện Edge Cases (chuột sát tâm cá, biên map, rớt mạng, đấu lại Rematch vs Về sảnh), Tinh chỉnh cân bằng PvP 7 Thủy Thần, Tối ưu gói tin di chuyển Delta Check và hoàn thiện Responsive UI.

---

## 🚀 CÁCH KHỞI CHẠY ĐÚNG (SERVER / LOCALHOST)

> [!IMPORTANT]
> **Không mở game bằng cách double-click file trực tiếp (`file:///.../index.html`)** vì các trình duyệt hiện đại (Chrome, Edge, Firefox) sẽ chặn CORS API, cô lập `sessionStorage`/`localStorage` và vô hiệu hóa `BroadcastChannel` giữa các Tab!

### Cách 1: Khởi Chạy Nhanh Bằng Server Tích Hợp (Khuyên Dùng - 1 Click)
- **Nhấp đúp chuột vào file `run_web.bat`** (hoặc chọn mục **[5]** trong `start_all.bat`).
- Script sẽ tự động khởi động một máy chủ HTTP nhẹ nhàng trên cổng `3000` (sử dụng PowerShell có sẵn trên mọi máy Windows, không cần cài đặt thêm bất kỳ phần mềm nào) và tự động mở trình duyệt tới địa chỉ:
  👉 **`http://localhost:3000`**

### Cách 2: Khởi Chạy Bằng VS Code Live Server
- Mở thư mục dự án trong VS Code.
- Nhấp chuột phải vào file `index.html` và chọn **"Open with Live Server"** (hoặc nhấn tổ hợp phím `Alt + L, Alt + O`).
- Trình duyệt sẽ mở tại `http://127.0.0.1:5500`.

### Cách 3: Khởi Chạy Với Backend Java (Nếu Máy Đã Cài Java JDK)
```bash
# Khởi động Backend API:
.\run_backend.bat
# Hoặc chạy toàn bộ hệ thống:
.\start_all.bat
```
Sau đó mở trình duyệt truy cập: `http://localhost:8080`

---

## ✅ CHECKLIST KIỂM THỬ TOÀN DIỆN 5 GIAI ĐOẠN

### 1. Phase 1: Tài Khoản & Sảnh Đối Kháng PvP Mã 4 Số
- [ ] Mở trang chủ, nhấp nút **"ĐỔI THỦY THỦ"** $\rightarrow$ Thử đổi tên thành `Player A` và `Player B` (hoặc dùng 2 nút chọn nhanh).
- [ ] Chọn PvP $\rightarrow$ Nhấp **"TẠO PHÒNG MỚI"**:
  - Mã 4 số được sinh ngẫu nhiên trong khoảng `1000 - 9999`.
  - Nút **"Sao Chép Mã"** copy mã vào clipboard thành công.
  - Trạng thái phòng hiển thị: `⏳ ĐANG CHỜ ĐỐI THỦ NHẬP MÃ (1/2)...`
- [ ] Thử nhập mã sai / không đủ 4 chữ số $\rightarrow$ Báo lỗi `Vui lòng nhập đầy đủ mã phòng 4 chữ số!`.
- [ ] Thử nhập mã phòng không tồn tại $\rightarrow$ Báo lỗi `Mã phòng không tồn tại hoặc phòng đã bị đóng!`.
- [ ] Mở Tab 2, nhập đúng mã 4 số $\rightarrow$ Phòng cập nhật tức thì `🟢 CẢ 2 ĐÃ SẴN SÀNG! (2/2)`.
- [ ] Nhấp **"BẮT ĐẦU TRẬN ĐẤU"** $\rightarrow$ Cả 2 màn hình đồng loạt chuyển vào game canvas.

### 2. Phase 2: Mouse-Aim & Realtime Sync 2 Người Chơi
- [ ] Rê chuột khắp màn hình: Cá bơi liên tục hướng theo trỏ chuột, góc bơi mượt mà không bị giật.
- [ ] Rê chuột sát tâm cá ($< 22\text{px}$): Vùng đệm Deadzone kích hoạt, cá không bị xoay tròn rung lắc.
- [ ] Bơi ra 4 cạnh góc của bản đồ $1920 \times 1080$: Vùng đệm mềm trượt bờ tường hoạt động chuẩn, cá không bị bay ra ngoài map.
- [ ] Bơi ăn cá mồi nhỏ $\rightarrow$ Kích thước cá tăng dần theo điểm ăn, đối thủ thấy cá mồi biến mất tức thì.
- [ ] Va chạm 2 người chơi:
  - Bằng cấp kích thước $\rightarrow$ Hai bên húc nhau dội ngược ra, sinh bọt nước trắng.
  - Chênh lệch cấp kích thước $\rightarrow$ Cá to nuốt chửng cá nhỏ, hiện bảng chiến thắng/thất bại.

### 3. Phase 3: Thanh Mana & Kỹ Năng [Q] - [W] - [E]
- [ ] Ăn 5 cá mồi nhỏ $\rightarrow$ Thanh Mana tăng từ $0.0 \rightarrow 5.0$. Khi đạt 5.0, thanh Mana và ô `[Q]` phát sáng vàng rực.
- [ ] Nhấn phím **Q** khi đầy Mana $\rightarrow$ Mana tiêu hao về $0.0$, kỹ năng kích hoạt hướng về con trỏ chuột.
- [ ] Sau 4.5s không ăn cá $\rightarrow$ Mana giảm nhẹ $0.35\text{/s}$.
- [ ] Bơi chạm vào các viên ngọc phát sáng trên biển $\rightarrow$ Nạp vào ô [W] $\rightarrow$ Nhặt viên tiếp theo nạp vào ô [E].
- [ ] Khi đã đầy cả 2 ô [W] và [E], nhặt tiếp viên thứ 3 $\rightarrow$ Ghi đè vào ô [E], giữ nguyên ô [W].
- [ ] Nhấn phím **W** hoặc **E** $\rightarrow$ Kỹ năng thi triển theo chuột và tiêu hao ngay (ô trở về Trống).

### 4. Phase 4: Kỹ Năng Chuyên Sâu Tôm, Cua, Cá Ép
- [ ] **Tôm (Q - Dịch Chuyển Định Hướng):**
  - Trỏ chuột vào khoảng cách xa $\rightarrow$ Bấm Q: Tôm dịch chuyển tới đúng hướng chuột (tối đa 360px), bung sóng chấn tại điểm đi và lóe sáng tại điểm đến.
  - Trỏ chuột sát bờ tường $\rightarrow$ Bấm Q: Tôm dịch chuyển an toàn cách mép tường 35px, không kẹt ngoài map.
- [ ] **Cua (Q - Phóng Càng Sắt Bắt Mồi):**
  - Trỏ chuột vào cá mồi xa $\rightarrow$ Bấm Q: Càng sắt và sợi dây xích phóng vút ra (450px), kẹp dính con cá và kéo giật về miệng Cua ăn thịt.
  - Trong PvP: Bắn trúng đối thủ $\rightarrow$ Đối thủ bị choáng khựng lại và bị kéo sát vào Cua.
- [ ] **Cá Ép (Q - Lao Bám Ký Sinh):**
  - Hướng chuột vào cá lớn hơn $\rightarrow$ Bấm Q: Cá Ép lao bứt tốc $460\text{px/s}$, chạm cá lớn sẽ bám chặt vào lưng.
  - Khi đang bám: Được bảo vệ bất tử, ô Q đổi thành **"TÁCH RA"**.
  - Nhấn phím **Q** lần nữa $\rightarrow$ Tách khỏi cá chủ và nhận cú lướt nhẹ tẩu thoát an toàn (không tốn mana).

### 5. Phase 5: Edge Cases, Đấu Lại (Rematch) & Chống Rớt Mạng
- [ ] **Chuột sát tâm cá ($< 8\text{px}$):** Nhấn Q, W, E khi chuột nằm ngay tại tâm cá $\rightarrow$ Kỹ năng tự động thi triển theo hướng bơi hiện tại, không phát sinh lỗi `NaN`.
- [ ] **Tái Đấu (Rematch):**
  - Sau khi kết thúc trận, Player A bấm **"CHƠI LẠI"** $\rightarrow$ Nút chuyển thành `⏳ ĐANG CHỜ ĐỐI THỦ...`.
  - Player B thấy thông báo và bấm **"CHẤP NHẬN TÁI ĐẤU"** $\rightarrow$ Cả hai được reset trạng thái sạch sẽ, đặt lại vị trí 2 góc và bắt đầu trận tái đấu ngay tại phòng cũ.
- [ ] **Rớt mạng / Thoát giữa chừng:**
  - Player A tắt tab hoặc bấm "VỀ SẢNH CHÍNH" $\rightarrow$ Player B nhận thông báo đối thủ đã ngắt kết nối và nhận chiến thắng, nút Đấu Lại tự động khóa.
