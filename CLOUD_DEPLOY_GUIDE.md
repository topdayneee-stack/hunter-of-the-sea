# 🌊 HƯỚNG DẪN TRIỂN KHAI SERVER ONLINE CỐ ĐỊNH (CLOUD DEPLOY)
### DỰ ÁN: HUNTER OF THE SEA (PVP ONLINE & DESKTOP APP)

Tài liệu này hướng dẫn chi tiết từng bước đưa Server game lên Internet cố định (vĩnh viễn) mà **không cần mở Cloudflare Tunnel hay Ngrok thủ công mỗi lần chơi**. Bạn bè chỉ cần tải bản game Windows về là kết nối ngay!

---

## 🚀 CÁCH 1: TRIỂN KHAI LÊN RENDER.COM (KHUYÊN DÙNG - 100% MIỄN PHÍ)

Render cung cấp máy chủ Web Service miễn phí, tự động cấp tên miền HTTPS và WebSocket Secure (`wss://`) bảo mật.

### Bước 1: Chuẩn bị mã nguồn
1. Đẩy thư mục dự án lên **GitHub** (tài khoản cá nhân của bạn).
2. Đảm bảo trong repo có thư mục `server/` chứa `server.js` và `package.json`.

### Bước 2: Tạo Web Service trên Render
1. Truy cập [https://render.com](https://render.com) và đăng nhập (bằng tài khoản GitHub).
2. Nhấn nút **New +** ở góc trên bên phải $\rightarrow$ Chọn **Web Service**.
3. Chọn kho lưu trữ GitHub của bạn: `hunter-of-the-sea`.
4. Điền các thông số như sau:
   - **Name:** `hunter-sea-server` (hoặc tên tùy thích)
   - **Region:** `Singapore` (để ping thấp nhất cho người chơi tại Việt Nam)
   - **Branch:** `main` (hoặc branch bạn đang dùng)
   - **Root Directory:** `server`
   - **Runtime:** `Node`
   - **Build Command:** `npm install || true`
   - **Start Command:** `node server.js`
   - **Instance Type:** `Free` (0$/tháng)
5. Nhấn **Create Web Service**.

### Bước 3: Lấy URL Server Online
Chờ khoảng 1 - 2 phút để Render build xong. Khi thấy trạng thái chuyển sang **`Live`**, bạn sẽ thấy URL máy chủ ở ngay dưới tên dự án, ví dụ:
👉 **`https://hunter-sea-server.onrender.com`**

> [!TIP]
> Bạn có thể kiểm tra nhanh server đã hoạt động chưa bằng cách mở URL đó trên trình duyệt. Nếu thấy JSON trả về `{"status":"ONLINE", "server":"Hunter of the Sea..."}` là thành công 100%!

---

## ⚡ CÁCH 2: TRIỂN KHAI LÊN RAILWAY.APP

1. Đăng nhập [https://railway.app](https://railway.app).
2. Nhấn **New Project** $\rightarrow$ **Deploy from GitHub repo** $\rightarrow$ Chọn repo game.
3. Vào phần **Settings** của Service:
   - Root Directory: `server`
   - Start Command: `node server.js`
4. Vào tab **Networking** $\rightarrow$ Nhấn **Generate Domain**.
5. Bạn sẽ nhận được domain cố định, ví dụ: `https://hunter-sea-production.up.railway.app`.

---

## 🐳 CÁCH 3: TRIỂN KHAI BẰNG DOCKER HOẶC VPS RIÊNG

Nếu bạn có VPS riêng (Ubuntu / Debian / CentOS):
```bash
# 1. Clone repo về VPS
git clone <repo_url>
cd "hunter of the sea"

# 2. Build Docker container
docker build -t hunter-sea-server .

# 3. Khởi chạy container trên cổng 8080 (hoặc cổng 80/443 với Reverse Proxy)
docker run -d -p 8080:8080 --name hunter-server --restart always hunter-sea-server

# 4. Kiểm tra container
curl http://localhost:8080/health
```

---

## 🔗 BƯỚC TIẾP THEO: ĐIỀN URL CỐ ĐỊNH VÀO GAME

Sau khi đã có URL server từ Render/Railway (ví dụ `https://hunter-sea-server.onrender.com`):

1. Mở file `js/config.js` trong thư mục game.
2. Tìm dòng:
   ```javascript
   ONLINE_SERVER_URL: 'https://PLACEHOLDER-YOUR-SERVER-DOMAIN.com',
   ```
3. Đổi thành URL thật của bạn:
   ```javascript
   ONLINE_SERVER_URL: 'https://hunter-sea-server.onrender.com',
   ```
4. Lưu file lại.
5. Bây giờ, cả game web lẫn bản desktop Windows sẽ tự động kết nối tới server này.
   - Giao diện góc trên bên phải sẽ hiển thị: **`v2.6.0-DESKTOP • Cloud Server`** kèm chấm xanh lá!
