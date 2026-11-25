/**
 * HUNTER OF THE SEA - ROOM LIFECYCLE & PVP ROOM MANAGER (PHASE 1)
 * Quản lý vòng đời phòng: WAITING -> IN_GAME -> FINISHED -> CLOSED.
 * Tự động đồng bộ trạng thái giữa 2 người chơi, giải phóng mã khi cả 2 rời sảnh hoặc timeout.
 */

const RoomManager = (function () {
    let currentRoom = null;
    let heartbeatTimer = null;
    let pollTimer = null;
    const roomStateListeners = new Set();

    function startRoomWatch(roomCode) {
        stopRoomWatch();

        // Heartbeat định kỳ mỗi 5s để server không dọn nhầm phòng đang hoạt động
        heartbeatTimer = setInterval(() => {
            if (currentRoom && currentRoom.roomCode) {
                window.GameApi.heartbeat(currentRoom.roomCode);
            }
        }, 5000);

        // Polling kiểm tra trạng thái phòng mỗi 1.2s (đặc biệt khi dùng REST API)
        pollTimer = setInterval(async () => {
            if (!currentRoom || !currentRoom.roomCode) return;
            try {
                const updated = await window.GameApi.getRoom(currentRoom.roomCode);
                if (updated) {
                    const statusChanged = currentRoom.status !== updated.status;
                    const guestJoined = !currentRoom.guest && updated.guest;
                    currentRoom = updated;
                    if (statusChanged || guestJoined) {
                        notifyListeners(currentRoom, 'POLL_UPDATE');
                    }
                } else {
                    // Phòng đã bị đóng hoặc xóa
                    handleRoomClosedLocally('Phòng đã bị đóng hoặc chủ phòng đã hủy!');
                }
            } catch (err) {
                console.warn('[RoomManager] Lỗi poll room:', err);
            }
        }, 1200);
    }

    function stopRoomWatch() {
        if (heartbeatTimer) clearInterval(heartbeatTimer);
        if (pollTimer) clearInterval(pollTimer);
        heartbeatTimer = null;
        pollTimer = null;
    }

    function handleRoomClosedLocally(reason) {
        stopRoomWatch();
        const prev = currentRoom;
        currentRoom = null;
        notifyListeners(null, 'ROOM_CLOSED', reason);
    }

    function notifyListeners(room, eventType, extra) {
        roomStateListeners.forEach(cb => {
            try { cb(room, eventType, extra); } catch (e) {}
        });
    }

    // Đăng ký lắng nghe realtime bus từ GameApi
    window.GameApi.onRoomEvent((code, room, eventType) => {
        if (!currentRoom || currentRoom.roomCode !== code) return;

        if (eventType === 'ROOM_CLOSED' || eventType === 'ROOM_TIMEOUT') {
            handleRoomClosedLocally('Phòng đã bị hủy hoặc đối thủ đã rời đi.');
            return;
        }

        if (room) {
            currentRoom = room;
            notifyListeners(currentRoom, eventType);
        }
    });

    return {
        getCurrentRoom() {
            return currentRoom;
        },

        async createRoom(character) {
            const user = window.AuthManager.getCurrentUser();
            const room = await window.GameApi.createRoom(user.username, character);
            currentRoom = room;
            startRoomWatch(room.roomCode);
            notifyListeners(currentRoom, 'CREATED');
            return room;
        },

        async joinRoom(roomCode, character) {
            const user = window.AuthManager.getCurrentUser();
            const room = await window.GameApi.joinRoom(roomCode, user.username, character);
            currentRoom = room;
            startRoomWatch(room.roomCode);
            notifyListeners(currentRoom, 'JOINED');
            return room;
        },

        async leaveRoom() {
            if (!currentRoom) return;
            const code = currentRoom.roomCode;
            const user = window.AuthManager.getCurrentUser();
            stopRoomWatch();
            currentRoom = null;
            await window.GameApi.leaveRoom(code, user ? user.userId : null);
            notifyListeners(null, 'LEFT');
        },

        onRoomChange(callback) {
            if (typeof callback === 'function') {
                roomStateListeners.add(callback);
            }
        },

        copyCodeToClipboard() {
            if (!currentRoom || !currentRoom.roomCode) return false;
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(currentRoom.roomCode);
                return true;
            } else {
                const ta = document.createElement('textarea');
                ta.value = currentRoom.roomCode;
                document.body.appendChild(ta);
                ta.select();
                document.execCommand('copy');
                document.body.removeChild(ta);
                return true;
            }
        }
    };
})();

window.RoomManager = RoomManager;
