package com.game.hunter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Máy chủ HTTP RESTful API độc lập, tương thích 100% với Spring Boot Backend của Hunter of the Sea.
 * Được xây dựng bằng thuần Java Built-in (com.sun.net.httpserver), không cần Maven và không cần Internet.
 * Đóng vai trò là In-Memory Server Fallback siêu tốc tại cổng 8080 cho test_dashboard.html và Game Client.
 */
public class StandaloneServer {
    private static int PORT = 8080;
    private static int WS_PORT = 8081;

    // Quản lý kết nối WebSocket theo từng phòng PvP (Phase 2)
    private static final Map<String, Set<WebSocketSession>> roomWsSessions = new ConcurrentHashMap<>();

    // In-memory Database
    public static class CharacterInfo {
        public int characterId;
        public String name;
        public int baseSizeWidth;
        public int baseSizeHeight;
        public double baseSpeed;
        public String skillName;
        public String skillDescription;
        public int manaCost;

        public CharacterInfo(int id, String name, int w, int h, double spd, String sName, String sDesc, int mana) {
            this.characterId = id;
            this.name = name;
            this.baseSizeWidth = w;
            this.baseSizeHeight = h;
            this.baseSpeed = spd;
            this.skillName = sName;
            this.skillDescription = sDesc;
            this.manaCost = mana;
        }
    }

    public static class ConfigItem {
        public String configKey;
        public String configValue;
        public String description;
        public String updatedAt;

        public ConfigItem(String k, String v, String desc) {
            this.configKey = k;
            this.configValue = v;
            this.description = desc;
            this.updatedAt = getIsoTime();
        }
    }

    public static class UserInfo {
        public int userId;
        public String username;
        public String createdAt;

        public UserInfo(int id, String name) {
            this.userId = id;
            this.username = name;
            this.createdAt = getIsoTime();
        }
    }

    public static class ScoreRecord {
        public int recordId;
        public UserInfo user;
        public CharacterInfo character;
        public int score;
        public String gameMode;
        public String playedAt;

        public ScoreRecord(int id, UserInfo u, CharacterInfo c, int s, String mode) {
            this.recordId = id;
            this.user = u;
            this.character = c;
            this.score = s;
            this.gameMode = mode;
            this.playedAt = getIsoTime();
        }
    }

    public static class PvpRecord {
        public int matchId;
        public UserInfo player1;
        public CharacterInfo player1Character;
        public UserInfo player2;
        public CharacterInfo player2Character;
        public UserInfo winner;
        public int matchDurationSeconds;
        public String playedAt;

        public PvpRecord(int id, UserInfo p1, CharacterInfo c1, UserInfo p2, CharacterInfo c2, UserInfo win, int dur) {
            this.matchId = id;
            this.player1 = p1;
            this.player1Character = c1;
            this.player2 = p2;
            this.player2Character = c2;
            this.winner = win;
            this.matchDurationSeconds = dur;
            this.playedAt = getIsoTime();
        }
    }

    public static class RoomInfo {
        public String roomCode;
        public String status; // WAITING, IN_GAME, FINISHED, CLOSED
        public UserInfo host;
        public CharacterInfo hostCharacter;
        public UserInfo guest;
        public CharacterInfo guestCharacter;
        public long createdAt;
        public long lastActiveAt;
        public boolean hostExit = false;
        public boolean guestExit = false;

        public RoomInfo(String code, UserInfo host, CharacterInfo hostChar) {
            this.roomCode = code;
            this.status = "WAITING";
            this.host = host;
            this.hostCharacter = hostChar;
            this.createdAt = System.currentTimeMillis();
            this.lastActiveAt = System.currentTimeMillis();
        }
    }

    private static final Map<String, RoomInfo> rooms = new ConcurrentHashMap<>();

    private static final Map<Integer, CharacterInfo> characters = new LinkedHashMap<>();
    private static final Map<String, ConfigItem> configs = new LinkedHashMap<>();
    private static final Map<Integer, UserInfo> users = new ConcurrentHashMap<>();
    private static final Map<String, Integer> userByName = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<Integer>> userUnlockedChars = new ConcurrentHashMap<>();
    private static final List<ScoreRecord> leaderboards = new CopyOnWriteArrayList<>();
    private static final List<PvpRecord> pvpHistory = new CopyOnWriteArrayList<>();

    private static final AtomicInteger nextUserId = new AtomicInteger(1);
    private static final AtomicInteger nextRecordId = new AtomicInteger(1);
    private static final AtomicInteger nextMatchId = new AtomicInteger(1);

    static {
        // Nạp 7 Thủy Thần chuẩn theo CSDL_CA.sql
        addChar(1, "Tôm", 2, 2, 4.50, "Búng Lùi Dịch Chuyển", "Dịch chuyển tức thời tới vùng an toàn: xa cá lớn và gần bầy cá nhỏ.", 5);
        addChar(2, "Cá Kiếm", 3, 2, 4.80, "Lướt Thần Tốc", "Tăng mạnh tốc độ bơi gấp 2.2 lần trong 3.5 giây để săn mồi hoặc tẩu thoát.", 5);
        addChar(3, "Cua", 3, 3, 3.80, "Kẹp Sắt Đoạt Mạng", "Tung đôi càng kẹp bắt và nuốt trọn 2 con cá nhỏ gần nhất trong phạm vi.", 5);
        addChar(4, "Cá Ép", 2, 2, 4.20, "Ký Sinh Bám Dính", "Tạm bám vào cá lớn, điều khiển cá chủ và cộng dồn kích thước trong 5s.", 5);
        addChar(5, "Bạch Tuộc", 3, 3, 3.90, "Vùng Mực Hắc Ám", "Phun vùng mây mực: Bản thân bất tử và tăng tốc, cá khác bị làm chậm.", 5);
        addChar(6, "Cá Đuối Điện", 3, 3, 4.00, "Xung Điện Định Kỳ", "Phóng xung điện trong 3s, làm choáng 0.5s toàn bộ cá ở gần sau mỗi 1s.", 5);
        addChar(7, "Rùa Biển", 3, 3, 3.50, "Mai Rùa Bất Hoại", "Bất tử trong 4s; cá lớn tấn công sẽ bị đẩy lùi mạnh và làm choáng 1s.", 5);

        // Nạp cấu hình mặc định chuẩn theo DataSeeder.java
        addConfig("haki_buff_duration_sec", "5", "Thời gian hiệu lực tăng 1 cấp của viên bá khí (5 giây)");
        addConfig("squid_ink_blind_sec", "2", "Thời gian đen màn hình khi ăn phải mực (2 giây)");
        addConfig("eat_count_to_level_up", "10", "Số lượng cá nhỏ cần ăn để tăng 1 cấp kích thước");
        addConfig("game_duration_timed_sec", "180", "Thời gian thi đấu chế độ 3 phút tính bằng giây");
        addConfig("poison_fish_stun_sec", "2", "Thời gian làm chậm tốc độ di chuyển khi ăn phải cá độc (giây)");
        addConfig("pve_phase1_duration_sec", "90", "Thời gian giai đoạn 1 của chế độ chơi với máy PvE");

        // Người chơi mẫu ThuyThuSanMoi & Player2
        UserInfo sampleUser = getOrCreateUser("ThuyThuSanMoi");
        UserInfo p2User = getOrCreateUser("Player2_DoiThu");

        // Dữ liệu mẫu Leaderboard
        leaderboards.add(new ScoreRecord(nextRecordId.getAndIncrement(), sampleUser, characters.get(2), 6500, "time_limit"));
        leaderboards.add(new ScoreRecord(nextRecordId.getAndIncrement(), sampleUser, characters.get(2), 12500, "endless"));

        // Dữ liệu mẫu PvP
        pvpHistory.add(new PvpRecord(nextMatchId.getAndIncrement(), sampleUser, characters.get(2), p2User, characters.get(3), sampleUser, 145));
    }

    private static void addChar(int id, String name, int w, int h, double spd, String sName, String sDesc, int mana) {
        characters.put(id, new CharacterInfo(id, name, w, h, spd, sName, sDesc, mana));
    }

    private static void addConfig(String k, String v, String desc) {
        configs.put(k, new ConfigItem(k, v, desc));
    }

    private static synchronized UserInfo getOrCreateUser(String username) {
        if (username == null || username.trim().isEmpty()) username = "ThuyThuSanMoi";
        username = username.trim();
        Integer id = userByName.get(username);
        if (id != null) {
            return users.get(id);
        }
        int newId = nextUserId.getAndIncrement();
        UserInfo u = new UserInfo(newId, username);
        users.put(newId, u);
        userByName.put(username, newId);

        // Mặc định tặng Tôm (1) và Cá Kiếm (2)
        Set<Integer> unlocked = Collections.newSetFromMap(new ConcurrentHashMap<>());
        unlocked.add(1);
        unlocked.add(2);
        userUnlockedChars.put(newId, unlocked);
        return u;
    }

    private static String getIsoTime() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
    }

    public static void main(String[] args) throws IOException {
        // Đọc cổng từ biến môi trường PORT (Chuẩn Cloud Hosting: Render, Railway, Heroku, Docker)
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try { PORT = Integer.parseInt(envPort.trim()); } catch (Exception ignored) {}
        }
        if (args.length > 0) {
            try { PORT = Integer.parseInt(args[0].trim()); } catch (Exception ignored) {}
        }

        String envWs = System.getenv("WS_PORT");
        if (envWs != null && !envWs.trim().isEmpty()) {
            try { WS_PORT = Integer.parseInt(envWs.trim()); } catch (Exception ignored) {}
        } else {
            WS_PORT = (PORT == 8080) ? 8081 : (PORT + 1);
        }
        if (args.length > 1) {
            try { WS_PORT = Integer.parseInt(args[1].trim()); } catch (Exception ignored) {}
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        // Định tuyến toàn bộ API
        server.createContext("/api/characters", new CharactersHandler());
        server.createContext("/api/configs", new ConfigsHandler());
        server.createContext("/api/users", new UsersHandler());
        server.createContext("/api/leaderboards", new LeaderboardsHandler());
        server.createContext("/api/pvp", new PvpHandler());
        server.createContext("/api/rooms", new RoomsHandler());

        server.start();

        // Khởi động WebSocket Server Realtime
        startWebSocketServer(WS_PORT);

        System.out.println("=====================================================================");
        System.out.println("   HUNTER OF THE SEA - BACKEND REST API SERVER DA KHOI CHAY!");
        System.out.println("   HTTP Port: " + PORT + " | WebSocket Port: " + WS_PORT);
        System.out.println("   API Root:           http://0.0.0.0:" + PORT + "/api");
        System.out.println("   WebSocket Realtime: ws://0.0.0.0:" + WS_PORT + "/ws/pvp/{roomCode}");
        System.out.println("=====================================================================");
    }

    // --- CÁC ROUTE HANDLERS ---

    private static class CharactersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOptions(exchange)) return;
            String path = exchange.getRequestURI().getPath(); // /api/characters hoặc /api/characters/{id}
            String sub = path.substring("/api/characters".length());

            if (sub.isEmpty() || sub.equals("/")) {
                // GET ALL
                sendJson(exchange, 200, toJsonList(characters.values(), StandaloneServer::charToJson));
            } else {
                try {
                    int id = Integer.parseInt(sub.replace("/", ""));
                    CharacterInfo c = characters.get(id);
                    if (c != null) sendJson(exchange, 200, charToJson(c));
                    else sendJson(exchange, 404, "{\"error\":\"Character not found\"}");
                } catch (Exception e) {
                    sendJson(exchange, 400, "{\"error\":\"Invalid ID\"}");
                }
            }
        }
    }

    private static class ConfigsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOptions(exchange)) return;
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String sub = path.substring("/api/configs".length());

            if (sub.isEmpty() || sub.equals("/")) {
                if ("GET".equalsIgnoreCase(method)) {
                    sendJson(exchange, 200, toJsonList(configs.values(), StandaloneServer::configToJson));
                } else {
                    sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                }
            } else {
                String key = sub.replace("/", "").trim();
                ConfigItem item = configs.get(key);
                if (item == null) {
                    sendJson(exchange, 404, "{\"error\":\"Config key not found\"}");
                    return;
                }
                if ("GET".equalsIgnoreCase(method)) {
                    sendJson(exchange, 200, configToJson(item));
                } else if ("PUT".equalsIgnoreCase(method)) {
                    String body = readBody(exchange);
                    String val = extractJsonVal(body, "configValue");
                    if (val != null) {
                        item.configValue = val;
                        item.updatedAt = getIsoTime();
                        sendJson(exchange, 200, configToJson(item));
                    } else {
                        sendJson(exchange, 400, "{\"error\":\"configValue required\"}");
                    }
                }
            }
        }
    }

    private static class UsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOptions(exchange)) return;
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/api/users/login") && "POST".equalsIgnoreCase(method)) {
                String body = readBody(exchange);
                String name = extractJsonVal(body, "username");
                if (name == null || name.trim().isEmpty()) {
                    sendJson(exchange, 400, "{\"error\":\"Username required\"}");
                    return;
                }
                UserInfo user = getOrCreateUser(name);
                sendJson(exchange, 200, userToJson(user));
                return;
            }

            // /api/users/{userId}/characters/{charId} hoặc /api/users/{userId}/characters
            String[] parts = path.split("/");
            if (parts.length >= 4) {
                try {
                    int userId = Integer.parseInt(parts[3]);
                    UserInfo user = users.get(userId);
                    if (user == null) {
                        sendJson(exchange, 404, "{\"error\":\"User not found\"}");
                        return;
                    }

                    if (parts.length == 4) {
                        // GET /api/users/{id}
                        sendJson(exchange, 200, userToJson(user));
                        return;
                    }

                    if (parts.length >= 5 && parts[4].equals("characters")) {
                        Set<Integer> unlocked = userUnlockedChars.get(userId);
                        if (unlocked == null) {
                            unlocked = Collections.newSetFromMap(new ConcurrentHashMap<>());
                            userUnlockedChars.put(userId, unlocked);
                        }

                        if (parts.length == 5 && "GET".equalsIgnoreCase(method)) {
                            // GET /api/users/{id}/characters
                            List<CharacterInfo> list = new ArrayList<>();
                            for (Integer cid : unlocked) {
                                CharacterInfo ci = characters.get(cid);
                                if (ci != null) list.add(ci);
                            }
                            sendJson(exchange, 200, toJsonList(list, StandaloneServer::charToJson));
                            return;
                        }

                        if (parts.length >= 6) {
                            int charId = Integer.parseInt(parts[5]);
                            if (parts.length == 6 && "POST".equalsIgnoreCase(method)) {
                                // POST /api/users/{userId}/characters/{charId}
                                unlocked.add(charId);
                                sendJson(exchange, 200, String.format(Locale.US,
                                    "{\"userId\":%d,\"characterId\":%d,\"unlocked\":true,\"unlockedAt\":\"%s\"}",
                                    userId, charId, getIsoTime()));
                                return;
                            }
                            if (parts.length == 7 && parts[6].equals("check")) {
                                boolean isU = unlocked.contains(charId);
                                sendJson(exchange, 200, String.format(Locale.US,
                                    "{\"userId\":%d,\"characterId\":%d,\"unlocked\":%b}", userId, charId, isU));
                                return;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            sendJson(exchange, 404, "{\"error\":\"Not found\"}");
        }
    }

    private static class LeaderboardsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOptions(exchange)) return;
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/api/leaderboards")) {
                if ("GET".equalsIgnoreCase(method)) {
                    String query = exchange.getRequestURI().getQuery();
                    String mode = "time_limit";
                    boolean top10 = true;
                    if (query != null) {
                        for (String p : query.split("&")) {
                            if (p.startsWith("mode=")) mode = p.substring(5);
                            if (p.startsWith("top10=")) top10 = Boolean.parseBoolean(p.substring(6));
                        }
                    }
                    List<ScoreRecord> filtered = new ArrayList<>();
                    for (ScoreRecord r : leaderboards) {
                        if (r.gameMode.equalsIgnoreCase(mode)) filtered.add(r);
                    }
                    filtered.sort((a, b) -> Integer.compare(b.score, a.score));
                    if (top10 && filtered.size() > 10) filtered = filtered.subList(0, 10);
                    sendJson(exchange, 200, toJsonList(filtered, StandaloneServer::scoreToJson));
                    return;
                } else if ("POST".equalsIgnoreCase(method)) {
                    String body = readBody(exchange);
                    Integer uid = extractJsonInt(body, "userId");
                    Integer cid = extractJsonInt(body, "characterId");
                    Integer score = extractJsonInt(body, "score");
                    String mode = extractJsonVal(body, "gameMode");

                    UserInfo u = (uid != null) ? users.get(uid) : null;
                    if (u == null) u = getOrCreateUser("ThuyThuSanMoi");
                    CharacterInfo c = (cid != null) ? characters.get(cid) : characters.get(2);
                    if (c == null) c = characters.get(2);
                    if (score == null) score = 0;
                    if (mode == null || mode.isEmpty()) mode = "time_limit";

                    ScoreRecord rec = new ScoreRecord(nextRecordId.getAndIncrement(), u, c, score, mode);
                    leaderboards.add(rec);
                    sendJson(exchange, 200, scoreToJson(rec));
                    return;
                }
            }

            sendJson(exchange, 404, "{\"error\":\"Not found\"}");
        }
    }

    private static class PvpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOptions(exchange)) return;
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/api/pvp/history") && "GET".equalsIgnoreCase(method)) {
                List<PvpRecord> list = new ArrayList<>(pvpHistory);
                list.sort((a, b) -> Integer.compare(b.matchId, a.matchId));
                sendJson(exchange, 200, toJsonList(list, StandaloneServer::pvpToJson));
                return;
            }

            if (path.equals("/api/pvp/record") && "POST".equalsIgnoreCase(method)) {
                String body = readBody(exchange);
                Integer p1Id = extractJsonInt(body, "player1Id");
                Integer c1Id = extractJsonInt(body, "player1CharacterId");
                Integer p2Id = extractJsonInt(body, "player2Id");
                Integer c2Id = extractJsonInt(body, "player2CharacterId");
                Integer winId = extractJsonInt(body, "winnerId");
                Integer dur = extractJsonInt(body, "matchDurationSeconds");

                UserInfo p1 = (p1Id != null) ? users.get(p1Id) : getOrCreateUser("Player 1");
                UserInfo p2 = (p2Id != null) ? users.get(p2Id) : getOrCreateUser("Player 2");
                UserInfo win = (winId != null) ? users.get(winId) : p1;
                CharacterInfo c1 = (c1Id != null) ? characters.get(c1Id) : characters.get(1);
                CharacterInfo c2 = (c2Id != null) ? characters.get(c2Id) : characters.get(2);
                int duration = (dur != null) ? dur : 120;

                PvpRecord rec = new PvpRecord(nextMatchId.getAndIncrement(), p1, c1, p2, c2, win, duration);
                pvpHistory.add(rec);
                sendJson(exchange, 200, pvpToJson(rec));
                return;
            }

            sendJson(exchange, 404, "{\"error\":\"Not found\"}");
        }
    }

    private static synchronized String generateUniqueRoomCode() {
        cleanExpiredRooms();
        Random rand = new Random();
        for (int i = 0; i < 9000; i++) {
            int code = 1000 + rand.nextInt(9000);
            String codeStr = String.valueOf(code);
            RoomInfo r = rooms.get(codeStr);
            if (r == null || "CLOSED".equals(r.status)) {
                return codeStr;
            }
        }
        return null;
    }

    private static void cleanExpiredRooms() {
        long now = System.currentTimeMillis();
        rooms.entrySet().removeIf(entry -> {
            RoomInfo r = entry.getValue();
            if ("CLOSED".equals(r.status)) return true;
            // WAITING timeout sau 60 giây không hoạt động
            if ("WAITING".equals(r.status) && (now - r.lastActiveAt > 60000)) {
                return true;
            }
            // IN_GAME / FINISHED timeout sau 10 phút
            if (now - r.lastActiveAt > 600000) {
                return true;
            }
            return false;
        });
    }

    private static class RoomsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOptions(exchange)) return;
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String sub = path.substring("/api/rooms".length());

            cleanExpiredRooms();

            // POST /api/rooms/create
            if (sub.equals("/create") && "POST".equalsIgnoreCase(method)) {
                String body = readBody(exchange);
                String username = extractJsonVal(body, "username");
                Integer charId = extractJsonInt(body, "characterId");
                if (username == null || username.trim().isEmpty()) {
                    username = "PlayerA";
                }
                if (charId == null) charId = 2; // Cá kiếm

                UserInfo hostUser = getOrCreateUser(username);
                CharacterInfo hostChar = characters.get(charId);
                if (hostChar == null) hostChar = characters.get(2);

                String code = generateUniqueRoomCode();
                if (code == null) {
                    sendJson(exchange, 500, "{\"error\":\"Không thể tạo thêm phòng lúc này (hết mã khả dụng)\"}");
                    return;
                }

                RoomInfo room = new RoomInfo(code, hostUser, hostChar);
                rooms.put(code, room);
                sendJson(exchange, 200, roomToJson(room));
                return;
            }

            // POST /api/rooms/join
            if (sub.equals("/join") && "POST".equalsIgnoreCase(method)) {
                String body = readBody(exchange);
                String roomCode = extractJsonVal(body, "roomCode");
                String username = extractJsonVal(body, "username");
                Integer charId = extractJsonInt(body, "characterId");

                if (roomCode == null || !roomCode.matches("^\\d{4}$")) {
                    sendJson(exchange, 400, "{\"error\":\"Mã phòng phải gồm đúng 4 chữ số (1000 - 9999)\"}");
                    return;
                }

                RoomInfo room = rooms.get(roomCode);
                if (room == null || "CLOSED".equals(room.status)) {
                    sendJson(exchange, 404, "{\"error\":\"Mã phòng không tồn tại hoặc phòng đã đóng\"}");
                    return;
                }

                if (!"WAITING".equals(room.status)) {
                    sendJson(exchange, 400, "{\"error\":\"Phòng đã bắt đầu trận đấu hoặc đã kết thúc\"}");
                    return;
                }

                if (username == null || username.trim().isEmpty()) {
                    username = "PlayerB";
                }
                UserInfo guestUser = getOrCreateUser(username);
                if (guestUser.userId == room.host.userId) {
                    sendJson(exchange, 400, "{\"error\":\"Bạn không thể tự vào phòng do chính mình tạo\"}");
                    return;
                }

                if (charId == null) charId = 1; // Tôm
                CharacterInfo guestChar = characters.get(charId);
                if (guestChar == null) guestChar = characters.get(1);

                room.guest = guestUser;
                room.guestCharacter = guestChar;
                room.status = "IN_GAME";
                room.lastActiveAt = System.currentTimeMillis();

                sendJson(exchange, 200, roomToJson(room));
                return;
            }

            // /api/rooms/{code} hoặc /api/rooms/{code}/leave hoặc /api/rooms/{code}/heartbeat
            String[] parts = sub.split("/");
            if (parts.length >= 2) {
                String code = parts[1];
                RoomInfo room = rooms.get(code);

                if (parts.length == 2 && "GET".equalsIgnoreCase(method)) {
                    if (room == null || "CLOSED".equals(room.status)) {
                        sendJson(exchange, 404, "{\"error\":\"Phòng không tồn tại hoặc đã đóng\"}");
                        return;
                    }
                    room.lastActiveAt = System.currentTimeMillis();
                    sendJson(exchange, 200, roomToJson(room));
                    return;
                }

                if (parts.length >= 3) {
                    String action = parts[2];
                    if ("heartbeat".equals(action) && "POST".equalsIgnoreCase(method)) {
                        if (room == null || "CLOSED".equals(room.status)) {
                            sendJson(exchange, 404, "{\"error\":\"Phòng không tồn tại\"}");
                            return;
                        }
                        room.lastActiveAt = System.currentTimeMillis();
                        sendJson(exchange, 200, "{\"success\":true,\"status\":\"" + room.status + "\"}");
                        return;
                    }

                    if ("leave".equals(action) && "POST".equalsIgnoreCase(method)) {
                        String body = readBody(exchange);
                        Integer userId = extractJsonInt(body, "userId");

                        if (room != null) {
                            if ("WAITING".equals(room.status)) {
                                room.status = "CLOSED";
                                rooms.remove(code);
                            } else {
                                if (userId != null && room.host != null && userId == room.host.userId) {
                                    room.hostExit = true;
                                }
                                if (userId != null && room.guest != null && userId == room.guest.userId) {
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
                            sendJson(exchange, 200, "{\"success\":true,\"message\":\"Đã rời phòng thành công\"}");
                        } else {
                            sendJson(exchange, 200, "{\"success\":true,\"message\":\"Phòng đã đóng\"}");
                        }
                        return;
                    }

                    if ("status".equals(action) && "POST".equalsIgnoreCase(method)) {
                        String body = readBody(exchange);
                        String newStatus = extractJsonVal(body, "status");
                        if (room != null && newStatus != null) {
                            room.status = newStatus;
                            room.lastActiveAt = System.currentTimeMillis();
                            if ("CLOSED".equals(newStatus)) {
                                rooms.remove(code);
                            }
                            sendJson(exchange, 200, roomToJson(room));
                        } else {
                            sendJson(exchange, 400, "{\"error\":\"Trạng thái không hợp lệ\"}");
                        }
                        return;
                    }
                }
            }

            if ((sub.isEmpty() || sub.equals("/")) && "GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, toJsonList(rooms.values(), StandaloneServer::roomToJson));
                return;
            }

            sendJson(exchange, 404, "{\"error\":\"Endpoint phòng không tồn tại\"}");
        }
    }

    // --- JSON SERIALIZERS & HELPERS ---

    private static boolean handleOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept");
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            return true;
        }
        return false;
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static <T> String toJsonList(Collection<T> items, java.util.function.Function<T, String> mapper) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (T item : items) {
            if (!first) sb.append(",");
            sb.append(mapper.apply(item));
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    private static String charToJson(CharacterInfo c) {
        return String.format(Locale.US,
            "{\"characterId\":%d,\"name\":\"%s\",\"baseSizeWidth\":%d,\"baseSizeHeight\":%d,\"baseSpeed\":%.2f,\"skillName\":\"%s\",\"skillDescription\":\"%s\",\"manaCost\":%d}",
            c.characterId, escape(c.name), c.baseSizeWidth, c.baseSizeHeight, c.baseSpeed, escape(c.skillName), escape(c.skillDescription), c.manaCost);
    }

    private static String configToJson(ConfigItem ci) {
        return String.format(Locale.US,
            "{\"configKey\":\"%s\",\"configValue\":\"%s\",\"description\":\"%s\",\"updatedAt\":\"%s\"}",
            escape(ci.configKey), escape(ci.configValue), escape(ci.description), ci.updatedAt);
    }

    private static String userToJson(UserInfo u) {
        return String.format(Locale.US, "{\"userId\":%d,\"username\":\"%s\",\"createdAt\":\"%s\"}", u.userId, escape(u.username), u.createdAt);
    }

    private static String scoreToJson(ScoreRecord r) {
        return String.format(Locale.US,
            "{\"recordId\":%d,\"user\":%s,\"character\":%s,\"score\":%d,\"gameMode\":\"%s\",\"playedAt\":\"%s\"}",
            r.recordId, userToJson(r.user), charToJson(r.character), r.score, escape(r.gameMode), r.playedAt);
    }

    private static String pvpToJson(PvpRecord p) {
        return String.format(Locale.US,
            "{\"matchId\":%d,\"player1\":%s,\"player1Character\":%s,\"player2\":%s,\"player2Character\":%s,\"winner\":%s,\"matchDurationSeconds\":%d,\"playedAt\":\"%s\"}",
            p.matchId, userToJson(p.player1), charToJson(p.player1Character), userToJson(p.player2), charToJson(p.player2Character), userToJson(p.winner), p.matchDurationSeconds, p.playedAt);
    }

    private static String roomToJson(RoomInfo r) {
        return String.format(Locale.US,
            "{\"roomCode\":\"%s\",\"status\":\"%s\",\"host\":%s,\"hostCharacter\":%s,\"guest\":%s,\"guestCharacter\":%s,\"createdAt\":%d,\"lastActiveAt\":%d,\"hostExit\":%b,\"guestExit\":%b}",
            r.roomCode,
            r.status,
            userToJson(r.host),
            r.hostCharacter != null ? charToJson(r.hostCharacter) : "null",
            r.guest != null ? userToJson(r.guest) : "null",
            r.guestCharacter != null ? charToJson(r.guestCharacter) : "null",
            r.createdAt,
            r.lastActiveAt,
            r.hostExit,
            r.guestExit
        );
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private static String extractJsonVal(String json, String key) {
        if (json == null) return null;
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return null;
        int colon = json.indexOf(":", idx);
        if (colon == -1) return null;
        int q1 = json.indexOf("\"", colon);
        if (q1 == -1) return null;
        int q2 = json.indexOf("\"", q1 + 1);
        if (q2 == -1) return null;
        return json.substring(q1 + 1, q2);
    }

    private static Integer extractJsonInt(String json, String key) {
        if (json == null) return null;
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return null;
        int colon = json.indexOf(":", idx);
        if (colon == -1) return null;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        if (start == end) return null;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================================
    // WEBSOCKET REALTIME SERVER (RFC 6455) - ZERO DEPENDENCY (PHASE 2)
    // =========================================================================

    public static class WebSocketSession {
        public final String roomCode;
        public final java.net.Socket socket;
        public final java.io.InputStream in;
        public final java.io.OutputStream out;

        public WebSocketSession(String room, java.net.Socket s) throws IOException {
            this.roomCode = room;
            this.socket = s;
            this.in = s.getInputStream();
            this.out = s.getOutputStream();
        }

        public synchronized void sendText(String text) {
            try {
                byte[] payload = text.getBytes(StandardCharsets.UTF_8);
                int len = payload.length;
                out.write(0x81); // FIN = 1, Text frame = 1
                if (len <= 125) {
                    out.write(len);
                } else if (len <= 65535) {
                    out.write(126);
                    out.write((len >> 8) & 0xFF);
                    out.write(len & 0xFF);
                } else {
                    out.write(127);
                    for (int i = 7; i >= 0; i--) {
                        out.write((int) ((len >> (8 * i)) & 0xFF));
                    }
                }
                out.write(payload);
                out.flush();
            } catch (Exception e) {
                close();
            }
        }

        public void close() {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private static void startWebSocketServer(int port) {
        new Thread(() -> {
            try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(port)) {
                while (!serverSocket.isClosed()) {
                    java.net.Socket client = serverSocket.accept();
                    new Thread(() -> handleWsClient(client)).start();
                }
            } catch (Exception e) {
                System.out.println("   [WebSocket] Khong the mo port " + port + ": " + e.getMessage());
            }
        }, "Hunter-WebSocket-Server").start();
    }

    private static void handleWsClient(java.net.Socket client) {
        WebSocketSession session = null;
        String roomCode = "default";
        try {
            java.io.InputStream in = client.getInputStream();
            java.io.OutputStream out = client.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

            String reqLine = reader.readLine();
            if (reqLine == null) { client.close(); return; }

            String secKey = null;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                    secKey = line.substring(line.indexOf(":") + 1).trim();
                }
            }

            if (secKey == null) {
                client.close();
                return;
            }

            // Trích xuất roomCode từ URI (ví dụ: /ws/pvp/1234 -> 1234)
            String[] parts = reqLine.split(" ");
            if (parts.length >= 2) {
                String path = parts[1];
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash != -1 && lastSlash < path.length() - 1) {
                    roomCode = path.substring(lastSlash + 1).trim();
                }
            }

            // Tính Sec-WebSocket-Accept
            String acceptKey = Base64.getEncoder().encodeToString(
                java.security.MessageDigest.getInstance("SHA-1")
                    .digest((secKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8))
            );

            // Gửi HTTP 101 Handshake
            String handshake = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
            out.write(handshake.getBytes(StandardCharsets.UTF_8));
            out.flush();

            session = new WebSocketSession(roomCode, client);
            final String fRoom = roomCode;
            final WebSocketSession fSession = session;
            roomWsSessions.computeIfAbsent(roomCode, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(session);

            // Vòng lặp đọc WebSocket Frames từ client
            while (!client.isClosed()) {
                int b1 = in.read();
                if (b1 == -1) break;
                int b2 = in.read();
                if (b2 == -1) break;

                int opcode = b1 & 0x0F;
                if (opcode == 8) { // Close frame
                    break;
                }

                boolean masked = (b2 & 0x80) != 0;
                long length = b2 & 0x7F;
                if (length == 126) {
                    length = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
                } else if (length == 127) {
                    length = 0;
                    for (int i = 0; i < 8; i++) {
                        length = (length << 8) | (in.read() & 0xFF);
                    }
                }

                byte[] mask = new byte[4];
                if (masked) {
                    for (int i = 0; i < 4; i++) mask[i] = (byte) in.read();
                }

                byte[] payload = new byte[(int) length];
                int readBytes = 0;
                while (readBytes < length) {
                    int r = in.read(payload, readBytes, (int) length - readBytes);
                    if (r == -1) break;
                    readBytes += r;
                }

                if (masked) {
                    for (int i = 0; i < length; i++) {
                        payload[i] ^= mask[i % 4];
                    }
                }

                if (opcode == 1) { // Text message
                    String message = new String(payload, StandardCharsets.UTF_8);
                    // Broadcast tới tất cả người chơi khác trong cùng phòng
                    Set<WebSocketSession> sessions = roomWsSessions.get(fRoom);
                    if (sessions != null) {
                        for (WebSocketSession s : sessions) {
                            if (s != fSession) {
                                s.sendText(message);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (session != null) {
                Set<WebSocketSession> sessions = roomWsSessions.get(roomCode);
                if (sessions != null) {
                    sessions.remove(session);
                    if (sessions.isEmpty()) roomWsSessions.remove(roomCode);
                }
                session.close();
            }
        }
    }
    }
}
