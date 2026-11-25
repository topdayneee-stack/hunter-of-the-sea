/**
 * HUNTER OF THE SEA - REALTIME SYNC CHANNEL (PHASE 2)
 * Kênh truyền thời gian thực hai chiều (WebSocket Port 8081 + BroadcastChannel Fallback).
 * Mô hình: Host-Authoritative với Client-Side Interpolation (LERP).
 */

class RealtimeChannel {
    constructor(roomCode, currentUserId) {
        this.roomCode = String(roomCode);
        this.userId = currentUserId;
        this.ws = null;
        this.broadcastChannel = null;
        this.listeners = new Set();
        this.isConnected = false;

        this.init();
    }

    resolveWsUrl() {
        if (typeof window !== 'undefined' && window.HUNTER_CONFIG && typeof window.HUNTER_CONFIG.getWsUrl === 'function') {
            return window.HUNTER_CONFIG.getWsUrl(this.roomCode);
        }

        if (typeof window !== 'undefined') {
            if (window.HUNTER_CONFIG && window.HUNTER_CONFIG.WS_URL) {
                const base = window.HUNTER_CONFIG.WS_URL.replace(/\/+$/, '');
                return `${base}/ws/pvp/${this.roomCode}`;
            }

            const loc = window.location;
            if (loc && loc.protocol && loc.protocol.startsWith('http')) {
                const isSecure = loc.protocol === 'https:';
                const protocol = isSecure ? 'wss:' : 'ws:';
                const hostname = loc.hostname || 'localhost';
                // Nếu chạy qua tunnel có cổng riêng hoặc qua reverse proxy:
                const port = (loc.port === '8081') ? '' : ':8081';
                return `${protocol}//${hostname}${port}/ws/pvp/${this.roomCode}`;
            }
        }
        return `ws://localhost:8081/ws/pvp/${this.roomCode}`;
    }

    init() {
        // 1. Khởi tạo BroadcastChannel theo mã phòng (Độ trễ <1ms giữa các Tab trên cùng máy)
        if (typeof BroadcastChannel !== 'undefined') {
            try {
                this.broadcastChannel = new BroadcastChannel(`hos_room_bus_${this.roomCode}`);
                this.broadcastChannel.onmessage = (event) => {
                    const data = event.data;
                    if (data && data.senderId !== this.userId) {
                        this.dispatchMessage(data);
                    }
                };
            } catch (e) {
                console.warn('[RealtimeChannel] BroadcastChannel lỗi:', e);
            }
        }

        // 2. Thử kết nối WebSocket Server Realtime (Tự động suy đoán ws:// hoặc wss://)
        try {
            const wsUrl = this.resolveWsUrl();
            this.ws = new WebSocket(wsUrl);

            this.ws.onopen = () => {
                this.isConnected = true;
                console.log(`[RealtimeChannel] Đã kết nối WebSocket thành công tới phòng [${this.roomCode}] qua ${wsUrl}`);
            };

            this.ws.onmessage = (event) => {
                try {
                    const msg = JSON.parse(event.data);
                    if (msg && msg.senderId !== this.userId) {
                        this.dispatchMessage(msg);
                    }
                } catch (err) {
                    console.error('[RealtimeChannel] Lỗi parse WS message:', err);
                }
            };

            this.ws.onerror = () => {
                this.isConnected = false;
            };

            this.ws.onclose = () => {
                this.isConnected = false;
            };
        } catch (e) {
            console.log('[RealtimeChannel] Dùng LocalSyncChannel (Offline/Browser bus mode)');
        }
    }

    send(type, payload) {
        const packet = Object.assign({
            type,
            roomCode: this.roomCode,
            senderId: this.userId,
            timestamp: Date.now()
        }, payload);

        // Gửi qua WebSocket nếu đang online
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            try {
                this.ws.send(JSON.stringify(packet));
            } catch (e) {}
        }

        // Đồng thời broadcast cho các tab cục bộ
        if (this.broadcastChannel) {
            try {
                this.broadcastChannel.postMessage(packet);
            } catch (e) {}
        }
    }

    dispatchMessage(msg) {
        this.listeners.forEach(fn => {
            try { fn(msg); } catch (e) {}
        });
    }

    onMessage(callback) {
        if (typeof callback === 'function') {
            this.listeners.add(callback);
        }
    }

    close() {
        this.listeners.clear();
        if (this.ws) {
            try { this.ws.close(); } catch (e) {}
            this.ws = null;
        }
        if (this.broadcastChannel) {
            try { this.broadcastChannel.close(); } catch (e) {}
            this.broadcastChannel = null;
        }
    }
}

window.RealtimeChannel = RealtimeChannel;
