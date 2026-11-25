/**
 * =========================================================================
 * HUNTER OF THE SEA - CẤU HÌNH HỆ THỐNG & KẾT NỐI SERVER ONLINE CỐ ĐỊNH
 * =========================================================================
 * File cấu hình trung tâm cho cả bản Desktop tải về (Windows) và Web.
 * 
 * HƯỚNG DẪN KHI DEPLOY SERVER ONLINE:
 * 1. Deploy backend lên Cloud (Render, Railway, Fly.io, VPS...) -> Nhận URL (ví dụ: https://hunter-pvp.onrender.com)
 * 2. Thay thế giá trị tại ONLINE_SERVER_URL bên dưới bằng URL của bạn.
 * 3. Đóng gói hoặc gửi game cho bạn bè -> Cả hai máy sẽ tự động kết nối chung server mà KHÔNG CẦN BẬT TUNNEL!
 */

(function () {
    const HunterConfig = {
        // Mã phiên bản ứng dụng hiển thị trên giao diện (Lobby HUD, PvP badge)
        VERSION: 'v2.6.0-DESKTOP',
        APP_NAME: 'Hunter of the Sea',

        /**
         * 🌐 ĐỊA CHỈ SERVER ONLINE CỐ ĐỊNH (CLOUD SERVER DOMAIN)
         * -------------------------------------------------------------
         * Khi triển khai server online cố định (Render, Railway, Koyeb, VPS...),
         * bạn chỉ cần điền domain của server vào đây (giữ nguyên https://).
         * 
         * Ví dụ thật:
         *   'https://hunter-sea-server.onrender.com'
         *   hoặc 'https://hunter-pvp.up.railway.app'
         * 
         * NẾU GIỮ NGUYÊN PLACEHOLDER HOẶC TRỐNG:
         *   Hệ thống sẽ tự động kích hoạt chế độ Fallback:
         *   - Chạy trên localhost:8080 (khi bật server cục bộ)
         *   - Chạy P2P Offline Browser Bus (khi test 2 tab trên cùng máy)
         */
        ONLINE_SERVER_URL: 'https://hunter-of-the-sea.onrender.com',

        /**
         * Tùy chọn nâng cao (nếu muốn cấu hình riêng rẽ API và WebSocket):
         * Để null hoặc '' để hệ thống tự động suy ra từ ONLINE_SERVER_URL.
         */
        API_URL: null, // Mặc định tự động: ONLINE_SERVER_URL + '/api'
        WS_URL: null,  // Mặc định tự động: wss://<domain>/ws/pvp (hoặc ws://)

        /**
         * 🔄 CHẾ ĐỘ DỰ PHÒNG CỤC BỘ (LOCAL FALLBACK)
         * Khi không có kết nối tới server online, tự động kết nối localhost
         */
        ENABLE_LOCAL_FALLBACK: true,
        LOCAL_API_BASE: 'http://localhost:8080/api',
        LOCAL_WS_BASE: 'ws://localhost:8081',

        // Thời gian chờ kiểm tra kết nối server (ms)
        HEALTH_CHECK_TIMEOUT_MS: 2500,

        // =====================================================================
        // CÁC HÀM TIỆN ÍCH TRÍCH XUẤT VÀ TỰ ĐỘNG CHUẨN HÓA URL
        // =====================================================================

        /**
         * Kiểm tra xem URL hiện tại có phải là placeholder hay không
         */
        isPlaceholder(url) {
            if (!url || typeof url !== 'string') return true;
            const cleaned = url.trim().toLowerCase();
            return (
                cleaned === '' ||
                cleaned.includes('placeholder') ||
                cleaned.includes('your-server-domain') ||
                cleaned === 'null' ||
                cleaned === 'undefined'
            );
        },

        /**
         * Xác định địa chỉ REST API Base (ví dụ: https://domain.com/api)
         */
        getApiBase() {
            // 1. Nếu có chỉ định rõ API_URL riêng biệt và không phải placeholder
            if (this.API_URL && !this.isPlaceholder(this.API_URL)) {
                return this.API_URL.replace(/\/+$/, '');
            }

            // 2. Nếu có cấu hình ONLINE_SERVER_URL cố định
            if (this.ONLINE_SERVER_URL && !this.isPlaceholder(this.ONLINE_SERVER_URL)) {
                const base = this.ONLINE_SERVER_URL.replace(/\/+$/, '');
                return `${base}/api`;
            }

            // 3. Nếu đang chạy trên web server cục bộ (http://localhost:... hoặc tunnel)
            if (typeof window !== 'undefined' && window.location && window.location.protocol.startsWith('http')) {
                const loc = window.location;
                // Nếu đang mở cổng 3000 (web client), backend local thường ở 8080
                if (loc.port === '3000' || loc.port === '5500' || loc.port === '28765') {
                    return this.LOCAL_API_BASE;
                }
                return `${loc.protocol}//${loc.host}/api`;
            }

            // 4. Mặc định dự phòng máy chủ cục bộ
            return this.LOCAL_API_BASE;
        },

        /**
         * Xác định địa chỉ WebSocket theo mã phòng (ví dụ: wss://domain.com/ws/pvp/1234)
         */
        getWsUrl(roomCode) {
            const cleanCode = encodeURIComponent(String(roomCode || ''));

            // 1. Nếu có chỉ định rõ WS_URL riêng biệt
            if (this.WS_URL && !this.isPlaceholder(this.WS_URL)) {
                const base = this.WS_URL.replace(/\/+$/, '');
                return `${base}/ws/pvp/${cleanCode}`;
            }

            // 2. Nếu có ONLINE_SERVER_URL cố định -> tự chuyển https: thành wss: (hoặc http: thành ws:)
            if (this.ONLINE_SERVER_URL && !this.isPlaceholder(this.ONLINE_SERVER_URL)) {
                let wsBase = this.ONLINE_SERVER_URL.replace(/\/+$/, '');
                if (wsBase.startsWith('https://')) {
                    wsBase = 'wss://' + wsBase.substring('https://'.length);
                } else if (wsBase.startsWith('http://')) {
                    wsBase = 'ws://' + wsBase.substring('http://'.length);
                }
                return `${wsBase}/ws/pvp/${cleanCode}`;
            }

            // 3. Nếu đang chạy trên web server cục bộ
            if (typeof window !== 'undefined' && window.location && window.location.protocol.startsWith('http')) {
                const loc = window.location;
                const isSecure = loc.protocol === 'https:';
                const protocol = isSecure ? 'wss:' : 'ws:';
                const hostname = loc.hostname || 'localhost';

                // Nếu chạy qua tunnel hoặc reverse proxy gom 1 cổng:
                if (loc.port === '8081') {
                    return `${protocol}//${hostname}:8081/ws/pvp/${cleanCode}`;
                }
                if (loc.port === '3000' || loc.port === '5500' || loc.port === '28765') {
                    return `${this.LOCAL_WS_BASE}/ws/pvp/${cleanCode}`;
                }
                const portPart = loc.port ? `:${loc.port}` : '';
                return `${protocol}//${hostname}${portPart}/ws/pvp/${cleanCode}`;
            }

            // 4. Mặc định dự phòng cục bộ
            return `${this.LOCAL_WS_BASE}/ws/pvp/${cleanCode}`;
        },

        /**
         * Cập nhật hiển thị mã phiên bản và trạng thái kết nối trên giao diện
         */
        applyVersionToUi(connectionStatus) {
            if (typeof document === 'undefined') return;

            // 1. Cập nhật Lobby Version Tag
            const badge = document.getElementById('app-version-badge');
            if (badge) {
                const verText = badge.querySelector('.ver-text') || badge;
                let statusLabel = '';
                let statusColor = '#00ff87'; // xanh lá (online cloud)

                if (connectionStatus === 'CLOUD_ONLINE') {
                    statusLabel = ' • Cloud Server';
                    statusColor = '#00ff87';
                } else if (connectionStatus === 'LOCAL_ONLINE') {
                    statusLabel = ' • Localhost:8080';
                    statusColor = '#ffb703'; // vàng
                } else if (connectionStatus === 'OFFLINE_BUS') {
                    statusLabel = ' • Offline P2P';
                    statusColor = '#90e0ef'; // xanh lơ
                }

                if (verText.querySelector) {
                    const txtSpan = verText.querySelector('.ver-text');
                    if (txtSpan) txtSpan.textContent = `${this.VERSION}${statusLabel}`;
                } else {
                    verText.textContent = `${this.VERSION}${statusLabel}`;
                }

                const dot = badge.querySelector('.ver-dot');
                if (dot) dot.style.background = statusColor;
            }

            // 2. Cập nhật các badge phiên bản khác (nếu có trong HUD hoặc PvP modal)
            document.querySelectorAll('.pvp-version-badge, .hud-version-label').forEach(el => {
                el.textContent = this.VERSION;
            });
        }
    };

    // Đưa cấu hình ra global scope
    if (typeof window !== 'undefined') {
        window.HUNTER_CONFIG = HunterConfig;
        window.HunterConfig = HunterConfig;

        // Tự động gán phiên bản khi DOM sẵn sàng
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => HunterConfig.applyVersionToUi());
        } else {
            HunterConfig.applyVersionToUi();
        }
    }

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = HunterConfig;
    }
})();
