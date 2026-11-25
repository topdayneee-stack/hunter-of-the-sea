/**
 * HUNTER OF THE SEA - API & REALTIME SYNC LAYER (PHASE 1)
 * Hỗ trợ giao tiếp trực tiếp với Backend REST API (tự động suy ra origin / tunnel / IP từ xa)
 * và tự động chuyển đổi sang LocalSyncEngine (BroadcastChannel + LocalStorage) khi máy chủ Offline.
 */

const GameApi = (function () {
    // Tự động nhận diện cấu hình Endpoint Backend hoặc suy ra từ HunterConfig
    function resolveApiBase() {
        if (typeof window !== 'undefined' && window.HUNTER_CONFIG && typeof window.HUNTER_CONFIG.getApiBase === 'function') {
            return window.HUNTER_CONFIG.getApiBase();
        }
        if (typeof window !== 'undefined') {
            if (window.HUNTER_CONFIG && window.HUNTER_CONFIG.API_URL) {
                return window.HUNTER_CONFIG.API_URL.replace(/\/+$/, '');
            }
            const loc = window.location;
            if (loc && loc.protocol && loc.protocol.startsWith('http')) {
                return `${loc.protocol}//${loc.host}/api`;
            }
        }
        return 'http://localhost:8080/api';
    }

    let API_BASE = resolveApiBase();
    const LOCAL_STORAGE_ROOMS_KEY = 'hos_pvp_shared_rooms_v1';
    const LOCAL_STORAGE_USERS_KEY = 'hos_users_db_v1';
    const BROADCAST_CHANNEL_NAME = 'hos_pvp_bus_channel';

    let isServerOnline = false;
    let activeServerType = 'OFFLINE_BUS'; // 'CLOUD_ONLINE', 'LOCAL_ONLINE', 'OFFLINE_BUS'
    let broadcastChannel = null;
    const roomListeners = new Set();

    // Khởi tạo BroadcastChannel để đồng bộ realtime giữa các Tab trên cùng trình duyệt
    if (typeof BroadcastChannel !== 'undefined') {
        try {
            broadcastChannel = new BroadcastChannel(BROADCAST_CHANNEL_NAME);
            broadcastChannel.onmessage = (event) => {
                const msg = event.data;
                if (msg && msg.roomCode) {
                    notifyListeners(msg.roomCode, msg.room, msg.type);
                }
            };
        } catch (e) {
            console.warn('[GameApi] BroadcastChannel không khả dụng, dùng storage fallback:', e);
        }
    }

    // Lắng nghe thay đổi storage từ các tab khác
    window.addEventListener('storage', (event) => {
        if (event.key === LOCAL_STORAGE_ROOMS_KEY) {
            try {
                const rooms = JSON.parse(event.newValue || '{}');
                for (const code of Object.keys(rooms)) {
                    notifyListeners(code, rooms[code], 'STORAGE_SYNC');
                }
            } catch (e) {}
        }
    });

    function notifyListeners(roomCode, room, eventType) {
        roomListeners.forEach(listener => {
            try {
                listener(roomCode, room, eventType);
            } catch (err) {
                console.error('[GameApi] Lỗi trong room listener:', err);
            }
        });
    }

    function broadcastEvent(type, roomCode, room) {
        if (broadcastChannel) {
            broadcastChannel.postMessage({ type, roomCode, room });
        }
        notifyListeners(roomCode, room, type);
    }

    // Kiểm tra trạng thái máy chủ Backend & cập nhật UI
    async function checkServerHealth() {
        API_BASE = resolveApiBase();
        const timeoutMs = (window.HUNTER_CONFIG && window.HUNTER_CONFIG.HEALTH_CHECK_TIMEOUT_MS) || 2200;

        async function probe(url) {
            try {
                const controller = new AbortController();
                const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
                const res = await fetch(`${url}/configs`, {
                    method: 'GET',
                    signal: controller.signal,
                    headers: { 'Accept': 'application/json' }
                });
                clearTimeout(timeoutId);
                return res.ok;
            } catch {
                return false;
            }
        }

        // 1. Thử probe API_BASE chính
        let ok = await probe(API_BASE);
        if (ok) {
            isServerOnline = true;
            const isLocal = API_BASE.includes('localhost') || API_BASE.includes('127.0.0.1');
            activeServerType = isLocal ? 'LOCAL_ONLINE' : 'CLOUD_ONLINE';
        } else {
            // 2. Thử Fallback về Localhost nếu đang cấu hình server cloud nhưng server cloud chưa online
            const cfg = window.HUNTER_CONFIG;
            if (cfg && cfg.ENABLE_LOCAL_FALLBACK && !API_BASE.includes('localhost') && !API_BASE.includes('127.0.0.1')) {
                const localBase = cfg.LOCAL_API_BASE || 'http://localhost:8080/api';
                const localOk = await probe(localBase);
                if (localOk) {
                    API_BASE = localBase;
                    isServerOnline = true;
                    activeServerType = 'LOCAL_ONLINE';
                } else {
                    isServerOnline = false;
                    activeServerType = 'OFFLINE_BUS';
                }
            } else {
                isServerOnline = false;
                activeServerType = 'OFFLINE_BUS';
            }
        }

        if (window.HUNTER_CONFIG && typeof window.HUNTER_CONFIG.applyVersionToUi === 'function') {
            window.HUNTER_CONFIG.applyVersionToUi(activeServerType);
        }

        return isServerOnline;
    }

    // Tự động kiểm tra sức khỏe server khi khởi động và định kỳ
    checkServerHealth();
    setInterval(checkServerHealth, 6000);

    // =========================================================================
    // LOCAL SYNC ENGINE (Hỗ trợ chạy ngay khi Backend chưa bật hoặc mở file trực tiếp)
    // =========================================================================
    const LocalSyncEngine = {
        memoryRooms: {},
        memoryUsers: {},

        getRoomsMap() {
            try {
                const raw = localStorage.getItem(LOCAL_STORAGE_ROOMS_KEY);
                return raw ? JSON.parse(raw) : (this.memoryRooms || {});
            } catch {
                return this.memoryRooms || {};
            }
        },

        saveRoomsMap(map) {
            this.memoryRooms = map;
            try {
                localStorage.setItem(LOCAL_STORAGE_ROOMS_KEY, JSON.stringify(map));
            } catch (e) {}
        },

        cleanExpired() {
            const rooms = this.getRoomsMap();
            const now = Date.now();
            let changed = false;

            for (const [code, r] of Object.entries(rooms)) {
                if (r.status === 'CLOSED') {
                    delete rooms[code];
                    changed = true;
                } else if (r.status === 'WAITING' && (now - (r.lastActiveAt || 0) > 60000)) {
                    // Phòng WAITING quá 60s không có heartbeat -> tự đóng, giải phóng mã
                    r.status = 'CLOSED';
                    delete rooms[code];
                    changed = true;
                    broadcastEvent('ROOM_TIMEOUT', code, null);
                } else if (now - (r.lastActiveAt || 0) > 600000) {
                    delete rooms[code];
                    changed = true;
                }
            }

            if (changed) this.saveRoomsMap(rooms);
        },

        generate4DigitCode() {
            this.cleanExpired();
            const rooms = this.getRoomsMap();
            for (let i = 0; i < 9000; i++) {
                const code = String(Math.floor(1000 + Math.random() * 9000));
                const existing = rooms[code];
                if (!existing || existing.status === 'CLOSED') {
                    return code;
                }
            }
            throw new Error('Đã hết mã phòng khả dụng lúc này (1000 - 9999)');
        },

        loginUser(username) {
            const trimmed = (username || 'Thủy Thủ Vô Danh').trim();
            let users = {};
            try {
                users = JSON.parse(localStorage.getItem(LOCAL_STORAGE_USERS_KEY) || '{}');
            } catch {
                users = this.memoryUsers || {};
            }

            let user = users[trimmed];
            if (!user) {
                const id = Object.keys(users).length + 1;
                user = {
                    userId: id,
                    username: trimmed,
                    createdAt: new Date().toISOString()
                };
                users[trimmed] = user;
                this.memoryUsers = users;
                try {
                    localStorage.setItem(LOCAL_STORAGE_USERS_KEY, JSON.stringify(users));
                } catch (e) {}
            }
            return user;
        },

        createRoom(user, character) {
            this.cleanExpired();
            const code = this.generate4DigitCode();
            const rooms = this.getRoomsMap();

            const room = {
                roomCode: code,
                status: 'WAITING',
                host: user,
                hostCharacter: character,
                guest: null,
                guestCharacter: null,
                createdAt: Date.now(),
                lastActiveAt: Date.now(),
                hostExit: false,
                guestExit: false
            };

            rooms[code] = room;
            this.saveRoomsMap(rooms);
            broadcastEvent('ROOM_CREATED', code, room);
            return room;
        },

        joinRoom(code, user, character) {
            this.cleanExpired();
            const rooms = this.getRoomsMap();
            const room = rooms[code];

            if (!room || room.status === 'CLOSED') {
                throw new Error('Mã phòng không tồn tại hoặc phòng đã bị đóng!');
            }

            if (room.status !== 'WAITING') {
                throw new Error('Phòng đã bắt đầu trận đấu hoặc đã kết thúc!');
            }

            if (room.host && room.host.userId === user.userId) {
                throw new Error('Bạn không thể tự tham gia phòng của chính mình!');
            }

            if (room.guest && room.guest.userId !== user.userId) {
                throw new Error('Phòng đối kháng đã đầy (2/2 người chơi)!');
            }

            room.guest = user;
            room.guestCharacter = character;
            room.status = 'IN_GAME';
            room.lastActiveAt = Date.now();

            rooms[code] = room;
            this.saveRoomsMap(rooms);
            broadcastEvent('ROOM_JOINED', code, room);
            return room;
        },

        getRoom(code) {
            this.cleanExpired();
            const rooms = this.getRoomsMap();
            const room = rooms[code];
            if (room && room.status !== 'CLOSED') {
                room.lastActiveAt = Date.now();
                rooms[code] = room;
                this.saveRoomsMap(rooms);
                return room;
            }
            return null;
        },

        heartbeat(code) {
            const rooms = this.getRoomsMap();
            const room = rooms[code];
            if (room && room.status !== 'CLOSED') {
                room.lastActiveAt = Date.now();
                rooms[code] = room;
                this.saveRoomsMap(rooms);
                return { success: true, status: room.status };
            }
            return { success: false };
        },

        leaveRoom(code, userId) {
            const rooms = this.getRoomsMap();
            const room = rooms[code];
            if (!room) return { success: true };

            if (room.status === 'WAITING') {
                room.status = 'CLOSED';
                delete rooms[code];
                this.saveRoomsMap(rooms);
                broadcastEvent('ROOM_CLOSED', code, null);
                return { success: true, freed: true };
            }

            if (room.host && room.host.userId === userId) {
                room.hostExit = true;
            }
            if (room.guest && room.guest.userId === userId) {
                room.guestExit = true;
            }

            // Nếu cả 2 thoát hoặc host thoát ra sảnh -> đóng phòng và giải phóng mã
            if (room.hostExit && room.guestExit || room.hostExit) {
                room.status = 'CLOSED';
                delete rooms[code];
                this.saveRoomsMap(rooms);
                broadcastEvent('ROOM_CLOSED', code, null);
            } else {
                rooms[code] = room;
                this.saveRoomsMap(rooms);
                broadcastEvent('PLAYER_LEFT', code, room);
            }
            return { success: true };
        }
    };

    // =========================================================================
    // PUBLIC API METHODS (Tự động thích nghi Server Online / Offline)
    // =========================================================================

    return {
        isOnline() {
            return isServerOnline;
        },

        async loginUser(username) {
            const online = await checkServerHealth();
            if (online) {
                try {
                    const res = await fetch(`${API_BASE}/users/login`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ username })
                    });
                    if (res.ok) {
                        return await res.json();
                    }
                } catch (e) {
                    console.warn('[GameApi] Không thể kết nối backend, dùng local user fallback:', e);
                }
            }
            return LocalSyncEngine.loginUser(username);
        },

        async createRoom(username, character) {
            const online = await checkServerHealth();
            if (online) {
                try {
                    const res = await fetch(`${API_BASE}/rooms/create`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            username,
                            characterId: character.id || character.characterId || 2
                        })
                    });
                    if (res.ok) {
                        const room = await res.json();
                        broadcastEvent('ROOM_CREATED', room.roomCode, room);
                        return room;
                    }
                    const errData = await res.json();
                    throw new Error(errData.error || 'Lỗi khi tạo phòng trên Server');
                } catch (e) {
                    if (e.message && !e.message.includes('fetch')) throw e;
                    console.warn('[GameApi] Server error, fallback to local room creation:', e);
                }
            }

            const user = LocalSyncEngine.loginUser(username);
            return LocalSyncEngine.createRoom(user, character);
        },

        async joinRoom(roomCode, username, character) {
            if (!roomCode || !/^\d{4}$/.test(roomCode.trim())) {
                throw new Error('Vui lòng nhập đúng mã phòng gồm 4 chữ số (1000 - 9999)!');
            }
            const cleanCode = roomCode.trim();

            const online = await checkServerHealth();
            if (online) {
                try {
                    const res = await fetch(`${API_BASE}/rooms/join`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            roomCode: cleanCode,
                            username,
                            characterId: character.id || character.characterId || 1
                        })
                    });
                    const data = await res.json();
                    if (res.ok) {
                        broadcastEvent('ROOM_JOINED', cleanCode, data);
                        return data;
                    }
                    throw new Error(data.error || 'Không thể tham gia phòng này');
                } catch (e) {
                    if (e.message && !e.message.includes('fetch') && !e.message.includes('NetworkError')) {
                        throw e;
                    }
                    console.warn('[GameApi] Server unreachable, fallback to local join:', e);
                }
            }

            const user = LocalSyncEngine.loginUser(username);
            return LocalSyncEngine.joinRoom(cleanCode, user, character);
        },

        async getRoom(roomCode) {
            if (!roomCode) return null;
            const cleanCode = roomCode.trim();

            const online = await checkServerHealth();
            if (online) {
                try {
                    const res = await fetch(`${API_BASE}/rooms/${cleanCode}`);
                    if (res.ok) return await res.json();
                } catch (e) {}
            }
            return LocalSyncEngine.getRoom(cleanCode);
        },

        async heartbeat(roomCode) {
            if (!roomCode) return;
            const cleanCode = roomCode.trim();
            if (isServerOnline) {
                try {
                    await fetch(`${API_BASE}/rooms/${cleanCode}/heartbeat`, { method: 'POST' });
                } catch (e) {}
            }
            LocalSyncEngine.heartbeat(cleanCode);
        },

        async leaveRoom(roomCode, userId) {
            if (!roomCode) return;
            const cleanCode = roomCode.trim();

            if (isServerOnline) {
                try {
                    await fetch(`${API_BASE}/rooms/${cleanCode}/leave`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ userId })
                    });
                } catch (e) {}
            }
            return LocalSyncEngine.leaveRoom(cleanCode, userId);
        },

        onRoomEvent(callback) {
            if (typeof callback === 'function') {
                roomListeners.add(callback);
            }
        },

        offRoomEvent(callback) {
            roomListeners.delete(callback);
        },

        broadcastGameStart(roomCode) {
            broadcastEvent('GAME_START_SIGNAL', roomCode, null);
        }
    };
})();

window.GameApi = GameApi;
