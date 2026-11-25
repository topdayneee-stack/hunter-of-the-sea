package huntersea;

import huntersea.model.*;
import java.util.*;

/**
 * Trung tâm điều hành logic của Hunter of the Sea:
 * - Vòng lặp 60 FPS
 * - Bản đồ chuẩn 48 cột x 27 hàng = 1296 ô (1920 x 1080 px)
 * - 4 Chế độ chơi: Basic, High Score (3m / Vô hạn), PvE (2 giai đoạn + Radar AI), PvP (2 người)
 * - Thuật toán cân bằng độ khó DDA (Dynamic Difficulty Adjustment)
 */
public class GameEngine {
    public static final int GRID_COLS = 48;
    public static final int GRID_ROWS = 27;
    public static final double TILE_SIZE = 40.0;
    public static final double MAP_WIDTH = GRID_COLS * TILE_SIZE;  // 1920.0
    public static final double MAP_HEIGHT = GRID_ROWS * TILE_SIZE; // 1080.0

    public enum GameMode {
        BASIC,
        HIGH_SCORE_TIMED,
        HIGH_SCORE_UNTIMED,
        PVE,
        PVP
    }

    public enum State {
        LOBBY,
        PLAYING,
        PAUSED,
        GAME_OVER,
        VICTORY
    }

    private State state = State.LOBBY;
    private GameMode currentMode = GameMode.BASIC;
    private AIBot.Difficulty pveDifficulty = AIBot.Difficulty.MEDIUM;

    // Thực thể người chơi
    private Player player1;
    private Player player2; // Chỉ dùng cho PVP
    private AIBot aiBot;    // Chỉ dùng cho PVE

    // Danh sách thực thể trong đại dương
    private final List<FishEntity> fishes = new ArrayList<>();
    private final List<HazardEntity> hazards = new ArrayList<>();
    private final List<ItemEntity> items = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();

    // Thời gian và tiến trình màn chơi
    private double gameTime = 0.0;
    private double modeTimer = 180.0; // Đếm ngược 3 phút cho High Score
    private double pvePhaseTimer = 120.0; // Giai đoạn 1 PvE kéo dài 90s-120s (120s)
    private boolean pvePhase2Active = false;

    // Bộ đếm sinh vật & vật phẩm
    private double fishSpawnTimer = 0.0;
    private double hazardSpawnTimer = 0.0;
    private double itemSpawnTimer = 0.0;
    private double pvpSkillDropTimer = 0.0;

    // Hiệu ứng đặc biệt
    private double screenShakeTimer = 0.0;
    private double screenShakeMagnitude = 0.0;
    private double blindTimer = 0.0; // Hiệu ứng tối đen màn hình 1.25s (khi dính mực)

    // Âm thanh & Kỷ lục
    private final SoundManager sound = SoundManager.getInstance();
    private final HighScoreManager highScores = HighScoreManager.getInstance();

    // Thông tin kết thúc trận
    private String endMessage = "";
    private String winnerName = "";
    private int pvpWinner = 0; // 0: Hòa / Chưa phân định, 1: Player 1 thắng, 2: Player 2 thắng

    public GameEngine() {}

    // =========================================================================
    // KHỞI ĐỘNG VÁN CHƠI THEO TỪNG CHẾ ĐỘ
    // =========================================================================

    public void startBasicMode(CharacterType selectedChar) {
        currentMode = GameMode.BASIC;
        resetCommonState();

        player1 = new Player(selectedChar, "Người Chơi", MAP_WIDTH / 2, MAP_HEIGHT / 2);
        player2 = null;
        aiBot = null;

        spawnInitialEcosystem(false);
        // Đặt người chơi ở vị trí an toàn, xa tất cả cá to
        double[] safePos = findSafeSpawnPosition();
        player1.setPosition(safePos[0], safePos[1]);
        state = State.PLAYING;
    }

    public void startHighScoreTimed(CharacterType selectedChar) {
        currentMode = GameMode.HIGH_SCORE_TIMED;
        resetCommonState();
        modeTimer = BackendClient.getInstance().getConfigs().gameDurationTimedSec;

        player1 = new Player(selectedChar, "Người Chơi", MAP_WIDTH / 2, MAP_HEIGHT / 2);
        player2 = null;
        aiBot = null;

        spawnInitialEcosystem(false);
        // Đặt người chơi ở vị trí an toàn, xa tất cả cá to
        double[] safePos = findSafeSpawnPosition();
        player1.setPosition(safePos[0], safePos[1]);
        state = State.PLAYING;
    }

    public void startHighScoreUntimed(CharacterType selectedChar) {
        currentMode = GameMode.HIGH_SCORE_UNTIMED;
        resetCommonState();

        player1 = new Player(selectedChar, "Người Chơi", MAP_WIDTH / 2, MAP_HEIGHT / 2);
        player2 = null;
        aiBot = null;

        spawnInitialEcosystem(false);
        // Đặt người chơi ở vị trí an toàn, xa tất cả cá to
        double[] safePos = findSafeSpawnPosition();
        player1.setPosition(safePos[0], safePos[1]);
        state = State.PLAYING;
    }

    public void startPvEMode(AIBot.Difficulty diff) {
        currentMode = GameMode.PVE;
        pveDifficulty = diff;
        resetCommonState();

        pvePhaseTimer = BackendClient.getInstance().getConfigs().pvePhase1DurationSec;
        pvePhase2Active = false;

        // Chọn ngẫu nhiên nhân vật cho cả Người chơi và Máy theo đúng đặc tả
        CharacterType[] allChars = CharacterType.values();
        CharacterType charP1 = allChars[(int) (Math.random() * allChars.length)];
        CharacterType charBot = allChars[(int) (Math.random() * allChars.length)];

        player1 = new Player(charP1, "Người Chơi", MAP_WIDTH * 0.3, MAP_HEIGHT / 2);
        player1.setLives(3);
        player1.setDynamicSpeedMultiplier(1.0);

        aiBot = new AIBot(charBot, MAP_WIDTH * 0.7, MAP_HEIGHT / 2, diff);
        aiBot.getPlayerEntity().setLives(3);
        aiBot.setSpeedMultiplier(1.0);
        aiBot.resetRadarRadius();
        player2 = null;

        // Bỏ cơ chế bom trong PvE theo yêu cầu đặc tả
        spawnInitialEcosystem(true);
        // Đặt người chơi ở vị trí an toàn, xa tất cả cá to
        double[] safePos = findSafeSpawnPosition();
        player1.setPosition(safePos[0], safePos[1]);
        state = State.PLAYING;
    }

    public void startPvPMode(CharacterType p1Char, CharacterType p2Char) {
        currentMode = GameMode.PVP;
        resetCommonState();
        pvpWinner = 0;
        modeTimer = 180.0; // 3 phút thi đấu PvP

        player1 = new Player(p1Char, "Player 1", MAP_WIDTH * 0.25, MAP_HEIGHT / 2);
        player2 = new Player(p2Char, "Player 2", MAP_WIDTH * 0.75, MAP_HEIGHT / 2);
        // Trong chế độ PvP: giảm số cá mồi cần ăn để tăng 1 cấp kích cỡ xuống 2 con
        player1.setFishPerSizeLevel(2);
        player2.setFishPerSizeLevel(2);
        aiBot = null;

        spawnInitialEcosystem(false);
        pvpSkillDropTimer = 10.0; // Xuất hiện kỹ năng rơi sau 10s
        state = State.PLAYING;
    }

    private void resetCommonState() {
        pvpWinner = 0;
        fishes.clear();
        hazards.clear();
        items.clear();
        particles.clear();
        gameTime = 0.0;
        screenShakeTimer = 0.0;
        blindTimer = 0.0;
        fishSpawnTimer = 0.0;
        hazardSpawnTimer = 0.0;
        itemSpawnTimer = 0.0;
        endMessage = "";
        winnerName = "";
        resetInputStates();
    }

    private void spawnInitialEcosystem(boolean noBombs) {
        // Sinh 20 cá ban đầu (giảm từ 35), ưu tiên cá nhỏ:
        // 60% cấp 1, 30% cấp 2, 10% cấp 3 — tránh quá nhiều cá to ngay đầu game
        for (int i = 0; i < 20; i++) {
            double r = Math.random();
            int lvl = r < 0.6 ? 1 : (r < 0.9 ? 2 : 3);
            double x = Math.random() * (MAP_WIDTH - 200) + 100;
            double y = Math.random() * (MAP_HEIGHT - 200) + 100;
            boolean movingRight = Math.random() > 0.5;
            fishes.add(new FishEntity(x, y, lvl, movingRight));
        }

        // Sinh chướng ngại vật ban đầu (nếu không phải PvE)
        if (!noBombs) {
            for (int i = 0; i < 4; i++) {
                hazards.add(new HazardEntity(HazardEntity.Type.BOMB, 
                    200 + Math.random() * (MAP_WIDTH - 400), 100 + Math.random() * (MAP_HEIGHT - 200)));
            }
            hazards.add(new HazardEntity(HazardEntity.Type.POISON_FISH, 100, 300));
            hazards.add(new HazardEntity(HazardEntity.Type.INK_BLOB, 500, 400));
            hazards.add(new HazardEntity(HazardEntity.Type.INK_BLOB, 1100, 500));
        }
    }

    // =========================================================================
    // VÒNG LẶP UPDATE CHÍNH (60 FPS)
    // =========================================================================

    public void update(double dt) {
        if (state != State.PLAYING) return;

        gameTime += dt;

        // Cập nhật rung màn hình & mù màn hình
        if (screenShakeTimer > 0) screenShakeTimer -= dt;
        if (blindTimer > 0) blindTimer -= dt;

        // 1. Cập nhật Player 1
        if (player1 != null && !player1.isDead()) {
            player1.update(dt, MAP_WIDTH, MAP_HEIGHT, fishes);
            checkElectricShockAura(player1);
            SkillType p1Skill = player1.pollLastTriggeredSkill();
            if (p1Skill != null) {
                castSkill(player1, p1Skill);
            }
        }

        // 2. Cập nhật Player 2 (PVP)
        if (player2 != null && !player2.isDead()) {
            player2.update(dt, MAP_WIDTH, MAP_HEIGHT, fishes);
            checkElectricShockAura(player2);
            SkillType p2Skill = player2.pollLastTriggeredSkill();
            if (p2Skill != null) {
                castSkill(player2, p2Skill);
            }
        }

        // 3. Cập nhật AI Bot (PVE)
        if (aiBot != null && !aiBot.getPlayerEntity().isDead()) {
            aiBot.setPhase2Active(pvePhase2Active);
            aiBot.update(dt, MAP_WIDTH, MAP_HEIGHT, player1, fishes, items, hazards);
            checkElectricShockAura(aiBot.getPlayerEntity());
            if (aiBot.checkAndConsumeSkillActivation()) {
                castSkill(aiBot.getPlayerEntity(), aiBot.getSkillType());
            }
        }

        // Vùng mực hắc ám (Octopus Ink) tác động hai chiều
        checkInkSanctuaryEffects();

        // Kỹ năng Kéo Cua (Crab Pull) cập nhật mỗi frame
        updateCrabPullSkills(dt);

        // 4. Cập nhật bầy cá thường AI
        for (int i = fishes.size() - 1; i >= 0; i--) {
            FishEntity f = fishes.get(i);
            f.update(dt, MAP_WIDTH, MAP_HEIGHT);
            if (f.isOffscreen(MAP_WIDTH)) {
                fishes.remove(i);
            }
        }

        // 5. Cập nhật chướng ngại vật
        for (HazardEntity h : hazards) {
            h.update(dt, MAP_WIDTH, MAP_HEIGHT);
        }

        // 6. Cập nhật vật phẩm rơi (truyền kích thước map để bounce biên)
        for (int i = items.size() - 1; i >= 0; i--) {
            ItemEntity it = items.get(i);
            it.update(dt, MAP_WIDTH, MAP_HEIGHT);
            if (it.isExpired()) {
                items.remove(i);
            }
        }

        // 7. Cập nhật hạt bọt nước & hiệu ứng
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.update(dt);
            if (p.isDead()) particles.remove(i);
        }
        if (Math.random() < 0.3) {
            particles.add(Particle.createBubble(Math.random() * MAP_WIDTH, MAP_HEIGHT - 20));
        }

        // 8. Xử lý va chạm
        handleCollisions();

        // 9. Sinh thêm cá, vật phẩm theo thuật toán DDA
        handleSpawning(dt);

        // 10. Tiến trình chế độ chơi riêng
        handleGameModeProgression(dt);
    }

    // Phạm vi vòng tròn điện (dùng cả trong logic lẫn render)
    public static final double ELECTRIC_RANGE = 12.0 * TILE_SIZE; // 480px — tăng phạm vi

    private void checkElectricShockAura(Player p) {
        if (!p.isElectricShockActive()) return;

        // Mỗi 1 giây phát 1 xung điện: làm choáng tất cả cá và đối thủ trong phạm vi
        boolean ticked = p.isElectricTickTriggered();
        if (ticked) {
            sound.playElectricShock();

            double range = ELECTRIC_RANGE;
            double px = p.getCenterX();
            double py = p.getCenterY();

            for (FishEntity f : fishes) {
                double dist = Math.hypot(f.getCenterX() - px, f.getCenterY() - py);
                if (dist <= range) {
                    applySkillToTarget(p, f, SkillType.RAY_SHOCK);
                }
            }

            // Áp dụng hai chiều giữa Player và AI Bot
            if (currentMode == GameMode.PVE && aiBot != null) {
                if (p == player1 && !aiBot.getPlayerEntity().isDead()) {
                    double dist = Math.hypot(aiBot.getPlayerEntity().getCenterX() - px, 
                                             aiBot.getPlayerEntity().getCenterY() - py);
                    if (dist <= range) {
                        applySkillToTarget(p, aiBot.getPlayerEntity(), SkillType.RAY_SHOCK);
                    }
                } else if (p == aiBot.getPlayerEntity() && player1 != null && !player1.isDead()) {
                    double dist = Math.hypot(player1.getCenterX() - px, player1.getCenterY() - py);
                    if (dist <= range) {
                        applySkillToTarget(p, player1, SkillType.RAY_SHOCK);
                    }
                }
            } else if (currentMode == GameMode.PVP && player1 != null && player2 != null) {
                Player enemy = (p == player1) ? player2 : player1;
                if (!enemy.isDead()) {
                    double dist = Math.hypot(enemy.getCenterX() - px, enemy.getCenterY() - py);
                    if (dist <= range) {
                        applySkillToTarget(p, enemy, SkillType.RAY_SHOCK);
                    }
                }
            }
        }
    }

    private void checkInkSanctuaryEffects() {
        if (player1 != null && player1.isInkActive()) {
            if (aiBot != null && !aiBot.getPlayerEntity().isDead() &&
                player1.isInsideInk(aiBot.getPlayerEntity().getCenterX(), aiBot.getPlayerEntity().getCenterY())) {
                aiBot.slow(0.5, 0.4);
            }
            if (player2 != null && !player2.isDead() &&
                player1.isInsideInk(player2.getCenterX(), player2.getCenterY())) {
                player2.slow(0.5, 0.4);
            }
            for (FishEntity f : fishes) {
                if (player1.isInsideInk(f.getCenterX(), f.getCenterY())) {
                    f.slow(0.5, 0.4);
                }
            }
        }
        if (aiBot != null && aiBot.getPlayerEntity().isInkActive()) {
            if (player1 != null && !player1.isDead() &&
                aiBot.getPlayerEntity().isInsideInk(player1.getCenterX(), player1.getCenterY())) {
                player1.slow(0.5, 0.4);
            }
            for (FishEntity f : fishes) {
                if (aiBot.getPlayerEntity().isInsideInk(f.getCenterX(), f.getCenterY())) {
                    f.slow(0.5, 0.4);
                }
            }
        }
    }

    /**
     * Phương thức thống nhất áp dụng hiệu ứng kỹ năng lên bất kỳ mục tiêu nào (Player, AIBot, FishEntity).
     * Hoạt động hai chiều giữa Player, AIBot và bầy cá FishEntity.
     */
    public boolean applySkillToTarget(Player caster, Object target, SkillType skillType) {
        if (caster == null || target == null || skillType == null) return false;

        Player targetPlayer = null;
        FishEntity targetFish = null;
        if (target instanceof Player) {
            targetPlayer = (Player) target;
        } else if (target instanceof AIBot) {
            targetPlayer = ((AIBot) target).getPlayerEntity();
        } else if (target instanceof FishEntity) {
            targetFish = (FishEntity) target;
        } else {
            return false;
        }

        if (targetPlayer == caster) return false;

        double tX = (targetPlayer != null) ? targetPlayer.getCenterX() : targetFish.getCenterX();
        double tY = (targetPlayer != null) ? targetPlayer.getCenterY() : targetFish.getCenterY();
        double cX = caster.getCenterX();
        double cY = caster.getCenterY();
        double dx = tX - cX;
        double dy = tY - cY;
        double dist = Math.max(1.0, Math.hypot(dx, dy));
        double dirX = dx / dist;
        double dirY = dy / dist;

        boolean targetInvincible = (targetPlayer != null) ? targetPlayer.isInvincible() : targetFish.isInvincible();
        boolean targetShield = (targetPlayer != null) ? targetPlayer.isShieldActive() : targetFish.isShieldActive();

        if (targetShield || targetInvincible) {
            if (targetPlayer != null && targetPlayer.isTurtleShieldActive()) {
                caster.stun(1.5);
                caster.applyKnockback(-dirX * 300.0, -dirY * 300.0);
                sound.playHit();
                shakeScreen(0.2, 7.0);
                particles.add(Particle.createFloatingText(caster.getCenterX(), caster.getCenterY(),
                    "✦ PHẢN ĐÒN MAI RÙA! ✦", java.awt.Color.ORANGE));
            }
            return false;
        }

        switch (skillType) {
            case SWORD_DASH:
                if (targetPlayer != null) {
                    targetPlayer.stun(1.2);
                    targetPlayer.slow(2.5, 0.4);
                    targetPlayer.applyKnockback(dirX * 350.0, dirY * 350.0);
                    particles.add(Particle.createFloatingText(tX, tY, "⚔ TRÚNG ĐÒN LƯỚT! ⚔", java.awt.Color.YELLOW));
                } else {
                    targetFish.stun(1.5);
                    targetFish.slow(2.5, 0.3);
                    targetFish.applyKnockback(dirX * 400.0, dirY * 400.0);
                }
                sound.playHit();
                shakeScreen(0.15, 5.0);
                return true;

            case RAY_SHOCK:
                if (targetPlayer != null) {
                    targetPlayer.stun(1.5);
                    targetPlayer.slow(2.0, 0.5);
                    if (targetPlayer.isCrabPulling()) {
                        targetPlayer.stopCrabPull();
                    }
                    particles.add(Particle.createFloatingText(tX, tY, "⚡ TÊ LIỆT ĐIỆN! ⚡", java.awt.Color.CYAN));
                } else {
                    targetFish.stun(1.5);
                    targetFish.slow(2.0, 0.5);
                    if (targetFish.isBeingPulledByCrab()) {
                        targetFish.setPullingCrab(null);
                    }
                }
                for (int i = 0; i < 4; i++) {
                    particles.add(new Particle(Particle.ParticleType.ELECTRIC_ZAP,
                        tX + (Math.random() - 0.5) * 30, tY + (Math.random() - 0.5) * 30,
                        0, 0, 18, 0.35, java.awt.Color.CYAN, null));
                }
                return true;

            case CRAB_PINCER:
                if (targetPlayer != null) {
                    targetPlayer.applyPull(cX, cY, 220.0);
                    targetPlayer.stun(1.8);
                    targetPlayer.slow(3.0, 0.3);
                    particles.add(Particle.createFloatingText(tX, tY, "✂ BỊ KẸP CHẶT! ✂", java.awt.Color.RED));
                } else {
                    targetFish.applyPull(cX, cY, 250.0);
                    targetFish.stun(2.0);
                    targetFish.slow(3.0, 0.2);
                    if (targetFish.getLevel() <= caster.getEffectiveSizeLevel()) {
                        if (targetFish.markEaten()) {
                            caster.onEatFish(targetFish.getLevel(), targetFish.getLevel() * 15);
                            fishes.remove(targetFish);
                        }
                    }
                }
                sound.playHit();
                shakeScreen(0.2, 6.0);
                return true;

            case OCTOPUS_INK:
                if (targetPlayer != null) {
                    targetPlayer.slow(4.0, 0.4);
                    targetPlayer.triggerPoison();
                    if (targetPlayer == player1) {
                        blindTimer = Math.max(blindTimer, BackendClient.getInstance().getConfigs().squidInkBlindSec);
                    }
                    particles.add(Particle.createFloatingText(tX, tY, "☁ NHIỄM MỰC ĐỘC! ☁", java.awt.Color.MAGENTA));
                } else {
                    targetFish.slow(4.0, 0.4);
                    targetFish.triggerPoison(4.0);
                }
                return true;

            case TURTLE_SHELL:
                if (targetPlayer != null) {
                    targetPlayer.stun(1.5);
                    targetPlayer.applyKnockback(dirX * 380.0, dirY * 380.0);
                } else {
                    targetFish.stun(1.5);
                    targetFish.applyKnockback(dirX * 420.0, dirY * 420.0);
                }
                return true;

            case SHRIMP_BLINK:
                if (targetPlayer != null) {
                    targetPlayer.stun(0.8);
                    targetPlayer.applyKnockback(dirX * 220.0, dirY * 220.0);
                } else {
                    targetFish.stun(0.8);
                    targetFish.applyKnockback(dirX * 250.0, dirY * 250.0);
                }
                return true;

            case REMORA_ATTACH:
                if (targetPlayer != null) {
                    targetPlayer.slow(4.0, 0.5);
                    particles.add(Particle.createFloatingText(tX, tY, "🪸 BỊ KÝ SINH! 🪸", java.awt.Color.GREEN));
                } else {
                    targetFish.slow(4.0, 0.5);
                }
                return true;

            case VORTEX_PULL:
                if (targetPlayer != null) {
                    targetPlayer.applyPull(cX, cY, 300.0);
                    targetPlayer.slow(2.5, 0.4);
                } else {
                    targetFish.applyPull(cX, cY, 320.0);
                    targetFish.slow(2.5, 0.4);
                }
                return true;

            case POISON_STING:
                if (targetPlayer != null) {
                    targetPlayer.triggerPoison();
                    targetPlayer.slow(3.5, 0.4);
                    targetPlayer.stun(0.8);
                } else {
                    targetFish.triggerPoison(4.0);
                    targetFish.slow(3.5, 0.4);
                    targetFish.stun(0.8);
                }
                return true;

            case ROAR_FEAR:
                if (targetPlayer != null) {
                    targetPlayer.applyKnockback(dirX * 350.0, dirY * 350.0);
                    targetPlayer.stun(1.2);
                } else {
                    targetFish.applyKnockback(dirX * 380.0, dirY * 380.0);
                    targetFish.stun(1.2);
                }
                return true;

            default:
                return false;
        }
    }

    /**
     * Kích hoạt toàn diện kỹ năng: áp dụng self-buff cho caster, hiệu ứng hạt/âm thanh,
     * và phân phối mục tiêu hai chiều (FishEntity, Player, AIBot).
     */
    public void castSkill(Player caster, SkillType skill) {
        if (caster == null || skill == null) return;

        // 1. Áp dụng self-buff & hạt/âm thanh
        applySkillEffects(caster, skill);

        // Kỹ năng Kẹp Cua: Cơ chế riêng biệt khóa và kéo tối đa 2 mục tiêu nhỏ hơn dần dần
        if (skill == SkillType.CRAB_PINCER) {
            initiateCrabPull(caster);
            return;
        }

        // 2. Phân phối mục tiêu trong phạm vi kỹ năng
        double range = skill.getRange();
        double cx = caster.getCenterX();
        double cy = caster.getCenterY();

        // Target: FishEntity
        for (int i = fishes.size() - 1; i >= 0; i--) {
            FishEntity f = fishes.get(i);
            double dist = Math.hypot(f.getCenterX() - cx, f.getCenterY() - cy);
            if (dist <= range) {
                applySkillToTarget(caster, f, skill);
            }
        }

        // Target hai chiều giữa Player 1 và AI Bot trong PvE
        if (currentMode == GameMode.PVE && aiBot != null) {
            if (caster == player1 && !aiBot.getPlayerEntity().isDead()) {
                double dist = Math.hypot(aiBot.getPlayerEntity().getCenterX() - cx, 
                                         aiBot.getPlayerEntity().getCenterY() - cy);
                if (dist <= range) {
                    applySkillToTarget(caster, aiBot.getPlayerEntity(), skill);
                }
            } else if (caster == aiBot.getPlayerEntity() && player1 != null && !player1.isDead()) {
                double dist = Math.hypot(player1.getCenterX() - cx, player1.getCenterY() - cy);
                if (dist <= range) {
                    applySkillToTarget(caster, player1, skill);
                }
            }
        }

        // Target hai chiều trong PvP
        if (currentMode == GameMode.PVP && player1 != null && player2 != null) {
            Player enemy = (caster == player1) ? player2 : player1;
            if (!enemy.isDead()) {
                double dist = Math.hypot(enemy.getCenterX() - cx, enemy.getCenterY() - cy);
                if (dist <= range) {
                    applySkillToTarget(caster, enemy, skill);
                }
            }
        }
    }

    /**
     * Kích hoạt hiệu ứng âm thanh và hạt đặc trưng cho SkillType
     */
    public void applySkillEffects(Player p, SkillType skill) {
        if (p == null || skill == null) return;
        sound.playPowerUp();
        particles.add(Particle.createFloatingText(p.getCenterX(), p.getCenterY() - 25,
            "✦ " + skill.getName().toUpperCase() + " ✦", java.awt.Color.CYAN));

        switch (skill) {
            case SHRIMP_BLINK:
                sound.playDash();
                for (int i = 0; i < 8; i++) {
                    particles.add(Particle.createBubble(p.getCenterX() + (Math.random() - 0.5) * 40,
                                                        p.getCenterY() + (Math.random() - 0.5) * 40));
                }
                break;
            case SWORD_DASH:
                sound.playDash();
                shakeScreen(0.2, 5.0);
                break;
            case OCTOPUS_INK:
                sound.playInkSplash();
                for (int i = 0; i < 8; i++) {
                    particles.add(new Particle(Particle.ParticleType.INK_SMOKE,
                        p.getCenterX(), p.getCenterY(),
                        (Math.random() - 0.5) * 60, (Math.random() - 0.5) * 60,
                        80, 2.0, java.awt.Color.BLACK, null));
                }
                break;
            case RAY_SHOCK:
                sound.playElectricShock();
                for (int i = 0; i < 6; i++) {
                    particles.add(new Particle(Particle.ParticleType.ELECTRIC_ZAP,
                        p.getCenterX() + (Math.random() - 0.5) * 40,
                        p.getCenterY() + (Math.random() - 0.5) * 40,
                        0, 0, 20, 0.4, java.awt.Color.CYAN, null));
                }
                break;
            case TURTLE_SHELL:
                shakeScreen(0.15, 4.0);
                break;
            default:
                break;
        }
    }

    public void triggerPlayerSkill(Player p) {
        if (p == null || !p.canUseSkill()) return;
        SkillType skill = SkillType.fromCharacter(p.getCharacterType());
        p.triggerCharacterSkill(fishes, MAP_WIDTH, MAP_HEIGHT);
        castSkill(p, skill);
    }

    public void initiateCrabPull(Player crab) {
        if (crab == null || crab.isDead()) return;
        crab.startCrabPull(3.5);

        double range = SkillType.CRAB_PINCER.getRange();
        double cx = crab.getCenterX();
        double cy = crab.getCenterY();
        int crabSize = crab.getEffectiveSizeLevel();

        class TargetDist {
            final Object entity;
            final double dist;
            TargetDist(Object entity, double dist) {
                this.entity = entity;
                this.dist = dist;
            }
        }

        List<TargetDist> candidates = new ArrayList<>();

        // 1. Quét FishEntity nhỏ hơn
        for (FishEntity f : fishes) {
            if (!f.isEaten() && !f.isShieldActive() && !f.isInvincible()) {
                if (f.getLevel() < crabSize || (crabSize == 1 && f.getLevel() == 1)) {
                    double d = Math.hypot(f.getCenterX() - cx, f.getCenterY() - cy);
                    if (d <= range) {
                        candidates.add(new TargetDist(f, d));
                    }
                }
            }
        }

        // 2. Quét đối thủ (PvE hoặc PvP)
        if (currentMode == GameMode.PVE && aiBot != null) {
            Player other = (crab == player1) ? aiBot.getPlayerEntity() : player1;
            if (other != null && !other.isDead() && other.getEffectiveSizeLevel() < crabSize &&
                !other.isInvincible() && !other.isShieldActive() && !other.isAttachedTo(crab)) {
                double d = Math.hypot(other.getCenterX() - cx, other.getCenterY() - cy);
                if (d <= range) {
                    candidates.add(new TargetDist(other, d));
                }
            }
        } else if (currentMode == GameMode.PVP && player1 != null && player2 != null) {
            Player other = (crab == player1) ? player2 : player1;
            if (other != null && !other.isDead() && other.getEffectiveSizeLevel() < crabSize &&
                !other.isInvincible() && !other.isShieldActive() && !other.isAttachedTo(crab)) {
                double d = Math.hypot(other.getCenterX() - cx, other.getCenterY() - cy);
                if (d <= range) {
                    candidates.add(new TargetDist(other, d));
                }
            }
        }

        candidates.sort((a, b) -> Double.compare(a.dist, b.dist));
        int count = Math.min(2, candidates.size());
        for (int i = 0; i < count; i++) {
            Object target = candidates.get(i).entity;
            crab.addCrabLockedTarget(target);
            if (target instanceof FishEntity) {
                FishEntity f = (FishEntity) target;
                f.setPullingCrab(crab);
                particles.add(Particle.createFloatingText(f.getCenterX(), f.getCenterY(),
                    "✂ BỊ CUA KÉO! ✂", java.awt.Color.RED));
            } else if (target instanceof Player) {
                Player p = (Player) target;
                p.setPullingCrab(crab);
                particles.add(Particle.createFloatingText(p.getCenterX(), p.getCenterY(),
                    "✂ BỊ CUA KÉO! ✂", java.awt.Color.RED));
            }
        }

        sound.playHit();
        shakeScreen(0.15, 4.0);
    }

    private void updateCrabPullSkills(double dt) {
        List<Player> crabs = new ArrayList<>();
        if (player1 != null && !player1.isDead() && player1.isCrabPulling()) crabs.add(player1);
        if (player2 != null && !player2.isDead() && player2.isCrabPulling()) crabs.add(player2);
        if (aiBot != null && !aiBot.getPlayerEntity().isDead() && aiBot.getPlayerEntity().isCrabPulling()) {
            crabs.add(aiBot.getPlayerEntity());
        }

        for (Player crab : crabs) {
            if (crab.isStunned()) {
                crab.stopCrabPull();
                continue;
            }

            List<Object> targets = new ArrayList<>(crab.getCrabLockedTargets());
            double cx = crab.getCenterX();
            double cy = crab.getCenterY();

            for (Object target : targets) {
                if (target instanceof FishEntity) {
                    FishEntity f = (FishEntity) target;
                    if (f.isEaten() || !fishes.contains(f) || f.getPullingCrab() != crab) {
                        crab.removeCrabLockedTarget(f);
                        f.setPullingCrab(null);
                        continue;
                    }
                    if (f.isShieldActive() || f.isInvincible()) {
                        crab.removeCrabLockedTarget(f);
                        f.setPullingCrab(null);
                        continue;
                    }

                    double fx = f.getCenterX();
                    double fy = f.getCenterY();
                    double dx = cx - fx;
                    double dy = cy - fy;
                    double dist = Math.hypot(dx, dy);

                    // Ăn khi chạm vào hitbox miệng của Cua
                    if (crab.getCollisionBounds().intersects(f.getCollisionBounds()) || dist <= (crab.getPixelWidth() * 0.45 + f.getWidth() * 0.35)) {
                        if (f.markEaten()) {
                            crab.onEatFish(f.getLevel(), f.getLevel() * 15);
                            fishes.remove(f);
                            crab.removeCrabLockedTarget(f);
                            f.setPullingCrab(null);
                            sound.playEat();
                            particles.add(Particle.createFloatingText(fx, fy,
                                "+" + (f.getLevel() * 15), java.awt.Color.YELLOW));
                        }
                    } else {
                        // Kéo dần dần về phía Cua
                        double pullSpeed = 160.0;
                        double step = Math.min(dist, pullSpeed * dt);
                        f.pushBack((dx / dist) * step, (dy / dist) * step);
                        if (Math.random() < 0.2) {
                            particles.add(Particle.createBubble(fx, fy));
                        }
                    }
                } else if (target instanceof Player) {
                    Player p = (Player) target;
                    if (p.isDead() || p.getPullingCrab() != crab) {
                        crab.removeCrabLockedTarget(p);
                        p.breakCrabTether();
                        continue;
                    }
                    // Phản đòn/phá kéo từ các skill phòng thủ hoặc ký sinh
                    if (p.isTurtleShieldActive() || p.isShieldActive() || p.isInkActive() || p.isAttachedTo(crab)) {
                        crab.removeCrabLockedTarget(p);
                        p.breakCrabTether();
                        continue;
                    }

                    double px = p.getCenterX();
                    double py = p.getCenterY();
                    double dx = cx - px;
                    double dy = cy - py;
                    double dist = Math.hypot(dx, dy);

                    // Ăn khi chạm vào hitbox miệng của Cua
                    if (crab.getCollisionBounds().intersects(p.getCollisionBounds()) || dist <= (crab.getPixelWidth() * 0.45 + p.getPixelWidth() * 0.35)) {
                        crab.removeCrabLockedTarget(p);
                        p.breakCrabTether();
                        crab.onEatFish(p.getEffectiveSizeLevel(), p.getScore());
                        sound.playEat();
                        if (currentMode == GameMode.PVE && aiBot != null) {
                            if (crab == player1) {
                                onPvEKill(aiBot.getPlayerEntity(), player1, true);
                            } else {
                                onPvEKill(player1, aiBot.getPlayerEntity(), false);
                            }
                        } else if (currentMode == GameMode.PVP) {
                            String msg = (crab == player1) ? "Player 1 dùng Càng Cua nuốt chửng Player 2!" : "Player 2 dùng Càng Cua nuốt chửng Player 1!";
                            killPlayer(p, msg);
                            triggerVictory(crab == player1 ? "Player 1" : "Player 2");
                        }
                    } else {
                        // Kéo dần dần về phía Cua
                        double pullSpeed = 160.0;
                        double step = Math.min(dist, pullSpeed * dt);
                        p.pushBack((dx / dist) * step, (dy / dist) * step);
                        if (Math.random() < 0.2) {
                            particles.add(Particle.createBubble(px, py));
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // XỬ LÝ VA CHẠM (COLLISIONS)
    // =========================================================================

    private void handleCollisions() {
        if (player1 != null && !player1.isDead()) {
            checkPlayerCollisionsWithEntities(player1);
        }
        if (player2 != null && !player2.isDead()) {
            checkPlayerCollisionsWithEntities(player2);
        }
        if (aiBot != null && !aiBot.getPlayerEntity().isDead()) {
            checkPlayerCollisionsWithEntities(aiBot.getPlayerEntity());
        }

        // Va chạm giữa Player 1 và Player 2 (PVP)
        if (currentMode == GameMode.PVP && player1 != null && player2 != null && !player1.isDead() && !player2.isDead()) {
            if (player1.isAttachedTo(player2) || player2.isAttachedTo(player1)) {
                // Đang ký sinh vật chủ: miễn nhiễm va chạm nuốt thịt lẫn nhau
            } else if (player1.getCollisionBounds().intersects(player2.getCollisionBounds())) {
                int s1 = player1.getEffectiveSizeLevel();
                int s2 = player2.getEffectiveSizeLevel();
                boolean p1Inv = player1.isInvincible();
                boolean p2Inv = player2.isInvincible();

                if (p1Inv && !p2Inv) {
                    killPlayer(player2, "Player 1 nuốt trọn Player 2 bằng Siêu Kỹ Năng!");
                    triggerVictory("Player 1");
                } else if (p2Inv && !p1Inv) {
                    killPlayer(player1, "Player 2 nuốt trọn Player 1 bằng Siêu Kỹ Năng!");
                    triggerVictory("Player 2");
                } else if (s1 > s2) {
                    killPlayer(player2, "Player 1 to hơn đã nuốt chửng Player 2!");
                    triggerVictory("Player 1");
                } else if (s2 > s1) {
                    killPlayer(player1, "Player 2 to hơn đã nuốt chửng Player 1!");
                    triggerVictory("Player 2");
                }
            }
        }

        // Va chạm giữa Player 1 và AI Bot (PVE)
        if (currentMode == GameMode.PVE && player1 != null && aiBot != null && !player1.isDead() && !aiBot.getPlayerEntity().isDead()) {
            Player bot = aiBot.getPlayerEntity();
            if (player1.isAttachedTo(bot) || bot.isAttachedTo(player1)) {
                // Đang ký sinh vật chủ: miễn nhiễm va chạm nuốt thịt lẫn nhau
            } else if (player1.getCollisionBounds().intersects(bot.getCollisionBounds())) {
                int s1 = player1.getEffectiveSizeLevel();
                int sBot = bot.getEffectiveSizeLevel();

                // 1. Phản đòn khiên mai rùa
                if (player1.isTurtleShieldActive() && !bot.isInvincible()) {
                    applySkillToTarget(player1, bot, SkillType.TURTLE_SHELL);
                } else if (bot.isTurtleShieldActive() && !player1.isInvincible()) {
                    applySkillToTarget(bot, player1, SkillType.TURTLE_SHELL);
                }
                // 2. Lướt cá kiếm (Sword Dash lethality)
                else if (player1.isSkillDashing() && !bot.isInvincible()) {
                    applySkillToTarget(player1, bot, SkillType.SWORD_DASH);
                    if (s1 >= sBot) onPvEKill(bot, player1, true);
                } else if (bot.isSkillDashing() && !player1.isInvincible()) {
                    applySkillToTarget(bot, player1, SkillType.SWORD_DASH);
                    if (sBot >= s1) onPvEKill(player1, bot, false);
                }
                // 3. Va chạm thường
                else if (player1.isInvincible() || s1 > sBot) {
                    onPvEKill(bot, player1, true);
                } else if (bot.isInvincible() || sBot > s1) {
                    onPvEKill(player1, bot, false);
                }
            }
        }
    }

    private void checkPlayerCollisionsWithEntities(Player p) {
        // 1. Va chạm với cá thường
        for (int i = fishes.size() - 1; i >= 0; i--) {
            FishEntity f = fishes.get(i);

            // BUG FIX: Bỏ qua con cá vật chủ khi đang ký sinh —
            // nếu không, collision bounds của player sẽ là bounds của chính host fish
            // và ngay lập tức "tự ăn" vật chủ trong mỗi frame.
            if (p.getAttachedFish() != null && f == p.getAttachedFish()) continue;

            if (p.getCollisionBounds().intersects(f.getCollisionBounds())) {
                int playerSize = p.getEffectiveSizeLevel();

                // ── MAI RÙA BẤT HOẠI ──────────────────────────────────────────
                // Ưu tiên check TRƯỚC: khi mai rùa active, cá to hơn hoặc bằng
                // sẽ bị CHOÁNG + ĐẨY RA (không bị ăn, không giết người chơi).
                // Cá nhỏ hơn vẫn bị ăn bình thường (rớt xuống nhánh dưới).
                if (p.isTurtleShieldActive() && f.getLevel() >= playerSize) {
                    f.stun(1.5); // Choáng 1.5 giây
                    // Lực đẩy: càng gần càng mạnh, tối thiểu 80px
                    double dx = f.getCenterX() - p.getCenterX();
                    double dy = f.getCenterY() - p.getCenterY();
                    double dist = Math.max(1.0, Math.hypot(dx, dy));
                    double pushForce = Math.max(80.0, 150.0 - dist * 0.3); // 80-150px
                    f.pushBack((dx / dist) * pushForce, (dy / dist) * pushForce);
                    sound.playHit();
                    shakeScreen(0.15, 6.0);
                    particles.add(Particle.createFloatingText(f.getCenterX(), f.getCenterY(),
                        "✦ CHOÁNG! MAI RÙA PHẢN ĐÒN ✦", java.awt.Color.ORANGE));
                }
                // ── ĂN CÁ THƯỜNG ─────────────────────────────────────────────
                // Ăn khi: cá nhỏ hơn/bằng, HOẶC đang bất tử từ skill khác
                // (không phải mai rùa — mai rùa đã xử lý ở trên)
                else if (playerSize >= f.getLevel()
                         || (p.isInvincible() && !p.isTurtleShieldActive())
                         || p.hasBiteLethality()) {
                    // Chỉ tính mồi ăn thành công, mỗi con chỉ tính 1 lần
                    if (f.markEaten()) {
                        int oldSize = p.getSizeLevel();
                        p.onEatFish(f.getLevel(), f.getLevel() * 10);
                        sound.playEat();
                        particles.add(Particle.createFloatingText(f.getCenterX(), f.getCenterY(),
                            "+" + (f.getLevel() * 10), java.awt.Color.YELLOW));
                        fishes.remove(i);

                        if (aiBot != null && p == aiBot.getPlayerEntity()) {
                            aiBot.addMana(1.0);
                        }

                        if (p.getSizeLevel() > oldSize) {
                            sound.playLevelUp();
                            particles.add(Particle.createFloatingText(p.getCenterX(), p.getCenterY() - 30,
                                "TIẾN HÓA SIZE " + p.getSizeLevel() + "!", java.awt.Color.GREEN));
                        }
                    }
                }
                // ── CÁ LỚN TẤN CÔNG ──────────────────────────────────────────
                else if (!p.isInvincible()) {
                    if (currentMode == GameMode.PVE) {
                        handlePvEDeath(p, "Bị cá khổng lồ Level " + f.getLevel() + " ăn thịt!");
                    } else {
                        killPlayer(p, "Bạn đã bị cá lớn Level " + f.getLevel() + " nuốt chửng!");
                    }
                }
            }
        }

        // 2. Va chạm với chướng ngại vật
        for (int i = hazards.size() - 1; i >= 0; i--) {
            HazardEntity h = hazards.get(i);
            if (p.getCollisionBounds().intersects(h.getBounds())) {
                switch (h.getType()) {
                    case BOMB:
                        // Bom nổ chết ngay lập tức (Game Over trong Basic)
                        h.setExploded(true);
                        sound.playBombExplode();
                        shakeScreen(0.5, 15.0);
                        particles.add(new Particle(Particle.ParticleType.EXPLOSION_SPARK, 
                            h.getCenterX(), h.getCenterY(), 0, 0, 80, 0.6, java.awt.Color.RED, null));
                        hazards.remove(i);

                        if (!p.isInvincible()) {
                            killPlayer(p, "Va phải thủy lôi! Bom nổ tung!");
                        }
                        break;

                    case POISON_FISH:
                        // Cá độc làm chậm — xóa khỏi map (đã bị ăn)
                        double poisonSlowDur = BackendClient.getInstance().getConfigs().poisonFishStunSec;
                        p.applyPoisonSlow(poisonSlowDur);
                        sound.playHit();
                        particles.add(Particle.createFloatingText(p.getCenterX(), p.getCenterY(), 
                            "☠️ TRÚNG ĐỘC! GIẢM 50% TỐC ĐỘ! ☠️", new java.awt.Color(180, 50, 255)));
                        hazards.remove(i);
                        break;

                    case INK_BLOB:
                        // Mực làm tối màn hình theo config
                        blindTimer = BackendClient.getInstance().getConfigs().squidInkBlindSec;
                        sound.playInkSplash();
                        particles.add(Particle.createFloatingText(p.getCenterX(), p.getCenterY(), 
                            "🦑 PHUN MỰC ĐEN! TỐI MÀN HÌNH!", new java.awt.Color(160, 160, 255)));
                        for (int k = 0; k < 6; k++) {
                            particles.add(new Particle(Particle.ParticleType.INK_SMOKE, 
                                h.getCenterX() + (Math.random() - 0.5) * 40, 
                                h.getCenterY() + (Math.random() - 0.5) * 40, 
                                (Math.random() - 0.5) * 30, (Math.random() - 0.5) * 30, 
                                60 + Math.random() * 40, 1.8, java.awt.Color.BLACK, null));
                        }
                        hazards.remove(i);
                        break;
                }
            }
        }

        // 3. Va chạm với vật phẩm nhặt
        for (int i = items.size() - 1; i >= 0; i--) {
            ItemEntity it = items.get(i);
            if (p.getBounds().intersects(it.getBounds())) { // Vật phẩm giữ full bounds để dễ nhặt
                if (it.getType() == ItemEntity.Type.BA_KHI) {
                    p.triggerBaKhi();
                    sound.playPowerUp();
                    particles.add(Particle.createFloatingText(p.getCenterX(), p.getCenterY(), 
                        "⚡ SIÊU BÁ KHÍ 5S! ⚡", java.awt.Color.CYAN));
                    items.remove(i);
                } else if (it.getType() == ItemEntity.Type.SKILL_DROP) {
                    if (p.addPvPSkill(it.getDroppedSkill())) {
                        sound.playPowerUp();
                        particles.add(Particle.createFloatingText(p.getCenterX(), p.getCenterY(), 
                            "+ CHIÊU " + it.getDroppedSkill().getName(), java.awt.Color.GREEN));
                        items.remove(i);
                    }
                }
            }
        }
    }

    private void onPvEKill(Player victim, Player killer, boolean isPlayerKiller) {
        sound.playHit();
        killer.addScore(200);

        if (!pvePhase2Active) {
            // Giai đoạn 1 (90s - 120s): Trừ 1 mạng và hồi sinh ở vị trí thuận lợi
            victim.decrementLife();
            // Đảm bảo không bên nào mất hết mạng trước khi sang Giai đoạn 2 để giữ game kéo dài tối thiểu 180s
            if (victim.getLives() <= 0) {
                victim.setLives(1);
            }
            respawnAtFavorableLocation(victim);
        } else {
            // Giai đoạn 2 (Sau 90-120s): 1 mạng duy nhất! Ai bị ăn lập tức GAME_OVER
            victim.setLives(0);
            if (isPlayerKiller) {
                triggerVictory("Người Chơi");
            } else {
                killPlayer(victim, "Máy đã hạ gục bạn trong Tử Chiến!");
            }
        }
    }

    private void handlePvEDeath(Player p, String reason) {
        sound.playHit();
        if (!pvePhase2Active) {
            // Giai đoạn 1: Trừ 1 mạng và hồi sinh ở vị trí thuận lợi, không mất hết mạng trước Giai đoạn 2
            p.decrementLife();
            if (p.getLives() <= 0) {
                p.setLives(1);
            }
            respawnAtFavorableLocation(p);
        } else {
            // Giai đoạn 2: Ai bị ăn lập tức GAME_OVER
            p.setLives(0);
            if (p == player1) {
                killPlayer(p, reason);
            } else {
                triggerVictory("Người Chơi");
            }
        }
    }

    private void respawnAtFavorableLocation(Player p) {
        // Hồi sinh ở vị trí thuận lợi (tọa độ xa các cá lớn hơn và gần nhiều cá con)
        double[] favorablePos = findFavorableSpawnPosition(p);
        p.setPosition(favorablePos[0], favorablePos[1]);
        sound.playPowerUp();
        particles.add(Particle.createFloatingText(p.getCenterX(), p.getCenterY(), 
            "HỒI SINH! (CÒN " + p.getLives() + " MẠNG)", java.awt.Color.MAGENTA));
    }

    /**
     * Tìm vị trí hồi sinh thuận lợi:
     * - Tọa độ xa các cá lớn hơn (tránh nguy hiểm bị ăn tiếp)
     * - Gần nhiều cá con (thuận tiện săn mồi để hồi phục và tiến hóa)
     */
    private double[] findFavorableSpawnPosition(Player p) {
        double bestX = 120;
        double bestY = MAP_HEIGHT * 0.5;
        double bestScore = -Double.MAX_VALUE;
        int mySize = p.getEffectiveSizeLevel();

        for (int attempt = 0; attempt < 100; attempt++) {
            double cx = 120 + Math.random() * (MAP_WIDTH - 240);
            double cy = 100 + Math.random() * (MAP_HEIGHT - 200);

            double minDistToBigger = Double.MAX_VALUE;
            int smallFishCount = 0;

            for (FishEntity f : fishes) {
                double d = Math.hypot(f.getCenterX() - cx, f.getCenterY() - cy);
                if (f.getLevel() > mySize) {
                    if (d < minDistToBigger) minDistToBigger = d;
                } else {
                    if (d <= 400.0) smallFishCount++;
                }
            }

            // Kiểm tra đối thủ người chơi / AI
            Player opp = (p == player1) ? (aiBot != null ? aiBot.getPlayerEntity() : player2) : player1;
            if (opp != null && !opp.isDead()) {
                double dOpp = Math.hypot(opp.getCenterX() - cx, opp.getCenterY() - cy);
                if (opp.getEffectiveSizeLevel() > mySize || opp.isBaKhiActive()) {
                    if (dOpp < minDistToBigger) minDistToBigger = dOpp;
                }
            }

            if (minDistToBigger == Double.MAX_VALUE) minDistToBigger = 1000.0;

            // Đánh giá điểm: xa cá lớn + gần nhiều cá con
            double score = minDistToBigger + (smallFishCount * 120.0);
            if (minDistToBigger < 200.0) score -= 5000.0; // Phạt nặng nếu quá gần cá lớn

            if (score > bestScore) {
                bestScore = score;
                bestX = cx;
                bestY = cy;
            }
        }
        return new double[]{bestX, bestY};
    }

    /**
     * Tìm vị trí xuất phát an toàn: thử 100 điểm ngẫu nhiên,
     * chọn điểm có khoảng cách tối thiểu đến cá cấp >= 2 là lớn nhất.
     */
    private double[] findSafeSpawnPosition() {
        double bestX = 120;
        double bestY = MAP_HEIGHT * 0.5;
        double bestMinDist = -1;

        for (int attempt = 0; attempt < 100; attempt++) {
            double cx = 120 + Math.random() * (MAP_WIDTH - 240);
            double cy = 100 + Math.random() * (MAP_HEIGHT - 200);

            // Tính khoảng cách tối thiểu tới cá to (cấp >= 2)
            double minDist = Double.MAX_VALUE;
            for (FishEntity f : fishes) {
                if (f.getLevel() >= 2) {
                    double d = Math.hypot(f.getCenterX() - cx, f.getCenterY() - cy);
                    if (d < minDist) minDist = d;
                }
            }
            if (minDist == Double.MAX_VALUE) minDist = 99999; // Không có cá to nào

            if (minDist > bestMinDist) {
                bestMinDist = minDist;
                bestX = cx;
                bestY = cy;
            }
        }
        return new double[]{bestX, bestY};
    }

    private void killPlayer(Player p, String reason) {
        p.setDead(true);

        // Chế độ PvP: 1 người chơi bị chết -> người còn lại giành chiến thắng
        if (currentMode == GameMode.PVP) {
            if (p == player1 && player2 != null && !player2.isDead()) {
                triggerPvPVictory(2, "Player 1 đã bị hạ gục! Player 2 CHIẾN THẮNG!");
                return;
            } else if (p == player2 && player1 != null && !player1.isDead()) {
                triggerPvPVictory(1, "Player 2 đã bị hạ gục! Player 1 CHIẾN THẮNG!");
                return;
            } else if (player1 != null && player2 != null) {
                int sc1 = player1.getScore();
                int sc2 = player2.getScore();
                int w = (sc1 > sc2) ? 1 : (sc2 > sc1 ? 2 : 0);
                triggerPvPVictory(w, "Cả hai đấu thủ đều ngã xuống! " + (w == 0 ? "TRẬN ĐẤU HÒA!" : "Player " + w + " thắng điểm!"));
                return;
            }
        }

        sound.playGameOver();
        this.endMessage = reason;

        if (currentMode == GameMode.HIGH_SCORE_TIMED) {
            highScores.addTimedScore(p.getName(), p.getScore(), p.getSizeLevel(), p.getCharacterType().getDisplayName());
        } else if (currentMode == GameMode.HIGH_SCORE_UNTIMED) {
            highScores.addUntimedScore(p.getName(), p.getScore(), p.getSizeLevel(), p.getCharacterType().getDisplayName());
        }

        state = State.GAME_OVER;
    }

    public void triggerPvPVictory(int winnerNum, String reason) {
        this.pvpWinner = winnerNum;
        this.endMessage = reason;
        this.winnerName = (winnerNum == 1) ? "Player 1" : (winnerNum == 2 ? "Player 2" : "Hòa");
        sound.playVictory();
        state = State.VICTORY;

        // Tự động lưu lịch sử trận đấu PvP lên CSDL Backend
        if (player1 != null && player2 != null) {
            BackendClient.getInstance().recordPvpMatchAsync(
                player1.getCharacterType(), 
                player2.getCharacterType(), 
                winnerNum, 
                Math.max(1, (int) gameTime)
            );
        }
    }

    private void triggerVictory(String winner) {
        if (currentMode == GameMode.PVP) {
            int winPlayerNum = (winner != null && winner.contains("1")) ? 1 : (winner != null && winner.contains("2") ? 2 : 0);
            triggerPvPVictory(winPlayerNum, winner + " CHIẾN THẮNG TUYỆT ĐỐI!");
            return;
        }

        this.winnerName = winner;
        this.endMessage = winner + " CHIẾN THẮNG TUYỆT ĐỐI!";
        sound.playVictory();
        state = State.VICTORY;
    }

    // =========================================================================
    // THUẬT TOÁN ĐIỀU HÀNH DDA & SPAWNING
    // =========================================================================

    private void handleSpawning(double dt) {
        fishSpawnTimer += dt;
        hazardSpawnTimer += dt;
        itemSpawnTimer += dt;
        pvpSkillDropTimer += dt;

        // 1. Thuật toán DDA điều tiết cá (đã giảm số lượng để bớt quá tải)
        double targetFishCount = 20; // Giảm từ 35 → 20
        double spawnInterval = 1.0;

        if (currentMode == GameMode.BASIC && player1 != null) {
            int eaten = player1.getFishEatenCount();
            if (eaten < 5 && gameTime > 30.0) {
                // Người chơi gặp khó khăn ("gà"): Sinh thêm nhiều cá con, giãn cá to
                targetFishCount = 28; // Giảm từ 45 → 28
                spawnInterval = 0.6;
            } else if (eaten > 20 && gameTime < 60.0) {
                // Người chơi tiến hóa quá nhanh: Giảm cá con, tăng cá to
                targetFishCount = 15; // Giảm từ 30 → 15
                spawnInterval = 1.4;
            }
        }

        // CÂN BẰNG ĐỘNG CHO CHẾ ĐỘ PVE: diff = player.eatenCount - aiBot.eatenCount
        if (currentMode == GameMode.PVE && player1 != null && aiBot != null) {
            int diff = player1.getFishEatenCount() - aiBot.getPlayerEntity().getFishEatenCount();

            // Ngưỡng rubber-banding theo độ khó:
            // EASY:   Buff AI nếu diff >= 5; Buff Player nếu diff <= -2
            // MEDIUM: Buff AI nếu diff >= 3; Buff Player nếu diff <= -3
            // HARD:   Buff AI nếu diff >= 2; Buff Player nếu diff <= -4
            int buffAiThreshold = 3;
            int buffPlayerThreshold = -3;
            switch (pveDifficulty) {
                case EASY:
                    buffAiThreshold = 5;
                    buffPlayerThreshold = -2;
                    break;
                case MEDIUM:
                    buffAiThreshold = 3;
                    buffPlayerThreshold = -3;
                    break;
                case HARD:
                    buffAiThreshold = 2;
                    buffPlayerThreshold = -4;
                    break;
            }

            if (diff >= buffAiThreshold) {
                // Người chơi áp đảo: Tăng tốc độ AIBot, mở rộng radarRadius, và ưu tiên spawn nhiều FishEntity nhỏ gần AIBot
                aiBot.setSpeedMultiplier(1.35);
                aiBot.setRadarRadius(aiBot.getDefaultRadarRadius() * 1.5);
                player1.setDynamicSpeedMultiplier(1.0);

                if (fishes.size() < targetFishCount && fishSpawnTimer >= spawnInterval * 0.75) {
                    fishSpawnTimer = 0.0;
                    spawnSmallFishNear(aiBot.getPlayerEntity());
                }
            } else if (diff <= buffPlayerThreshold) {
                // Người chơi yếu thế: Tăng tốc độ Player và ưu tiên spawn nhiều FishEntity nhỏ gần Player
                player1.setDynamicSpeedMultiplier(1.35);
                aiBot.setSpeedMultiplier(1.0);
                aiBot.resetRadarRadius();

                if (fishes.size() < targetFishCount && fishSpawnTimer >= spawnInterval * 0.75) {
                    fishSpawnTimer = 0.0;
                    spawnSmallFishNear(player1);
                }
            } else {
                // Cân bằng: Đưa tốc độ và radar về mặc định
                player1.setDynamicSpeedMultiplier(1.0);
                aiBot.setSpeedMultiplier(1.0);
                aiBot.resetRadarRadius();

                if (fishes.size() < targetFishCount && fishSpawnTimer >= spawnInterval) {
                    fishSpawnTimer = 0.0;
                    spawnNewFish();
                }
            }
        } else {
            if (fishes.size() < targetFishCount && fishSpawnTimer >= spawnInterval) {
                fishSpawnTimer = 0.0;
                spawnNewFish();
            }
        }

        // 2. Sinh chướng ngại vật: bom, cá độc & mực đen (Trừ PvE)
        if (currentMode != GameMode.PVE && hazards.size() < 6 && hazardSpawnTimer >= 7.0) {
            hazardSpawnTimer = 0.0;
            double r = Math.random();
            if (r < 0.40) {
                hazards.add(new HazardEntity(HazardEntity.Type.BOMB, 
                    Math.random() > 0.5 ? 50 : MAP_WIDTH - 100, Math.random() * (MAP_HEIGHT - 200) + 100));
            } else if (r < 0.70) {
                hazards.add(new HazardEntity(HazardEntity.Type.POISON_FISH, 50, Math.random() * MAP_HEIGHT));
            } else {
                hazards.add(new HazardEntity(HazardEntity.Type.INK_BLOB, 
                    Math.random() * (MAP_WIDTH - 300) + 150, MAP_HEIGHT - 120));
            }
        }

        // 3. Sinh ngọc Bá Khí — mỗi 5 giây, chỉ 1 viên duy nhất trên map
        long baKhiCount = items.stream()
            .filter(it -> it.getType() == ItemEntity.Type.BA_KHI)
            .count();
        if (baKhiCount == 0 && itemSpawnTimer >= 5.0) {
            itemSpawnTimer = 0.0;
            items.add(new ItemEntity(ItemEntity.Type.BA_KHI,
                100 + Math.random() * (MAP_WIDTH  - 200),
                100 + Math.random() * (MAP_HEIGHT - 200), null));
        }

        // 4. Sinh kỹ năng rơi trong PvP (sau mỗi 12-15 giây)
        // OCTOPUS_INK bị loại khỏi PvP — vùng mực không phù hợp với chế độ này
        if (currentMode == GameMode.PVP && pvpSkillDropTimer >= 14.0) {
            pvpSkillDropTimer = 0.0;
            java.util.List<SkillType> pvpSkills = new java.util.ArrayList<>();
            for (SkillType s : SkillType.values()) {
                if (s != SkillType.OCTOPUS_INK) pvpSkills.add(s);
            }
            SkillType randSkill = pvpSkills.get((int) (Math.random() * pvpSkills.size()));
            items.add(new ItemEntity(ItemEntity.Type.SKILL_DROP, 
                300 + Math.random() * (MAP_WIDTH - 600), 200 + Math.random() * (MAP_HEIGHT - 400), randSkill));
            sound.playPowerUp();
        }
    }

    private void spawnNewFish() {
        boolean fromLeft = Math.random() > 0.5;
        double spawnX = fromLeft ? -80 : MAP_WIDTH + 80;
        double spawnY = 40 + Math.random() * (MAP_HEIGHT - 120);

        // Giới hạn cấp độ cá tối đa: player_size + 1, không vượt quá 5
        // Giảm từ (size+2, max 7) → (size+1, max 5) để bớt cá to áp đảo
        int maxLvl = (player1 != null) ? Math.min(5, player1.getSizeLevel() + 1) : 3;
        int lvl = 1 + (int) (Math.random() * maxLvl);

        fishes.add(new FishEntity(spawnX, spawnY, lvl, fromLeft));
    }

    /**
     * Sinh cá con (Level 1) xung quanh một thực thể được chỉ định để hỗ trợ cân bằng động
     */
    private void spawnSmallFishNear(Player target) {
        if (target == null) return;
        double angle = Math.random() * Math.PI * 2;
        double dist = 120 + Math.random() * 200; // 120-320px
        double sx = target.getCenterX() + Math.cos(angle) * dist;
        double sy = target.getCenterY() + Math.sin(angle) * dist;
        sx = Math.max(40, Math.min(MAP_WIDTH - 80, sx));
        sy = Math.max(40, Math.min(MAP_HEIGHT - 80, sy));
        boolean movingRight = Math.random() > 0.5;
        fishes.add(new FishEntity(sx, sy, 1, movingRight));
    }

    // =========================================================================
    // QUẢN LÝ TIẾN TRÌNH CÁC CHẾ ĐỘ CHƠI
    // =========================================================================

    private void handleGameModeProgression(double dt) {
        switch (currentMode) {
            case BASIC:
                // Thắng khi đạt Max Size (Size 4) và hoàn thành ăn thêm 10 con cá
                if (player1 != null && player1.getSizeLevel() >= 4 && player1.getFishEatenCount() >= 40) {
                    triggerVictory(player1.getName());
                }
                break;

            case HIGH_SCORE_TIMED:
                modeTimer -= dt;
                if (modeTimer <= 0) {
                    modeTimer = 0;
                    killPlayer(player1, "Hết thời gian 3 phút! Điểm số xuất sắc!");
                }
                break;

            case HIGH_SCORE_UNTIMED:
                // Không giới hạn thời gian, độ khó tăng dần
                break;

            case PVE:
                if (!pvePhase2Active) {
                    pvePhaseTimer -= dt;
                    if (pvePhaseTimer <= 0) {
                        // Kích hoạt Giai đoạn 2: Tử chiến đi săn!
                        pvePhase2Active = true;
                        if (player1 != null) player1.setLives(1);
                        if (aiBot != null) aiBot.getPlayerEntity().setLives(1);

                        sound.playLevelUp();
                        shakeScreen(0.4, 10.0);
                        particles.add(Particle.createFloatingText(MAP_WIDTH / 2, 200, 
                            "⚔️ BẮT ĐẦU GIAI ĐOẠN 2: TỬ CHIẾN! 1 MẠNG DUY NHẤT! ⚔️", java.awt.Color.RED));
                    }
                }
                break;

            case PVP:
                modeTimer -= dt;
                if (modeTimer <= 0) {
                    modeTimer = 0;
                    handlePvPTimeOut();
                }
                break;
        }
    }

    private void handlePvPTimeOut() {
        if (player1 == null || player2 == null) return;
        int s1 = player1.getEffectiveSizeLevel();
        int s2 = player2.getEffectiveSizeLevel();
        int sc1 = player1.getScore();
        int sc2 = player2.getScore();

        int winner;
        String reason;
        if (s1 > s2) {
            winner = 1;
            reason = "Hết giờ! Player 1 chiến thắng nhờ kích thước lớn hơn (Size " + s1 + " vs " + s2 + ")!";
        } else if (s2 > s1) {
            winner = 2;
            reason = "Hết giờ! Player 2 chiến thắng nhờ kích thước lớn hơn (Size " + s2 + " vs " + s1 + ")!";
        } else {
            if (sc1 > sc2) {
                winner = 1;
                reason = "Hết giờ! Player 1 chiến thắng với điểm số cao hơn (" + sc1 + " vs " + sc2 + ")!";
            } else if (sc2 > sc1) {
                winner = 2;
                reason = "Hết giờ! Player 2 chiến thắng với điểm số cao hơn (" + sc2 + " vs " + sc1 + ")!";
            } else {
                winner = 0;
                reason = "Hết giờ! Hai bên có cùng kích thước và điểm số (" + sc1 + " điểm) - TRẬN ĐẤU HÒA!";
            }
        }
        triggerPvPVictory(winner, reason);
    }

    public void shakeScreen(double duration, double magnitude) {
        this.screenShakeTimer = duration;
        this.screenShakeMagnitude = magnitude;
    }

    // =========================================================================
    // GETTERS & CONTROLS
    // =========================================================================

    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public GameMode getCurrentMode() { return currentMode; }
    public Player getPlayer1() { return player1; }
    public Player getPlayer2() { return player2; }
    public AIBot getAiBot() { return aiBot; }
    public List<FishEntity> getFishes() { return fishes; }
    public List<HazardEntity> getHazards() { return hazards; }
    public List<ItemEntity> getItems() { return items; }
    public List<Particle> getParticles() { return particles; }
    public double getGameTime() { return gameTime; }
    public double getModeTimer() { return modeTimer; }
    public double getPvePhaseTimer() { return pvePhaseTimer; }
    public boolean isPvePhase2Active() { return pvePhase2Active; }
    public double getScreenShakeTimer() { return screenShakeTimer; }
    public double getScreenShakeMagnitude() { return screenShakeMagnitude; }
    public double getBlindTimer() { return blindTimer; }
    public String getEndMessage() { return endMessage; }
    public String getWinnerName() { return winnerName; }
    public int getPvpWinner() { return pvpWinner; }

    public void resetInputStates() {
        if (player1 != null) player1.resetInputStates();
        if (player2 != null) player2.resetInputStates();
        if (aiBot != null) aiBot.resetInputStates();
        this.blindTimer = 0.0;
        this.screenShakeTimer = 0.0;
    }

    public void pauseGame() {
        if (state == State.PLAYING) {
            this.state = State.PAUSED;
            resetInputStates();
        }
    }

    public void resumeGame() {
        if (state == State.PAUSED) {
            resetInputStates();
            this.state = State.PLAYING;
        }
    }

    public void stopMatch() {
        this.state = State.LOBBY;
        resetInputStates();
        resetCommonState();
        this.player1 = null;
        this.player2 = null;
        this.aiBot = null;
    }
}
