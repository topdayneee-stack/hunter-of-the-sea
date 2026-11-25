package huntersea.model;

import java.awt.geom.Rectangle2D;

/**
 * Các mối nguy hiểm trong đại dương:
 * - BOMB: Va vào phát nổ tức thì (Game Over trong Basic)
 * - POISON_FISH: Chạm phải bị trúng độc làm chậm
 * - INK_BLOB: Mực đen trôi nổi làm tối màn hình 2 giây
 */
public class HazardEntity {
    public enum Type {
        BOMB,
        POISON_FISH,
        INK_BLOB
    }

    private final Type type;
    private double x, y;
    private double vx, vy;
    private double width, height;
    private boolean exploded = false;
    private double animPhase;

    public HazardEntity(Type type, double x, double y) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.animPhase = Math.random() * Math.PI * 2;

        switch (type) {
            case BOMB:
                this.width = 65;
                this.height = 65;
                this.vx = (Math.random() - 0.5) * 15;
                this.vy = (Math.random() - 0.5) * 15;
                break;
            case POISON_FISH:
                this.width = 75;
                this.height = 60;
                this.vx = (Math.random() > 0.5 ? 1 : -1) * (40 + Math.random() * 30);
                this.vy = (Math.random() - 0.5) * 20;
                break;
            case INK_BLOB:
                this.width = 60;
                this.height = 60;
                this.vx = (Math.random() > 0.5 ? 1 : -1) * (20 + Math.random() * 20);
                this.vy = -12 - Math.random() * 12; // Mực từ từ nổi lên
                break;
        }
    }

    public void update(double dt, double mapWidth, double mapHeight) {
        animPhase += dt * 2.0;
        x += vx * dt;
        y += vy * dt;

        // Bập bềnh nhẹ
        if (type == Type.BOMB) {
            y += Math.sin(animPhase) * 10 * dt;
        } else if (type == Type.INK_BLOB) {
            // Mực đen bập bềnh uốn lượn nhẹ
            x += Math.sin(animPhase * 1.5) * 12 * dt;
        }

        // Đảo chiều khi chạm mép map
        if (x < 10) { x = 10; vx = Math.abs(vx); }
        if (x + width > mapWidth - 10) { x = mapWidth - 10 - width; vx = -Math.abs(vx); }
        if (y < 20) { y = 20; vy = Math.abs(vy); }
        if (y + height > mapHeight - 20) { y = mapHeight - 20 - height; vy = -Math.abs(vy); }
    }

    public Rectangle2D.Double getBounds() {
        return new Rectangle2D.Double(x, y, width, height);
    }

    public Type getType() { return type; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public double getCenterX() { return x + width / 2.0; }
    public double getCenterY() { return y + height / 2.0; }
    public double getVx() { return vx; }
    public double getVy() { return vy; }
    public boolean isExploded() { return exploded; }
    public void setExploded(boolean val) { this.exploded = val; }
}
