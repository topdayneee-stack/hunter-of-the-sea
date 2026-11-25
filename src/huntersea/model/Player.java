package huntersea.model;

import huntersea.BackendClient;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp thực thể Người chơi (Thủy Thần săn mồi)
 * Thực hiện chính xác bảng thông số Size - Tốc độ, hệ thống Mana,
 * 7 kĩ năng nhân vật, và cơ chế tiến hóa.
 */
public class Player {
    public static final double TILE_SIZE = 40.0;

    private final CharacterType characterType;
    private final String name;

    // Vị trí & Vận tốc
    private double x, y;
    private double vx, vy;
    private boolean facingLeft = false;

    // Tiến hóa & Kích thước
    private int sizeLevel = 1; // 1 to 4
    private int fishEatenCount = 0;
    private int score = 0;
    private int lives = 3; // Dùng cho PvE
    private double dynamicSpeedMultiplier = 1.0; // Hệ số tốc độ cân bằng động PvE

    // Hệ thống Mana
    private double mana = 0.0; // 0.0 -> 5.0 (5 con cá = 100% mana)
    private double timeSinceLastEat = 0.0;

    // Trạng thái hiệu ứng
    private double stunTimer = 0.0;        // Choáng đóng băng di chuyển
    private double slowTimer = 0.0;        // Làm chậm
    private double slowMultiplier = 0.5;   // Hệ số làm chậm
    private double knockbackVx = 0.0;      // Vector hất văng
    private double knockbackVy = 0.0;
    private boolean shieldActive = false;  // Khiên bảo vệ
    private SkillType lastTriggeredSkill = null; // Kỹ năng vừa kích hoạt
    private double crabPullTimer = 0.0;     // Thời gian duy trì chiêu Kéo Cua
    private final List<Object> crabLockedTargets = new ArrayList<>(); // Danh sách 2 mục tiêu bị Cua kéo
    private Player pullingCrab = null;      // Cua đang kéo đối tượng này
    private Player attachedPlayerHost = null; // Vật chủ Player mà Cá Ép ký sinh
    private double biteAnimTimer = 0.0;
    private double baKhiTimer = 0.0;       // 5 giây Siêu Bá Khí
    private double poisonTimer = 0.0;      // Bị trúng độc làm chậm
    private double speedBurstTimer = 0.0;  // Cá Kiếm Dash
    private double inkSanctuaryTimer = 0.0;// Bạch Tuộc vùng mực (4s)
    private double inkX, inkY;
    private double electricShockTimer = 0.0;// Cá Đuối Điện (3s)
    private double electricTickTimer = 0.0;
    private double turtleShieldTimer = 0.0; // Rùa bất tử & phản đòn (4s)
    private double remoraAttachTimer = 0.0; // Cá Ép ký sinh (4s)
    private double remoraFallbackSpeedTimer = 0.0; // Fallback: bơi siêu tốc 3s
    private FishEntity attachedFish = null;
    private double remoraTickTimer = 0.0;  // Tick mỗi 1s để cộng điểm ký sinh
    private int scoreEarnedDuringAttach = 0;
    // Phase "lao đến" cá chủ trước khi ký sinh
    private FishEntity remoraChaseTarget = null; // Mục tiêu đang lao đến
    private double remoraChaseTimer = 0.0;       // Giới hạn thời gian lao (tối đa 3s)

    // PvP Slots kỹ năng nhặt thêm (tối đa 2)
    private SkillType pvpSlot1 = null;
    private SkillType pvpSlot2 = null;

    // Số cá mồi cần ăn để tăng 1 cấp kích cỡ (chế độ thường: 10, PvP: 2)
    private int fishPerSizeLevel = 10;

    private boolean dead = false;

    public Player(CharacterType type, String name, double startX, double startY) {
        this.characterType = type;
        this.name = name;
        this.x = startX;
        this.y = startY;
        this.sizeLevel = 1;
        this.fishPerSizeLevel = BackendClient.getInstance().getConfigs().eatCountToLevelUp;
        resetInputStates();
    }

    /**
     * Cập nhật logic người chơi mỗi khung hình (dt tính bằng giây)
     * Nhận danh sách cá để kiểm tra cá chủ (Remora) còn sống không.
     */
    public void update(double dt, double mapWidth, double mapHeight) {
        update(dt, mapWidth, mapHeight, null);
    }

    public void update(double dt, double mapWidth, double mapHeight, List<FishEntity> fishList) {
        if (dead) return;

        // Giảm dần các bộ đếm thời gian hiệu ứng
        if (stunTimer > 0) stunTimer -= dt;
        if (slowTimer > 0) slowTimer -= dt;
        if (biteAnimTimer > 0) biteAnimTimer -= dt;
        if (baKhiTimer > 0) baKhiTimer -= dt;
        if (poisonTimer > 0) poisonTimer -= dt;
        if (inkSanctuaryTimer > 0) inkSanctuaryTimer -= dt;
        if (turtleShieldTimer > 0) turtleShieldTimer -= dt;

        if (crabPullTimer > 0) {
            crabPullTimer -= dt;
            if (crabPullTimer <= 0 || stunTimer > 0) {
                stopCrabPull();
            }
        }
        if (pullingCrab != null && (!pullingCrab.isCrabPulling() || pullingCrab.isDead())) {
            pullingCrab = null;
        }

        // Ký sinh vào Player host (Remora -> Cua hoặc Player khác)
        if (remoraAttachTimer > 0 && attachedPlayerHost != null) {
            remoraAttachTimer -= dt;
            if (remoraAttachTimer <= 0 || attachedPlayerHost.isDead()) {
                attachedPlayerHost = null;
                remoraAttachTimer = 0;
                remoraFallbackSpeedTimer = 2.0;
            } else {
                facingLeft = attachedPlayerHost.isFacingLeft();
                x = attachedPlayerHost.getCenterX() - getPixelWidth() / 2.0;
                y = attachedPlayerHost.getCenterY() - getPixelHeight() / 2.0;
                remoraTickTimer += dt;
                if (remoraTickTimer >= 1.0) {
                    remoraTickTimer -= 1.0;
                    scoreEarnedDuringAttach += attachedPlayerHost.getSizeLevel() * 30;
                }
            }
            timeSinceLastEat += dt;
            if (timeSinceLastEat > 3.5 && mana > 0) {
                mana = Math.max(0, mana - dt * 0.5);
            }
            return;
        }

        // Nếu bị choáng (stun): đóng băng di chuyển nhưng vẫn áp dụng lực đẩy knockback
        if (stunTimer > 0) {
            if (Math.abs(knockbackVx) > 0.1 || Math.abs(knockbackVy) > 0.1) {
                x += knockbackVx * dt;
                y += knockbackVy * dt;
                knockbackVx *= Math.pow(0.02, dt);
                knockbackVy *= Math.pow(0.02, dt);
                x = Math.max(0, Math.min(mapWidth - getPixelWidth(), x));
                y = Math.max(0, Math.min(mapHeight - getPixelHeight(), y));
            }
            return;
        }

        // Logic Cá Đuối Điện: Xung điện mỗi 1 giây trong 3 giây
        if (electricShockTimer > 0) {
            electricShockTimer -= dt;
            electricTickTimer += dt;
        }

        // Fallback Cá Ép: Tăng tốc siêu mạnh 3s khi không có cá chủ
        if (remoraFallbackSpeedTimer > 0) {
            remoraFallbackSpeedTimer -= dt;
        }

        // Cập nhật speedBurstTimer (Cá Kiếm) — phải trước phần di chuyển
        if (speedBurstTimer > 0) speedBurstTimer -= dt;

        // ---------------------------------------------------------------
        // PHASE LAO ĐẾN (Chase): Cá Ép bơi siêu tốc về phía cá chủ
        // ---------------------------------------------------------------
        if (remoraChaseTimer > 0 && remoraChaseTarget != null) {
            remoraChaseTimer -= dt;

            // Kiểm tra mục tiêu còn tồn tại không
            if (fishList != null && !fishList.contains(remoraChaseTarget)) {
                // Mục tiêu đã bị ăn mất → hủy, chuyển sang fallback
                remoraChaseTarget = null;
                remoraChaseTimer = 0;
                remoraFallbackSpeedTimer = 3.0;
                scoreEarnedDuringAttach = 0;
            } else if (remoraChaseTimer <= 0) {
                // Hết thời gian lao mà không đến được → fallback
                remoraChaseTarget = null;
                remoraFallbackSpeedTimer = 3.0;
                scoreEarnedDuringAttach = 0;
            } else {
                // Lao thẳng đến mục tiêu với tốc độ rất cao (3.5x tốc độ cơ bản)
                double tx = remoraChaseTarget.getCenterX() - getCenterX();
                double ty = remoraChaseTarget.getCenterY() - getCenterY();
                double dist = Math.hypot(tx, ty);

                if (dist <= 80.0) {
                    // ĐÃ ĐẾN NƠI → Chính thức ký sinh!
                    remoraChaseTarget.setPlayerControlled(true);
                    attachedFish = remoraChaseTarget;
                    remoraChaseTarget = null;
                    remoraChaseTimer = 0;
                    remoraAttachTimer = 4.0;
                    remoraTickTimer = 0.0;
                    scoreEarnedDuringAttach = 0;
                    remoraFallbackSpeedTimer = 0.0;
                } else {
                    // Tiếp tục lao đến: di chuyển theo hướng mục tiêu
                    double chaseSpeed = getBaseSpeedPixels() * 3.5;
                    double nx = tx / dist;
                    double ny = ty / dist;
                    x += nx * chaseSpeed * dt;
                    y += ny * chaseSpeed * dt;
                    // Cập nhật hướng quay mặt theo chiều lao
                    if (nx < -0.1) facingLeft = true;
                    else if (nx > 0.1) facingLeft = false;
                }
            }

            // Soft edge push trong bản đồ (giống phase di chuyển thường)
            double pw0 = getPixelWidth();
            double ph0 = getPixelHeight();
            final double EZ = 80.0;
            double chaseSpd = getBaseSpeedPixels() * 3.5;
            if (x < EZ)              x += (1.0 - x / EZ) * chaseSpd * 0.3 * dt;
            if (x + pw0 > mapWidth - EZ)  x -= (1.0 - (mapWidth - x - pw0) / EZ) * chaseSpd * 0.3 * dt;
            if (y < EZ)              y += (1.0 - y / EZ) * chaseSpd * 0.3 * dt;
            if (y + ph0 > mapHeight - EZ) y -= (1.0 - (mapHeight - y - ph0) / EZ) * chaseSpd * 0.3 * dt;
            // Hard clamp cuối cùng
            x = Math.max(0, Math.min(mapWidth - pw0, x));
            y = Math.max(0, Math.min(mapHeight - ph0, y));

            // Cập nhật Mana rồi thoát sớm
            timeSinceLastEat += dt;
            if (timeSinceLastEat > 3.5 && mana > 0) {
                mana = Math.max(0, mana - dt * 0.5);
            }
            return;
        }

        // Logic Cá Ép: Người chơi điều khiển cá chủ trực tiếp trong 4 giây
        if (remoraAttachTimer > 0) {
            remoraAttachTimer -= dt;

            // Kiểm tra cá chủ có còn trong danh sách không (bị ăn mất)
            if (attachedFish != null && fishList != null) {
                if (!fishList.contains(attachedFish)) {
                    attachedFish.setPlayerControlled(false);
                    attachedFish = null;
                }
            }

            if (remoraAttachTimer <= 0 || attachedFish == null) {
                // Kết thúc: trả quyền điều khiển, cộng dồn điểm
                if (attachedFish != null) {
                    // ĐẨY CÁ ÉP RA NGOÀI VÙNG CỦA VẬT CHỦ để tránh va chạm chết ngay:
                    // Tính hướng đẩy ra: ra phía sau vật chủ (ngược chiều cá đang bơi)
                    double hostCX = attachedFish.getCenterX();
                    double hostCY = attachedFish.getCenterY();
                    double safeGap = attachedFish.getWidth() * 0.6 + getPixelWidth() * 0.6;
                    // Đẩy sang trái nếu vật chủ đang nhìn phải, và ngược lại
                    double pushDirX = attachedFish.isFacingLeft() ? 1.0 : -1.0;
                    double newX = hostCX + pushDirX * safeGap - getPixelWidth() / 2.0;
                    double newY = hostCY - getPixelHeight() / 2.0;
                    // Clamp trong bản đồ
                    x = Math.max(0, Math.min(mapWidth - getPixelWidth(), newX));
                    y = Math.max(0, Math.min(mapHeight - getPixelHeight(), newY));

                    attachedFish.setPlayerControlled(false);
                    attachedFish = null;

                    // Kích hoạt tăng tốc thoát 2 giây để cá ép không bị ăn ngay sau khi rời vật chủ
                    remoraFallbackSpeedTimer = 2.0;
                }
                score += scoreEarnedDuringAttach;
                scoreEarnedDuringAttach = 0;
                remoraAttachTimer = 0;
            } else {
                // Người chơi điều khiển cá chủ: WASD di chuyển cá chủ
                double hostSpeed = getBaseSpeedPixels() * 1.1; // nhanh hơn cá AI một chút
                double newFishX = attachedFish.getX() + vx * hostSpeed * dt;
                double newFishY = attachedFish.getY() + vy * hostSpeed * dt;
                // Giới hạn cá chủ trong bản đồ
                newFishX = Math.max(0, Math.min(mapWidth - attachedFish.getWidth(), newFishX));
                newFishY = Math.max(0, Math.min(mapHeight - attachedFish.getHeight(), newFishY));
                attachedFish.setPosition(newFishX, newFishY);

                // Đổi hướng cá chủ theo điều khiển
                if (vx < -0.1) attachedFish.setFacingLeft(true);
                else if (vx > 0.1) attachedFish.setFacingLeft(false);

                // Vị trí người chơi: bám phía sau cá chủ
                facingLeft = attachedFish.isFacingLeft();
                x = attachedFish.getCenterX() - getPixelWidth() / 2.0;
                y = attachedFish.getCenterY() - getPixelHeight() / 2.0;

                // Tick điểm ký sinh: cứ mỗi 1 giây cộng điểm dựa trên cấp cá chủ
                remoraTickTimer += dt;
                if (remoraTickTimer >= 1.0) {
                    remoraTickTimer -= 1.0;
                    scoreEarnedDuringAttach += attachedFish.getLevel() * 25;
                }
            }

            // Khi đang ký sinh, chỉ cập nhật Mana, bỏ qua di chuyển thông thường
            timeSinceLastEat += dt;
            if (timeSinceLastEat > 3.5 && mana > 0) {
                mana = Math.max(0, mana - dt * 0.5);
            }
            return;
        }

        // Hệ thống Mana tụt dần sau 3.5s không ăn cá
        timeSinceLastEat += dt;
        if (timeSinceLastEat > 3.5 && mana > 0) {
            mana = Math.max(0, mana - dt * 0.5); // Tụt 1 mana sau mỗi 2 giây
        }

        // Tính toán tốc độ di chuyển hiện tại
        double currentSpeed = getBaseSpeedPixels() * dynamicSpeedMultiplier;

        // Buff / Debuff tốc độ
        if (speedBurstTimer > 0) currentSpeed *= 2.2;           // Cá Kiếm Lướt
        if (remoraFallbackSpeedTimer > 0) currentSpeed *= 2.5;  // Cá Ép Fallback siêu tốc
        if (baKhiTimer > 0) currentSpeed *= 1.4;               // Bá Khí
        if (inkSanctuaryTimer > 0 && isInsideInk(getCenterX(), getCenterY())) {
            currentSpeed *= 1.2;                                // Bạch Tuộc trong mực (+20%)
        }
        if (poisonTimer > 0) currentSpeed *= 0.5;              // Trúng độc làm chậm (-50% tốc độ di chuyển)
        if (slowTimer > 0) currentSpeed *= slowMultiplier;     // Làm chậm

        double pw = getPixelWidth();
        double ph = getPixelHeight();

        // Clamp vị trí trước để đảm bảo luôn trong bounds (tránh floating-point drift)
        x = Math.max(0, Math.min(mapWidth - pw, x));
        y = Math.max(0, Math.min(mapHeight - ph, y));

        // Áp dụng di chuyển tự do theo từng trục độc lập kèm lực hất văng
        double newX = x + vx * currentSpeed * dt + knockbackVx * dt;
        double newY = y + vy * currentSpeed * dt + knockbackVy * dt;
        knockbackVx *= Math.pow(0.02, dt);
        knockbackVy *= Math.pow(0.02, dt);

        // ── SOFT EDGE PUSH ──────────────────────────────────────────────────
        // Dùng tốc độ CƠ BẢN (không nhân buff) để push không bao giờ vượt quá
        // tốc độ di chuyển của người chơi, tránh lỗi WASD bị "kẹt/trôi".
        final double EDGE_ZONE  = 40.0;                       // vùng đệm 40px
        final double PUSH_SPEED = getBaseSpeedPixels() * 0.18; // ~18% tốc độ cơ bản — luôn nhỏ hơn input

        // Mép trái
        if (newX >= 0 && newX < EDGE_ZONE) {
            double ratio = Math.min(1.0, 1.0 - (newX / EDGE_ZONE));
            newX += ratio * PUSH_SPEED * dt;
        }
        // Mép phải
        double distRight = mapWidth - newX - pw;
        if (distRight >= 0 && distRight < EDGE_ZONE) {
            double ratio = Math.min(1.0, 1.0 - (distRight / EDGE_ZONE));
            newX -= ratio * PUSH_SPEED * dt;
        }
        // Mép trên
        if (newY >= 0 && newY < EDGE_ZONE) {
            double ratio = Math.min(1.0, 1.0 - (newY / EDGE_ZONE));
            newY += ratio * PUSH_SPEED * dt;
        }
        // Mép dưới
        double distBottom = mapHeight - newY - ph;
        if (distBottom >= 0 && distBottom < EDGE_ZONE) {
            double ratio = Math.min(1.0, 1.0 - (distBottom / EDGE_ZONE));
            newY -= ratio * PUSH_SPEED * dt;
        }
        // ────────────────────────────────────────────────────────────────────

        // Hard-clamp cuối cùng (lưới an toàn)
        x = Math.max(0, Math.min(mapWidth - pw, newX));
        y = Math.max(0, Math.min(mapHeight - ph, newY));

        // Cập nhật hướng quay mặt (dùng vx gốc để không bị ảnh hưởng bởi clamp)
        if (vx < -0.1) facingLeft = true;
        else if (vx > 0.1) facingLeft = false;
    }

    /**
     * Bảng tốc độ quy đổi theo thông số chuẩn đặc tả:
     * Size 1 (2x2 ô): 4.8 ô/s = 192 px/s
     * Size 2 (3x2 ô): 4.2 ô/s = 168 px/s
     * Size 3 (4x3 ô): 3.6 ô/s = 144 px/s
     * Size 4 (5x4 ô): 3.0 ô/s = 120 px/s
     */
    public double getBaseSpeedPixels() {
        int effSize = getEffectiveSizeLevel();
        switch (effSize) {
            case 1: return 4.8 * TILE_SIZE; // 192 px/s
            case 2: return 4.2 * TILE_SIZE; // 168 px/s
            case 3: return 3.6 * TILE_SIZE; // 144 px/s
            case 4: 
            default: return 3.0 * TILE_SIZE; // 120 px/s
        }
    }

    /**
     * Kích thước chiều rộng pixel (1 ô = 40px)
     * Khi Cá Ép đang ký sinh, KHÔNG tính kích thước cá chủ vào
     * để tránh collision box bất thường.
     */
    public double getPixelWidth() {
        int effSize = getEffectiveSizeLevel();
        int gridW;
        switch (effSize) {
            case 1: gridW = 2; break; // 2x2
            case 2: gridW = 3; break; // 3x2
            case 3: gridW = 4; break; // 4x3
            case 4: 
            default: gridW = 5; break; // 5x4
        }
        return gridW * TILE_SIZE;
    }

    /**
     * Kích thước chiều cao pixel
     */
    public double getPixelHeight() {
        int effSize = getEffectiveSizeLevel();
        int gridH;
        switch (effSize) {
            case 1: gridH = 2; break; // 2x2
            case 2: gridH = 2; break; // 3x2
            case 3: gridH = 3; break; // 4x3
            case 4: 
            default: gridH = 4; break; // 5x4
        }
        return gridH * TILE_SIZE;
    }

    /**
     * Cấp kích thước hiệu dụng:
     * - Bá Khí: +1 cấp
     * - Cá Ép đang ký sinh: size = player_size + cấp cá chủ (tối đa 4)
     */
    public int getEffectiveSizeLevel() {
        int lvl = sizeLevel;
        if (baKhiTimer > 0) lvl = Math.min(4, lvl + 1);
        if (remoraAttachTimer > 0 && attachedFish != null) {
            // Kích thước kết hợp: cộng thêm cấp cá chủ, giới hạn tối đa 4
            lvl = Math.min(4, lvl + attachedFish.getLevel());
        }
        return lvl;
    }

    /**
     * Xử lý khi ăn 1 con cá: Tích điểm, nạp mana, và tiến hóa theo fishPerSizeLevel
     */
    public void onEatFish(int fishLevel, int points) {
        fishEatenCount++;
        timeSinceLastEat = 0.0;
        biteAnimTimer = 0.2; // Bật sprite ăn trong 0.2s

        // Cộng mana: Ăn 5 con cá là đầy 100% mana
        mana = Math.min(5.0, mana + 1.0);

        // Cộng điểm
        int earned = points * (baKhiTimer > 0 ? 2 : 1);
        if (remoraAttachTimer > 0) {
            scoreEarnedDuringAttach += earned;
        } else {
            score += earned;
        }

        // Tiến hóa: Cứ mỗi fishPerSizeLevel con cá ăn được thì tăng 1 size
        int newSize = 1 + (fishEatenCount / fishPerSizeLevel);
        if (newSize > sizeLevel && sizeLevel < 4) {
            sizeLevel = Math.min(4, newSize);
        }
    }

    /**
     * Kích hoạt Kỹ năng nhân vật khi đầy Mana (5/5)
     */
    public boolean canUseSkill() {
        return mana >= 5.0;
    }

    public void triggerCharacterSkill(List<FishEntity> fishes, double mapW, double mapH) {
        if (!canUseSkill()) return;
        mana = 0.0; // Tiêu thụ toàn bộ mana

        executeSkill(SkillType.fromCharacter(characterType), fishes, mapW, mapH);
    }

    /**
     * Thực thi một kĩ năng cụ thể
     */
    public void executeSkill(SkillType skill, List<FishEntity> fishes, double mapW, double mapH) {
        this.lastTriggeredSkill = skill;
        switch (skill) {
            case SHRIMP_BLINK:
                // Tôm: Dịch chuyển tới vị trí xa cá lớn và gần cá nhỏ
                executeShrimpBlink(fishes, mapW, mapH);
                break;

            case SWORD_DASH:
                // Cá Kiếm: Tăng mạnh tốc độ trong 3.5s
                this.speedBurstTimer = 3.5;
                break;

            case CRAB_PINCER:
                // Cua: Bật cơ chế kéo 2 mục tiêu nhỏ hơn về phía mình
                startCrabPull(3.5);
                break;

            case REMORA_ATTACH:
                // Cá Ép: Ký sinh vào vật chủ (ưu tiên bám trực tiếp vào Cua đang kéo)
                if (pullingCrab != null) {
                    attachToHost(pullingCrab);
                } else {
                    executeRemoraAttach(fishes);
                }
                break;

            case OCTOPUS_INK:
                // Bạch Tuộc: Phun vùng mực hắc ám 4s & phá dây kéo của Cua
                this.inkSanctuaryTimer = 4.0;
                this.inkX = getCenterX();
                this.inkY = getCenterY();
                breakCrabTether();
                break;

            case RAY_SHOCK:
                // Cá Đuối Điện: Xung điện 4s. Nếu đang bị Cua kéo -> làm choáng Cua và ngắt kéo
                this.electricShockTimer = 4.0;
                this.electricTickTimer = 0.0;
                if (pullingCrab != null) {
                    pullingCrab.stun(1.5);
                    pullingCrab.stopCrabPull();
                    breakCrabTether();
                }
                break;

            case TURTLE_SHELL:
                // Rùa: Mai rùa bất tử 4s & phản đòn, phá vỡ dây kéo của Cua
                this.turtleShieldTimer = 4.0;
                this.shieldActive = true;
                breakCrabTether();
                break;

            case VORTEX_PULL:
                // Hút xoáy nước
                break;

            case POISON_STING:
                // Gai độc
                break;

            case ROAR_FEAR:
                // Tiếng gầm
                break;
        }
    }

    // --- KĨ NĂNG CHI TIẾT ---

    private void executeShrimpBlink(List<FishEntity> fishes, double mapW, double mapH) {
        // Tìm tọa độ an toàn: xa cá to, gần nhiều cá bé
        double bestX = x;
        double bestY = y;
        double bestScore = -999999;

        for (int i = 0; i < 15; i++) {
            double candidateX = 80 + Math.random() * (mapW - 200);
            double candidateY = 80 + Math.random() * (mapH - 200);

            double score = 0;
            for (FishEntity f : fishes) {
                double dist = Math.hypot(f.getCenterX() - candidateX, f.getCenterY() - candidateY);
                if (f.getLevel() > getEffectiveSizeLevel()) {
                    // Càng gần cá to càng bị trừ điểm nặng
                    if (dist < 400) score -= (400 - dist) * 2;
                } else {
                    // Càng gần cá bé càng được cộng điểm
                    if (dist < 300) score += (300 - dist);
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestX = candidateX;
                bestY = candidateY;
            }
        }

        this.x = bestX;
        this.y = bestY;
    }

    private void executeCrabPincer(List<FishEntity> fishes) {
        // Tìm 2 con cá nhỏ gần nhất trong phạm vi 6 ô (240px)
        List<FishEntity> candidates = new ArrayList<>();
        double range = 6.0 * TILE_SIZE;

        for (FishEntity f : fishes) {
            if (f.getLevel() <= getEffectiveSizeLevel() && !f.isEaten()) {
                double dist = Math.hypot(f.getCenterX() - getCenterX(), f.getCenterY() - getCenterY());
                if (dist <= range) {
                    candidates.add(f);
                }
            }
        }

        // Sắp xếp theo khoảng cách gần nhất
        candidates.sort((a, b) -> {
            double da = Math.hypot(a.getCenterX() - getCenterX(), a.getCenterY() - getCenterY());
            double db = Math.hypot(b.getCenterX() - getCenterX(), b.getCenterY() - getCenterY());
            return Double.compare(da, db);
        });

        int caught = 0;
        for (FishEntity target : candidates) {
            if (caught >= 2) break;
            if (target.isEaten()) continue;
            if (target.markEaten()) {
                onEatFish(target.getLevel(), target.getLevel() * 15);
                fishes.remove(target);
                caught++;
            }
        }
    }

    private void executeRemoraAttach(List<FishEntity> fishes) {
        // Giải phóng cá chủ cũ nếu đang bám
        if (attachedFish != null) {
            attachedFish.setPlayerControlled(false);
            attachedFish = null;
        }
        // Hủy chase cũ nếu có
        remoraChaseTarget = null;
        remoraChaseTimer = 0;

        // Tìm cá TO HƠN người chơi GẦN NHẤT (không giới hạn phạm vi)
        FishEntity bestTarget = null;
        double bestDist = Double.MAX_VALUE;

        for (FishEntity f : fishes) {
            if (f.isPlayerControlled()) continue; // Bỏ qua cá đang bị điều khiển
            if (f.getLevel() <= sizeLevel) continue; // Chỉ nhắm cá TO HƠN
            double dist = Math.hypot(f.getCenterX() - getCenterX(), f.getCenterY() - getCenterY());
            if (dist < bestDist) {
                bestDist = dist;
                bestTarget = f;
            }
        }

        if (bestTarget != null) {
            // BẮT ĐẦU PHASE LAO ĐẾN: cá ép bơi siêu tốc về phía cá chủ
            // (Chưa setPlayerControlled — chỉ khi chạm đến nơi mới ký sinh)
            this.remoraChaseTarget = bestTarget;
            this.remoraChaseTimer = 5.0; // Tối đa 5 giây để lao đến
            this.scoreEarnedDuringAttach = 0;
            this.remoraFallbackSpeedTimer = 0.0;
        } else {
            // Không có cá to hơn trong map: fallback bơi siêu tốc 3s
            this.remoraFallbackSpeedTimer = 3.0;
            this.scoreEarnedDuringAttach = 0;
        }
    }

    public boolean isInsideInk(double targetX, double targetY) {
        if (inkSanctuaryTimer <= 0) return false;
        double dist = Math.hypot(targetX - inkX, targetY - inkY);
        return dist <= (5.0 * TILE_SIZE); // Bán kính 5 ô
    }

    public boolean isInvincible() {
        return (baKhiTimer > 0) || (turtleShieldTimer > 0) || shieldActive ||
               (inkSanctuaryTimer > 0 && isInsideInk(getCenterX(), getCenterY())) ||
               (remoraAttachTimer > 0 && (attachedFish != null || attachedPlayerHost != null)) || // Bất tử khi đang ký sinh
               (remoraChaseTimer > 0 && remoraChaseTarget != null); // Bất tử khi đang lao đến vật chủ
    }

    public void stun(double duration) {
        this.stunTimer = Math.max(this.stunTimer, duration);
    }
    public boolean isStunned() { return stunTimer > 0; }
    public double getStunTimer() { return stunTimer; }

    public void slow(double duration) { slow(duration, 0.5); }
    public void slow(double duration, double mult) {
        this.slowTimer = Math.max(this.slowTimer, duration);
        this.slowMultiplier = mult;
    }
    public boolean isSlowed() { return slowTimer > 0 || poisonTimer > 0; }
    public double getSlowTimer() { return slowTimer; }
    public double getSlowMultiplier() { return slowMultiplier; }

    public void applyKnockback(double kx, double ky) {
        this.knockbackVx += kx;
        this.knockbackVy += ky;
    }

    public void applyPull(double targetX, double targetY, double force) {
        double dx = targetX - getCenterX();
        double dy = targetY - getCenterY();
        double dist = Math.hypot(dx, dy);
        if (dist > 1.0) {
            applyKnockback((dx / dist) * force, (dy / dist) * force);
        }
    }

    public void pushBack(double pushX, double pushY) {
        this.x += pushX;
        this.y += pushY;
    }

    public boolean isShieldActive() {
        return turtleShieldTimer > 0 || shieldActive;
    }

    public void setShieldActive(boolean active) {
        this.shieldActive = active;
    }

    public boolean isSkillDashing() {
        return speedBurstTimer > 0;
    }

    public boolean hasBiteLethality() {
        return isSkillDashing() || isInvincible();
    }

    public SkillType pollLastTriggeredSkill() {
        SkillType s = lastTriggeredSkill;
        lastTriggeredSkill = null;
        return s;
    }

    /** Kiểm tra Cá Ép có đang ký sinh không (để render hiệu ứng) */
    public boolean isRemoraAttached() {
        return remoraAttachTimer > 0 && (attachedFish != null || attachedPlayerHost != null);
    }

    public boolean isCrabPulling() {
        return crabPullTimer > 0 && stunTimer <= 0;
    }

    public double getCrabPullTimer() {
        return crabPullTimer;
    }

    public void startCrabPull(double duration) {
        this.crabPullTimer = duration;
        this.crabLockedTargets.clear();
    }

    public void stopCrabPull() {
        this.crabPullTimer = 0.0;
        for (Object t : new ArrayList<>(crabLockedTargets)) {
            if (t instanceof FishEntity) {
                ((FishEntity) t).setPullingCrab(null);
            } else if (t instanceof Player) {
                ((Player) t).setPullingCrab(null);
            }
        }
        this.crabLockedTargets.clear();
    }

    public List<Object> getCrabLockedTargets() {
        return crabLockedTargets;
    }

    public void addCrabLockedTarget(Object target) {
        if (crabLockedTargets.size() < 2 && !crabLockedTargets.contains(target)) {
            crabLockedTargets.add(target);
        }
    }

    public void removeCrabLockedTarget(Object target) {
        crabLockedTargets.remove(target);
    }

    public void setPullingCrab(Player crab) {
        this.pullingCrab = crab;
    }

    public Player getPullingCrab() {
        return pullingCrab;
    }

    public boolean isBeingPulledByCrab() {
        return pullingCrab != null && pullingCrab.isCrabPulling();
    }

    public void breakCrabTether() {
        if (pullingCrab != null) {
            pullingCrab.removeCrabLockedTarget(this);
            this.pullingCrab = null;
        }
    }

    public void attachToHost(Player host) {
        if (host != null) {
            breakCrabTether();
            this.attachedFish = null;
            this.remoraChaseTarget = null;
            this.remoraChaseTimer = 0.0;
            this.attachedPlayerHost = host;
            this.remoraAttachTimer = 4.0;
            this.remoraTickTimer = 0.0;
            this.scoreEarnedDuringAttach = 0;
            this.remoraFallbackSpeedTimer = 0.0;
        }
    }

    public Player getAttachedPlayerHost() {
        return attachedPlayerHost;
    }

    public boolean isAttachedTo(Player host) {
        return remoraAttachTimer > 0 && attachedPlayerHost != null && (host == null || attachedPlayerHost == host);
    }

    /** Kiểm tra Cá Ép đang ở phase lao đến cá chủ không */
    public boolean isRemoraChasing() {
        return remoraChaseTimer > 0 && remoraChaseTarget != null;
    }

    /** Kiểm tra Cá Ép đang ở chế độ fallback tăng tốc không */
    public boolean isRemoraFallbackActive() {
        return remoraFallbackSpeedTimer > 0;
    }

    public void triggerBaKhi() {
        this.baKhiTimer = BackendClient.getInstance().getConfigs().hakiBuffDurationSec;
    }

    public void triggerPoison() {
        double slowDur = BackendClient.getInstance().getConfigs().poisonFishStunSec;
        applyPoisonSlow(slowDur);
    }

    public void triggerPoison(double duration) {
        applyPoisonSlow(duration);
    }

    public void applyPoisonSlow(double duration) {
        this.poisonTimer = Math.max(this.poisonTimer, duration);
        // Tách biệt hoàn toàn với stun: nhân vật vẫn bơi và đổi hướng bình thường, chỉ bị giảm tốc 50%
    }

    public double getPoisonTimer() {
        return poisonTimer;
    }

    public Rectangle2D.Double getBounds() {
        return new Rectangle2D.Double(x, y, getPixelWidth(), getPixelHeight());
    }

    /**
     * Vùng va chạm thực tế:
     * - Khi Cá Ép đang ký sinh: sử dụng vùng va chạm của cá chủ (vì ta đang điều khiển nó)
     * - Bình thường: 62% bounding box của bản thân
     */
    public Rectangle2D.Double getCollisionBounds() {
        if (remoraAttachTimer > 0 && attachedFish != null) {
            // Va chạm = vùng thân cá chủ (ta đang điều khiển cá chủ)
            double fw = attachedFish.getWidth();
            double fh = attachedFish.getHeight();
            double shrink = 0.65;
            double cw = fw * shrink;
            double ch = fh * shrink;
            return new Rectangle2D.Double(
                attachedFish.getX() + (fw - cw) / 2.0,
                attachedFish.getY() + (fh - ch) / 2.0,
                cw, ch
            );
        }
        double pw = getPixelWidth();
        double ph = getPixelHeight();
        double shrink = 0.62;
        double cw = pw * shrink;
        double ch = ph * shrink;
        return new Rectangle2D.Double(x + (pw - cw) / 2.0, y + (ph - ch) / 2.0, cw, ch);
    }

    // PvP Slots Kỹ Năng Rơi
    public boolean addPvPSkill(SkillType skill) {
        if (pvpSlot1 == null) {
            pvpSlot1 = skill;
            return true;
        } else if (pvpSlot2 == null) {
            pvpSlot2 = skill;
            return true;
        } else {
            // Thay thế slot 2 (không cộng dồn)
            pvpSlot2 = skill;
            return true;
        }
    }

    public void usePvPSlot1(List<FishEntity> fishes, double mapW, double mapH) {
        if (pvpSlot1 != null) {
            executeSkill(pvpSlot1, fishes, mapW, mapH);
            pvpSlot1 = null;
        }
    }

    public void usePvPSlot2(List<FishEntity> fishes, double mapW, double mapH) {
        if (pvpSlot2 != null) {
            executeSkill(pvpSlot2, fishes, mapW, mapH);
            pvpSlot2 = null;
        }
    }

    // Getters & Setters
    public CharacterType getCharacterType() { return characterType; }
    public String getName() { return name; }
    public double getX() { return x; }
    public double getY() { return y; }
    public void setPosition(double x, double y) { this.x = x; this.y = y; }
    public double getCenterX() { return x + getPixelWidth() / 2.0; }
    public double getCenterY() { return y + getPixelHeight() / 2.0; }
    public void setMovement(double vx, double vy) { this.vx = vx; this.vy = vy; }
    public boolean isFacingLeft() { return facingLeft; }
    public int getSizeLevel() { return sizeLevel; }
    public int getFishEatenCount() { return fishEatenCount; }
    public int getEatenCount() { return fishEatenCount; }
    public double getDynamicSpeedMultiplier() { return dynamicSpeedMultiplier; }
    public void setDynamicSpeedMultiplier(double dynamicSpeedMultiplier) { this.dynamicSpeedMultiplier = dynamicSpeedMultiplier; }
    public int getScore() { return score; }
    public void addScore(int delta) { this.score += delta; }
    public double getMana() { return mana; }
    public int getLives() { return lives; }
    public void setLives(int lives) { this.lives = lives; }
    public void decrementLife() { this.lives--; }
    public boolean isBiting() { return biteAnimTimer > 0; }
    public boolean isBaKhiActive() { return baKhiTimer > 0; }
    public boolean isPoisoned() { return poisonTimer > 0; }
    public boolean isTurtleShieldActive() { return turtleShieldTimer > 0; }
    public boolean isElectricShockActive() { return electricShockTimer > 0; }
    public boolean isElectricTickTriggered() {
        if (electricTickTimer >= 1.0) {
            electricTickTimer -= 1.0;
            return true;
        }
        return false;
    }
    public boolean isInkActive() { return inkSanctuaryTimer > 0; }
    public double getInkX() { return inkX; }
    public double getInkY() { return inkY; }
    public boolean isDead() { return dead; }
    public void setDead(boolean dead) { this.dead = dead; }
    public SkillType getPvPSlot1() { return pvpSlot1; }
    public SkillType getPvPSlot2() { return pvpSlot2; }
    public FishEntity getAttachedFish() { return attachedFish; }
    public double getRemoraAttachTimer() { return remoraAttachTimer; }
    public int getScoreEarnedDuringAttach() { return scoreEarnedDuringAttach; }
    public int getFishPerSizeLevel() { return fishPerSizeLevel; }
    public void setFishPerSizeLevel(int fishPerSizeLevel) { this.fishPerSizeLevel = Math.max(1, fishPerSizeLevel); }

    /**
     * Xóa sạch các cờ phím, trạng thái khống chế (CC), bám dính, vận tốc và hiệu ứng khi bắt đầu ván mới
     */
    public void resetInputStates() {
        this.vx = 0.0;
        this.vy = 0.0;
        this.stunTimer = 0.0;
        this.slowTimer = 0.0;
        this.poisonTimer = 0.0;
        this.slowMultiplier = 0.5;
        this.knockbackVx = 0.0;
        this.knockbackVy = 0.0;
        this.biteAnimTimer = 0.0;
        this.shieldActive = false;
        this.crabPullTimer = 0.0;
        if (this.crabLockedTargets != null) {
            this.crabLockedTargets.clear();
        }
        this.pullingCrab = null;
        this.attachedPlayerHost = null;
        this.speedBurstTimer = 0.0;
        this.inkSanctuaryTimer = 0.0;
        this.electricShockTimer = 0.0;
        this.electricTickTimer = 0.0;
        this.turtleShieldTimer = 0.0;
        this.remoraAttachTimer = 0.0;
        this.remoraFallbackSpeedTimer = 0.0;
        this.remoraTickTimer = 0.0;
        this.attachedFish = null;
        this.scoreEarnedDuringAttach = 0;
        this.remoraChaseTarget = null;
        this.remoraChaseTimer = 0.0;
        this.baKhiTimer = 0.0;
        this.dynamicSpeedMultiplier = 1.0;
    }
}
