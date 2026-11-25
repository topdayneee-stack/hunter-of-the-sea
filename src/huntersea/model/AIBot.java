package huntersea.model;

import java.util.List;

/**
 * Trí tuệ nhân tạo Cá Máy (PvE Mode)
 * Được trang bị "Radar" vô hình quét bán kính X ô:
 * - Né tránh cá lớn
 * - Săn lùng con mồi nhỏ
 * - Nhặt ngọc Bá Khí
 * - Tự động tung chiêu khi đầy Mana
 */
public class AIBot {
    public enum Difficulty {
        EASY("Dễ", 10.0, 0.35, 0.7, 0.85, 0.7),
        MEDIUM("Vừa", 10.0, 0.20, 1.0, 1.0, 1.0),
        HARD("Khó", 10.0, 0.08, 1.4, 1.1, 1.3);

        private final String label;
        private final double baseRadarTiles;
        private final double reactionInterval;
        private final double radarScale;
        private final double speedScale;
        private final double manaRegenScale;

        Difficulty(String label, double baseRadarTiles, double reactionInterval,
                   double radarScale, double speedScale, double manaRegenScale) {
            this.label = label;
            this.baseRadarTiles = baseRadarTiles;
            this.reactionInterval = reactionInterval;
            this.radarScale = radarScale;
            this.speedScale = speedScale;
            this.manaRegenScale = manaRegenScale;
        }

        public String getLabel() { return label; }
        public double getRadarTiles() { return baseRadarTiles * radarScale; }
        public double getReactionInterval() { return reactionInterval; }
        public double getRadarScale() { return radarScale; }
        public double getSpeedScale() { return speedScale; }
        public double getManaRegenScale() { return manaRegenScale; }
    }

    private final Player playerEntity;
    private final Difficulty difficulty;
    private final CharacterType characterType;
    private final SkillType skillType;
    private final double baseSpeedScale;
    private boolean phase2Active = false;
    private double mana = 0.0;
    private double maxMana = 5.0;
    private double skillCooldown = 0.0;
    private boolean skillActivated = false;
    private double decisionTimer = 0.0;
    private double radarRadius;
    private final double defaultRadarRadius;
    private double lastPlayerX = Double.NaN;
    private double lastPlayerY = Double.NaN;
    private double playerVx = 0.0;
    private double playerVy = 0.0;

    public AIBot(CharacterType type, double startX, double startY, Difficulty diff) {
        this.characterType = type;
        this.skillType = SkillType.fromCharacter(type);
        this.playerEntity = new Player(type, "Máy (AI)", startX, startY);
        this.difficulty = diff;
        this.baseSpeedScale = diff.getSpeedScale();
        this.defaultRadarRadius = diff.getRadarScale() * (10.0 * Player.TILE_SIZE);
        this.radarRadius = this.defaultRadarRadius;
        this.mana = 0.0;
        this.maxMana = 5.0;
        this.skillCooldown = 0.0;
        setSpeedMultiplier(1.0);
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public boolean isPhase2Active() {
        return phase2Active;
    }

    public void setPhase2Active(boolean active) {
        this.phase2Active = active;
    }

    public CharacterType getCharacterType() {
        return characterType;
    }

    public SkillType getSkillType() {
        return skillType;
    }

    public double getMana() {
        return mana;
    }

    public void setMana(double mana) {
        this.mana = Math.max(0, Math.min(maxMana, mana));
    }

    public double getMaxMana() {
        return maxMana;
    }

    public void setMaxMana(double maxMana) {
        this.maxMana = maxMana;
    }

    public double getSkillCooldown() {
        return skillCooldown;
    }

    public void setSkillCooldown(double skillCooldown) {
        this.skillCooldown = skillCooldown;
    }

    public void addMana(double amount) {
        this.mana = Math.min(maxMana, this.mana + amount);
    }

    public void onEatFish(FishEntity fish) {
        addMana(1.0);
    }

    public void onEatFish(int fishLevel, int points) {
        addMana(1.0);
    }

    public boolean canUseSkill() {
        return mana >= maxMana && skillCooldown <= 0;
    }

    public boolean useSkill(List<FishEntity> fishes, double mapW, double mapH) {
        if (!canUseSkill()) return false;
        this.mana = 0.0;
        this.skillCooldown = 5.0;
        this.skillActivated = true;
        if (playerEntity != null) {
            if (playerEntity.canUseSkill()) {
                playerEntity.triggerCharacterSkill(fishes, mapW, mapH);
            } else {
                playerEntity.executeSkill(skillType, fishes, mapW, mapH);
            }
        }
        return true;
    }

    public boolean activateSkill(List<FishEntity> fishes, double mapW, double mapH) {
        return useSkill(fishes, mapW, mapH);
    }

    public boolean useSkill() {
        if (!canUseSkill()) return false;
        this.mana = 0.0;
        this.skillCooldown = 5.0;
        this.skillActivated = true;
        return true;
    }

    public boolean activateSkill() {
        return useSkill();
    }

    public boolean checkAndConsumeSkillActivation() {
        if (skillActivated) {
            skillActivated = false;
            return true;
        }
        return false;
    }

    public Player getPlayerEntity() {
        return playerEntity;
    }

    public double getRadarRadius() {
        return radarRadius;
    }

    public void setRadarRadius(double radarRadius) {
        this.radarRadius = radarRadius;
    }

    public double getDefaultRadarRadius() {
        return defaultRadarRadius;
    }

    public void resetRadarRadius() {
        this.radarRadius = this.defaultRadarRadius;
    }

    public void resetInputStates() {
        if (playerEntity != null) {
            playerEntity.resetInputStates();
        }
        resetRadarRadius();
        this.decisionTimer = 0.0;
        this.skillCooldown = 0.0;
        this.lastPlayerX = Double.NaN;
        this.lastPlayerY = Double.NaN;
        this.playerVx = 0.0;
        this.playerVy = 0.0;
    }

    public int getEatenCount() {
        return playerEntity.getFishEatenCount();
    }

    public void setSpeedMultiplier(double mult) {
        playerEntity.setDynamicSpeedMultiplier(baseSpeedScale * mult);
    }

    public void stun(double duration) { playerEntity.stun(duration); }
    public boolean isStunned() { return playerEntity.isStunned(); }
    public double getStunTimer() { return playerEntity.getStunTimer(); }

    public void slow(double duration) { playerEntity.slow(duration); }
    public void slow(double duration, double mult) { playerEntity.slow(duration, mult); }
    public boolean isSlowed() { return playerEntity.isSlowed(); }
    public double getSlowTimer() { return playerEntity.getSlowTimer(); }

    public void applyKnockback(double kx, double ky) { playerEntity.applyKnockback(kx, ky); }
    public void applyPull(double targetX, double targetY, double force) { playerEntity.applyPull(targetX, targetY, force); }
    public void pushBack(double pushX, double pushY) { playerEntity.pushBack(pushX, pushY); }

    public boolean isShieldActive() { return playerEntity.isShieldActive(); }
    public void setShieldActive(boolean active) { playerEntity.setShieldActive(active); }
    public boolean isInvincible() { return playerEntity.isInvincible(); }
    public boolean isSkillDashing() { return playerEntity.isSkillDashing(); }
    public boolean hasBiteLethality() { return playerEntity.hasBiteLethality(); }
    public void triggerPoison() { playerEntity.triggerPoison(); }
    public void triggerPoison(double duration) { playerEntity.triggerPoison(duration); }
    public void applyPoisonSlow(double duration) { playerEntity.applyPoisonSlow(duration); }
    public boolean isPoisoned() { return playerEntity.isPoisoned(); }
    public double getPoisonTimer() { return playerEntity.getPoisonTimer(); }

    public boolean isCrabPulling() { return playerEntity.isCrabPulling(); }
    public void stopCrabPull() { playerEntity.stopCrabPull(); }
    public void setPullingCrab(Player crab) { playerEntity.setPullingCrab(crab); }
    public Player getPullingCrab() { return playerEntity.getPullingCrab(); }
    public boolean isBeingPulledByCrab() { return playerEntity.isBeingPulledByCrab(); }
    public void breakCrabTether() { playerEntity.breakCrabTether(); }
    public void attachToHost(Player host) { playerEntity.attachToHost(host); }
    public Player getAttachedPlayerHost() { return playerEntity.getAttachedPlayerHost(); }
    public boolean isAttachedTo(Player host) { return playerEntity.isAttachedTo(host); }

    /**
     * Cập nhật trí tuệ nhân tạo mỗi frame
     */
    public void update(double dt, double mapW, double mapH, Player humanPlayer, 
                       List<FishEntity> fishes, List<ItemEntity> items) {
        update(dt, mapW, mapH, humanPlayer, fishes, items, null);
    }

    public void update(double dt, double mapW, double mapH, Player humanPlayer, 
                       List<FishEntity> fishes, List<ItemEntity> items, List<HazardEntity> hazards) {
        if (playerEntity.isDead()) return;

        // Ước lượng vận tốc người chơi cho predictive steering
        if (humanPlayer != null && !humanPlayer.isDead()) {
            if (!Double.isNaN(lastPlayerX) && dt > 0.0001) {
                playerVx = (humanPlayer.getCenterX() - lastPlayerX) / dt;
                playerVy = (humanPlayer.getCenterY() - lastPlayerY) / dt;
            }
            lastPlayerX = humanPlayer.getCenterX();
            lastPlayerY = humanPlayer.getCenterY();
        }

        // Giảm hồi chiêu kỹ năng
        if (skillCooldown > 0) {
            skillCooldown = Math.max(0, skillCooldown - dt);
        }

        // Tăng mana thụ động theo hệ số Difficulty (EASY: 0.7x, MEDIUM: 1.0x, HARD: 1.3x)
        mana = Math.min(maxMana, mana + dt * 0.5 * difficulty.getManaRegenScale());

        // Nếu đang bị choáng (stun): không thể ra quyết định hoặc tung chiêu
        if (playerEntity.isStunned()) {
            playerEntity.update(dt, mapW, mapH);
            return;
        }

        // Tự động giải cứu khi bị Cua kéo (Turtle, Ray, Octopus, Remora)
        if (playerEntity.isBeingPulledByCrab() && canUseSkill()) {
            if (skillType == SkillType.TURTLE_SHELL ||
                skillType == SkillType.RAY_SHOCK ||
                skillType == SkillType.OCTOPUS_INK ||
                skillType == SkillType.REMORA_ATTACH) {
                useSkill(fishes, mapW, mapH);
            }
        }

        // Auto-cast kỹ năng theo cấp độ khó
        if (canUseSkill()) {
            if (difficulty == Difficulty.EASY) {
                // EASY: Tung chiêu mù quáng ngay khi đầy Mana
                useSkill(fishes, mapW, mapH);
            } else if (difficulty == Difficulty.MEDIUM) {
                // MEDIUM: Tung chiêu khi đầy Mana VÀ có mục tiêu trong radarRadius
                if (hasTargetInRadar(humanPlayer, fishes)) {
                    useSkill(fishes, mapW, mapH);
                }
            }
            // HARD: Smart skill usage được điều khiển chi tiết trong makeDecision
        }

        decisionTimer -= dt;
        if (decisionTimer <= 0) {
            decisionTimer = difficulty.getReactionInterval();
            makeDecision(mapW, mapH, humanPlayer, fishes, items, hazards);
        }

        playerEntity.update(dt, mapW, mapH);
    }

    private boolean hasTargetInRadar(Player humanPlayer, List<FishEntity> fishes) {
        double botX = playerEntity.getCenterX();
        double botY = playerEntity.getCenterY();
        if (humanPlayer != null && !humanPlayer.isDead()) {
            double dist = Math.hypot(humanPlayer.getCenterX() - botX, humanPlayer.getCenterY() - botY);
            if (dist <= radarRadius) return true;
        }
        if (fishes != null) {
            for (FishEntity f : fishes) {
                double dist = Math.hypot(f.getCenterX() - botX, f.getCenterY() - botY);
                if (dist <= radarRadius) return true;
            }
        }
        return false;
    }

    private void makeDecision(double mapW, double mapH, Player humanPlayer, 
                             List<FishEntity> fishes, List<ItemEntity> items) {
        makeDecision(mapW, mapH, humanPlayer, fishes, items, null);
    }

    private void makeDecision(double mapW, double mapH, Player humanPlayer, 
                             List<FishEntity> fishes, List<ItemEntity> items, List<HazardEntity> hazards) {
        double botX = playerEntity.getCenterX();
        double botY = playerEntity.getCenterY();
        int botSize = playerEntity.getEffectiveSizeLevel();

        // 0. Phản xạ khi bị Cua kéo: ưu tiên tung chiêu thoát hiểm và bơi ngược hướng Cua
        if (playerEntity.isBeingPulledByCrab()) {
            if (canUseSkill() && (skillType == SkillType.TURTLE_SHELL ||
                                  skillType == SkillType.RAY_SHOCK ||
                                  skillType == SkillType.OCTOPUS_INK ||
                                  skillType == SkillType.REMORA_ATTACH)) {
                useSkill(fishes, mapW, mapH);
            }
            Player crab = playerEntity.getPullingCrab();
            if (crab != null) {
                double cdx = crab.getCenterX() - botX;
                double cdy = crab.getCenterY() - botY;
                double cdist = Math.hypot(cdx, cdy);
                if (cdist > 1.0) {
                    playerEntity.setMovement(-cdx / cdist, -cdy / cdist);
                    return;
                }
            }
        }

        // 1. Quét mối đe dọa trong phạm vi radarRadius
        boolean hasThreat = false;
        double fleeVx = 0.0;
        double fleeVy = 0.0;

        // Kiểm tra người chơi nếu lớn hơn hoặc đang bật Bá Khí
        if (humanPlayer != null && !humanPlayer.isDead()) {
            double dx = humanPlayer.getCenterX() - botX;
            double dy = humanPlayer.getCenterY() - botY;
            double dist = Math.hypot(dx, dy);

            if (dist < radarRadius && (humanPlayer.getEffectiveSizeLevel() > botSize || humanPlayer.isBaKhiActive())) {
                hasThreat = true;
                fleeVx -= (dx / dist);
                fleeVy -= (dy / dist);
            }
        }

        // Kiểm tra cá lớn hơn trong phạm vi radarRadius
        if (fishes != null) {
            for (FishEntity f : fishes) {
                double dx = f.getCenterX() - botX;
                double dy = f.getCenterY() - botY;
                double dist = Math.hypot(dx, dy);

                if (dist < radarRadius && f.getLevel() > botSize) {
                    hasThreat = true;
                    double weight = 1.0 + (1.0 - dist / radarRadius);
                    fleeVx -= (dx / dist) * weight;
                    fleeVy -= (dy / dist) * weight;
                }
            }
        }

        // Kiểm tra chướng ngại vật (HazardEntity: Bom / Cá độc)
        // EASY bỏ qua chướng ngại vật; MEDIUM & HARD né tránh
        if (difficulty != Difficulty.EASY && hazards != null && !playerEntity.isInvincible()) {
            for (HazardEntity h : hazards) {
                double dx = h.getCenterX() - botX;
                double dy = h.getCenterY() - botY;
                double dist = Math.hypot(dx, dy);

                if (dist < radarRadius) {
                    hasThreat = true;
                    double weight = (1.0 + (1.0 - dist / radarRadius)) * 1.6;
                    fleeVx -= (dx / dist) * weight;
                    fleeVy -= (dy / dist) * weight;
                }
            }
        }

        // ƯU TIÊN 1 (Né): Nếu có nguy hiểm -> di chuyển vector ngược lại để chạy trốn
        if (hasThreat) {
            double len = Math.hypot(fleeVx, fleeVy);
            if (len > 0.001) {
                playerEntity.setMovement(fleeVx / len, fleeVy / len);
            }

            // Tung chiêu thoát thân
            if (canUseSkill()) {
                useSkill(fishes, mapW, mapH);
            }
            return;
        }

        // GIAI ĐOẠN 2: HARD AI săn lùng Player bất chấp khoảng cách nếu aiBot.size > player.size
        if (difficulty == Difficulty.HARD && phase2Active && humanPlayer != null && !humanPlayer.isDead()) {
            if (botSize > humanPlayer.getEffectiveSizeLevel()) {
                double targetX = humanPlayer.getCenterX();
                double targetY = humanPlayer.getCenterY();
                double dist = Math.hypot(targetX - botX, targetY - botY);
                double botSpeed = playerEntity.getBaseSpeedPixels() * difficulty.getSpeedScale();
                double t = dist / Math.max(1.0, botSpeed);
                targetX += playerVx * t;
                targetY += playerVy * t;
                targetX = Math.max(40, Math.min(mapW - 40, targetX));
                targetY = Math.max(40, Math.min(mapH - 40, targetY));

                double dx = targetX - botX;
                double dy = targetY - botY;
                double len = Math.hypot(dx, dy);
                if (len > 0.001) {
                    playerEntity.setMovement(dx / len, dy / len);
                }

                // Smart skill usage: Tung chiêu khi player nằm trong tầm hitbox kỹ năng
                if (canUseSkill() && dist <= skillType.getRange()) {
                    useSkill(fishes, mapW, mapH);
                }
                return;
            }
        }

        // HARD AI: Chủ động săn ItemEntity (Bá Khí / Buffs)
        if (difficulty == Difficulty.HARD && items != null) {
            ItemEntity bestItem = null;
            double minItemDist = radarRadius;
            for (ItemEntity item : items) {
                double dist = Math.hypot(item.getCenterX() - botX, item.getCenterY() - botY);
                if (dist < minItemDist) {
                    minItemDist = dist;
                    bestItem = item;
                }
            }
            if (bestItem != null && minItemDist < radarRadius * 0.75) {
                double dx = bestItem.getCenterX() - botX;
                double dy = bestItem.getCenterY() - botY;
                double len = Math.hypot(dx, dy);
                if (len > 0.001) {
                    playerEntity.setMovement(dx / len, dy / len);
                }
                return;
            }
        }

        // ƯU TIÊN 2 (Săn): Nếu không có nguy hiểm và phát hiện cá nhỏ hơn -> lao tới săn con mồi gần nhất
        double minPreyDist = radarRadius;
        boolean foundPrey = false;
        FishEntity targetFish = null;
        boolean targetIsPlayer = false;

        // Quét con cá nhỏ gần nhất trong phạm vi radarRadius
        if (fishes != null) {
            for (FishEntity f : fishes) {
                if (f.getLevel() <= botSize) {
                    double dist = Math.hypot(f.getCenterX() - botX, f.getCenterY() - botY);
                    if (dist < minPreyDist) {
                        minPreyDist = dist;
                        targetFish = f;
                        targetIsPlayer = false;
                        foundPrey = true;
                    }
                }
            }
        }

        // Kiểm tra người chơi nếu nhỏ hơn bot và gần hơn con cá mồi kia
        if (humanPlayer != null && !humanPlayer.isDead() && botSize > humanPlayer.getEffectiveSizeLevel()) {
            double dx = humanPlayer.getCenterX() - botX;
            double dy = humanPlayer.getCenterY() - botY;
            double dist = Math.hypot(dx, dy);
            if (dist < minPreyDist) {
                minPreyDist = dist;
                targetFish = null;
                targetIsPlayer = true;
                foundPrey = true;
            }
        }

        if (foundPrey) {
            double targetX;
            double targetY;
            if (targetIsPlayer) {
                targetX = humanPlayer.getCenterX();
                targetY = humanPlayer.getCenterY();
                if (difficulty == Difficulty.HARD) {
                    double botSpeed = playerEntity.getBaseSpeedPixels() * difficulty.getSpeedScale();
                    double t = minPreyDist / Math.max(1.0, botSpeed);
                    targetX += playerVx * t;
                    targetY += playerVy * t;
                }
            } else {
                targetX = targetFish.getCenterX();
                targetY = targetFish.getCenterY();
                if (difficulty == Difficulty.HARD) {
                    double botSpeed = playerEntity.getBaseSpeedPixels() * difficulty.getSpeedScale();
                    double t = minPreyDist / Math.max(1.0, botSpeed);
                    double fVx = targetFish.isFacingLeft() ? -90.0 : 90.0;
                    targetX += fVx * t;
                }
            }
            targetX = Math.max(40, Math.min(mapW - 40, targetX));
            targetY = Math.max(40, Math.min(mapH - 40, targetY));

            double targetDx = targetX - botX;
            double targetDy = targetY - botY;
            double len = Math.hypot(targetDx, targetDy);
            if (len > 0.001) {
                playerEntity.setMovement(targetDx / len, targetDy / len);
            }

            // Smart skill usage: Tung chiêu khi player hoặc mồi giá trị cao nằm trong hitbox kỹ năng
            if (canUseSkill() && difficulty == Difficulty.HARD) {
                boolean highValue = targetIsPlayer || (targetFish != null && targetFish.getLevel() >= botSize - 1);
                if (highValue && minPreyDist <= skillType.getRange()) {
                    useSkill(fishes, mapW, mapH);
                }
            }
            return;
        }

        // Nhặt ItemEntity nếu không có mồi (EASY bỏ qua ItemEntity hoàn toàn; MEDIUM nhặt Bá Khí; HARD nhặt mọi Item)
        if (difficulty != Difficulty.EASY && items != null) {
            ItemEntity targetItem = null;
            double minItemDist = radarRadius;
            for (ItemEntity item : items) {
                if (item.getType() == ItemEntity.Type.BA_KHI || difficulty == Difficulty.HARD) {
                    double dist = Math.hypot(item.getCenterX() - botX, item.getCenterY() - botY);
                    if (dist < minItemDist) {
                        minItemDist = dist;
                        targetItem = item;
                    }
                }
            }

            if (targetItem != null) {
                double dx = targetItem.getCenterX() - botX;
                double dy = targetItem.getCenterY() - botY;
                double len = Math.hypot(dx, dy);
                if (len > 0.001) {
                    playerEntity.setMovement(dx / len, dy / len);
                }
                return;
            }
        }

        // Tuần tra tự do
        double randAngle = Math.random() * Math.PI * 2;
        playerEntity.setMovement(Math.cos(randAngle) * 0.8, Math.sin(randAngle) * 0.8);
    }
}
