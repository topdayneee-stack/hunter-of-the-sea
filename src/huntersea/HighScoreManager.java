package huntersea;

import java.io.*;
import java.util.*;

/**
 * Quản lý lưu trữ và xếp hạng điểm cao (High Scores) cho 2 chế độ:
 * - Giới hạn 3 phút
 * - Không giới hạn thời gian (Vô tận)
 */
public class HighScoreManager {
    private static final String FILE_NAME = "highscores.dat";
    private static HighScoreManager instance;

    public static class Entry implements Serializable, Comparable<Entry> {
        private static final long serialVersionUID = 1L;
        private final String playerName;
        private final int score;
        private final int sizeLevel;
        private final String characterName;
        private final String dateStr;

        public Entry(String playerName, int score, int sizeLevel, String characterName) {
            this.playerName = playerName;
            this.score = score;
            this.sizeLevel = sizeLevel;
            this.characterName = characterName;
            this.dateStr = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date());
        }

        public String getPlayerName() { return playerName; }
        public int getScore() { return score; }
        public int getSizeLevel() { return sizeLevel; }
        public String getCharacterName() { return characterName; }
        public String getDateStr() { return dateStr; }

        @Override
        public int compareTo(Entry o) {
            return Integer.compare(o.score, this.score); // Sắp xếp giảm dần
        }
    }

    private List<Entry> timedScores = new ArrayList<>();
    private List<Entry> untimedScores = new ArrayList<>();

    private HighScoreManager() {
        load();
    }

    public static synchronized HighScoreManager getInstance() {
        if (instance == null) {
            instance = new HighScoreManager();
        }
        return instance;
    }

    public synchronized void addTimedScore(String name, int score, int sizeLevel, String charName) {
        timedScores.add(new Entry(name, score, sizeLevel, charName));
        Collections.sort(timedScores);
        if (timedScores.size() > 10) timedScores = timedScores.subList(0, 10);
        save();
        // Tự động đồng bộ lên CSDL Online nếu Server Backend đang bật
        try {
            huntersea.model.CharacterType charType = findCharacterByName(charName);
            BackendClient.getInstance().submitScoreAsync(charType, score, "time_limit", null);
        } catch (Exception ignored) {}
    }

    public synchronized void addUntimedScore(String name, int score, int sizeLevel, String charName) {
        untimedScores.add(new Entry(name, score, sizeLevel, charName));
        Collections.sort(untimedScores);
        if (untimedScores.size() > 10) untimedScores = untimedScores.subList(0, 10);
        save();
        // Tự động đồng bộ lên CSDL Online nếu Server Backend đang bật
        try {
            huntersea.model.CharacterType charType = findCharacterByName(charName);
            BackendClient.getInstance().submitScoreAsync(charType, score, "endless", null);
        } catch (Exception ignored) {}
    }

    private huntersea.model.CharacterType findCharacterByName(String name) {
        for (huntersea.model.CharacterType ct : huntersea.model.CharacterType.values()) {
            if (ct.getDisplayName().equalsIgnoreCase(name)) return ct;
        }
        return huntersea.model.CharacterType.SWORDFISH;
    }

    public List<Entry> getTimedScores() { return Collections.unmodifiableList(timedScores); }
    public List<Entry> getUntimedScores() { return Collections.unmodifiableList(untimedScores); }

    public int getBestScore() {
        int best = 0;
        for (Entry e : timedScores) if (e.score > best) best = e.score;
        for (Entry e : untimedScores) if (e.score > best) best = e.score;
        return best;
    }

    @SuppressWarnings("unchecked")
    private void load() {
        File file = new File(FILE_NAME);
        if (!file.exists()) return;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            timedScores = (List<Entry>) ois.readObject();
            untimedScores = (List<Entry>) ois.readObject();
        } catch (Exception e) {
            System.err.println("Không thể đọc file kỷ lục: " + e.getMessage());
        }
    }

    private void save() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE_NAME))) {
            oos.writeObject(timedScores);
            oos.writeObject(untimedScores);
        } catch (Exception e) {
            System.err.println("Không thể lưu file kỷ lục: " + e.getMessage());
        }
    }
}
