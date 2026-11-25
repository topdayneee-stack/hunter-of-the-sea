package huntersea.model;

import java.awt.geom.Rectangle2D;

/**
 * Các vật phẩm có thể nhặt trong game:
 * - BA_KHI: Ngọc Bá Khí — xuất hiện mỗi 5s, trôi lơ lửng ngẫu nhiên, tồn tại 3 giây
 * - SKILL_DROP: Bong bóng kỹ năng rơi trong chế độ PvP (tồn tại 5 giây)
 */
public class ItemEntity {
    public enum Type {
        BA_KHI,
        SKILL_DROP
    }

    private final Type type;
    private final SkillType droppedSkill; // Chỉ dùng cho SKILL_DROP
    private double x, y;
    private double vx, vy;
    private double width = 45, height = 45;
    private double lifetime;
    private double animPhase;

    public ItemEntity(Type type, double x, double y, SkillType skill) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.droppedSkill = skill;
        this.animPhase = Math.random() * Math.PI * 2;

        if (type == Type.SKILL_DROP) {
            this.lifetime = 5.0; // Kỹ năng rơi tồn tại 5 giây
            this.width = 50;
            this.height = 50;
        } else {
            this.lifetime = 3.0; // Bá Khí tồn tại 3 giây
            this.width = 46;
            this.height = 46;
        }

        // Vận tốc trôi ngẫu nhiên theo mọi hướng (50–110 px/s)
        double speed = 50 + Math.random() * 60;
        double angle  = Math.random() * Math.PI * 2;
        this.vx = Math.cos(angle) * speed;
        this.vy = Math.sin(angle) * speed;
    }

    /**
     * Cập nhật vị trí, bounce khi chạm biên map.
     */
    public void update(double dt, double mapWidth, double mapHeight) {
        lifetime -= dt;
        animPhase += dt * 3.0;

        // Chuyển động + hiệu ứng lơ lửng sóng nhẹ theo trục Y
        x += vx * dt;
        y += (vy + Math.sin(animPhase) * 12.0) * dt;

        // Bounce biên — đổi chiều khi chạm mép
        if (x < 40)                        { x = 40;                        vx =  Math.abs(vx); }
        if (x + width  > mapWidth  - 40)   { x = mapWidth  - 40 - width;    vx = -Math.abs(vx); }
        if (y < 40)                        { y = 40;                        vy =  Math.abs(vy); }
        if (y + height > mapHeight - 40)   { y = mapHeight - 40 - height;   vy = -Math.abs(vy); }
    }

    /** Gọi update không cần biên (tương thích ngược với code cũ) */
    public void update(double dt) {
        update(dt, 3200, 1800);
    }

    public boolean isExpired() {
        return lifetime <= 0;
    }

    public Rectangle2D.Double getBounds() {
        return new Rectangle2D.Double(x, y, width, height);
    }

    public Type getType()              { return type; }
    public SkillType getDroppedSkill() { return droppedSkill; }
    public double getX()               { return x; }
    public double getY()               { return y; }
    public double getWidth()           { return width; }
    public double getHeight()          { return height; }
    public double getCenterX()         { return x + width  / 2.0; }
    public double getCenterY()         { return y + height / 2.0; }
    public double getLifetime()        { return lifetime; }
}
