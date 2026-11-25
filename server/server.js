/**
 * =========================================================================
 * HUNTER OF THE SEA - ONLINE CLOUD SERVER (UNIFIED SINGLE PORT)
 * =========================================================================
 * Tương thích 100% các nền tảng Cloud: Render.com, Railway, Koyeb, Fly.io,
 * Docker, VPS...
 * 
 * ĐẶC ĐIỂM NỔI BẬT:
 * - Chạy cả HTTP REST API và WebSocket Realtime trên CÙNG 1 CỔNG DUY NHẤT (PORT).
 * - Tự động nhận diện biến môi trường PORT của Cloud (Render, Railway gán ngẫu nhiên).
 * - Hỗ trợ cả thư viện 'ws' lẫn engine WebSocket RFC 6455 thuần (0 dependency).
 * - CORS mở toàn diện, cho phép kết nối an toàn từ cả Web và Windows Desktop App.
 * =========================================================================
 */

const http = require('http');
const crypto = require('crypto');
const url = require('url');

// Cổng lắng nghe: Lấy từ biến môi trường PORT (Cloud standard) hoặc 8080
const PORT = parseInt(process.env.PORT || process.argv[2] || '8080', 10);

// =========================================================================
// IN-MEMORY DATABASE (ĐỒNG BỘ VỚI STANDALONE SERVER JAVA)
// =========================================================================

const characters = [
    { characterId: 1, name: "Cá Nhỏ", baseSizeWidth: 40, baseSizeHeight: 30, baseSpeed: 4.2, skillName: "Bứt Tốc", skillDescription: "Tăng 50% tốc độ trong 3s", manaCost: 30 },
    { characterId: 2, name: "Cá Kiếm", baseSizeWidth: 70, baseSizeHeight: 35, baseSpeed: 4.0, skillName: "Phi Kiếm", skillDescription: "Lao thẳng về phía trước gây sát thương", manaCost: 40 },
    { characterId: 3, name: "Bạch Tuộc", baseSizeWidth: 60, baseSizeHeight: 50, baseSpeed: 3.5, skillName: "Phun Mực", skillDescription: "Tạo đám mây mực làm chậm và che tầm nhìn kẻ địch", manaCost: 45 },
    { characterId: 4, name: "Cá Mập", baseSizeWidth: 90, baseSizeHeight: 50, baseSpeed: 3.8, skillName: "Cắn Xé", skillDescription: "Cắn mạnh gây sát thương lớn và hồi máu", manaCost: 50 },
    { characterId: 5, name: "Rùa Biển", baseSizeWidth: 65, baseSizeHeight: 55, baseSpeed: 3.0, skillName: "Thu Mai", skillDescription: "Bất tử trong 3s nhưng giảm 50% tốc độ", manaCost: 35 },
    { characterId: 6, name: "Cá Đuối", baseSizeWidth: 75, baseSizeHeight: 45, baseSpeed: 3.6, skillName: "Phóng Điện", skillDescription: "Gây choáng kẻ địch xung quanh trong 2s", manaCost: 45 },
    { characterId: 7, name: "Cua Khổng Lồ", baseSizeWidth: 70, baseSizeHeight: 50, baseSpeed: 3.2, skillName: "Càng Thép", skillDescription: "Chặn sát thương từ phía trước và kẹp đối thủ", manaCost: 40 }
];

const configs = {
    GAME_VERSION: { configKey: "GAME_VERSION", configValue: "2.6.0", description: "Phiên bản hệ thống" },
    MAP_WIDTH: { configKey: "MAP_WIDTH", configValue: "1920", description: "Chiều rộng bản đồ" },
    MAP_HEIGHT: { configKey: "MAP_HEIGHT", configValue: "1080", description: "Chiều cao bản đồ" },
    PVP_TIME_LIMIT_SECONDS: { configKey: "PVP_TIME_LIMIT_SECONDS", configValue: "180", description: "Thời gian tối đa trận PvP" },
    SPAWN_FOOD_INTERVAL_MS: { configKey: "SPAWN_FOOD_INTERVAL_MS", configValue: "800", description: "Thời gian sinh cá mồi (ms)" },
    MAX_FOOD_COUNT: { configKey: "MAX_FOOD_COUNT", configValue: "35", description: "Số lượng cá mồi tối đa" },
    MAX_HAZARDS_COUNT: { configKey: "MAX_HAZARDS_COUNT", configValue: "6", description: "Số lượng bom/mực tối đa" },
    EVOLUTION_LEVEL_MAX: { configKey: "EVOLUTION_LEVEL_MAX", configValue: "4", description: "Cấp độ tiến hóa tối đa" }
};

const users = new Map(); // username -> UserInfo
let nextUserId = 1;

function getOrCreateUser(username) {
    const cleanName = (username || 'Player').trim();
    if (users.has(cleanName)) {
        return users.get(cleanName);
    }
    const u = {
        userId: nextUserId++,
        username: cleanName,
        createdAt: new Date().toISOString()
    };
    users.set(cleanName, u);
    return u;
}

// Khởi tạo một số user mặc định
getOrCreateUser('PlayerA');
getOrCreateUser('PlayerB');

// Danh sách phòng PvP: roomCode -> RoomModel
const rooms = new Map();
const pvpHistory = [];

function cleanExpiredRooms() {
    const now = Date.now();
    for (const [code, r] of rooms.entries()) {
        if (r.status === 'CLOSED') {
            rooms.delete(code);
            continue;
        }
        // WAITING quá 60s không hoạt động
        if (r.status === 'WAITING' && (now - r.lastActiveAt > 60000)) {
            rooms.delete(code);
            continue;
        }
        // IN_GAME / FINISHED quá 10 phút
        if (now - r.lastActiveAt > 600000) {
            rooms.delete(code);
            continue;
        }
    }
}

// Dọn dẹp phòng mỗi 30s
setInterval(cleanExpiredRooms, 30000);

function generateRoomCode() {
    cleanExpiredRooms();
    for (let i = 0; i < 9000; i++) {
        const code = String(Math.floor(1000 + Math.random() * 9000));
        if (!rooms.has(code) || rooms.get(code).status === 'CLOSED') {
            return code;
        }
    }
    return null;
}

// =========================================================================
// WEBSOCKET SESSIONS THEO PHÒNG
// =========================================================================

// roomCode -> Set of client sessions
const wsRoomSessions = new Map();

function addWsSession(roomCode, session) {
    if (!wsRoomSessions.has(roomCode)) {
        wsRoomSessions.set(roomCode, new Set());
    }
    wsRoomSessions.get(roomCode).add(session);
    console.log(`[WS] Client đã vào phòng [${roomCode}] (Tổng trong phòng: ${wsRoomSessions.get(roomCode).size})`);
}

function removeWsSession(roomCode, session) {
    if (wsRoomSessions.has(roomCode)) {
        const set = wsRoomSessions.get(roomCode);
        set.delete(session);
        if (set.size === 0) {
            wsRoomSessions.delete(roomCode);
        }
        console.log(`[WS] Client đã rời phòng [${roomCode}]`);
    }
}

function broadcastToRoom(roomCode, senderSession, messageStr) {
    const set = wsRoomSessions.get(roomCode);
    if (!set) return;
    for (const s of set) {
        if (s !== senderSession) {
            try {
                s.send(messageStr);
            } catch (err) {
                // Ignore error on dead socket
            }
        }
    }
}

// =========================================================================
// HTTP REQUEST HANDLER (REST API)
// =========================================================================

function sendJson(res, statusCode, data) {
    const body = typeof data === 'string' ? data : JSON.stringify(data);
    res.writeHead(statusCode, {
        'Content-Type': 'application/json; charset=utf-8',
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type, Authorization, Accept'
    });
    res.end(body);
}

function parseJsonBody(req) {
    return new Promise((resolve) => {
        let body = '';
        req.on('data', chunk => { body += chunk; });
        req.on('end', () => {
            try {
                resolve(body ? JSON.parse(body) : {});
            } catch {
                resolve({});
            }
        });
        req.on('error', () => resolve({}));
    });
}

const server = http.createServer(async (req, res) => {
    // Xử lý CORS Preflight
    if (req.method === 'OPTIONS') {
        res.writeHead(200, {
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type, Authorization, Accept'
        });
        res.end();
        return;
    }

    const parsedUrl = url.parse(req.url, true);
    const pathname = parsedUrl.pathname.replace(/\/+$/, '') || '/';
    const method = req.method.toUpperCase();

    // Health check root
    if (pathname === '/' || pathname === '/health') {
        return sendJson(res, 200, {
            status: 'ONLINE',
            server: 'Hunter of the Sea Online Cloud Server',
            version: '2.6.0',
            time: new Date().toISOString(),
            roomsActive: rooms.size,
            wsConnected: Array.from(wsRoomSessions.values()).reduce((acc, s) => acc + s.size, 0)
        });
    }

    // 1. GET /api/characters
    if (pathname === '/api/characters' && method === 'GET') {
        return sendJson(res, 200, characters);
    }
    if (pathname.startsWith('/api/characters/') && method === 'GET') {
        const id = parseInt(pathname.split('/').pop(), 10);
        const c = characters.find(ch => ch.characterId === id);
        return c ? sendJson(res, 200, c) : sendJson(res, 404, { error: 'Character not found' });
    }

    // 2. GET /api/configs
    if (pathname === '/api/configs' && method === 'GET') {
        return sendJson(res, 200, Object.values(configs));
    }
    if (pathname.startsWith('/api/configs/') && method === 'GET') {
        const key = pathname.split('/').pop();
        const conf = configs[key];
        return conf ? sendJson(res, 200, conf) : sendJson(res, 404, { error: 'Config not found' });
    }

    // 3. POST /api/users/login
    if (pathname === '/api/users/login' && method === 'POST') {
        const body = await parseJsonBody(req);
        const user = getOrCreateUser(body.username || 'Player');
        return sendJson(res, 200, user);
    }

    // 4. POST /api/rooms/create
    if (pathname === '/api/rooms/create' && method === 'POST') {
        const body = await parseJsonBody(req);
        const username = (body.username || 'PlayerA').trim();
        const charId = parseInt(body.characterId || 2, 10);
        const hostUser = getOrCreateUser(username);
        const hostChar = characters.find(c => c.characterId === charId) || characters[1];

        const code = generateRoomCode();
        if (!code) {
            return sendJson(res, 500, { error: 'Không thể sinh mã phòng lúc này' });
        }

        const room = {
            roomCode: code,
            status: 'WAITING',
            host: hostUser,
            hostCharacter: hostChar,
            guest: null,
            guestCharacter: null,
            createdAt: Date.now(),
            lastActiveAt: Date.now(),
            hostExit: false,
            guestExit: false
        };
        rooms.set(code, room);
        console.log(`[Room] Đã tạo phòng mới: ${code} bởi ${username}`);
        return sendJson(res, 200, room);
    }

    // 5. POST /api/rooms/join
    if (pathname === '/api/rooms/join' && method === 'POST') {
        const body = await parseJsonBody(req);
        const code = String(body.roomCode || '').trim();
        const username = (body.username || 'PlayerB').trim();
        const charId = parseInt(body.characterId || 3, 10);

        const room = rooms.get(code);
        if (!room) {
            return sendJson(res, 404, { error: 'Phòng không tồn tại hoặc đã bị đóng' });
        }
        if (room.status !== 'WAITING') {
            return sendJson(res, 400, { error: 'Phòng đã đủ người chơi hoặc đang trong trận đấu' });
        }

        const guestUser = getOrCreateUser(username);
        const guestChar = characters.find(c => c.characterId === charId) || characters[2];

        room.guest = guestUser;
        room.guestCharacter = guestChar;
        room.status = 'IN_GAME';
        room.lastActiveAt = Date.now();

        console.log(`[Room] ${username} đã tham gia phòng: ${code}`);
        return sendJson(res, 200, room);
    }

    // 6. GET /api/rooms/:code
    if (pathname.startsWith('/api/rooms/') && method === 'GET') {
        const code = pathname.split('/').pop();
        const room = rooms.get(code);
        return room ? sendJson(res, 200, room) : sendJson(res, 404, { error: 'Room not found' });
    }

    // 7. POST /api/rooms/:code/heartbeat
    if (pathname.startsWith('/api/rooms/') && pathname.endsWith('/heartbeat') && method === 'POST') {
        const parts = pathname.split('/');
        const code = parts[parts.length - 2];
        const room = rooms.get(code);
        if (room) {
            room.lastActiveAt = Date.now();
            return sendJson(res, 200, { status: 'OK', lastActiveAt: room.lastActiveAt });
        }
        return sendJson(res, 404, { error: 'Room not found' });
    }

    // 8. POST /api/rooms/:code/leave
    if (pathname.startsWith('/api/rooms/') && pathname.endsWith('/leave') && method === 'POST') {
        const parts = pathname.split('/');
        const code = parts[parts.length - 2];
        const body = await parseJsonBody(req);
        const room = rooms.get(code);
        if (room) {
            room.status = 'CLOSED';
            rooms.delete(code);
            console.log(`[Room] Phòng ${code} đã đóng do người chơi rời phòng`);
            return sendJson(res, 200, { status: 'CLOSED' });
        }
        return sendJson(res, 200, { status: 'CLOSED' });
    }

    // 9. GET /api/leaderboards
    if (pathname === '/api/leaderboards' && method === 'GET') {
        return sendJson(res, 200, []);
    }

    // 10. GET /api/pvp/history & POST /api/pvp/record
    if (pathname === '/api/pvp/history' && method === 'GET') {
        return sendJson(res, 200, pvpHistory);
    }
    if (pathname === '/api/pvp/record' && method === 'POST') {
        const body = await parseJsonBody(req);
        const rec = {
            matchId: pvpHistory.length + 1,
            player1: getOrCreateUser(body.player1 || 'Player 1'),
            player2: getOrCreateUser(body.player2 || 'Player 2'),
            winner: getOrCreateUser(body.winner || 'Player 1'),
            playedAt: new Date().toISOString()
        };
        pvpHistory.push(rec);
        return sendJson(res, 200, rec);
    }

    // 404 Default
    sendJson(res, 404, { error: 'Endpoint not found', path: pathname });
});

// =========================================================================
// WEBSOCKET SERVER IMPLEMENTATION (ZERO DEPENDENCY / RFC 6455)
// =========================================================================

server.on('upgrade', (req, socket, head) => {
    const parsedUrl = url.parse(req.url);
    const pathname = parsedUrl.pathname || '';

    // Route: /ws/pvp/:roomCode
    if (!pathname.startsWith('/ws/pvp/')) {
        socket.destroy();
        return;
    }

    const roomCode = pathname.substring('/ws/pvp/'.length).trim() || 'default';
    const secKey = req.headers['sec-websocket-key'];

    if (!secKey) {
        socket.destroy();
        return;
    }

    // Tính Sec-WebSocket-Accept
    const sha1 = crypto.createHash('sha1');
    sha1.update(secKey + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11');
    const acceptKey = sha1.digest('base64');

    const responseHeaders = [
        'HTTP/1.1 101 Switching Protocols',
        'Upgrade: websocket',
        'Connection: Upgrade',
        `Sec-WebSocket-Accept: ${acceptKey}`
    ];

    socket.write(responseHeaders.join('\r\n') + '\r\n\r\n');

    // Tạo session adapter đơn giản
    const session = {
        send: (text) => {
            if (socket.destroyed) return;
            const payload = Buffer.from(text, 'utf8');
            const length = payload.length;

            let header;
            if (length <= 125) {
                header = Buffer.from([0x81, length]);
            } else if (length <= 65535) {
                header = Buffer.alloc(4);
                header[0] = 0x81;
                header[1] = 126;
                header.writeUInt16BE(length, 2);
            } else {
                header = Buffer.alloc(10);
                header[0] = 0x81;
                header[1] = 127;
                header.writeBigUInt64BE(BigInt(length), 2);
            }

            try {
                socket.write(Buffer.concat([header, payload]));
            } catch (err) {}
        }
    };

    addWsSession(roomCode, session);

    // Buffer tích lũy phân mảnh frame
    let buffer = Buffer.alloc(0);

    socket.on('data', (chunk) => {
        buffer = Buffer.concat([buffer, chunk]);

        while (buffer.length >= 2) {
            const b1 = buffer[0];
            const b2 = buffer[1];
            const opcode = b1 & 0x0F;
            const isMasked = (b2 & 0x80) !== 0;
            let payloadLength = b2 & 0x7F;
            let offset = 2;

            if (payloadLength === 126) {
                if (buffer.length < 4) break;
                payloadLength = buffer.readUInt16BE(2);
                offset = 4;
            } else if (payloadLength === 127) {
                if (buffer.length < 10) break;
                payloadLength = Number(buffer.readBigUInt64BE(2));
                offset = 10;
            }

            const maskLength = isMasked ? 4 : 0;
            const totalFrameSize = offset + maskLength + payloadLength;
            if (buffer.length < totalFrameSize) break;

            let mask = null;
            if (isMasked) {
                mask = buffer.slice(offset, offset + 4);
                offset += 4;
            }

            const payload = buffer.slice(offset, offset + payloadLength);
            if (isMasked) {
                for (let i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i % 4];
                }
            }

            buffer = buffer.slice(totalFrameSize);

            // Opcode 8: Close frame
            if (opcode === 0x08) {
                socket.end();
                break;
            }

            // Opcode 9: Ping
            if (opcode === 0x09) {
                const pong = Buffer.from([0x8A, 0x00]);
                socket.write(pong);
                continue;
            }

            // Opcode 1: Text message
            if (opcode === 0x01) {
                const messageStr = payload.toString('utf8');
                broadcastToRoom(roomCode, session, messageStr);
            }
        }
    });

    socket.on('close', () => {
        removeWsSession(roomCode, session);
    });

    socket.on('error', () => {
        removeWsSession(roomCode, session);
    });
});

// Khởi động server
server.listen(PORT, '0.0.0.0', () => {
    console.log('=====================================================================');
    console.log('  HUNTER OF THE SEA - ONLINE CLOUD SERVER DA KHOI CHAY THANH CONG!');
    console.log(`  Cong lang nghe (PORT): ${PORT}`);
    console.log(`  HTTP API Root:         http://0.0.0.0:${PORT}/api`);
    console.log(`  WebSocket Realtime:    ws://0.0.0.0:${PORT}/ws/pvp/{roomCode}`);
    console.log(`  Health Check:          http://0.0.0.0:${PORT}/health`);
    console.log('=====================================================================');
});
