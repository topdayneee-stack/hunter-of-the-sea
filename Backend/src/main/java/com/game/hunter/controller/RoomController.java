package com.game.hunter.controller;

import com.game.hunter.dto.RoomCreateRequest;
import com.game.hunter.dto.RoomJoinRequest;
import com.game.hunter.entity.GameCharacter;
import com.game.hunter.entity.User;
import com.game.hunter.service.GameCharacterService;
import com.game.hunter.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "*")
public class RoomController {

    @Autowired
    private UserService userService;

    @Autowired
    private GameCharacterService characterService;

    public static class RoomModel {
        public String roomCode;
        public String status; // WAITING, IN_GAME, FINISHED, CLOSED
        public User host;
        public GameCharacter hostCharacter;
        public User guest;
        public GameCharacter guestCharacter;
        public long createdAt;
        public long lastActiveAt;
        public boolean hostExit = false;
        public boolean guestExit = false;

        public RoomModel(String code, User host, GameCharacter hostChar) {
            this.roomCode = code;
            this.status = "WAITING";
            this.host = host;
            this.hostCharacter = hostChar;
            this.createdAt = System.currentTimeMillis();
            this.lastActiveAt = System.currentTimeMillis();
        }
    }

    private static final Map<String, RoomModel> rooms = new ConcurrentHashMap<>();

    private synchronized String generateUniqueRoomCode() {
        cleanExpiredRooms();
        Random rand = new Random();
        for (int i = 0; i < 9000; i++) {
            int code = 1000 + rand.nextInt(9000);
            String codeStr = String.valueOf(code);
            RoomModel r = rooms.get(codeStr);
            if (r == null || "CLOSED".equals(r.status)) {
                return codeStr;
            }
        }
        return null;
    }

    private void cleanExpiredRooms() {
        long now = System.currentTimeMillis();
        rooms.entrySet().removeIf(entry -> {
            RoomModel r = entry.getValue();
            if ("CLOSED".equals(r.status)) return true;
            // Timeout sau 60s không heartbeat khi đang chờ
            if ("WAITING".equals(r.status) && (now - r.lastActiveAt > 60000)) {
                return true;
            }
            if (now - r.lastActiveAt > 600000) {
                return true;
            }
            return false;
        });
    }

    @PostMapping("/create")
    public ResponseEntity<?> createRoom(@RequestBody RoomCreateRequest req) {
        String username = req.getUsername() != null && !req.getUsername().trim().isEmpty()
                ? req.getUsername().trim() : "PlayerA";
        Integer charId = req.getCharacterId() != null ? req.getCharacterId() : 2;

        User hostUser = userService.loginOrRegister(username);
        GameCharacter hostChar = characterService.getCharacterById(charId).orElse(null);

        String code = generateUniqueRoomCode();
        if (code == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Không thể tạo thêm phòng lúc này (hết mã khả dụng)"));
        }

        RoomModel room = new RoomModel(code, hostUser, hostChar);
        rooms.put(code, room);
        return ResponseEntity.ok(room);
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinRoom(@RequestBody RoomJoinRequest req) {
        String code = req.getRoomCode();
        if (code == null || !code.matches("^\\d{4}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mã phòng phải gồm đúng 4 chữ số (1000 - 9999)"));
        }

        cleanExpiredRooms();
        RoomModel room = rooms.get(code);
        if (room == null || "CLOSED".equals(room.status)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Mã phòng không tồn tại hoặc phòng đã đóng"));
        }

        if (!"WAITING".equals(room.status)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Phòng đã bắt đầu trận đấu hoặc đã kết thúc"));
        }

        String username = req.getUsername() != null && !req.getUsername().trim().isEmpty()
                ? req.getUsername().trim() : "PlayerB";
        User guestUser = userService.loginOrRegister(username);

        if (room.host != null && room.host.getUserId().equals(guestUser.getUserId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bạn không thể tự vào phòng do chính mình tạo"));
        }

        Integer charId = req.getCharacterId() != null ? req.getCharacterId() : 1;
        GameCharacter guestChar = characterService.getCharacterById(charId).orElse(null);

        room.guest = guestUser;
        room.guestCharacter = guestChar;
        room.status = "IN_GAME";
        room.lastActiveAt = System.currentTimeMillis();

        return ResponseEntity.ok(room);
    }

    @GetMapping("/{code}")
    public ResponseEntity<?> getRoom(@PathVariable String code) {
        cleanExpiredRooms();
        RoomModel room = rooms.get(code);
        if (room == null || "CLOSED".equals(room.status)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Phòng không tồn tại hoặc đã đóng"));
        }
        room.lastActiveAt = System.currentTimeMillis();
        return ResponseEntity.ok(room);
    }

    @PostMapping("/{code}/heartbeat")
    public ResponseEntity<?> heartbeat(@PathVariable String code) {
        RoomModel room = rooms.get(code);
        if (room == null || "CLOSED".equals(room.status)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Phòng không tồn tại"));
        }
        room.lastActiveAt = System.currentTimeMillis();
        return ResponseEntity.ok(Map.of("success", true, "status", room.status));
    }

    @PostMapping("/{code}/leave")
    public ResponseEntity<?> leaveRoom(@PathVariable String code, @RequestBody(required = false) Map<String, Object> body) {
        RoomModel room = rooms.get(code);
        if (room != null) {
            Integer userId = body != null && body.get("userId") != null ? (Integer) body.get("userId") : null;
            if ("WAITING".equals(room.status)) {
                room.status = "CLOSED";
                rooms.remove(code);
            } else {
                if (userId != null && room.host != null && userId.equals(room.host.getUserId())) {
                    room.hostExit = true;
                }
                if (userId != null && room.guest != null && userId.equals(room.guest.getUserId())) {
                    room.guestExit = true;
                }
                if (room.hostExit && room.guestExit) {
                    room.status = "CLOSED";
                    rooms.remove(code);
                } else if (room.hostExit) {
                    room.status = "CLOSED";
                    rooms.remove(code);
                }
            }
            return ResponseEntity.ok(Map.of("success", true, "message", "Đã rời phòng thành công"));
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Phòng đã đóng"));
    }

    @PostMapping("/{code}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String code, @RequestBody Map<String, String> body) {
        RoomModel room = rooms.get(code);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Phòng không tồn tại"));
        }
        String newStatus = body.get("status");
        if (newStatus != null) {
            room.status = newStatus;
            room.lastActiveAt = System.currentTimeMillis();
            if ("CLOSED".equals(newStatus)) {
                rooms.remove(code);
            }
            return ResponseEntity.ok(room);
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Trạng thái không hợp lệ"));
    }
}
