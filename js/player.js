/**
 * HUNTER OF THE SEA - PLAYER ENTITY & MOUSE-AIM CONTROLLER (PHASE 2 & 3)
 * Điều khiển chuột liên tục theo atan2, tốc độ cố định theo loài cá,
 * vùng đệm deadzone chống rung, thanh Mana (0-5), kỹ năng mặc định [Q],
 * 2 ô kỹ năng nhặt được [W] và [E], cùng hệ thống hiệu ứng thị giác.
 */

class Player {
    constructor(config) {
        this.id = config.id || 'p1';
        this.username = config.username || 'Thủy Thủ';
        this.character = config.character || { id: 2, name: 'Cá Kiếm', img: 'cá kiếm.jpg', eatImg: 'cá kiếm ăn.png' };
        this.isLocal = !!config.isLocal;
        this.isBot = !!config.isBot; // Đánh dấu là cá máy (Bot AI trong PvE)
        this.aiDecisionTimer = 0.0;  // Bộ đếm chu kỳ ra quyết định AI
        this.aiTarget = null;         // Mục tiêu AI đang nhắm tới

        // Kích thước chuẩn bản đồ
        this.MAP_WIDTH = 1920;
        this.MAP_HEIGHT = 1080;
        this.TILE_SIZE = 40;
        this.DEADZONE = 22; // Vùng đệm quanh tâm (pixel) chống rung giật hướng chuột

        // Vị trí & Góc bơi
        this.x = config.x !== undefined ? config.x : 400;
        this.y = config.y !== undefined ? config.y : 500;
        this.angle = config.angle || 0;
        this.targetAngle = this.angle;
        this.facingLeft = false;

        // Cấp kích thước & Tiến hóa
        this.sizeLevel = config.sizeLevel || 1; // 1 -> 4
        this.fishEaten = 0;
        this.score = config.score || 0;
        this.dead = false;

        // Máu & Mạng sống (Hệ thống Sinh Tồn)
        this.hp = config.hp !== undefined ? config.hp : 100;
        this.maxHp = 100;
        this.lives = config.lives !== undefined ? config.lives : 3;
        this.maxLives = 3;
        this.fishPerSizeLevel = config.fishPerSizeLevel !== undefined ? config.fishPerSizeLevel : 10; // Cứ mỗi 10 con cá ăn được thì tăng 1 size level

        // Hệ thống Mana (0.0 -> 5.0: ăn 5 cá là đầy 100% để kích hoạt phím Q)
        this.mana = 0.0;
        this.maxMana = 5.0;
        this.timeSinceLastEat = 0.0;

        // Cấu hình kỹ năng Cá Đuối Điện (Q): Vùng điện xung quanh
        this.ELECTRIC_AURA_BASE_RADIUS = 260; // Bán kính điện trường cơ bản (tối thiểu)
        this.ELECTRIC_AURA_EXPAND_PER_SIZE = 45; // Mở rộng thêm theo từng cấp kích thước

        // 2 Ô Kỹ năng nhặt được: Phím W và Phím E (Phase 3)
        this.slotW = null; // Kỹ năng nhặt được Slot 1
        this.slotE = null; // Kỹ năng nhặt được Slot 2

        // Hiệu ứng mở miệng khi ăn
        this.biteAnimTimer = 0;

        // Hiệu ứng trạng thái kỹ năng (Buff & Debuff)
        this.speedBurstTimer = 0.0;  // Lướt tốc (Cá Kiếm / Phím W)
        this.shieldTimer = 0.0;      // Khiên bất tử (Rùa / Mai Rùa)
        this.stunTimer = 0.0;        // Choáng tê liệt
        this.slowTimer = 0.0;        // Bị làm chậm (mực đen)
        this.baKhiTimer = 0.0;       // Siêu Bá Khí (+1 Size Level trong 5s)
        this.shockWaveTimer = 0.0;   // Vùng điện xung quanh (5.0s)
        this.shockWaveTickTimer = 0.0; // Nhịp tick kích hoạt choáng (mỗi 1.0s)

        // Cấu hình kỹ năng Bạch Tuộc (Q): Vùng Mực Hắc Ám
        this.inkTimer = 0.0;         // Thời gian tồn tại của vùng mực (5.0s)
        this.inkMaxDuration = 5.0;   // Thời lượng chuẩn 5.0 giây
        this.inkX = 0;               // Tọa độ tâm vùng mực X trên bản đồ
        this.inkY = 0;               // Tọa độ tâm vùng mực Y trên bản đồ
        this.INK_ZONE_RADIUS = 220;  // Bán kính vùng mực cố định trên map (220px)
        this.inInkBuff = false;      // Cờ trạng thái: bản thân đang ở trong vùng mực của mình (bất tử & tăng tốc)

        // KỸ NĂNG PHASE 4: TÔM, CUA, CÁ ÉP
        // 1. Tôm: Dịch chuyển định hướng
        this.teleportTimer = 0.0;
        this.teleportFromX = 0;
        this.teleportFromY = 0;
        this.teleportToX = 0;
        this.teleportToY = 0;

        // 2. Cua: Phóng càng sắt bắt mục tiêu (Hook)
        this.clawActive = false;
        this.clawState = 'IDLE'; // 'OUT' | 'PULLING' | 'RETRACT'
        this.clawX = 0;
        this.clawY = 0;
        this.clawAngle = 0;
        this.clawDist = 0;
        this.clawMaxDist = 480; // Tầm phóng tối đa 480px
        this.clawSpeed = 850;   // Tốc độ phóng/kéo 850px/s
        this.clawTarget = null; // Mục tiêu bị găm kéo { type, entity, id }

        // 3. Cá Ép: Lao bám ký sinh
        this.isDashing = false;
        this.dashTimer = 0.0;
        this.dashSpeed = 480;
        this.dashAngle = 0;
        this.isAttached = false;
        this.attachedHost = null; // { type, entity, id }
        this.attachedTimer = 0.0;
        this.attachOffsetX = 0;
        this.attachOffsetY = 0;

        // Callbacks
        this.onClawEatFish = null;
        this.onClawEatPlayer = null;

        // Dữ liệu nội suy cho đối thủ từ xa (Remote LERP)
        this.remoteTargetX = this.x;
        this.remoteTargetY = this.y;
        this.remoteTargetAngle = this.angle;
        this.remoteTargetSize = this.sizeLevel;

        // Tốc độ cơ bản theo từng loài cá (pixel/giây) - Đã cân bằng lại cho PvP
        this.baseSpeeds = {
            1: 180, // Tôm (nhanh nhẹn, lướt định hướng)
            2: 192, // Cá Kiếm (nhanh nhất đại dương)
            3: 155, // Cua (chậm, chắc chắn, càng sắt)
            4: 170, // Cá Ép (tốc độ trung bình nhanh, ký sinh)
            5: 158, // Bạch Tuộc (mực hắc ám làm chậm)
            6: 162, // Cá Đuối Điện (xung điện làm choáng)
            7: 142  // Rùa Biển (chậm nhất nhưng khiên bất tử phản đòn)
        };

        // Hình ảnh sprite & Offscreen Canvas tách nền trong suốt
        const baseImgSrc = this.character.img || 'cá kiếm.jpg';
        const pngImgSrc = baseImgSrc.replace(/\.jpg$/i, '.png');
        const baseEatSrc = this.character.eatImg || baseImgSrc;
        const pngEatSrc = baseEatSrc.replace(/\.jpg$/i, '.png');

        this.spriteImg = new Image();
        this.eatSpriteImg = new Image();
        this.spriteCanvas = null;
        this.eatSpriteCanvas = null;

        const onPlayerSpriteLoad = () => {
            if (window.GameAssets) this.spriteCanvas = window.GameAssets.getTransparent(this.spriteImg, baseImgSrc);
        };
        const onPlayerEatSpriteLoad = () => {
            if (window.GameAssets) this.eatSpriteCanvas = window.GameAssets.getTransparent(this.eatSpriteImg, baseEatSrc);
        };

        this.spriteImg.onload = onPlayerSpriteLoad;
        this.spriteImg.onerror = () => {
            if (this.spriteImg.src.indexOf('.png') !== -1) {
                this.spriteImg = new Image();
                this.spriteImg.onload = onPlayerSpriteLoad;
                this.spriteImg.src = baseImgSrc;
            }
        };
        this.spriteImg.src = pngImgSrc;

        this.eatSpriteImg.onload = onPlayerEatSpriteLoad;
        this.eatSpriteImg.onerror = () => {
            if (this.eatSpriteImg.src.indexOf('.png') !== -1) {
                this.eatSpriteImg = new Image();
                this.eatSpriteImg.onload = onPlayerEatSpriteLoad;
                this.eatSpriteImg.src = baseEatSrc;
            }
        };
        this.eatSpriteImg.src = pngEatSrc;

        if (window.GameAssets) {
            this.spriteCanvas = window.GameAssets.getTransparent(baseImgSrc);
            this.eatSpriteCanvas = window.GameAssets.getTransparent(baseEatSrc);
        }
    }

    // Kích thước tự nhiên gốc của bản thân (không tính mượn vật chủ)
    getNaturalWidth() {
        let lvl = this.sizeLevel;
        if (this.baKhiTimer > 0) lvl = Math.min(4, lvl + 1);
        switch (lvl) {
            case 1: return 2 * this.TILE_SIZE; // 80px
            case 2: return 3 * this.TILE_SIZE; // 120px
            case 3: return 4 * this.TILE_SIZE; // 160px
            case 4:
            default: return 5 * this.TILE_SIZE; // 200px
        }
    }

    getNaturalHeight() {
        let lvl = this.sizeLevel;
        if (this.baKhiTimer > 0) lvl = Math.min(4, lvl + 1);
        switch (lvl) {
            case 1: return 1.4 * this.TILE_SIZE; // 56px
            case 2: return 2.1 * this.TILE_SIZE; // 84px
            case 3: return 2.8 * this.TILE_SIZE; // 112px
            case 4:
            default: return 3.5 * this.TILE_SIZE; // 140px
        }
    }

    // Cấp độ hiệu dụng: Khi Cá Ép ký sinh, mượn toàn bộ cấp độ của vật chủ!
    getEffectiveSizeLevel() {
        if (this.isAttached && this.attachedHost && this.attachedHost.entity) {
            const h = this.attachedHost.entity;
            if (this.attachedHost.type === 'fish') {
                return h.level || this.sizeLevel;
            } else if (this.attachedHost.type === 'player') {
                return h.getEffectiveSizeLevel ? h.getEffectiveSizeLevel() : (h.sizeLevel || this.sizeLevel);
            }
        }
        let lvl = this.sizeLevel;
        if (this.baKhiTimer > 0) {
            lvl = Math.min(4, lvl + 1);
        }
        return lvl;
    }

    // Chiều rộng hiệu dụng khi va chạm & ăn mồi: Mượn kích thước vật chủ khi đang ký sinh
    getWidth() {
        if (this.isAttached && this.attachedHost && this.attachedHost.entity) {
            const h = this.attachedHost.entity;
            return h.width || (h.getWidth ? h.getWidth() : this.getNaturalWidth());
        }
        return this.getNaturalWidth();
    }

    // Chiều cao hiệu dụng khi va chạm & ăn mồi: Mượn kích thước vật chủ khi đang ký sinh
    getHeight() {
        if (this.isAttached && this.attachedHost && this.attachedHost.entity) {
            const h = this.attachedHost.entity;
            return h.height || (h.getHeight ? h.getHeight() : this.getNaturalHeight());
        }
        return this.getNaturalHeight();
    }

    getCenterX() {
        if (this.isAttached && this.attachedHost && this.attachedHost.entity) {
            const h = this.attachedHost.entity;
            return h.getCenterX ? h.getCenterX() : (h.x + (h.width || 80) / 2);
        }
        return this.x + this.getNaturalWidth() / 2;
    }

    getCenterY() {
        if (this.isAttached && this.attachedHost && this.attachedHost.entity) {
            const h = this.attachedHost.entity;
            return h.getCenterY ? h.getCenterY() : (h.y + (h.height || 60) / 2);
        }
        return this.y + this.getNaturalHeight() / 2;
    }

    getRadius() {
        if (this.isAttached && this.attachedHost && this.attachedHost.entity) {
            const h = this.attachedHost.entity;
            return h.getRadius ? h.getRadius() : (this.getWidth() / 2) * 0.75;
        }
        return (this.getWidth() / 2) * 0.75;
    }

    getMouthX() {
        if (this.isAttached && this.attachedHost && this.attachedHost.entity) {
            const h = this.attachedHost.entity;
            const cx = h.getCenterX ? h.getCenterX() : (h.x + (h.width || 80) / 2);
            const r = h.getRadius ? h.getRadius() : (this.getWidth() / 2) * 0.75;
            const ang = h.angle !== undefined ? h.angle : this.angle;
            return cx + Math.cos(ang) * (r * 0.85);
        }
        return this.getCenterX() + Math.cos(this.angle) * this.getRadius();
    }

    getMouthY() {
        if (this.isAttached && this.attachedHost && this.attachedHost.entity) {
            const h = this.attachedHost.entity;
            const cy = h.getCenterY ? h.getCenterY() : (h.y + (h.height || 60) / 2);
            const r = h.getRadius ? h.getRadius() : (this.getWidth() / 2) * 0.75;
            const ang = h.angle !== undefined ? h.angle : this.angle;
            return cy + Math.sin(ang) * (r * 0.85);
        }
        return this.getCenterY() + Math.sin(this.angle) * this.getRadius();
    }

    getBaseSpeed() {
        const charId = this.character.id || 2;
        return this.baseSpeeds[charId] || 180;
    }

    getSizeSpeedMultiplier() {
        switch (this.getEffectiveSizeLevel()) {
            case 1: return 1.0;
            case 2: return 0.88;
            case 3: return 0.77;
            case 4:
            default: return 0.66;
        }
    }

    getSpeed() {
        if (this.stunTimer > 0 && this.baKhiTimer <= 0) return 0; // Đang bị choáng -> đóng băng di chuyển (Bá Khí miễn nhiễm)

        let spd = this.getBaseSpeed() * this.getSizeSpeedMultiplier();
        if (this.botBaseSpeedMult) spd *= this.botBaseSpeedMult;
        if (this.dynamicSpeedMult) spd *= this.dynamicSpeedMult;
        if (this.speedBurstTimer > 0) spd *= 2.0; // Lướt Thần Tốc (+100% tốc độ cân bằng)
        if (this.baKhiTimer > 0) spd *= 1.6;     // Bá Khí (+60% tốc độ bơi thần tốc)
        if (this.inInkBuff) spd *= 1.5;          // Bạch Tuộc đang ở trong vùng mực của mình: +50% tốc độ bơi
        if (this.slowTimer > 0 && this.baKhiTimer <= 0 && !this.inInkBuff) spd *= 0.55; // Bị dính mực làm chậm (Bá Khí & Bạch Tuộc trong mực miễn nhiễm)
        if (this.clawActive) spd *= 0.35;        // Cua đang phóng/kéo càng: khựng nhẹ giữ trụ
        return spd;
    }

    isInvincible() {
        return this.shieldTimer > 0 || this.baKhiTimer > 0 || this.isAttached || this.inInkBuff;
    }

    activateBaKhi(duration = 5.0) {
        this.baKhiTimer = Math.max(this.baKhiTimer, duration);
        this.slowTimer = 0;   // Xóa bỏ & miễn nhiễm làm chậm
        this.stunTimer = 0;   // Xóa bỏ & miễn nhiễm choáng
        this.poisonTimer = 0; // Xóa bỏ trúng độc
    }

    /**
     * Xử lý khi ăn 1 con cá: Tích điểm, nạp mana, mở miệng và tiến hóa
     */
    onEatPrey(points, fishLevel) {
        this.fishEaten++;
        this.timeSinceLastEat = 0.0;
        this.biteAnimTimer = 0.22; // Bật sprite há miệng ăn trong 0.22s

        // Nạp Mana: Ăn 5 con cá là đầy 100% mana
        this.mana = Math.min(this.maxMana, this.mana + 1.0);

        // Cộng điểm (nhân đôi nếu đang có Bá Khí)
        const earned = (points || 15) * (this.baKhiTimer > 0 ? 2 : 1);
        this.score += earned;

        // Tiến hóa: Cứ mỗi fishPerSizeLevel (5) con cá ăn được thì tăng 1 size level
        const newSize = Math.min(4, 1 + Math.floor(this.fishEaten / this.fishPerSizeLevel));
        let levelUp = false;
        if (newSize > this.sizeLevel && this.sizeLevel < 4) {
            this.sizeLevel = newSize;
            levelUp = true;
        }

        return { levelUp, newSize: this.sizeLevel, earned };
    }

    /**
     * Nhận sát thương và xử lý trừ máu / trừ mạng
     */
    takeDamage(amount, reason) {
        if (this.isInvincible() || this.dead) {
            return { damaged: false, hp: this.hp, lives: this.lives, lostLife: false, dead: this.dead };
        }

        this.hp = Math.max(0, this.hp - amount);
        let lostLife = false;

        if (this.hp <= 0) {
            this.lives--;
            lostLife = true;
            if (this.lives <= 0) {
                this.dead = true;
                this.hp = 0;
            } else {
                this.hp = this.maxHp;
            }
        }

        return { damaged: true, hp: this.hp, lives: this.lives, lostLife, dead: this.dead };
    }

    /**
     * Trúng độc cá gai: Bị làm chậm 50% trong duration và trừ 25 HP
     */
    applyPoison(duration) {
        if (this.isInvincible()) return null;
        this.slowTimer = Math.max(this.slowTimer, duration || 4.0);
        return this.takeDamage(25, 'Trúng độc cá gai');
    }

    // =========================================================================
    // CẬP NHẬT DI CHUYỂN BẰNG CHUỘT (MOUSE-AIM) & SUY GIẢM MANA
    // =========================================================================
    updateMouseAim(worldMouseX, worldMouseY, dt) {
        if (this.dead) return;

        // Giảm dần các bộ đếm hiệu ứng
        if (this.biteAnimTimer > 0) this.biteAnimTimer -= dt;
        if (this.speedBurstTimer > 0) this.speedBurstTimer -= dt;
        if (this.shieldTimer > 0) this.shieldTimer -= dt;
        if (this.stunTimer > 0) this.stunTimer -= dt;
        if (this.slowTimer > 0) this.slowTimer -= dt;
        if (this.baKhiTimer > 0) this.baKhiTimer -= dt;
        if (this.shockWaveTimer > 0) {
            this.shockWaveTimer -= dt;
            if (this.shockWaveTimer <= 0) {
                this.shockWaveTimer = 0.0;
                this.shockWaveTickTimer = 0.0;
            }
        } else {
            this.shockWaveTimer = 0.0;
            this.shockWaveTickTimer = 0.0;
        }
        if (this.inkTimer > 0) {
            this.inkTimer -= dt;
            if (this.inkTimer <= 0) {
                this.inkTimer = 0.0;
                this.inInkBuff = false;
            }
        } else {
            this.inkTimer = 0.0;
            this.inInkBuff = false;
        }

        // Giảm hiệu ứng kỹ năng Phase 4
        if (this.teleportTimer > 0) this.teleportTimer -= dt;

        // CẬP NHẬT CÀNG CUA (NẾU ĐANG PHÓNG CÀNG)
        if (this.clawActive) {
            this.updateClaw(dt);
        }

        // CẬP NHẬT CÁ ÉP LAO BÁM (DASH)
        if (this.isDashing) {
            this.dashTimer -= dt;
            if (this.dashTimer <= 0) {
                this.isDashing = false;
            }
        }

        // CẬP NHẬT CÁ ÉP ĐANG BÁM CÁ CHỦ (ATTACHED)
        if (this.isAttached) {
            this.attachedTimer -= dt;
            if (this.attachedTimer <= 0) {
                this.detachHost();
            } else if (this.attachedHost && this.attachedHost.entity) {
                const host = this.attachedHost.entity;
                if (host.dead || host.eaten) {
                    this.detachHost();
                } else {
                    const hostType = this.attachedHost.type;

                    // 1. ĐIỀU KHIỂN TRỰC TIẾP HƯỚNG DI CHUYỂN CỦA VẬT CHỦ BẰNG CON TRỎ CHUỘT
                    if (this.isLocal) {
                        const hostCenterX = host.getCenterX ? host.getCenterX() : (host.x + (host.width || 80) / 2);
                        const hostCenterY = host.getCenterY ? host.getCenterY() : (host.y + (host.height || 60) / 2);
                        const dx = worldMouseX - hostCenterX;
                        const dy = worldMouseY - hostCenterY;
                        const distToMouse = Math.hypot(dx, dy);

                        if (distToMouse > 15) {
                            const steerAngle = Math.atan2(dy, dx);

                            if (hostType === 'fish') {
                                // Điều khiển cá mồi AI
                                const hostSpeed = Math.max((host.speed || 80) * 1.6, 210);
                                host.vx = Math.cos(steerAngle) * hostSpeed;
                                host.vy = Math.sin(steerAngle) * hostSpeed;
                                host.x += host.vx * dt;
                                host.y += host.vy * dt;

                                // Giữ cá chủ an toàn trong biên bản đồ
                                const hw = host.width || 100;
                                const hh = host.height || 80;
                                host.x = Math.max(35, Math.min(this.MAP_WIDTH - hw - 35, host.x));
                                host.y = Math.max(35, Math.min(this.MAP_HEIGHT - hh - 35, host.y));

                                host.angle = steerAngle;
                                host.facingLeft = Math.cos(steerAngle) < 0;
                            } else if (hostType === 'player') {
                                // Điều khiển đối thủ PvP
                                const hostSpeed = host.getSpeed ? host.getSpeed() : 180;
                                host.vx = Math.cos(steerAngle) * hostSpeed;
                                host.vy = Math.sin(steerAngle) * hostSpeed;
                                host.x += host.vx * dt;
                                host.y += host.vy * dt;

                                const hw = host.getWidth ? host.getWidth() : 100;
                                const hh = host.getHeight ? host.getHeight() : 80;
                                host.x = Math.max(35, Math.min(this.MAP_WIDTH - hw - 35, host.x));
                                host.y = Math.max(35, Math.min(this.MAP_HEIGHT - hh - 35, host.y));

                                host.angle = steerAngle;
                                host.facingLeft = Math.cos(steerAngle) < 0;

                                if (typeof this.onHostSteer === 'function') {
                                    this.onHostSteer(host.id, steerAngle, host.x, host.y);
                                }
                            }
                        }
                    }

                    // 2. CẬP NHẬT TỌA ĐỘ VÀ HƯỚNG CỦA CÁ ÉP BÁM TRÊN LƯNG VẬT CHỦ
                    const curHostCenterX = host.getCenterX ? host.getCenterX() : (host.x + (host.width || 80) / 2);
                    const curHostCenterY = host.getCenterY ? host.getCenterY() : (host.y + (host.height || 60) / 2);
                    const hostAngle = host.angle !== undefined ? host.angle : (host.movingRight ? 0 : Math.PI);

                    this.angle = hostAngle;
                    this.facingLeft = Math.cos(this.angle) < 0;

                    const cosA = Math.cos(hostAngle);
                    const sinA = Math.sin(hostAngle);
                    const hostRadius = host.getRadius ? host.getRadius() : ((host.width || 80) / 2);
                    const offY = -hostRadius * 0.45;
                    const worldOffX = -sinA * offY;
                    const worldOffY = cosA * offY;

                    const myW = this.getNaturalWidth();
                    const myH = this.getNaturalHeight();
                    this.x = curHostCenterX + worldOffX - myW / 2;
                    this.y = curHostCenterY + worldOffY - myH / 2;
                }
            }
            return; // Đang bám cá chủ: không di chuyển độc lập
        }

        // Mana tụt dần sau 4 giây không ăn cá (0.5 mana/s)
        this.timeSinceLastEat += dt;
        if (this.timeSinceLastEat > 4.0 && this.mana > 0) {
            this.mana = Math.max(0, this.mana - 0.5 * dt);
        }

        if (this.isLocal) {
            if (this.stunTimer > 0) return; // Choáng không bơi được

            // NẾU CÁ ÉP ĐANG LAO BÁM (DASH): Di chuyển vụt nhanh theo dashAngle
            if (this.isDashing) {
                const w = this.getWidth();
                const h = this.getHeight();
                let dashX = this.x + Math.cos(this.dashAngle) * this.dashSpeed * dt;
                let dashY = this.y + Math.sin(this.dashAngle) * this.dashSpeed * dt;
                this.x = Math.max(35, Math.min(this.MAP_WIDTH - w - 35, dashX));
                this.y = Math.max(35, Math.min(this.MAP_HEIGHT - h - 35, dashY));
                this.angle = this.dashAngle;
                this.facingLeft = Math.cos(this.angle) < 0;
                return;
            }

            const centerX = this.getCenterX();
            const centerY = this.getCenterY();

            const dx = worldMouseX - centerX;
            const dy = worldMouseY - centerY;
            const dist = Math.hypot(dx, dy);

            // VÙNG ĐỆM CHỐNG RUNG (DEADZONE):
            if (dist >= this.DEADZONE) {
                this.targetAngle = Math.atan2(dy, dx);
                this.angle = this.lerpAngle(this.angle, this.targetAngle, 14 * dt);
            }

            // Hướng quay mặt
            this.facingLeft = Math.cos(this.angle) < 0;

            // Bơi theo tốc độ chuẩn
            const currentSpeed = this.getSpeed();
            let newX = this.x + Math.cos(this.angle) * currentSpeed * dt;
            let newY = this.y + Math.sin(this.angle) * currentSpeed * dt;

            // XỬ LÝ BIÊN BẢN ĐỒ VỚI VÙNG ĐỆM MỀM (SOFT EDGE BUFFER)
            const w = this.getWidth();
            const h = this.getHeight();
            const EDGE_BUFFER = 25;

            if (newX < EDGE_BUFFER && Math.cos(this.angle) < 0) {
                newX = Math.max(0, newX);
            } else if (newX + w > this.MAP_WIDTH - EDGE_BUFFER && Math.cos(this.angle) > 0) {
                newX = Math.min(this.MAP_WIDTH - w, newX);
            }

            if (newY < EDGE_BUFFER && Math.sin(this.angle) < 0) {
                newY = Math.max(0, newY);
            } else if (newY + h > this.MAP_HEIGHT - EDGE_BUFFER && Math.sin(this.angle) > 0) {
                newY = Math.min(this.MAP_HEIGHT - h, newY);
            }

            // Hard clamp
            this.x = Math.max(0, Math.min(this.MAP_WIDTH - w, newX));
            this.y = Math.max(0, Math.min(this.MAP_HEIGHT - h, newY));
        } else if (this.isBot) {
            // CÁ MÁY (BOT AI ĐỐI THỦ CHẾ ĐỘ PVE):
            if (this.stunTimer > 0) return;

            // Bơi theo góc angle hiện tại
            const currentSpeed = this.getSpeed();
            let newX = this.x + Math.cos(this.angle) * currentSpeed * dt;
            let newY = this.y + Math.sin(this.angle) * currentSpeed * dt;
            const w = this.getWidth();
            const h = this.getHeight();

            // Chạm biên tường -> quay đầu mượt mà
            if (newX < 40) {
                newX = 40;
                this.targetAngle = 0;
            } else if (newX + w > this.MAP_WIDTH - 40) {
                newX = this.MAP_WIDTH - w - 40;
                this.targetAngle = Math.PI;
            }
            if (newY < 40) {
                newY = 40;
                this.targetAngle = Math.PI / 2;
            } else if (newY + h > this.MAP_HEIGHT - 40) {
                newY = this.MAP_HEIGHT - h - 40;
                this.targetAngle = -Math.PI / 2;
            }

            this.angle = this.lerpAngle(this.angle, this.targetAngle, 6 * dt);
            this.facingLeft = Math.cos(this.angle) < 0;

            this.x = Math.max(35, Math.min(this.MAP_WIDTH - w - 35, newX));
            this.y = Math.max(35, Math.min(this.MAP_HEIGHT - h - 35, newY));
        } else {
            // ĐỐI THỦ TỪ XA (PVP): LERP NỘI SUY
            this.x += (this.remoteTargetX - this.x) * Math.min(1, 16 * dt);
            this.y += (this.remoteTargetY - this.y) * Math.min(1, 16 * dt);
            this.angle = this.lerpAngle(this.angle, this.remoteTargetAngle, 16 * dt);
            this.sizeLevel = this.remoteTargetSize;
            this.facingLeft = Math.cos(this.angle) < 0;
        }
    }

    // =========================================================================
    // AI LOGIC CHO CÁ MÁY (BOT ĐỐI THỦ CHẾ ĐỘ PVE)
    // =========================================================================
    updateBotAI(dt, entityManager, localPlayerTarget) {
        if (!this.isBot || this.dead) return;

        this.aiDecisionTimer -= dt;
        if (this.aiDecisionTimer <= 0) {
            this.aiDecisionTimer = 0.4 + Math.random() * 0.3; // Ra quyết định mỗi 0.4 - 0.7s
            const bx = this.getCenterX();
            const by = this.getCenterY();
            const mySize = this.getEffectiveSizeLevel();

            let targetDir = null;
            let minDist = Infinity;

            // 1. Kiểm tra nguy hiểm: Nếu người chơi lớn hơn và ở cự ly gần (< 260px) -> Bơi né tránh
            if (localPlayerTarget && !localPlayerTarget.dead && localPlayerTarget.getEffectiveSizeLevel() > mySize) {
                const distToPlayer = Math.hypot(localPlayerTarget.getCenterX() - bx, localPlayerTarget.getCenterY() - by);
                if (distToPlayer < 260) {
                    // Bơi theo hướng ngược lại với người chơi
                    const fleeAngle = Math.atan2(by - localPlayerTarget.getCenterY(), bx - localPlayerTarget.getCenterX());
                    this.targetAngle = fleeAngle;
                    return;
                }
            }

            // 2. Săn người chơi nếu cá máy lớn hơn và ở cự ly săn (< 320px)
            if (localPlayerTarget && !localPlayerTarget.dead && mySize > localPlayerTarget.getEffectiveSizeLevel() && !localPlayerTarget.isInvincible()) {
                const distToPlayer = Math.hypot(localPlayerTarget.getCenterX() - bx, localPlayerTarget.getCenterY() - by);
                if (distToPlayer < 320) {
                    this.targetAngle = Math.atan2(localPlayerTarget.getCenterY() - by, localPlayerTarget.getCenterX() - bx);
                    return;
                }
            }

            // 3. Săn đàn cá mồi nhỏ hơn gần nhất để ăn và tăng cấp
            if (entityManager && entityManager.fishes) {
                for (let f of entityManager.fishes) {
                    if (f.eaten) continue;
                    if (f.level <= mySize) {
                        const d = Math.hypot(f.getCenterX() - bx, f.getCenterY() - by);
                        if (d < minDist) {
                            minDist = d;
                            targetDir = Math.atan2(f.getCenterY() - by, f.getCenterX() - bx);
                        }
                    }
                }
            }

            if (targetDir !== null) {
                this.targetAngle = targetDir;
            } else {
                // Tự do tuần tra đại dương
                this.targetAngle += (Math.random() - 0.5) * 1.2;
            }
        }
    }

    setRemoteSnapshot(snapshot) {
        if (!snapshot) return;
        this.remoteTargetX = snapshot.x;
        this.remoteTargetY = snapshot.y;
        this.remoteTargetAngle = snapshot.angle;
        if (snapshot.sizeLevel) this.remoteTargetSize = snapshot.sizeLevel;
        if (snapshot.score !== undefined) this.score = snapshot.score;
        if (snapshot.bite) this.biteAnimTimer = 0.2;
    }

    lerpAngle(from, to, t) {
        const diff = (to - from + Math.PI) % (Math.PI * 2) - Math.PI;
        return from + diff * Math.min(1, Math.max(0, t));
    }


    applyBump(knockbackX, knockbackY) {
        this.x = Math.max(0, Math.min(this.MAP_WIDTH - this.getWidth(), this.x + knockbackX));
        this.y = Math.max(0, Math.min(this.MAP_HEIGHT - this.getHeight(), this.y + knockbackY));
    }

    // =========================================================================
    // HỆ THỐNG KỸ NĂNG: PHÍM [Q] (MẶC ĐỊNH) VÀ PHÍM [W] & [E] (NHẶT ĐƯỢC)
    // =========================================================================

    // 1. KỸ NĂNG PHÍM [Q]
    canUseSkillQ() {
        if (this.dead || this.stunTimer > 0) return false;

        const charId = this.character.id || 2;
        // Cá Ép đang bám cá chủ: Cho phép bấm Q để chủ động tách ra bất kỳ lúc nào (không tốn mana)
        if (charId === 4 && this.isAttached) {
            return true;
        }

        // Cua đang phóng càng chưa thu về: Khóa không cho bấm Q liên tiếp
        if (charId === 3 && this.clawActive) {
            return false;
        }

        return this.mana >= 5.0;
    }

    useSkillQ(targetX, targetY) {
        if (!this.canUseSkillQ()) return null;

        const charId = this.character.id || 2;

        // Nếu Cá Ép đang bám: bấm Q để chủ động tách ra
        if (charId === 4 && this.isAttached) {
            this.detachHost();
            return {
                skillKey: 'Q_DETACH',
                charId: 4,
                x: Math.round(this.x),
                y: Math.round(this.y),
                angle: Number(this.angle.toFixed(3))
            };
        }

        this.mana = 0.0; // Tiêu thụ toàn bộ 5 Mana

        const cx = this.getCenterX();
        const cy = this.getCenterY();

        // EDGE CASE: Chuột sát tâm cá (< 8px) hoặc tọa độ không hợp lệ -> fallback sang góc bơi hiện tại
        const rawDx = targetX - cx;
        const rawDy = targetY - cy;
        const rawDist = Math.hypot(rawDx, rawDy);
        let ang = this.angle;
        if (rawDist >= 8 && !isNaN(rawDx) && !isNaN(rawDy)) {
            ang = Math.atan2(rawDy, rawDx);
        }

        switch (charId) {
            case 1: {
                // TÔM: Dịch chuyển định hướng theo vector chuột tới R_max (360px)
                const dx = targetX - cx;
                const dy = targetY - cy;
                const dist = Math.hypot(dx, dy);
                const R_MAX = 360;
                const travelDist = Math.min(dist, R_MAX);

                const toCenterX = cx + Math.cos(ang) * travelDist;
                const toCenterY = cy + Math.sin(ang) * travelDist;
                const w = this.getWidth();
                const h = this.getHeight();

                // Kiểm tra biên an toàn bản đồ tránh kẹt mép tường
                const SAFE_MARGIN = 35;
                const clampedX = Math.max(SAFE_MARGIN, Math.min(this.MAP_WIDTH - w - SAFE_MARGIN, toCenterX - w / 2));
                const clampedY = Math.max(SAFE_MARGIN, Math.min(this.MAP_HEIGHT - h - SAFE_MARGIN, toCenterY - h / 2));

                // Lưu hiệu ứng điểm đi & điểm đến
                this.teleportFromX = cx;
                this.teleportFromY = cy;
                this.teleportToX = clampedX + w / 2;
                this.teleportToY = clampedY + h / 2;
                this.teleportTimer = 0.45;

                // Cập nhật vị trí tức thời
                this.x = clampedX;
                this.y = clampedY;
                this.angle = ang;

                return {
                    skillKey: 'Q',
                    charId: 1,
                    fromX: Math.round(this.teleportFromX),
                    fromY: Math.round(this.teleportFromY),
                    toX: Math.round(this.teleportToX),
                    toY: Math.round(this.teleportToY),
                    targetX: Math.round(targetX),
                    targetY: Math.round(targetY),
                    angle: Number(ang.toFixed(3))
                };
            }
            case 2: // Cá Kiếm: Lướt Thần Tốc (3.0 giây)
                this.speedBurstTimer = 3.0;
                break;
            case 3: {
                // CUA: Phóng càng sắt bắt mục tiêu (Hook) tới R_max (450px)
                this.clawActive = true;
                this.clawState = 'OUT';
                this.clawAngle = ang;
                this.clawDist = 0;
                this.clawMaxDist = 450;
                this.clawSpeed = 800;
                this.clawX = cx;
                this.clawY = cy;
                this.clawTarget = null;

                return {
                    skillKey: 'Q',
                    charId: 3,
                    targetX: Math.round(targetX),
                    targetY: Math.round(targetY),
                    angle: Number(ang.toFixed(3))
                };
            }
            case 4: {
                // CÁ ÉP: Lao nhanh theo hướng chuột bám vào cá lớn (0.65s)
                this.isDashing = true;
                this.dashTimer = 0.65;
                this.dashAngle = ang;
                this.angle = ang;

                return {
                    skillKey: 'Q',
                    charId: 4,
                    targetX: Math.round(targetX),
                    targetY: Math.round(targetY),
                    angle: Number(ang.toFixed(3))
                };
            }
            case 5: // Bạch Tuộc: Vùng Mực Hắc Ám (5.0s, bán kính 220px tại vị trí kích hoạt hoặc trỏ chuột)
                this.inkX = Math.round(targetX);
                this.inkY = Math.round(targetY);
                this.inkTimer = this.inkMaxDuration || 5.0;
                break;
            case 6: // Cá Đuối Điện: Vùng Điện Xung Quanh (5.0s, tick mỗi 1.0s)
                this.shockWaveTimer = 5.0;
                this.shockWaveTickTimer = 0.0; // Tick ngay lập tức lần đầu khi bật
                break;
            case 7: // Rùa Biển: Mai Rùa Bất Hoại (3.5s)
                this.shieldTimer = 3.5;
                break;
        }

        return {
            skillKey: 'Q',
            charId: charId,
            targetX,
            targetY,
            angle: this.angle
        };
    }

    // =========================================================================
    // HÀM XỬ LÝ CƠ CHẾ KỸ NĂNG RIÊNG: CUA PHÓNG CÀNG & CÁ ÉP LAO BÁM
    // =========================================================================

    // Cập nhật Càng Cua phóng & kéo mục tiêu
    updateClaw(dt) {
        if (!this.clawActive) return;

        const cx = this.getCenterX();
        const cy = this.getCenterY();

        if (this.clawState === 'OUT') {
            this.clawDist += this.clawSpeed * dt;
            this.clawX = cx + Math.cos(this.clawAngle) * this.clawDist;
            this.clawY = cy + Math.sin(this.clawAngle) * this.clawDist;

            if (this.clawDist >= this.clawMaxDist) {
                this.clawDist = this.clawMaxDist;
                this.clawState = 'RETRACT';
            }
        } else if (this.clawState === 'PULLING') {
            this.clawDist -= (this.clawSpeed * 1.1) * dt;
            this.clawX = cx + Math.cos(this.clawAngle) * this.clawDist;
            this.clawY = cy + Math.sin(this.clawAngle) * this.clawDist;

            // Kéo mục tiêu găm theo đầu càng về phía Cua
            if (this.clawTarget && this.clawTarget.entity) {
                const target = this.clawTarget.entity;
                if (target.dead || target.eaten) {
                    this.clawTarget = null;
                    this.clawState = 'RETRACT';
                } else {
                    const tw = target.getWidth ? target.getWidth() : target.width;
                    const th = target.getHeight ? target.getHeight() : target.height;
                    target.x = this.clawX - tw / 2;
                    target.y = this.clawY - th / 2;
                    if (target.stunTimer !== undefined) {
                        target.stunTimer = 0.5; // Khóa di chuyển mục tiêu trong khi đang bị kéo
                    }
                }
            }

            if (this.clawDist <= 40) {
                // Đã kéo mục tiêu về đến sát miệng Cua
                this.onClawPullFinished();
                this.clawActive = false;
                this.clawState = 'IDLE';
                this.clawTarget = null;
            }
        } else if (this.clawState === 'RETRACT') {
            this.clawDist -= (this.clawSpeed * 1.3) * dt;
            this.clawX = cx + Math.cos(this.clawAngle) * this.clawDist;
            this.clawY = cy + Math.sin(this.clawAngle) * this.clawDist;

            if (this.clawDist <= 20) {
                this.clawActive = false;
                this.clawState = 'IDLE';
                this.clawDist = 0;
            }
        }
    }

    onClawPullFinished() {
        if (!this.clawTarget || !this.clawTarget.entity) return;

        const target = this.clawTarget.entity;
        if (this.clawTarget.type === 'fish') {
            if (!target.eaten) {
                target.eaten = true;
                this.onEatPrey(target.points || 50, target.level || 1);
                if (typeof this.onClawEatFish === 'function') {
                    this.onClawEatFish(target);
                }
            }
        } else if (this.clawTarget.type === 'player') {
            // CUA KẸP ĐỐI THỦ THÀNH CÔNG: Xuyên mọi miễn nhiễm & chênh lệch kích thước
            target.stunTimer = 1.0;
            if (typeof this.onClawEatPlayer === 'function') {
                this.onClawEatPlayer(target);
            }
        }
    }

    // Cá Ép bám vào thân cá chủ (Ký sinh)
    attachToHost(hostEntity, hostType) {
        if (!hostEntity || this.isAttached) return;

        this.isAttached = true;
        this.attachedHost = {
            entity: hostEntity,
            type: hostType,
            id: hostEntity.id
        };
        this.attachedTimer = 5.0; // Bám tối đa 5.0 giây
        this.isDashing = false;
        this.dashTimer = 0;
    }

    // Cá Ép kết thúc ký sinh hoặc chủ động tách khỏi cá chủ
    detachHost() {
        if (!this.isAttached) return;

        const host = this.attachedHost ? this.attachedHost.entity : null;
        const hostType = this.attachedHost ? this.attachedHost.type : null;
        const detachAngle = this.angle;
        const oppositeAngle = detachAngle + Math.PI;

        this.isAttached = false;
        this.attachedHost = null;
        this.attachedTimer = 0;

        // 1. Tách Cá Ép ra an toàn: khôi phục kích thước gốc và phóng vọt về phía trước 1.5s
        this.speedBurstTimer = 1.5;
        this.x += Math.cos(detachAngle) * 75;
        this.y += Math.sin(detachAngle) * 75;

        const myW = this.getNaturalWidth();
        const myH = this.getNaturalHeight();
        this.x = Math.max(40, Math.min(this.MAP_WIDTH - myW - 40, this.x));
        this.y = Math.max(40, Math.min(this.MAP_HEIGHT - myH - 40, this.y));

        // 2. Tạo lực đẩy vật chủ văng mạnh ra xa theo hướng ngược lại
        if (host && !host.dead && !host.eaten) {
            const pushDist = 200;
            const targetHostX = host.x + Math.cos(oppositeAngle) * pushDist;
            const targetHostY = host.y + Math.sin(oppositeAngle) * pushDist;

            const hostW = host.width || (host.getWidth ? host.getWidth() : 100);
            const hostH = host.height || (host.getHeight ? host.getHeight() : 80);

            // Kiểm tra biên map để tránh kẹt tường cho vật chủ
            host.x = Math.max(40, Math.min(this.MAP_WIDTH - hostW - 40, targetHostX));
            host.y = Math.max(40, Math.min(this.MAP_HEIGHT - hostH - 40, targetHostY));

            host.vx = Math.cos(oppositeAngle) * 260;
            host.vy = Math.sin(oppositeAngle) * 260;
            host.stunTimer = 1.5; // Choáng vật chủ 1.5s
            if (host.wanderTimer !== undefined) host.wanderTimer = 2.0;

            if (typeof this.onDetachPush === 'function') {
                this.onDetachPush(host, hostType, oppositeAngle);
            }
        }
    }

    // 2. KỸ NĂNG NHẶT ĐƯỢC: PHÍM [W] VÀ [E]
    // Quy tắc: Nếu W trống -> nạp W; nếu W có mà E trống -> nạp E; nếu cả 2 đầy -> ghi đè vào E
    addSkillItem(skill) {
        if (!this.slotW) {
            this.slotW = skill;
            return 'W';
        } else if (!this.slotE) {
            this.slotE = skill;
            return 'E';
        } else {
            // Đã đầy cả 2 ô: Ghi đè vào ô [E], giữ nguyên ô [W]
            this.slotE = skill;
            return 'E_OVERWRITE';
        }
    }

    useSkillW(targetX, targetY) {
        if (!this.slotW || this.dead || this.stunTimer > 0) return null;
        const skill = this.slotW;
        this.slotW = null; // Tiêu hao sau khi dùng
        this.executeDroppedSkill(skill, targetX, targetY);
        return { slot: 'W', skill, targetX, targetY };
    }

    useSkillE(targetX, targetY) {
        if (!this.slotE || this.dead || this.stunTimer > 0) return null;
        const skill = this.slotE;
        this.slotE = null; // Tiêu hao sau khi dùng
        this.executeDroppedSkill(skill, targetX, targetY);
        return { slot: 'E', skill, targetX, targetY };
    }

    executeDroppedSkill(skill, targetX, targetY) {
        const type = skill.type;
        switch (type) {
            case 'SWORD_DASH':
                this.speedBurstTimer = 2.5;
                break;
            case 'RAY_SHOCK':
                this.shockWaveTimer = 5.0;
                this.shockWaveTickTimer = 0.0;
                break;
            case 'TURTLE_SHELL':
                this.shieldTimer = 3.5;
                break;
            case 'OCTOPUS_INK':
                this.inkX = Math.round(targetX);
                this.inkY = Math.round(targetY);
                this.inkTimer = 5.0;
                break;
            case 'BA_KHI':
                this.activateBaKhi(skill.duration || 5.0);
                break;
        }
    }

    // Bán kính vùng điện xung quanh của Cá Đuối Điện (Dễ dàng tinh chỉnh qua hằng số)
    getShockWaveRadius() {
        const base = this.ELECTRIC_AURA_BASE_RADIUS !== undefined ? this.ELECTRIC_AURA_BASE_RADIUS : 260;
        const expand = this.ELECTRIC_AURA_EXPAND_PER_SIZE !== undefined ? this.ELECTRIC_AURA_EXPAND_PER_SIZE : 45;
        const sizeLvl = this.getEffectiveSizeLevel ? this.getEffectiveSizeLevel() : this.sizeLevel;
        return base + (sizeLvl - 1) * expand;
    }

    // =========================================================================
    // VẼ NHÂN VẬT & HIỆU ỨNG KỸ NĂNG LÊN CANVAS
    // =========================================================================
    render(ctx, cameraX, cameraY) {
        if (this.dead) return;

        const screenX = this.x - cameraX;
        const screenY = this.y - cameraY;
        const w = this.isAttached ? this.getNaturalWidth() : this.getWidth();
        const h = this.isAttached ? this.getNaturalHeight() : this.getHeight();
        const cx = screenX + w / 2;
        const cy = screenY + h / 2;

        ctx.save();

        // 1. HIỆU ỨNG VÙNG MỰC HẮC ÁM (BẠCH TUỘC - 5.0S, R = 220px)
        if (this.inkTimer > 0) {
            const inkScreenX = this.inkX - cameraX;
            const inkScreenY = this.inkY - cameraY;
            const inkR = this.INK_ZONE_RADIUS || 220;
            const alpha = Math.min(1.0, this.inkTimer / 0.8); // Dần tan mờ khi sắp hết

            ctx.save();

            // Lớp mây mực đậm đặc nhiều tầng với khói hắc ám
            const inkGrad = ctx.createRadialGradient(inkScreenX, inkScreenY, inkR * 0.1, inkScreenX, inkScreenY, inkR);
            inkGrad.addColorStop(0, `rgba(5, 5, 15, ${0.92 * alpha})`);
            inkGrad.addColorStop(0.55, `rgba(15, 10, 30, ${0.82 * alpha})`);
            inkGrad.addColorStop(0.85, `rgba(20, 15, 35, ${0.65 * alpha})`);
            inkGrad.addColorStop(1, `rgba(10, 5, 20, 0)`);

            ctx.beginPath();
            ctx.arc(inkScreenX, inkScreenY, inkR, 0, Math.PI * 2);
            ctx.fillStyle = inkGrad;
            ctx.shadowColor = '#000000';
            ctx.shadowBlur = 35;
            ctx.fill();

            // Viền ranh giới vùng mực mờ ảo huyền bí
            ctx.beginPath();
            ctx.arc(inkScreenX, inkScreenY, inkR * 0.98, 0, Math.PI * 2);
            ctx.strokeStyle = `rgba(168, 85, 247, ${0.45 * alpha})`;
            ctx.lineWidth = 2.0;
            ctx.setLineDash([6, 10]);
            ctx.lineDashOffset = -(Date.now() / 60);
            ctx.stroke();

            ctx.restore();
        }

        // 2. HIỆU ỨNG VÙNG ĐIỆN XUNG QUANH (CÁ ĐUỐI ĐIỆN - 5.0S)
        // Thiết kế thanh thoát, trong suốt cao (low opacity) để dễ nhìn đàn cá & chướng ngại vật
        if (this.shockWaveTimer > 0) {
            const auraRadius = this.getShockWaveRadius();
            const now = Date.now();
            // Độ thở rất nhẹ (+/- 2px) với tần số êm dịu, không giật cục nhấp nháy
            const breath = Math.sin(now / 350) * 2.5;
            const r = auraRadius + breath;

            // Nhịp xung khi sắp tới hoặc ngay lúc phóng điện (dựa vào shockWaveTickTimer: 1.0s -> 0.0s)
            const tickPhase = this.shockWaveTickTimer !== undefined ? this.shockWaveTickTimer : 1.0;
            const pulseFactor = Math.max(0, 1.0 - (tickPhase / 0.22)); // Sáng lên tinh tế trong 0.22s mỗi nhịp phóng điện

            ctx.save();

            // 2.1 Lòng vùng điện: Lớp màu xanh băng / vàng rất mờ trong suốt (alpha ~0.03 - 0.07)
            const baseAlpha = 0.035 + pulseFactor * 0.045;
            const grad = ctx.createRadialGradient(cx, cy, r * 0.15, cx, cy, r);
            grad.addColorStop(0, `rgba(56, 189, 248, 0.015)`);
            grad.addColorStop(0.75, `rgba(56, 189, 248, ${baseAlpha * 0.8})`);
            grad.addColorStop(1, `rgba(250, 204, 21, ${baseAlpha * 1.5})`);

            ctx.beginPath();
            ctx.arc(cx, cy, r, 0, Math.PI * 2);
            ctx.fillStyle = grad;
            ctx.fill();

            // 2.2 Đường ranh giới bán kính: Mảnh, sắc nét, rõ ràng, phát sáng nhẹ
            ctx.beginPath();
            ctx.arc(cx, cy, r, 0, Math.PI * 2);
            ctx.strokeStyle = pulseFactor > 0 ? `rgba(255, 255, 255, ${0.7 + pulseFactor * 0.3})` : 'rgba(56, 189, 248, 0.55)';
            ctx.lineWidth = 1.6 + pulseFactor * 1.2;
            ctx.shadowColor = '#38bdf8';
            ctx.shadowBlur = 8 + pulseFactor * 10;
            ctx.stroke();

            // 2.3 Vòng phụ nét đứt tinh tế xoay chậm để biểu thị từ trường điện quay
            ctx.save();
            ctx.beginPath();
            ctx.arc(cx, cy, r, 0, Math.PI * 2);
            ctx.strokeStyle = 'rgba(250, 204, 21, 0.45)';
            ctx.lineWidth = 1.0;
            ctx.setLineDash([4, 12]);
            ctx.lineDashOffset = -(now / 45); // Xoay mượt mà theo thời gian
            ctx.stroke();
            ctx.restore();

            // 2.4 Một vài tia điện mini (3 tia mảnh) chạy vòng ngoài rìa ranh giới để tạo chất điện mà không che khuất màn hình
            const miniArcs = 4;
            for (let i = 0; i < miniArcs; i++) {
                const startAng = (i / miniArcs) * Math.PI * 2 + (now / 700);
                const arcSpan = 0.16 + Math.sin(now / 150 + i) * 0.06;
                ctx.beginPath();
                ctx.arc(cx, cy, r + 0.5, startAng, startAng + arcSpan);
                ctx.strokeStyle = '#ffffff';
                ctx.lineWidth = 2.0;
                ctx.shadowColor = '#facc15';
                ctx.shadowBlur = 8;
                ctx.stroke();
            }

            // 2.5 Khi tới nhịp giật điện (pulseFactor > 0): Một vòng sóng xung kích thanh mảnh bung ra ngoài
            if (pulseFactor > 0) {
                const ringR = r * (0.88 + pulseFactor * 0.14);
                ctx.beginPath();
                ctx.arc(cx, cy, ringR, 0, Math.PI * 2);
                ctx.strokeStyle = `rgba(255, 255, 255, ${pulseFactor * 0.6})`;
                ctx.lineWidth = 1.2;
                ctx.stroke();
            }

            ctx.restore();
        }

        // 3. HIỆU ỨNG KHIÊN HỘ THỂ / BÁ KHÍ (RÙA / NGỌC BÁ KHÍ)
        if (this.isInvincible()) {
            ctx.save();
            ctx.beginPath();
            ctx.arc(cx, cy, (w / 2) + 14, 0, Math.PI * 2);
            ctx.strokeStyle = this.baKhiTimer > 0 ? '#ef4444' : '#ffd700';
            ctx.lineWidth = 4;
            ctx.fillStyle = this.baKhiTimer > 0 ? 'rgba(239, 68, 68, 0.25)' : 'rgba(255, 215, 0, 0.22)';
            ctx.shadowColor = this.baKhiTimer > 0 ? '#ff3366' : '#ffd700';
            ctx.shadowBlur = 25;
            ctx.fill();
            ctx.stroke();
            ctx.restore();
        }

        // 4. HIỆU ỨNG TIA NƯỚC LƯỚT TỐC (CÁ KIẾM / LƯỚT TỐC / CÁ ÉP LAO DASH)
        if (this.speedBurstTimer > 0 || this.isDashing) {
            ctx.save();
            ctx.beginPath();
            ctx.arc(cx - Math.cos(this.angle) * 30, cy - Math.sin(this.angle) * 30, w * 0.45, 0, Math.PI * 2);
            ctx.fillStyle = this.isDashing ? 'rgba(0, 255, 170, 0.45)' : 'rgba(0, 240, 255, 0.4)';
            ctx.shadowColor = this.isDashing ? '#00ffa2' : '#00f0ff';
            ctx.shadowBlur = 20;
            ctx.fill();
            ctx.restore();
        }

        // 4.1 HIỆU ỨNG TÔM DỊCH CHUYỂN (PHASE 4)
        if (this.teleportTimer > 0) {
            const alpha = Math.max(0, this.teleportTimer / 0.45);
            const fromScrX = this.teleportFromX - cameraX;
            const fromScrY = this.teleportFromY - cameraY;
            const toScrX = this.teleportToX - cameraX;
            const toScrY = this.teleportToY - cameraY;

            ctx.save();
            ctx.globalAlpha = alpha;

            // Đường tia nước dịch chuyển tức thời
            ctx.beginPath();
            ctx.moveTo(fromScrX, fromScrY);
            ctx.lineTo(toScrX, toScrY);
            ctx.strokeStyle = '#00f0ff';
            ctx.lineWidth = 5;
            ctx.shadowColor = '#00f0ff';
            ctx.shadowBlur = 18;
            ctx.stroke();

            // Sóng chấn bung toả tại điểm xuất phát
            ctx.beginPath();
            ctx.arc(fromScrX, fromScrY, (1 - alpha) * 45 + 15, 0, Math.PI * 2);
            ctx.strokeStyle = 'rgba(0, 240, 255, 0.8)';
            ctx.lineWidth = 3;
            ctx.stroke();

            // Hào quang lóe sáng tại điểm đích
            ctx.beginPath();
            ctx.arc(toScrX, toScrY, alpha * 40 + 10, 0, Math.PI * 2);
            ctx.fillStyle = 'rgba(255, 255, 255, 0.6)';
            ctx.shadowColor = '#fff';
            ctx.shadowBlur = 22;
            ctx.fill();

            ctx.restore();
        }

        // 4.2 HIỆU ỨNG CUA PHÓNG CÀNG SẮT & DÂY XÍCH (PHASE 4)
        if (this.clawActive) {
            const clawScrX = this.clawX - cameraX;
            const clawScrY = this.clawY - cameraY;

            ctx.save();

            // 1. Dây càng / dây xích cơ bắp nối từ thân Cua đến đầu càng
            ctx.beginPath();
            ctx.moveTo(cx, cy);
            ctx.lineTo(clawScrX, clawScrY);
            ctx.strokeStyle = '#ea580c';
            ctx.lineWidth = 4.5;
            ctx.shadowColor = '#ff5722';
            ctx.shadowBlur = 10;
            ctx.stroke();

            // Các mắt xích kim loại trên dây
            const steps = Math.floor(this.clawDist / 25);
            ctx.fillStyle = '#ffedd5';
            for (let i = 1; i < steps; i++) {
                const stepX = cx + Math.cos(this.clawAngle) * (i * 25);
                const stepY = cy + Math.sin(this.clawAngle) * (i * 25);
                ctx.beginPath();
                ctx.arc(stepX, stepY, 2.5, 0, Math.PI * 2);
                ctx.fill();
            }

            // 2. Đầu Càng Sắt Kẹp Mạng
            ctx.translate(clawScrX, clawScrY);
            ctx.rotate(this.clawAngle);

            const isPulling = (this.clawState === 'PULLING');
            const clawSpread = isPulling ? 0.25 : 0.65; // Đang kẹp mục tiêu thì khép chặt càng lại

            ctx.fillStyle = '#c2410c';
            ctx.strokeStyle = '#fff';
            ctx.lineWidth = 1.5;

            // Ngón càng trên
            ctx.beginPath();
            ctx.arc(0, -6, 16, -clawSpread, Math.PI * 0.4);
            ctx.lineTo(14, -2);
            ctx.closePath();
            ctx.fill();
            ctx.stroke();

            // Ngón càng dưới
            ctx.beginPath();
            ctx.arc(0, 6, 16, -Math.PI * 0.4, clawSpread);
            ctx.lineTo(14, 2);
            ctx.closePath();
            ctx.fill();
            ctx.stroke();

            // Khớp nối càng
            ctx.beginPath();
            ctx.arc(0, 0, 7, 0, Math.PI * 2);
            ctx.fillStyle = '#7c2d12';
            ctx.fill();
            ctx.stroke();

            ctx.restore();
        }

        // 4.3 HIỆU ỨNG CÁ ÉP DÍNH BÁM GIÁC HÚT KÝ SINH (PHASE 4)
        if (this.isAttached) {
            ctx.save();
            ctx.beginPath();
            ctx.arc(cx, cy, (w / 2) + 8, 0, Math.PI * 2);
            ctx.strokeStyle = '#00ffc4';
            ctx.lineWidth = 3;
            ctx.fillStyle = 'rgba(0, 255, 196, 0.2)';
            ctx.shadowColor = '#00ffc4';
            ctx.shadowBlur = 18;
            ctx.fill();
            ctx.stroke();
            ctx.restore();
        }

        // 5. VÒNG HÀO QUANG DƯỚI THÂN CÁ
        ctx.save();
        ctx.translate(cx, cy);
        ctx.beginPath();
        ctx.arc(0, 0, w * 0.45, 0, Math.PI * 2);
        ctx.fillStyle = this.isLocal ? 'rgba(0, 240, 255, 0.18)' : 'rgba(255, 51, 153, 0.22)';
        ctx.fill();

        // CHUẨN HÓA HƯỚNG MẶT GỐC CỦA SPRITE:
        // Đa số sprite gốc (Cá Kiếm, Cá Ép, Tôm, Rùa, Cua, Bạch Tuộc, Cá Đuối Điện) có đầu hướng sang TRÁI (hoặc hướng lên/đối xứng).
        // Khi vẽ góc angle theo vector trỏ chuột (angle = 0 là hướng sang PHẢI):
        // Cần bù góc Math.PI để đầu quay đúng theo vector chuột, hoặc scale(-1, 1).
        // Quy tắc chuẩn:
        // - Xoay theo this.angle
        // - Lật scale(1, -1) khi bơi sang trái (cos(angle) < 0) để không bị ngửa bụng.
        // - Bù góc Math.PI nếu sprite gốc hướng sang TRÁI để đầu luôn hướng về vector di chuyển.
        const charId = (this.character && this.character.id) || 2;
        // Các nhân vật có ảnh gốc đầu quay sang TRÁI (hoặc đầu hướng LÊN): bù Math.PI để đầu trùng với angle
        // id 1: Tôm (đầu quay trái)
        // id 2: Cá Kiếm (đầu/mỏ kiếm quay trái)
        // id 4: Cá Ép (đầu quay trái)
        // id 7: Rùa (đầu/cổ quay trái)
        // id 3 (Cua), id 5 (Bạch Tuộc), id 6 (Cá Đuối Điện): đầu hướng lên trên -> bù +Math.PI/2
        let spriteRotation = this.angle;
        if (charId === 1 || charId === 2 || charId === 4 || charId === 7) {
            spriteRotation = this.angle + Math.PI;
        } else if (charId === 3 || charId === 5 || charId === 6) {
            spriteRotation = this.angle + Math.PI / 2;
        }

        ctx.rotate(spriteRotation);

        if (this.facingLeft) {
            ctx.scale(1, -1); // Chống ngửa bụng khi quay sang trái
        }

        const currentCanvas = (this.biteAnimTimer > 0 && this.eatSpriteCanvas)
            ? this.eatSpriteCanvas
            : (this.spriteCanvas || (window.GameAssets ? (window.GameAssets.getTransparent(this.character.img || 'cá kiếm.jpg')) : null));

        const currentImg = (this.biteAnimTimer > 0 && this.eatSpriteImg && this.eatSpriteImg.complete && this.eatSpriteImg.naturalWidth > 0)
            ? this.eatSpriteImg
            : (this.spriteImg && this.spriteImg.complete && this.spriteImg.naturalWidth > 0 ? this.spriteImg : null);

        const spr = currentCanvas || currentImg;

        if (spr) {
            ctx.drawImage(spr, -w / 2, -h / 2, w, h);
        }

        ctx.restore();

        // 6. VẼ TÊN & CẤP ĐỘ TRÊN ĐẦU CÁ
        ctx.save();
        ctx.font = 'bold 12px "Outfit", sans-serif';
        ctx.textAlign = 'center';

        ctx.fillStyle = this.isLocal ? '#00ffff' : (this.isBot ? '#ff5599' : '#ff99cc');
        const displayUsername = this.isBot ? `🤖 ${this.username}` : this.username;
        ctx.fillText(displayUsername, cx, screenY - 14);

        ctx.font = 'bold 10px "Fredoka One", sans-serif';
        ctx.fillStyle = '#ffd700';
        ctx.fillText(`LV.${this.getEffectiveSizeLevel()} (${this.score}đ)`, cx, screenY - 2);

        // HIỂN THỊ TRẠNG THÁI KÝ SINH (CÁ ÉP)
        if (this.isAttached) {
            ctx.font = 'bold 11px "Outfit", sans-serif';
            ctx.fillStyle = '#00ffc4';
            ctx.fillText(`🔗 KÝ SINH [CÁ CHỦ LV.${this.getEffectiveSizeLevel()}]: ${this.attachedTimer.toFixed(1)}s (Q: Tách)`, cx, screenY - 26);
        }

        // HIỂN THỊ TRẠNG THÁI CHOÁNG (NẾU CÓ)
        if (this.stunTimer > 0) {
            // Vòng điện viền gọn quanh thân người chơi khi bị giật
            ctx.save();
            ctx.strokeStyle = '#facc15';
            ctx.lineWidth = 1.8;
            ctx.setLineDash([5, 5]);
            ctx.lineDashOffset = Date.now() / 25;
            ctx.beginPath();
            ctx.arc(cx, cy, (w / 2) + 8, 0, Math.PI * 2);
            ctx.stroke();
            ctx.restore();

            ctx.font = 'bold 13px "Outfit", sans-serif';
            ctx.fillStyle = '#facc15';
            ctx.fillText('⚡ TÊ LIỆT ⚡', cx, screenY - 38);
        }

        ctx.restore();
    }

    // Reset sạch toàn bộ trạng thái nhân vật (Rematch / Thoát trận)
    resetState(startX, startY, startAngle) {
        this.x = startX !== undefined ? startX : 400;
        this.y = startY !== undefined ? startY : 500;
        this.angle = startAngle !== undefined ? startAngle : 0;
        this.targetAngle = this.angle;
        this.dead = false;
        this.hp = 100;
        this.maxHp = 100;
        this.lives = 3;
        this.mana = 0.0;
        this.sizeLevel = 1;
        this.fishEaten = 0;
        this.score = 0;
        this.slotW = null;
        this.slotE = null;

        // Reset sạch toàn bộ buff/debuff
        this.speedBurstTimer = 0.0;
        this.shieldTimer = 0.0;
        this.stunTimer = 0.0;
        this.slowTimer = 0.0;
        this.baKhiTimer = 0.0;
        this.shockWaveTimer = 0.0;
        this.shockWaveTickTimer = 0.0;
        this.inkTimer = 0.0;
        this.inInkBuff = false;

        // Reset sạch Cua (Càng sắt)
        this.clawActive = false;
        this.clawState = 'IDLE';
        this.clawTarget = null;
        this.clawDist = 0;

        // Reset sạch Cá Ép (Ký sinh & Dash)
        this.isDashing = false;
        this.dashTimer = 0.0;
        this.isAttached = false;
        this.attachedHost = null;
        this.attachedTimer = 0.0;

        // Reset Tôm
        this.teleportTimer = 0.0;

        // Reset Remote LERP
        this.remoteTargetX = this.x;
        this.remoteTargetY = this.y;
        this.remoteTargetAngle = this.angle;
        this.remoteTargetSize = 1;
    }
}

window.Player = Player;
