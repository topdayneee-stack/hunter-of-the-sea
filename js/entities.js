/**
 * HUNTER OF THE SEA - ENTITIES & PREY ECOSYSTEM (PHASE 2)
 * Quản lý đàn cá mồi đại dương cấp 1-3, kiểm tra va chạm nuốt mồi,
 * hiệu ứng bọt nước và phản hồi vật lý dội ngược.
 */

class PreyFish {
    constructor(id, x, y, level, movingRight) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.level = Math.max(1, Math.min(7, level || 1));
        this.movingRight = movingRight !== undefined ? movingRight : Math.random() > 0.5;
        this.facingLeft = !this.movingRight;
        this.stunTimer = 0;
        this.slowTimer = 0;    // Bị làm chậm (mực đen)
        this.blindTimer = 0;   // Bị mù/mất phương hướng (mực đen)

        // Kích thước chuẩn tương ứng với 7 cấp độ cá (khớp với GameEngine.java)
        switch (this.level) {
            case 1:
                this.width = 54;
                this.height = 40;
                this.speed = 100 + Math.random() * 20;
                this.points = 15;
                break;
            case 2:
                this.width = 72;
                this.height = 54;
                this.speed = 90 + Math.random() * 18;
                this.points = 30;
                break;
            case 3:
                this.width = 94;
                this.height = 68;
                this.speed = 82 + Math.random() * 15;
                this.points = 50;
                break;
            case 4:
                this.width = 118;
                this.height = 84;
                this.speed = 74 + Math.random() * 12;
                this.points = 80;
                break;
            case 5:
                this.width = 145;
                this.height = 104;
                this.speed = 66 + Math.random() * 10;
                this.points = 120;
                break;
            case 6:
                this.width = 175;
                this.height = 126;
                this.speed = 58 + Math.random() * 10;
                this.points = 170;
                break;
            case 7:
            default:
                this.width = 210;
                this.height = 150;
                this.speed = 50 + Math.random() * 10;
                this.points = 250;
                break;
        }

        this.vx = this.movingRight ? this.speed : -this.speed;
        this.vy = (Math.random() - 0.5) * 20;
        this.wanderTimer = Math.random() * 2;
        this.eaten = false;

        // Ảnh sprite cá mồi & Offscreen Canvas tách nền trong suốt
        this.spriteKey = `cá ${this.level}.png`;
        this.img = new Image();
        this.canvas = null;

        const onFishImgLoad = () => {
            if (window.GameAssets) {
                this.canvas = window.GameAssets.getTransparent(this.img, this.spriteKey);
            }
        };
        this.img.onload = onFishImgLoad;
        this.img.onerror = () => {
            // Thử nạp lại file .jpg nếu .png không tải được
            const jpgFallback = `cá ${this.level}.jpg`;
            if (this.img.src.indexOf('.png') !== -1) {
                this.img = new Image();
                this.img.onload = onFishImgLoad;
                this.img.onerror = () => {
                    console.error(`[PreyFish] ❌ Không thể load sprite: cá ${this.level}.png hoặc .jpg`);
                };
                this.img.src = jpgFallback;
            }
        };
        this.img.src = this.spriteKey;

        if (window.GameAssets) {
            this.canvas = window.GameAssets.getTransparent(this.spriteKey);
        }
    }

    update(dt, mapW, mapH) {
        if (this.eaten) return;

        if (this.stunTimer > 0) {
            this.stunTimer -= dt;
            return;
        }

        if (this.slowTimer > 0) this.slowTimer -= dt;
        if (this.blindTimer > 0) {
            this.blindTimer -= dt;
            // Bị mù: Mất phương hướng, bơi giật chao đảo nhẹ
            this.wanderTimer -= dt * 2.5;
        } else {
            this.wanderTimer -= dt;
        }

        if (this.wanderTimer <= 0) {
            this.wanderTimer = 2.5 + Math.random() * 3;
            this.vy = (Math.random() - 0.5) * 30;
            if (this.blindTimer > 0) {
                // Rối hướng: có thể đảo chiều bơi ngẫu nhiên
                if (Math.random() < 0.3) {
                    this.vx = -this.vx;
                    this.facingLeft = this.vx < 0;
                }
            }
        }

        // Tốc độ di chuyển (giảm 50% nếu đang bị dính mực slow, nhân với speedMultiplier từ DDA)
        const ddaMult = (typeof this.speedMultiplier === 'number') ? this.speedMultiplier : 1.0;
        const currentSpeedMult = (this.slowTimer > 0 ? 0.5 : 1.0) * ddaMult;
        this.x += this.vx * currentSpeedMult * dt;
        this.y += this.vy * currentSpeedMult * dt;

        // Chạm mép bản đồ -> quay đầu tự nhiên
        if (this.x < 30) {
            this.x = 30;
            this.vx = Math.abs(this.vx);
            this.facingLeft = false;
        } else if (this.x + this.width > mapW - 30) {
            this.x = mapW - this.width - 30;
            this.vx = -Math.abs(this.vx);
            this.facingLeft = true;
        }

        if (this.y < 30) {
            this.y = 30;
            this.vy = Math.abs(this.vy);
        } else if (this.y + this.height > mapH - 30) {
            this.y = mapH - this.height - 30;
            this.vy = -Math.abs(this.vy);
        }
    }

    getCenterX() { return this.x + this.width / 2; }
    getCenterY() { return this.y + this.height / 2; }
    getRadius() { return (this.width / 2) * 0.75; }

    render(ctx, cameraX, cameraY) {
        if (this.eaten) return;

        const screenX = this.x - cameraX;
        const screenY = this.y - cameraY;

        ctx.save();
        ctx.translate(screenX + this.width / 2, screenY + this.height / 2);

        // Chuẩn hóa hướng mặt của sprite gốc:
        // cá 1, cá 4: Ảnh gốc đầu quay sang PHẢI -> Khi bơi sang trái (facingLeft = true), cần scale(-1, 1).
        // cá 2, cá 3, cá 5, cá 6, cá 7: Ảnh gốc đầu quay sang TRÁI -> Mặc định đã hướng trái! Khi bơi sang phải (!facingLeft), cần scale(-1, 1); khi bơi sang trái (facingLeft), giữ nguyên scale(1, 1).
        const defaultFacesRight = (this.level === 1 || this.level === 4);
        let flipX = false;
        if (defaultFacesRight) {
            flipX = this.facingLeft;
        } else {
            flipX = !this.facingLeft;
        }

        if (flipX) {
            ctx.scale(-1, 1);
        }

        const spr = this.canvas || (window.GameAssets ? window.GameAssets.getTransparent(this.spriteKey) : null) || (this.img && this.img.complete && this.img.naturalWidth > 0 ? this.img : null);
        if (spr) {
            ctx.drawImage(spr, -this.width / 2, -this.height / 2, this.width, this.height);
        }

        // Vẽ hiệu ứng choáng / tê liệt điện gọn gàng, rõ ràng mà không làm lòe màn hình
        if (this.stunTimer > 0) {
            // Tia chớp điện mini xoay quanh thân cá bị trúng đòn
            const stunAlpha = Math.min(1.0, this.stunTimer / 0.25);
            ctx.save();
            ctx.strokeStyle = `rgba(250, 204, 21, ${stunAlpha * 0.9})`;
            ctx.lineWidth = 1.6;
            ctx.shadowColor = '#38bdf8';
            ctx.shadowBlur = 6;
            const rStun = Math.max(14, this.width * 0.45);
            ctx.beginPath();
            ctx.arc(0, 0, rStun, 0, Math.PI * 2);
            ctx.setLineDash([4, 6]);
            ctx.lineDashOffset = Date.now() / 30;
            ctx.stroke();
            ctx.restore();

            // Biểu tượng choáng gọn trên đầu
            ctx.font = 'bold 15px sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText('⚡💫', 0, -this.height / 2 - 4);
        } else if (this.blindTimer > 0) {
            // Biểu tượng bị mù / dính mực đen
            ctx.font = 'bold 14px sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText('👁️‍🗨️💨', 0, -this.height / 2 - 4);
        }

        ctx.restore();

        // Nhãn cấp độ nhỏ phía trên cá để nhận biết cá lớn vs cá bé
        ctx.save();
        ctx.font = 'bold 11px "Fredoka One", sans-serif';
        ctx.textAlign = 'center';
        ctx.fillStyle = this.level <= 2 ? '#a7f3d0' : (this.level <= 4 ? '#fef08a' : '#fca5a5');
        ctx.shadowColor = '#000';
        ctx.shadowBlur = 4;
        ctx.fillText(`LV.${this.level}`, screenX + this.width / 2, screenY - 4);
        ctx.restore();
    }
}

// =========================================================================
// VẬT PHẨM KỸ NĂNG RƠI TRÊN BIỂN (PHASE 3)
// =========================================================================
class SkillItemDrop {
    constructor(id, x, y, skillType) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = 46;
        this.height = 46;
        this.lifetime = 14.0; // Tồn tại 14 giây
        this.animPhase = Math.random() * Math.PI * 2;
        this.collected = false;

        this.vx = (Math.random() - 0.5) * 40;
        this.vy = (Math.random() - 0.5) * 40;

        const SKILL_TYPES_DEF = {
            SWORD_DASH: {
                type: 'SWORD_DASH',
                name: 'Lướt Tốc',
                icon: 'kĩ năng cá kiếm.jpg',
                color: '#00d2ff',
                duration: 2.5
            },
            RAY_SHOCK: {
                type: 'RAY_SHOCK',
                name: 'Xung Điện',
                icon: 'kĩ năng cá đuối điện.jpg',
                color: '#facc15',
                duration: 2.0
            },
            TURTLE_SHELL: {
                type: 'TURTLE_SHELL',
                name: 'Khiên Thần',
                icon: 'kĩ năng rùa.jpg',
                color: '#22c55e',
                duration: 3.5
            },
            OCTOPUS_INK: {
                type: 'OCTOPUS_INK',
                name: 'Vùng Mực',
                icon: 'kĩ năng bạch tuộc.jpg',
                color: '#a855f7',
                duration: 4.0
            },
            BA_KHI: {
                type: 'BA_KHI',
                name: 'Bá Khí',
                icon: 'bá khí.jpg',
                color: '#ef4444',
                duration: 5.0
            }
        };

        const keys = Object.keys(SKILL_TYPES_DEF);
        this.skill = SKILL_TYPES_DEF[skillType] || SKILL_TYPES_DEF[keys[Math.floor(Math.random() * keys.length)]];

        this.img = new Image();
        this.canvas = null;

        const onSkillLoad = () => {
            if (window.GameAssets) this.canvas = window.GameAssets.getTransparent(this.img, this.skill.icon);
        };
        this.img.onload = onSkillLoad;
        this.img.onerror = () => {
            const altSrc = this.skill.icon.endsWith('.png') ? this.skill.icon.replace(/\.png$/i, '.jpg') : this.skill.icon.replace(/\.jpg$/i, '.png');
            this.img = new Image();
            this.img.onload = onSkillLoad;
            this.img.src = altSrc;
        };
        this.img.src = this.skill.icon;
    }

    update(dt, mapW, mapH) {
        this.lifetime -= dt;
        this.animPhase += dt * 3.2;

        this.x += this.vx * dt;
        this.y += (this.vy + Math.sin(this.animPhase) * 14) * dt;

        if (this.x < 35) { this.x = 35; this.vx = Math.abs(this.vx); }
        else if (this.x + this.width > mapW - 35) { this.x = mapW - this.width - 35; this.vx = -Math.abs(this.vx); }

        if (this.y < 35) { this.y = 35; this.vy = Math.abs(this.vy); }
        else if (this.y + this.height > mapH - 35) { this.y = mapH - this.height - 35; this.vy = -Math.abs(this.vy); }
    }

    getCenterX() { return this.x + this.width / 2; }
    getCenterY() { return this.y + this.height / 2; }
    getRadius() { return this.width / 2; }

    render(ctx, cameraX, cameraY) {
        if (this.collected || this.lifetime <= 0) return;

        const screenX = this.x - cameraX;
        const screenY = this.y - cameraY;
        const cx = screenX + this.width / 2;
        const cy = screenY + this.height / 2;
        const r = this.width / 2;

        ctx.save();

        // Hào quang bong bóng phát sáng
        ctx.beginPath();
        ctx.arc(cx, cy, r + 4 + Math.sin(this.animPhase) * 2, 0, Math.PI * 2);
        ctx.fillStyle = this.skill.color;
        ctx.globalAlpha = 0.35;
        ctx.fill();

        // Vòng viền xoay
        ctx.strokeStyle = this.skill.color;
        ctx.lineWidth = 2.5;
        ctx.globalAlpha = 0.9;
        ctx.stroke();

        // Icon kỹ năng bên trong
        ctx.save();
        ctx.beginPath();
        ctx.arc(cx, cy, r - 3, 0, Math.PI * 2);
        ctx.clip();
        const spr = this.canvas || (window.GameAssets ? window.GameAssets.getTransparent(this.skill.icon) : null) || (this.img && this.img.complete && this.img.naturalWidth > 0 ? this.img : null);
        if (spr) {
            ctx.drawImage(spr, cx - r + 3, cy - r + 3, (r - 3) * 2, (r - 3) * 2);
        } else {
            ctx.fillStyle = this.skill.color;
            ctx.fill();
        }
        ctx.restore();

        // Nhãn tên kỹ năng
        ctx.font = 'bold 9px "Outfit", sans-serif';
        ctx.textAlign = 'center';
        ctx.fillStyle = '#ffffff';
        ctx.shadowColor = '#000';
        ctx.shadowBlur = 4;
        ctx.fillText(this.skill.name, cx, screenY - 6);

        ctx.restore();
    }
}

// =========================================================================
// MỐI NGUY HIỂM: BOM BIỂN & CÁ ĐỘC (CHẾ ĐỘ SINH TỒN PVE)
// =========================================================================
class HazardBomb {
    constructor(id, x, y) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = 46;
        this.height = 46;
        this.vx = (Math.random() - 0.5) * 25;
        this.vy = (Math.random() - 0.5) * 25;
        this.exploded = false;
        this.animTimer = 0;
        this.img = new Image();
        this.explodeImg = new Image();
        this.bombCanvas = null;
        this.explodeCanvas = null;

        const onBombLoad = () => {
            if (window.GameAssets) this.bombCanvas = window.GameAssets.getTransparent(this.img, 'bom.png');
        };
        const onExplodeLoad = () => {
            if (window.GameAssets) this.explodeCanvas = window.GameAssets.getTransparent(this.explodeImg, 'bom nổ.png');
        };

        this.img.onload = onBombLoad;
        this.img.onerror = () => {
            if (this.img.src.indexOf('.png') !== -1) {
                this.img = new Image();
                this.img.onload = onBombLoad;
                this.img.src = 'bom.jpg';
            }
        };
        this.img.src = 'bom.png';

        this.explodeImg.onload = onExplodeLoad;
        this.explodeImg.onerror = () => {
            if (this.explodeImg.src.indexOf('.png') !== -1) {
                this.explodeImg = new Image();
                this.explodeImg.onload = onExplodeLoad;
                this.explodeImg.src = 'bom nổ.jpg';
            }
        };
        this.explodeImg.src = 'bom nổ.png';
    }

    getCenterX() { return this.x + this.width / 2; }
    getCenterY() { return this.y + this.height / 2; }
    getRadius() { return this.width / 2; }

    update(dt, mapW, mapH) {
        if (this.exploded) {
            this.animTimer += dt;
            return;
        }
        this.x += this.vx * dt;
        this.y += this.vy * dt;
        if (this.x < 40) { this.x = 40; this.vx = Math.abs(this.vx); }
        else if (this.x + this.width > mapW - 40) { this.x = mapW - this.width - 40; this.vx = -Math.abs(this.vx); }
        if (this.y < 40) { this.y = 40; this.vy = Math.abs(this.vy); }
        else if (this.y + this.height > mapH - 40) { this.y = mapH - this.height - 40; this.vy = -Math.abs(this.vy); }
    }

    render(ctx, cameraX, cameraY) {
        if (this.exploded && this.animTimer > 0.6) return;
        const screenX = this.x - cameraX;
        const screenY = this.y - cameraY;
        const cx = screenX + this.width / 2;
        const cy = screenY + this.height / 2;

        ctx.save();
        if (this.exploded) {
            const spr = this.explodeCanvas || (window.GameAssets ? window.GameAssets.getTransparent('bom nổ.png') : null) || (this.explodeImg && this.explodeImg.complete && this.explodeImg.naturalWidth > 0 ? this.explodeImg : null);
            if (spr) {
                ctx.drawImage(spr, cx - 45, cy - 45, 90, 90);
            }
        } else {
            const spr = this.bombCanvas || (window.GameAssets ? window.GameAssets.getTransparent('bom.png') : null) || (this.img && this.img.complete && this.img.naturalWidth > 0 ? this.img : null);
            if (spr) {
                ctx.drawImage(spr, screenX, screenY, this.width, this.height);
            }
        }
        ctx.restore();
    }
}

class HazardPoisonFish {
    constructor(id, x, y) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = 54;
        this.height = 36;
        this.movingRight = Math.random() > 0.5;
        this.speed = 110;
        this.img = new Image();
        this.canvas = null;

        const onPoisonLoad = () => {
            if (window.GameAssets) this.canvas = window.GameAssets.getTransparent(this.img, 'cá độc.png');
        };
        this.img.onload = onPoisonLoad;
        this.img.onerror = () => {
            if (this.img.src.indexOf('.png') !== -1) {
                this.img = new Image();
                this.img.onload = onPoisonLoad;
                this.img.src = 'cá độc.jpg';
            }
        };
        this.img.src = 'cá độc.png';
    }

    getCenterX() { return this.x + this.width / 2; }
    getCenterY() { return this.y + this.height / 2; }
    getRadius() { return this.width * 0.42; }

    update(dt, mapW, mapH) {
        if (this.movingRight) {
            this.x += this.speed * dt;
            if (this.x + this.width > mapW - 50) this.movingRight = false;
        } else {
            this.x -= this.speed * dt;
            if (this.x < 50) this.movingRight = true;
        }
    }

    render(ctx, cameraX, cameraY) {
        const screenX = this.x - cameraX;
        const screenY = this.y - cameraY;
        ctx.save();
        ctx.translate(screenX + this.width / 2, screenY + this.height / 2);
        if (!this.movingRight) ctx.scale(-1, 1);
        const spr = this.canvas || (window.GameAssets ? window.GameAssets.getTransparent('cá độc.png') : null) || (this.img && this.img.complete && this.img.naturalWidth > 0 ? this.img : null);
        if (spr) {
            ctx.drawImage(spr, -this.width / 2, -this.height / 2, this.width, this.height);
        }
        ctx.restore();
    }
}

// Hệ thống hạt bọt nước
class WaterParticle {
    constructor(x, y, vx, vy, color, size, life) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.color = color || 'rgba(255, 255, 255, 0.7)';
        this.size = size || 4;
        this.maxLife = life || 0.8;
        this.life = this.maxLife;
    }

    update(dt) {
        this.x += this.vx * dt;
        this.y += this.vy * dt;
        this.life -= dt;
    }

    render(ctx, cameraX, cameraY) {
        if (this.life <= 0) return;
        const alpha = Math.max(0, this.life / this.maxLife);
        ctx.save();
        ctx.fillStyle = this.color;
        ctx.globalAlpha = alpha;
        ctx.beginPath();
        ctx.arc(this.x - cameraX, this.y - cameraY, this.size, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
    }
}

class EntityManager {
    constructor() {
        this.fishes = [];
        this.items = []; // Danh sách vật phẩm kỹ năng rơi trên biển (Phase 3)
        this.hazards = []; // Bom biển & Cá độc (Chế độ PvE)
        this.particles = [];
        this.MAP_WIDTH = 1920;
        this.MAP_HEIGHT = 1080;
        this.itemSpawnTimer = 3.0; // Xuất hiện ngọc kỹ năng đầu tiên sau 3s

        // Thuật toán hỗ trợ người chơi & Cân bằng động DDA (Dynamic Difficulty Adjustment)
        this.fishSpeedMultiplier = 1.0; // Hệ số tốc độ bơi cá mồi xung quanh
        this.targetFishCount = 28;      // Số lượng cá mồi duy trì trên bản đồ
        this.smallFishRatio = 0.45;     // Tỷ lệ sinh cá con (Level 1-2)
        this.spawnNearPlayer = false;   // Cờ ưu tiên sinh cá con gần người chơi
        this.nearPlayerPos = null;      // Tọa độ người chơi {x, y}
    }

    spawnSmallFishNear(targetX, targetY) {
        const newId = Date.now() + Math.floor(Math.random() * 1000);
        // Sinh ở cự ly 200px - 420px quanh người chơi, không quá sát để tự nhiên
        const angle = Math.random() * Math.PI * 2;
        const dist = 200 + Math.random() * 220;
        let x = targetX + Math.cos(angle) * dist;
        let y = targetY + Math.sin(angle) * dist;
        x = Math.max(60, Math.min(this.MAP_WIDTH - 120, x));
        y = Math.max(60, Math.min(this.MAP_HEIGHT - 120, y));

        // Ưu tiên cá con cấp 1 (75%) hoặc cấp 2 (25%)
        const level = Math.random() < 0.75 ? 1 : 2;
        const movingRight = Math.random() > 0.5;
        const fish = new PreyFish(newId, x, y, level, movingRight);
        fish.speedMultiplier = this.fishSpeedMultiplier || 1.0;
        this.fishes.push(fish);
        this.spawnBubbles(x, y, 10, '#00ffc4'); // Hiệu ứng bọt nước báo hiệu cá con xuất hiện
        return fish;
    }

    initEcosystem(seedList, gameMode) {
        this.fishes = [];
        this.items = [];
        this.hazards = [];

        // Sinh đàn cá mồi 7 cấp độ
        if (seedList && Array.isArray(seedList) && seedList.length > 0) {
            seedList.forEach(item => {
                this.fishes.push(new PreyFish(item.id, item.x, item.y, item.level, item.movingRight));
            });
        } else {
            // Sinh 32 con cá mồi đại dương với phân bổ tự nhiên 7 cấp độ
            for (let i = 0; i < 32; i++) {
                const r = Math.random();
                let level = 1;
                if (r < 0.40) level = 1;       // 40% Cá cấp 1 (mồi cho người mới)
                else if (r < 0.65) level = 2;  // 25% Cá cấp 2
                else if (r < 0.80) level = 3;  // 15% Cá cấp 3
                else if (r < 0.90) level = 4;  // 10% Cá cấp 4
                else if (r < 0.96) level = 5;  // 6% Cá cấp 5
                else if (r < 0.99) level = 6;  // 3% Cá cấp 6
                else level = 7;                // 1% Cá cấp 7 khổng lồ

                const x = 100 + Math.random() * (this.MAP_WIDTH - 200);
                const y = 80 + Math.random() * (this.MAP_HEIGHT - 160);
                this.fishes.push(new PreyFish(i + 1, x, y, level, Math.random() > 0.5));
            }
        }

        // Sinh Bom biển & Cá độc (Chướng ngại vật hiểm họa cho các chế độ chơi)
        const bombCount = (gameMode === 'PVE') ? 5 : 4;
        for (let i = 0; i < bombCount; i++) {
            const bx = 200 + Math.random() * (this.MAP_WIDTH - 400);
            const by = 150 + Math.random() * (this.MAP_HEIGHT - 300);
            this.hazards.push(new HazardBomb('bomb_' + i, bx, by));
        }

        const poisonCount = (gameMode === 'PVE') ? 4 : 3;
        for (let i = 0; i < poisonCount; i++) {
            const px = 150 + Math.random() * (this.MAP_WIDTH - 300);
            const py = 120 + Math.random() * (this.MAP_HEIGHT - 240);
            this.hazards.push(new HazardPoisonFish('poison_' + i, px, py));
        }

        // Sinh sẵn 1 viên Bá Khí khởi đầu gần khu vực trung tâm để người chơi có thể ăn và test ngay
        this.spawnSkillDrop('BA_KHI', this.MAP_WIDTH / 2 + 160, this.MAP_HEIGHT / 2 - 60);
    }

    getExportList() {
        return this.fishes.map(f => ({
            id: f.id,
            x: Math.round(f.x),
            y: Math.round(f.y),
            level: f.level,
            movingRight: f.movingRight
        }));
    }

    spawnSkillDrop(skillType, x, y) {
        const id = 'item_' + Date.now() + '_' + Math.floor(Math.random() * 1000);
        const posX = x !== undefined ? x : 150 + Math.random() * (this.MAP_WIDTH - 300);
        const posY = y !== undefined ? y : 120 + Math.random() * (this.MAP_HEIGHT - 240);
        const item = new SkillItemDrop(id, posX, posY, skillType);
        this.items.push(item);
        return item;
    }

    update(dt) {
        // 1. Cập nhật cá mồi và áp dụng tốc độ bơi DDA
        const curDdaSpeed = this.fishSpeedMultiplier || 1.0;
        this.fishes.forEach(f => {
            f.speedMultiplier = curDdaSpeed;
            f.update(dt, this.MAP_WIDTH, this.MAP_HEIGHT);
        });

        // 2. Cập nhật mối nguy hiểm (Bom biển & Cá độc)
        for (let i = this.hazards.length - 1; i >= 0; i--) {
            const h = this.hazards[i];
            h.update(dt, this.MAP_WIDTH, this.MAP_HEIGHT);
            if (h.exploded && h.animTimer > 0.6) {
                this.hazards.splice(i, 1);
            }
        }

        // 3. Cập nhật & sinh vật phẩm kỹ năng rơi
        this.itemSpawnTimer -= dt;
        if (this.itemSpawnTimer <= 0) {
            this.itemSpawnTimer = 8.0 + Math.random() * 4.0; // Cứ mỗi 8-12s sinh 1 ngọc kỹ năng
            if (this.items.length < 5) {
                this.spawnSkillDrop();
            }
        }

        for (let i = this.items.length - 1; i >= 0; i--) {
            const item = this.items[i];
            item.update(dt, this.MAP_WIDTH, this.MAP_HEIGHT);
            if (item.collected || item.lifetime <= 0) {
                this.items.splice(i, 1);
            }
        }

        // 4. Cập nhật hạt bọt nước
        for (let i = this.particles.length - 1; i >= 0; i--) {
            const p = this.particles[i];
            p.update(dt);
            if (p.life <= 0) {
                this.particles.splice(i, 1);
            }
        }

        // Tự động bù lại đàn cá mồi theo targetFishCount và smallFishRatio của DDA
        const targetCount = this.targetFishCount || 28;
        const aliveCount = this.fishes.filter(f => !f.eaten).length;
        if (aliveCount < targetCount) {
            const newId = Date.now() + Math.floor(Math.random() * 1000);
            const r = Math.random();
            const smallRatio = this.smallFishRatio !== undefined ? this.smallFishRatio : 0.45;

            // Phân bổ cấp độ cá mồi dựa trên smallRatio:
            // Khi gặp khó: smallRatio cao (0.65-0.75) -> phần lớn sinh cá con level 1
            // Khi tiến hóa quá nhanh: smallRatio thấp (0.20-0.25) -> giảm cá con, tăng cá to
            let level = 1;
            if (r < smallRatio) {
                level = 1;
            } else if (r < smallRatio + 0.28) {
                level = 2;
            } else if (r < smallRatio + 0.44) {
                level = 3;
            } else if (r < smallRatio + 0.52) {
                level = 4;
            } else {
                level = 5;
            }

            const movingRight = Math.random() > 0.5;
            let x, y;
            if (this.spawnNearPlayer && this.nearPlayerPos) {
                // Ưu tiên sinh cá hỗ trợ trong vùng biển quanh người chơi
                const angle = Math.random() * Math.PI * 2;
                const dist = 220 + Math.random() * 250;
                x = Math.max(60, Math.min(this.MAP_WIDTH - 120, this.nearPlayerPos.x + Math.cos(angle) * dist));
                y = Math.max(60, Math.min(this.MAP_HEIGHT - 120, this.nearPlayerPos.y + Math.sin(angle) * dist));
            } else {
                x = movingRight ? 40 : this.MAP_WIDTH - 80;
                y = 100 + Math.random() * (this.MAP_HEIGHT - 200);
            }

            const newFish = new PreyFish(newId, x, y, level, movingRight);
            newFish.speedMultiplier = curDdaSpeed;
            this.fishes.push(newFish);
        }
    }

    // Kiểm tra va chạm với Bom biển và Cá độc (PvE & Sinh tồn)
    checkHazardCollision(player) {
        if (!player || player.dead || player.isInvincible()) return null;
        const px = player.getCenterX();
        const py = player.getCenterY();
        const pr = player.getRadius();

        for (let i = this.hazards.length - 1; i >= 0; i--) {
            const h = this.hazards[i];
            const dist = Math.hypot(px - h.getCenterX(), py - h.getCenterY());

            if (h instanceof HazardBomb && !h.exploded) {
                if (dist < pr + h.getRadius()) {
                    h.exploded = true;
                    this.spawnBubbles(h.getCenterX(), h.getCenterY(), 35, 'rgba(255, 69, 0, 0.95)');
                    return { type: 'BOMB', hazard: h };
                }
            } else if (h instanceof HazardPoisonFish) {
                if (dist < pr + h.getRadius()) {
                    this.spawnBubbles(h.getCenterX(), h.getCenterY(), 20, 'rgba(168, 85, 247, 0.9)');
                    const hazard = h;
                    // Xóa cá độc sau khi va chạm (tương tự GameEngine.java)
                    this.hazards.splice(i, 1);
                    return { type: 'POISON', hazard };
                }
            }
        }
        return null;
    }

    // Kiểm tra người chơi nhặt vật phẩm kỹ năng trên biển
    checkItemCollection(player) {
        if (!player || player.dead) return null;
        const px = player.getCenterX();
        const py = player.getCenterY();
        const pr = player.getRadius();

        for (let i = 0; i < this.items.length; i++) {
            const item = this.items[i];
            if (item.collected || item.lifetime <= 0) continue;

            const dist = Math.hypot(item.getCenterX() - px, item.getCenterY() - py);
            if (dist < pr + item.getRadius()) {
                item.collected = true;

                // NẾU LÀ VIÊN BÁ KHÍ: KÍCH HOẠT TỨC THÌ HIỆU ỨNG BẤT TỬ & TĂNG TỐC!
                let isInstant = false;
                let slotName = null;
                if (item.skill && item.skill.type === 'BA_KHI') {
                    if (typeof player.activateBaKhi === 'function') {
                        player.activateBaKhi(item.skill.duration || 5.0);
                    } else {
                        player.baKhiTimer = item.skill.duration || 5.0;
                    }
                    isInstant = true;
                } else {
                    slotName = player.addSkillItem(item.skill);
                }

                this.spawnBubbles(item.getCenterX(), item.getCenterY(), 20, item.skill.color || '#ef4444');
                return { item, slotName, isInstant };
            }
        }
        return null;
    }

    spawnBubbles(x, y, count, color) {
        for (let i = 0; i < count; i++) {
            const angle = Math.random() * Math.PI * 2;
            const speed = 40 + Math.random() * 80;
            this.particles.push(new WaterParticle(
                x, y,
                Math.cos(angle) * speed,
                Math.sin(angle) * speed - 20, // Hơi nổi lên trên
                color || 'rgba(0, 240, 255, 0.7)',
                2 + Math.random() * 4,
                0.5 + Math.random() * 0.5
            ));
        }
    }

    render(ctx, cameraX, cameraY) {
        this.fishes.forEach(f => f.render(ctx, cameraX, cameraY));
        this.hazards.forEach(h => h.render(ctx, cameraX, cameraY));
        this.items.forEach(item => item.render(ctx, cameraX, cameraY));
        this.particles.forEach(p => p.render(ctx, cameraX, cameraY));
    }
}

window.PreyFish = PreyFish;
window.SkillItemDrop = SkillItemDrop;
window.HazardBomb = HazardBomb;
window.HazardPoisonFish = HazardPoisonFish;
window.EntityManager = EntityManager;
