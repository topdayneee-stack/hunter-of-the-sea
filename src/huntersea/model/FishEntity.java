package huntersea.model;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

/**
 * Thực thể cá thường AI (7 cấp độ: cá 1 đến cá 7)
 */
public class FishEntity {
    private double x, y; // Tọa độ pixel (gốc trên-trái)
    private double vx, vy;
    private int level; // 1 đến 7
    private double gridW, gridH; // Kích thước theo ô
    private double pixelW, pixelH;
    private boolean facingLeft;
    private double stunTimer = 0; // Thời gian bị choáng (giây)
    private double slowTimer = 0; // Thời gian bị làm chậm
    private double slowMultiplier = 0.5;
    private double knockbackVx = 0.0;
    private double knockbackVy = 0.0;
    private double shieldTimer = 0.0;
    private double invincibleTimer = 0.0;
    private double poisonTimer = 0.0;
    private Player pullingCrab = null; // Cua đang kéo đối tượng này
    private double animPhase;
    private double wanderTimer; // Bộ đếm thay đổi hướng bơi ngẫu nhiên
    private boolean playerControlled = false; // Khi Cá Ép chiếm quyền điều khiển
    private boolean eaten = false; // Đã bị ăn thành công chưa (đảm bảo mỗi con chỉ tính 1 lần)

    public FishEntity(double x, double y, int level, boolean movingRight) {
        this.x = x;
        this.y = y;
        this.level = Math.max(1, Math.min(7, level));
        this.animPhase = Math.random() * Math.PI * 2;
        this.wanderTimer = Math.random() * 2.0; // Stagger lần wander đầu tiên

        initDimensions();

        double baseSpeed = getBaseSpeedForLevel(this.level);
        this.vx = movingRight ? baseSpeed : -baseSpeed;
        this.vy = (Math.random() - 0.5) * 30.0;
        this.facingLeft = !movingRight;
    }

    private void initDimensions() {
        // Căn chỉnh kích thước ô chuẩn tương quan với người chơi
        switch (level) {
            case 1: gridW = 1.6; gridH = 1.3; break;
            case 2: gridW = 2.2; gridH = 1.6; break;
            case 3: gridW = 2.8; gridH = 2.0; break;
            case 4: gridW = 3.4; gridH = 2.4; break;
            case 5: gridW = 4.0; gridH = 2.9; break;
            case 6: gridW = 4.8; gridH = 3.5; break;
            case 7: gridW = 5.8; gridH = 4.4; break;
            default: gridW = 2.0; gridH = 1.5; break;
        }
        this.pixelW = gridW * 40.0;
        this.pixelH = gridH * 40.0;
    }

    private double getBaseSpeedForLevel(int lvl) {
        // Tốc độ pixel/giây: Cá nhỏ bơi nhanh nhẹn hơn
        switch (lvl) {
            case 1: return 120.0;
            case 2: return 110.0;
            case 3: return 100.0;
            case 4: return 90.0;
            case 5: return 80.0;
            case 6: return 70.0;
            case 7: return 60.0;
            default: return 90.0;
        }
    }

    /**
     * Cập nhật vị trí cá với chuyển động ngẫu nhiên tự nhiên.
     * Cá KHÔNG còn săn đuổi hay theo dõi người chơi.
     */
    public void update(double dt, double mapWidth, double mapHeight) {
        if (shieldTimer > 0) shieldTimer -= dt;
        if (invincibleTimer > 0) invincibleTimer -= dt;
        if (poisonTimer > 0) poisonTimer -= dt;
        if (pullingCrab != null && (!pullingCrab.isCrabPulling() || pullingCrab.isDead())) {
            pullingCrab = null;
        }

        // Xử lý lực đẩy / hất văng (knockback displacement)
        if (Math.abs(knockbackVx) > 0.1 || Math.abs(knockbackVy) > 0.1) {
            x += knockbackVx * dt;
            y += knockbackVy * dt;
            knockbackVx *= Math.pow(0.02, dt);
            knockbackVy *= Math.pow(0.02, dt);
        }

        // Khi người chơi (Cá Ép) đang điều khiển: bỏ qua AI, chỉ giữ animation
        if (playerControlled) {
            animPhase += dt * 3.0;
            return;
        }

        if (stunTimer > 0) {
            stunTimer -= dt;
            return;
        }

        double speedMod = 1.0;
        if (slowTimer > 0) {
            slowTimer -= dt;
            speedMod = slowMultiplier;
        }
        if (poisonTimer > 0) {
            speedMod *= 0.6;
        }

        animPhase += dt * 3.0;
        double waveY = Math.sin(animPhase) * 15.0;

        // Chuyển động ngẫu nhiên (wander): thay đổi hướng mỗi 1.5 - 4 giây
        wanderTimer -= dt;
        if (wanderTimer <= 0) {
            wanderTimer = 1.5 + Math.random() * 2.5;
            vy = (Math.random() - 0.5) * 70.0;
            if (Math.random() < 0.15) {
                vx = -vx;
            }
        }

        x += vx * speedMod * dt;
        y += (vy + waveY) * speedMod * dt;

        // Cập nhật hướng quay mặt
        if (vx < -5.0) facingLeft = true;
        else if (vx > 5.0) facingLeft = false;

        // Giữ trong giới hạn dọc của map (ngang vẫn có thể ra ngoài để spawn lại)
        if (y < 20) { y = 20; vy = Math.abs(vy) + 5; }
        if (y + pixelH > mapHeight - 20) { y = mapHeight - 20 - pixelH; vy = -(Math.abs(vy) + 5); }
    }

    public boolean isOffscreen(double mapWidth) {
        return (x < -pixelW - 100) || (x > mapWidth + 100);
    }

    public Rectangle2D.Double getBounds() {
        return new Rectangle2D.Double(x, y, pixelW, pixelH);
    }

    /**
     * Vùng va chạm thu nhỏ (65%) khớp với phần nhìn thấy của sprite.
     */
    public Rectangle2D.Double getCollisionBounds() {
        double shrink = 0.65;
        double cw = pixelW * shrink;
        double ch = pixelH * shrink;
        return new Rectangle2D.Double(x + (pixelW - cw) / 2.0, y + (pixelH - ch) / 2.0, cw, ch);
    }

    public double getCenterX() { return x + pixelW / 2.0; }
    public double getCenterY() { return y + pixelH / 2.0; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return pixelW; }
    public double getHeight() { return pixelH; }
    public int getLevel() { return level; }
    public boolean isFacingLeft() { return facingLeft; }
    public boolean isStunned() { return stunTimer > 0; }

    public void setPosition(double x, double y) { this.x = x; this.y = y; }
    public void setFacingLeft(boolean left) { this.facingLeft = left; }
    public void setPlayerControlled(boolean controlled) { this.playerControlled = controlled; }
    public boolean isPlayerControlled() { return playerControlled; }
    public void stun(double duration) { this.stunTimer = Math.max(this.stunTimer, duration); }
    public void slow(double duration) { slow(duration, 0.5); }
    public void slow(double duration, double mult) {
        this.slowTimer = Math.max(this.slowTimer, duration);
        this.slowMultiplier = mult;
    }
    public double getStunTimer() { return stunTimer; }
    public double getSlowTimer() { return slowTimer; }
    public boolean isSlowed() { return slowTimer > 0 || poisonTimer > 0; }
    public void pushBack(double pushX, double pushY) { this.x += pushX; this.y += pushY; }
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
    public boolean isShieldActive() { return shieldTimer > 0; }
    public boolean isInvincible() { return invincibleTimer > 0 || shieldTimer > 0; }
    public void setShield(double duration) { this.shieldTimer = Math.max(this.shieldTimer, duration); }
    public void setInvincible(double duration) { this.invincibleTimer = Math.max(this.invincibleTimer, duration); }
    public void triggerPoison(double duration) { this.poisonTimer = Math.max(this.poisonTimer, duration); }
    public boolean isPoisoned() { return poisonTimer > 0; }
    public double getPoisonTimer() { return poisonTimer; }

    public void setPullingCrab(Player crab) { this.pullingCrab = crab; }
    public Player getPullingCrab() { return pullingCrab; }
    public boolean isBeingPulledByCrab() { return pullingCrab != null; }

    public boolean isEaten() { return eaten; }
    public synchronized boolean markEaten() {
        if (eaten) return false;
        eaten = true;
        return true;
    }
}
