package huntersea;

import huntersea.model.CharacterType;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;

/**
 * Cổng giao tiếp mạng đồng bộ và bất đồng bộ giữa Game Client (Java Swing) và Backend RESTful API (Spring Boot).
 * Sử dụng java.net.HttpURLConnection chuẩn Java (không cần thư viện ngoài khi biên dịch với javac).
 * Tự động chuyển đổi callback về Swing Event Dispatch Thread để đảm bảo an toàn cho giao diện người dùng.
 * Hỗ trợ chế độ Offline Fallback hoàn toàn khi máy chủ tắt hoặc mất kết nối.
 */
public class BackendClient {
    private static BackendClient instance;

    public static final String DEFAULT_BASE_URL = "http://localhost:8080/api";
    private String baseUrl = DEFAULT_BASE_URL;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private boolean serverOnline = false;
    private Integer currentUserId = null;
    private String currentUsername = "ThuyThuSanMoi";
    private Integer player2UserId = null;

    // Danh sách id các nhân vật người chơi đã mở khóa (Mặc định: 1 - Tôm, 2 - Cá Kiếm)
    private final Set<Integer> unlockedCharacterIds = Collections.synchronizedSet(new HashSet<>(Arrays.asList(1, 2)));

    // Cấu hình luật chơi từ Backend
    private final GameConfigs configs = new GameConfigs();

    public static class GameConfigs {
        public double hakiBuffDurationSec = 5.0;
        public double squidInkBlindSec = 2.0;
        public int eatCountToLevelUp = 10;
        public double gameDurationTimedSec = 180.0;
        public double poisonFishStunSec = 2.0;
        public double pvePhase1DurationSec = 90.0;

        @Override
        public String toString() {
            return String.format(Locale.US,
                "Configs[Haki=%.1fs, Squid=%.1fs, EatToLevel=%d, Timed=%.0fs, PoisonStun=%.1fs, PvEPhase1=%.0fs]",
                hakiBuffDurationSec, squidInkBlindSec, eatCountToLevelUp,
                gameDurationTimedSec, poisonFishStunSec, pvePhase1DurationSec);
        }
    }

    public static class OnlineScoreEntry {
        public String username;
        public String characterName;
        public int score;
        public String gameMode;
        public String playedAt;

        public OnlineScoreEntry(String username, String characterName, int score, String gameMode, String playedAt) {
            this.username = username;
            this.characterName = characterName;
            this.score = score;
            this.gameMode = gameMode;
            this.playedAt = playedAt;
        }
    }

    public static class PvpMatchEntry {
        public int matchId;
        public String p1Name;
        public String p1Char;
        public String p2Name;
        public String p2Char;
        public String winnerName;
        public int durationSec;
        public String playedAt;

        public PvpMatchEntry(int matchId, String p1Name, String p1Char, String p2Name, String p2Char,
                             String winnerName, int durationSec, String playedAt) {
            this.matchId = matchId;
            this.p1Name = p1Name;
            this.p1Char = p1Char;
            this.p2Name = p2Name;
            this.p2Char = p2Char;
            this.winnerName = winnerName;
            this.durationSec = durationSec;
            this.playedAt = playedAt;
        }
    }

    private BackendClient() {
        // Kiểm tra kết nối và nạp cấu hình ban đầu bất đồng bộ
        refreshAllAsync(null);
    }

    public static synchronized BackendClient getInstance() {
        if (instance == null) {
            instance = new BackendClient();
        }
        return instance;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isServerOnline() {
        return serverOnline;
    }

    public Integer getCurrentUserId() {
        return currentUserId;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    public GameConfigs getConfigs() {
        return configs;
    }

    public Set<Integer> getUnlockedCharacterIds() {
        synchronized (unlockedCharacterIds) {
            return new HashSet<>(unlockedCharacterIds);
        }
    }

    public boolean isCharacterUnlocked(CharacterType type) {
        if (type == null) return true;
        return isCharacterUnlocked(type.getDbId());
    }

    public boolean isCharacterUnlocked(int charId) {
        synchronized (unlockedCharacterIds) {
            return unlockedCharacterIds.contains(charId);
        }
    }

    public int getCharacterDbId(CharacterType type) {
        return type != null ? type.getDbId() : 2;
    }

    private void runOnUI(Runnable task) {
        if (task == null) return;
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    // =========================================================================
    // HTTP UTILITIES (DÙNG HttpURLConnection AN TOÀN, KHÔNG SỢ LỖI NIO / LOOPBACK)
    // =========================================================================

    private String httpGet(String urlStr, int timeoutMs) throws Exception {
        URL url = new URI(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("Accept", "application/json");

        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } else {
            throw new Exception("HTTP Lỗi " + code);
        }
    }

    private String httpPost(String urlStr, String jsonBody, int timeoutMs) throws Exception {
        URL url = new URI(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");

        if (jsonBody != null && !jsonBody.isEmpty()) {
            conn.setDoOutput(true);
            byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }
        }

        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } else {
            throw new Exception("HTTP Lỗi " + code);
        }
    }

    // =========================================================================
    // 1. TỔNG HỢP / KIỂM TRA TRẠNG THÁI & CẤU HÌNH
    // =========================================================================

    public void refreshAllAsync(Consumer<Boolean> onFinished) {
        executor.submit(() -> {
            boolean online = checkServerStatusSync();
            this.serverOnline = online;
            if (online) {
                fetchConfigsSync();
                if (currentUsername != null && !currentUsername.trim().isEmpty()) {
                    loginOrRegisterSync(currentUsername);
                    if (currentUserId != null) {
                        fetchUnlockedCharactersSync(currentUserId);
                    }
                }
            }
            if (onFinished != null) {
                runOnUI(() -> onFinished.accept(online));
            }
        });
    }

    public void checkServerStatusAsync(Consumer<Boolean> callback) {
        executor.submit(() -> {
            boolean status = checkServerStatusSync();
            this.serverOnline = status;
            if (callback != null) {
                runOnUI(() -> callback.accept(status));
            }
        });
    }

    private boolean checkServerStatusSync() {
        try {
            String resp = httpGet(baseUrl + "/configs", 2000);
            return resp != null && !resp.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public void fetchConfigsAsync(Consumer<GameConfigs> callback) {
        executor.submit(() -> {
            boolean success = fetchConfigsSync();
            if (callback != null) {
                runOnUI(() -> callback.accept(configs));
            }
        });
    }

    private boolean fetchConfigsSync() {
        try {
            String resp = httpGet(baseUrl + "/configs", 2000);
            if (resp != null) {
                serverOnline = true;
                applyConfigsFromJson(resp);
                return true;
            }
        } catch (Exception e) {
            serverOnline = false;
        }
        return false;
    }

    private void applyConfigsFromJson(String json) {
        if (json == null || !json.trim().startsWith("[")) return;
        List<String> objects = splitJsonObjects(json);
        for (String obj : objects) {
            String key = extractStringFromJson(obj, "configKey");
            String val = extractStringFromJson(obj, "configValue");
            if (key == null || val == null) continue;
            try {
                switch (key.trim()) {
                    case "haki_buff_duration_sec":
                        configs.hakiBuffDurationSec = Double.parseDouble(val.trim());
                        break;
                    case "squid_ink_blind_sec":
                        configs.squidInkBlindSec = Double.parseDouble(val.trim());
                        break;
                    case "eat_count_to_level_up":
                        configs.eatCountToLevelUp = Integer.parseInt(val.trim());
                        break;
                    case "game_duration_timed_sec":
                        configs.gameDurationTimedSec = Double.parseDouble(val.trim());
                        break;
                    case "poison_fish_stun_sec":
                    case "poison_fish_slow_sec":
                        configs.poisonFishStunSec = Double.parseDouble(val.trim());
                        break;
                    case "pve_phase1_duration_sec":
                        configs.pvePhase1DurationSec = Double.parseDouble(val.trim());
                        break;
                }
            } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // 2. USERS & MỞ KHÓA NHÂN VẬT (`/api/users`)
    // =========================================================================

    public void loginOrRegisterAsync(String username, Consumer<Integer> onSuccess, Consumer<String> onError) {
        executor.submit(() -> {
            try {
                String trimmed = (username != null && !username.trim().isEmpty()) ? username.trim() : "ThuyThuSanMoi";
                Integer id = loginOrRegisterSync(trimmed);
                if (id != null) {
                    this.currentUserId = id;
                    this.currentUsername = trimmed;
                    serverOnline = true;
                    fetchUnlockedCharactersSync(id);
                    if (onSuccess != null) {
                        runOnUI(() -> onSuccess.accept(id));
                    }
                } else {
                    if (onError != null) {
                        runOnUI(() -> onError.accept("Không nhận được User ID từ máy chủ."));
                    }
                }
            } catch (Exception e) {
                serverOnline = false;
                if (onError != null) {
                    runOnUI(() -> onError.accept(e.getMessage()));
                }
            }
        });
    }

    private Integer loginOrRegisterSync(String username) {
        try {
            String json = "{\"username\":\"" + escapeJson(username) + "\"}";
            String resp = httpPost(baseUrl + "/users/login", json, 3000);
            if (resp != null) {
                serverOnline = true;
                return extractIntFromJson(resp, "userId");
            }
        } catch (Exception ignored) {
            serverOnline = false;
        }
        return null;
    }

    public void fetchUnlockedCharactersAsync(Consumer<Set<Integer>> callback) {
        executor.submit(() -> {
            Set<Integer> result = new HashSet<>(Arrays.asList(1, 2));
            if (currentUserId != null) {
                result = fetchUnlockedCharactersSync(currentUserId);
            }
            final Set<Integer> finalRes = result;
            if (callback != null) {
                runOnUI(() -> callback.accept(finalRes));
            }
        });
    }

    private Set<Integer> fetchUnlockedCharactersSync(int userId) {
        Set<Integer> result = new HashSet<>(Arrays.asList(1, 2));
        try {
            String body = httpGet(baseUrl + "/users/" + userId + "/characters", 3000);
            if (body != null) {
                serverOnline = true;
                List<String> list = splitJsonObjects(body);
                for (String charObj : list) {
                    Integer cid = extractIntFromJson(charObj, "characterId");
                    if (cid != null) {
                        result.add(cid);
                    }
                }
                synchronized (unlockedCharacterIds) {
                    unlockedCharacterIds.clear();
                    unlockedCharacterIds.addAll(result);
                }
            }
        } catch (Exception ignored) {
            serverOnline = false;
        }
        return result;
    }

    public void unlockCharacterAsync(int charId, Consumer<Boolean> callback) {
        executor.submit(() -> {
            boolean success = false;
            try {
                if (currentUserId == null) {
                    currentUserId = loginOrRegisterSync(currentUsername);
                }
                if (currentUserId != null) {
                    String resp = httpPost(baseUrl + "/users/" + currentUserId + "/characters/" + charId, "", 3000);
                    success = (resp != null);
                }
            } catch (Exception e) {
                serverOnline = false;
            }

            if (success) {
                synchronized (unlockedCharacterIds) {
                    unlockedCharacterIds.add(charId);
                }
            }
            final boolean finalSuccess = success;
            if (callback != null) {
                runOnUI(() -> callback.accept(finalSuccess));
            }
        });
    }

    // =========================================================================
    // 3. BẢNG XẾP HẠNG KỶ LỤC (`/api/leaderboards`)
    // =========================================================================

    public void submitScoreAsync(CharacterType charType, int score, String gameMode) {
        submitScoreAsync(charType, score, gameMode, null);
    }

    public void submitScoreAsync(CharacterType charType, int score, String gameMode, Consumer<Boolean> callback) {
        executor.submit(() -> {
            boolean success = false;
            try {
                if (currentUserId == null) {
                    currentUserId = loginOrRegisterSync(currentUsername);
                }
                if (currentUserId != null) {
                    int charId = getCharacterDbId(charType);
                    String json = String.format(Locale.US,
                            "{\"userId\":%d,\"characterId\":%d,\"score\":%d,\"gameMode\":\"%s\"}",
                            currentUserId, charId, score, escapeJson(gameMode));

                    String resp = httpPost(baseUrl + "/leaderboards", json, 3000);
                    success = (resp != null);
                    if (success) serverOnline = true;
                }
            } catch (Exception e) {
                serverOnline = false;
            }
            final boolean finalSuccess = success;
            if (callback != null) {
                runOnUI(() -> callback.accept(finalSuccess));
            }
        });
    }

    public void fetchLeaderboardAsync(String mode, boolean top10, Consumer<List<OnlineScoreEntry>> callback) {
        executor.submit(() -> {
            List<OnlineScoreEntry> result = new ArrayList<>();
            try {
                String uri = String.format("%s/leaderboards?mode=%s&top10=%b", baseUrl, mode, top10);
                String resp = httpGet(uri, 3000);
                if (resp != null) {
                    serverOnline = true;
                    result = parseLeaderboardJson(resp);
                }
            } catch (Exception e) {
                serverOnline = false;
            }
            final List<OnlineScoreEntry> finalRes = result;
            if (callback != null) {
                runOnUI(() -> callback.accept(finalRes));
            }
        });
    }

    // =========================================================================
    // 4. LỊCH SỬ ĐẤU PVP (`/api/pvp`)
    // =========================================================================

    public void recordPvpMatchAsync(CharacterType p1Char, CharacterType p2Char, int winnerPlayerNum, int durationSec) {
        recordPvpMatchAsync(p1Char, p2Char, winnerPlayerNum, durationSec, null);
    }

    public void recordPvpMatchAsync(CharacterType p1Char, CharacterType p2Char, int winnerPlayerNum, int durationSec, Consumer<Boolean> callback) {
        executor.submit(() -> {
            boolean success = false;
            try {
                if (currentUserId == null) {
                    currentUserId = loginOrRegisterSync(currentUsername);
                }
                int p1Id = currentUserId != null ? currentUserId : 1;

                if (player2UserId == null) {
                    player2UserId = loginOrRegisterSync("Player2_DoiThu");
                    if (player2UserId == null) player2UserId = 2;
                }
                int p2Id = player2UserId;
                int winId = (winnerPlayerNum == 1) ? p1Id : p2Id;

                int c1Id = getCharacterDbId(p1Char);
                int c2Id = getCharacterDbId(p2Char);

                String json = String.format(Locale.US,
                        "{\"player1Id\":%d,\"player1CharacterId\":%d,\"player2Id\":%d,\"player2CharacterId\":%d,\"winnerId\":%d,\"matchDurationSeconds\":%d}",
                        p1Id, c1Id, p2Id, c2Id, winId, Math.max(1, durationSec));

                String resp = httpPost(baseUrl + "/pvp/record", json, 3000);
                success = (resp != null);
                if (success) serverOnline = true;
            } catch (Exception e) {
                serverOnline = false;
            }
            final boolean finalSuccess = success;
            if (callback != null) {
                runOnUI(() -> callback.accept(finalSuccess));
            }
        });
    }

    public void fetchPvpHistoryAsync(Consumer<List<PvpMatchEntry>> callback) {
        executor.submit(() -> {
            List<PvpMatchEntry> result = new ArrayList<>();
            try {
                String resp = httpGet(baseUrl + "/pvp/history", 3000);
                if (resp != null) {
                    serverOnline = true;
                    result = parsePvpHistoryJson(resp);
                }
            } catch (Exception e) {
                serverOnline = false;
            }
            final List<PvpMatchEntry> finalRes = result;
            if (callback != null) {
                runOnUI(() -> callback.accept(finalRes));
            }
        });
    }

    // =========================================================================
    // 5. HELPER TRÍCH XUẤT JSON AN TOÀN
    // =========================================================================

    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static Integer extractIntFromJson(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx == -1) return null;
            int colon = json.indexOf(":", idx);
            if (colon == -1) return null;
            int start = colon + 1;
            while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
            if (start == end) return null;
            return Integer.parseInt(json.substring(start, end));
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractStringFromJson(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx == -1) return null;
            int colon = json.indexOf(":", idx);
            if (colon == -1) return null;
            int quoteStart = json.indexOf("\"", colon);
            if (quoteStart == -1) return null;
            int quoteEnd = json.indexOf("\"", quoteStart + 1);
            if (quoteEnd == -1) return null;
            return json.substring(quoteStart + 1, quoteEnd);
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractNestedObject(String json, String parentKey) {
        int pIdx = json.indexOf("\"" + parentKey + "\"");
        if (pIdx == -1) return null;
        int openBrace = json.indexOf("{", pIdx);
        if (openBrace == -1) return null;
        int closeBrace = findMatchingBrace(json, openBrace);
        if (closeBrace == -1) return null;
        return json.substring(openBrace, closeBrace + 1);
    }

    public static List<String> splitJsonObjects(String json) {
        List<String> list = new ArrayList<>();
        if (json == null) return list;
        int i = 0;
        while (i < json.length()) {
            int open = json.indexOf('{', i);
            if (open == -1) break;
            int close = findMatchingBrace(json, open);
            if (close == -1) break;
            list.add(json.substring(open, close + 1));
            i = close + 1;
        }
        return list;
    }

    private static int findMatchingBrace(String s, int openIndex) {
        int depth = 0;
        boolean inQuote = false;
        for (int i = openIndex; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            } else if (!inQuote) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private static List<OnlineScoreEntry> parseLeaderboardJson(String json) {
        List<OnlineScoreEntry> list = new ArrayList<>();
        List<String> objects = splitJsonObjects(json);
        for (String obj : objects) {
            String userObj = extractNestedObject(obj, "user");
            String username = userObj != null ? extractStringFromJson(userObj, "username") : "Người Chơi";
            if (username == null) username = "Người Chơi";

            String charObj = extractNestedObject(obj, "character");
            String character = charObj != null ? extractStringFromJson(charObj, "name") : "Cá";
            if (character == null) character = "Cá";

            Integer score = extractIntFromJson(obj, "score");
            String mode = extractStringFromJson(obj, "gameMode");
            String playedAt = extractStringFromJson(obj, "playedAt");

            if (score != null) {
                list.add(new OnlineScoreEntry(username, character, score, mode != null ? mode : "", playedAt != null ? playedAt : ""));
            }
        }
        return list;
    }

    private static List<PvpMatchEntry> parsePvpHistoryJson(String json) {
        List<PvpMatchEntry> list = new ArrayList<>();
        List<String> objects = splitJsonObjects(json);
        for (String obj : objects) {
            Integer mId = extractIntFromJson(obj, "matchId");
            int matchId = mId != null ? mId : 0;

            String p1Obj = extractNestedObject(obj, "player1");
            String p1Name = p1Obj != null ? extractStringFromJson(p1Obj, "username") : "Player 1";

            String p2Obj = extractNestedObject(obj, "player2");
            String p2Name = p2Obj != null ? extractStringFromJson(p2Obj, "username") : "Player 2";

            String winObj = extractNestedObject(obj, "winner");
            String winName = winObj != null ? extractStringFromJson(winObj, "username") : "Hòa";

            String c1Obj = extractNestedObject(obj, "player1Character");
            String c1Name = c1Obj != null ? extractStringFromJson(c1Obj, "name") : "Tướng 1";

            String c2Obj = extractNestedObject(obj, "player2Character");
            String c2Name = c2Obj != null ? extractStringFromJson(c2Obj, "name") : "Tướng 2";

            Integer dur = extractIntFromJson(obj, "matchDurationSeconds");
            int durationSec = dur != null ? dur : 0;

            String playedAt = extractStringFromJson(obj, "playedAt");

            list.add(new PvpMatchEntry(matchId, p1Name, c1Name, p2Name, c2Name, winName, durationSec, playedAt != null ? playedAt : ""));
        }
        return list;
    }

    public interface Consumer<T> {
        void accept(T t);
    }
}
