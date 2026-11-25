package huntersea.model;

import java.awt.Color;

/**
 * Hiệu ứng hạt trong game: Bọt nước, nổ bom, tia sét, mây mực, chữ bay
 */
public class Particle {
    public enum ParticleType {
        BUBBLE,
        EXPLOSION_SPARK,
        ELECTRIC_ZAP,
        INK_SMOKE,
        FLOATING_TEXT
    }

    private ParticleType type;
    private double x, y;
    private double vx, vy;
    private double size;
    private double life;
    private double maxLife;
    private Color color;
    private String text;

    public Particle(ParticleType type, double x, double y, double vx, double vy, 
                    double size, double maxLife, Color color, String text) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.size = size;
        this.maxLife = maxLife;
        this.life = maxLife;
        this.color = color;
        this.text = text;
    }

    public static Particle createBubble(double x, double y) {
        double r = 4 + Math.random() * 10;
        return new Particle(ParticleType.BUBBLE, x, y, 
            (Math.random() - 0.5) * 20, -30 - Math.random() * 40, 
            r, 2.5 + Math.random() * 2.0, new Color(255, 255, 255, 120), null);
    }

    public static Particle createFloatingText(double x, double y, String txt, Color col) {
        return new Particle(ParticleType.FLOATING_TEXT, x, y, 
            0, -35, 16, 1.2, col, txt);
    }

    public void update(double dt) {
        life -= dt;
        x += vx * dt;
        y += vy * dt;
        if (type == ParticleType.BUBBLE) {
            vx += (Math.random() - 0.5) * 5;
        }
    }

    public boolean isDead() { return life <= 0; }
    public double getAlpha() { return Math.max(0.0, Math.min(1.0, life / maxLife)); }

    public ParticleType getType() { return type; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getSize() { return size; }
    public Color getColor() { return color; }
    public String getText() { return text; }
}
