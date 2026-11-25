package huntersea.ui;

import huntersea.*;
import huntersea.model.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/**
 * Canvas đồ họa Java 2D vẽ thế giới đại dương 48x27 ô:
 * - Camera cuộn theo Thủy Thần
 * - Đồ họa mượt mà, hiệu ứng nước, bọt khí, hào quang Bá Khí
 * - HUD hiển thị kích thước, thanh Mana, Radar Mini-map, và nút kỹ năng
 */
public class GamePanel extends JPanel implements KeyListener, MouseListener, MouseMotionListener {
    private final GameEngine engine;
    private final JFrame parentFrame;
    private final AssetManager assets = AssetManager.getInstance();

    // Điều khiển bàn phím: P1
    private boolean p1Up, p1Down, p1Left, p1Right;
    // Điều khiển bàn phím: P2
    private boolean p2Up, p2Down, p2Left, p2Right;

    // Vị trí camera (-1 = chưa khởi tạo, sẽ snap ngay về player ở frame đầu)
    private double cameraX = -1;
    private double cameraY = -1;

    // Kích thước ô lưới
    private static final double TILE_SIZE = GameEngine.TILE_SIZE;

    public GamePanel(GameEngine engine, JFrame parentFrame) {
        this.engine = engine;
        this.parentFrame = parentFrame;
        setBackground(new Color(2, 16, 36));
        setFocusable(true);

        // 1. Chặn xung đột bộ gõ Tiếng Việt (IME / UniKey) trên màn hình game
        this.enableInputMethods(false);

        // 2. Lắng nghe sự kiện bàn phím thông thường
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);

        // 3. Đăng ký KeyEventDispatcher toàn cục: luôn nhận phím kể cả khi mất focus vào component khác
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (!isShowing() || engine.getState() != GameEngine.State.PLAYING) {
                    return false;
                }
                // Nếu focus đang nằm ở component nhập văn bản (JTextField/JTextArea), không chặn phím
                Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focusOwner instanceof javax.swing.text.JTextComponent) {
                    return false;
                }
                if (e.getID() == KeyEvent.KEY_PRESSED) {
                    return handleKeyPressed(e);
                } else if (e.getID() == KeyEvent.KEY_RELEASED) {
                    return handleKeyReleased(e);
                }
                return false;
            }
        });

        // 4. Tự động lấy lại Focus khi GamePanel hiển thị hoặc khi ComponentShown
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                SwingUtilities.invokeLater(() -> {
                    resetInputStates();
                    requestFocusInWindow();
                });
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    resetInputStates();
                    requestFocusInWindow();
                });
            }
        });

        // 5. Reset trạng thái phím khi cửa sổ mất focus (Alt+Tab, mở dialog, click ra ngoài)
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                resetInputStates();
            }
        });
    }

    /** Xóa sạch cờ phím và dừng chuyển động người chơi */
    public void resetInputStates() {
        p1Up = false;
        p1Down = false;
        p1Left = false;
        p1Right = false;
        p2Up = false;
        p2Down = false;
        p2Left = false;
        p2Right = false;
        if (engine.getPlayer1() != null) {
            engine.getPlayer1().setMovement(0, 0);
        }
        if (engine.getPlayer2() != null) {
            engine.getPlayer2().setMovement(0, 0);
        }
        engine.resetInputStates();
    }

    /** Gọi mỗi khi bắt đầu màn chơi mới để camera snap ngay đến player */
    public void resetCamera() {
        this.cameraX = -1;
        this.cameraY = -1;
        resetInputStates();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int viewW = getWidth();
        int viewH = getHeight();

        // 1. Cập nhật camera tracking
        updateCamera(viewW, viewH);

        // Áp dụng rung màn hình (Screen shake)
        double shakeX = 0, shakeY = 0;
        if (engine.getScreenShakeTimer() > 0) {
            double mag = engine.getScreenShakeMagnitude();
            shakeX = (Math.random() - 0.5) * mag * 2;
            shakeY = (Math.random() - 0.5) * mag * 2;
        }

        // Dịch chuyển hệ tọa độ thế giới theo Camera
        AffineTransform oldTrans = g2.getTransform();
        g2.translate(-cameraX + shakeX, -cameraY + shakeY);

        // --- BẮT ĐẦU VẼ THẾ GIỚI ĐẠI DƯƠNG ---
        renderBackground(g2);
        renderSpecialAuras(g2);
        renderHazards(g2);
        renderItems(g2);
        renderFishes(g2);
        renderPlayers(g2);
        renderParticles(g2);

        // Khôi phục hệ tọa độ màn hình để vẽ HUD
        g2.setTransform(oldTrans);

        // --- VẼ HIỆU ỨNG TỐI ĐEN MÀN HÌNH (BLIND EFFECT KHI DÍNH MỰC) ---
        if (engine.getBlindTimer() > 0) {
            renderBlindOverlay(g2, viewW, viewH);
        }

        // --- VẼ GIAO DIỆN HUD ---
        renderHUD(g2, viewW, viewH);
        // renderMiniMap đã được xóa theo yêu cầu người dùng
    }

    private void updateCamera(int viewW, int viewH) {
        Player p1 = engine.getPlayer1();
        if (p1 == null) return;

        double targetX = p1.getCenterX() - viewW / 2.0;
        double targetY = p1.getCenterY() - viewH / 2.0;

        // Nếu chế độ PvP, camera nằm giữa 2 người chơi
        Player p2 = engine.getPlayer2();
        if (p2 != null && !p2.isDead()) {
            targetX = (p1.getCenterX() + p2.getCenterX()) / 2.0 - viewW / 2.0;
            targetY = (p1.getCenterY() + p2.getCenterY()) / 2.0 - viewH / 2.0;
        }

        // Giới hạn target camera trong map trước khi lerp
        double clampedTargetX = Math.max(0, Math.min(GameEngine.MAP_WIDTH - viewW, targetX));
        double clampedTargetY = Math.max(0, Math.min(GameEngine.MAP_HEIGHT - viewH, targetY));

        // Lần đầu tiên (camera chưa khởi tạo): snap ngay đến vị trí player, không lerp
        if (cameraX < 0 && cameraY < 0) {
            cameraX = clampedTargetX;
            cameraY = clampedTargetY;
        } else {
            // Camera lướt mượt mà (Lerp)
            cameraX += (clampedTargetX - cameraX) * 0.1;
            cameraY += (clampedTargetY - cameraY) * 0.1;
        }

        // Giới hạn camera trong map
        cameraX = Math.max(0, Math.min(GameEngine.MAP_WIDTH - viewW, cameraX));
        cameraY = Math.max(0, Math.min(GameEngine.MAP_HEIGHT - viewH, cameraY));
    }

    private void renderBackground(Graphics2D g2) {
        BufferedImage mapImg = assets.getImage("map");
        if (mapImg == null) mapImg = assets.getImage("map_1");

        if (mapImg != null) {
            // Vẽ nền map bao phủ toàn bộ 1920x1080
            g2.drawImage(mapImg, 0, 0, (int) GameEngine.MAP_WIDTH, (int) GameEngine.MAP_HEIGHT, null);
        } else {
            // Nền đại dương dự phòng
            GradientPaint gp = new GradientPaint(0, 0, new Color(0, 105, 148), 
                                                0, (float) GameEngine.MAP_HEIGHT, new Color(1, 20, 48));
            g2.setPaint(gp);
            g2.fillRect(0, 0, (int) GameEngine.MAP_WIDTH, (int) GameEngine.MAP_HEIGHT);
        }

        // Lưới ô mờ tinh tế (Grid visualizer 48x27)
        g2.setColor(new Color(255, 255, 255, 10));
        for (int c = 0; c <= GameEngine.GRID_COLS; c++) {
            int x = (int) (c * TILE_SIZE);
            g2.drawLine(x, 0, x, (int) GameEngine.MAP_HEIGHT);
        }
        for (int r = 0; r <= GameEngine.GRID_ROWS; r++) {
            int y = (int) (r * TILE_SIZE);
            g2.drawLine(0, y, (int) GameEngine.MAP_WIDTH, y);
        }
    }

    private void renderSpecialAuras(Graphics2D g2) {
        // Vùng mực đen của Bạch Tuộc
        Player p1 = engine.getPlayer1();
        if (p1 != null && p1.isInkActive()) {
            double inkR = 5.0 * TILE_SIZE;
            RadialGradientPaint rgp = new RadialGradientPaint(
                (float) p1.getInkX(), (float) p1.getInkY(), (float) inkR,
                new float[]{0.0f, 0.7f, 1.0f},
                new Color[]{new Color(5, 5, 10, 220), new Color(15, 15, 25, 180), new Color(0, 0, 0, 0)}
            );
            g2.setPaint(rgp);
            g2.fill(new Ellipse2D.Double(p1.getInkX() - inkR, p1.getInkY() - inkR, inkR * 2, inkR * 2));
        }

        // Tương tự cho P2 nếu là Bạch Tuộc
        Player p2 = engine.getPlayer2();
        if (p2 != null && p2.isInkActive()) {
            double inkR = 5.0 * TILE_SIZE;
            RadialGradientPaint rgp = new RadialGradientPaint(
                (float) p2.getInkX(), (float) p2.getInkY(), (float) inkR,
                new float[]{0.0f, 0.7f, 1.0f},
                new Color[]{new Color(5, 5, 10, 220), new Color(15, 15, 25, 180), new Color(0, 0, 0, 0)}
            );
            g2.setPaint(rgp);
            g2.fill(new Ellipse2D.Double(p2.getInkX() - inkR, p2.getInkY() - inkR, inkR * 2, inkR * 2));
        }
    }

    private void renderFishes(Graphics2D g2) {
        Player p1 = engine.getPlayer1();
        Player p2 = engine.getPlayer2();

        for (FishEntity f : engine.getFishes()) {
            BufferedImage img = assets.getImageDirectional("fish_" + f.getLevel(), f.isFacingLeft());

            int baseX = (int) f.getX();
            int baseY = (int) f.getY();
            int baseW = (int) f.getWidth();
            int baseH = (int) f.getHeight();

            // Khi cá đang bị ký sinh: cộng dồn kích thước player vào cá chủ
            int drawX = baseX, drawY = baseY, drawW = baseW, drawH = baseH;
            boolean isParasitized = false;
            Player parasitePlayer = null;

            if (f.isPlayerControlled()) {
                // Tìm player đang ký sinh vào cá này
                if (p1 != null && p1.getAttachedFish() == f) parasitePlayer = p1;
                else if (p2 != null && p2.getAttachedFish() == f) parasitePlayer = p2;
                else if (engine.getAiBot() != null && engine.getAiBot().getPlayerEntity().getAttachedFish() == f)
                    parasitePlayer = engine.getAiBot().getPlayerEntity();

                if (parasitePlayer != null) {
                    isParasitized = true;
                    // Cộng kích thước player (dựa trên sizeLevel của player) vào cá chủ
                    int playerSize = parasitePlayer.getSizeLevel(); // 1-4
                    double bonusRatio = 1.0 + playerSize * 0.15; // Mỗi cấp player thêm 15% kích thước cá chủ
                    int newW = (int)(baseW * bonusRatio);
                    int newH = (int)(baseH * bonusRatio);
                    // Căn giữa theo center
                    drawX = baseX - (newW - baseW) / 2;
                    drawY = baseY - (newH - baseH) / 2;
                    drawW = newW;
                    drawH = newH;

                    // Vẽ hào quang ngoài: vòng sáng xanh lá nhấp nháy
                    long tick = System.currentTimeMillis() / 200;
                    int auraAlpha = (tick % 2 == 0) ? 130 : 60;
                    // Lớp nền phát sáng rộng
                    g2.setColor(new Color(0, 255, 120, auraAlpha));
                    g2.fillOval(drawX - 12, drawY - 12, drawW + 24, drawH + 24);
                    // Viền sáng
                    g2.setColor(new Color(50, 255, 80, 220));
                    g2.setStroke(new BasicStroke(3.5f));
                    g2.drawOval(drawX - 6, drawY - 6, drawW + 12, drawH + 12);
                }
            }

            if (img != null) {
                if (isParasitized) {
                    // Vẽ sprite cá với màu tint xanh-lá rõ ràng (ký sinh màu)
                    // Bước 1: vẽ sprite gốc
                    g2.drawImage(img, drawX, drawY, drawW, drawH, null);
                    // Bước 2: phủ lớp màu xanh-lá cyan bán trong suốt lên trên sprite
                    // để con cá này trông khác hẳn màu sắc bình thường
                    long tick2 = System.currentTimeMillis() / 300;
                    int tintAlpha = (tick2 % 2 == 0) ? 110 : 75;
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, tintAlpha / 255f));
                    g2.setColor(new Color(0, 230, 140)); // Màu xanh-lá tươi đặc trưng
                    g2.fillRoundRect(drawX, drawY, drawW, drawH, drawW / 4, drawH / 4);
                    g2.setComposite(AlphaComposite.SrcOver); // Khôi phục composite
                } else {
                    g2.drawImage(img, drawX, drawY, drawW, drawH, null);
                }
            } else {
                // Fallback: vẽ hình oval, màu khác biệt nếu bị ký sinh
                g2.setColor(isParasitized ? new Color(0, 230, 140) : new Color(255, 150, 50));
                g2.fillOval(drawX, drawY, drawW, drawH);
            }

            // Nhãn "ĐANG ĐIỀU KHIỂN" trên đầu cá vật chủ bị ký sinh
            if (isParasitized && parasitePlayer != null) {
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                String hostLabel = "🎮 [VẬT CHỦ - ĐANG ĐIỀU KHIỂN]";
                FontMetrics fmHost = g2.getFontMetrics();
                int lx = drawX + drawW / 2 - fmHost.stringWidth(hostLabel) / 2;
                int ly = drawY - 16;
                // Đổ bóng
                g2.setColor(Color.BLACK);
                g2.drawString(hostLabel, lx + 1, ly + 1);
                // Chữ màu xanh-lá sáng
                g2.setColor(new Color(0, 255, 120));
                g2.drawString(hostLabel, lx, ly);
            }

            // Hiệu ứng choáng (Stun stars)
            if (f.isStunned()) {
                g2.setColor(Color.YELLOW);
                g2.drawString("★ ★", drawX + drawW / 3, drawY - 5);
            }
        }
    }

    private void renderHazards(Graphics2D g2) {
        for (HazardEntity h : engine.getHazards()) {
            int x = (int) h.getX();
            int y = (int) h.getY();
            int w = (int) h.getWidth();
            int hH = (int) h.getHeight();

            switch (h.getType()) {
                case BOMB:
                    BufferedImage bombImg = assets.getImage("bomb");
                    if (bombImg != null) {
                        g2.drawImage(bombImg, x, y, w, hH, null);
                    } else {
                        g2.setColor(Color.RED);
                        g2.fillOval(x, y, w, hH);
                    }
                    // Viền đỏ cảnh báo
                    g2.setColor(new Color(255, 50, 50, 160));
                    g2.setStroke(new BasicStroke(2));
                    g2.drawOval(x - 3, y - 3, w + 6, hH + 6);
                    break;

                case POISON_FISH:
                    BufferedImage pImg = (h.getVx() < 0) 
                        ? assets.getFlippedImage("poison_fish") 
                        : assets.getImage("poison_fish");
                    if (pImg == null) pImg = assets.getImage("poison_fish");
                    if (pImg != null) g2.drawImage(pImg, x, y, w, hH, null);
                    else {
                        g2.setColor(new Color(180, 50, 220));
                        g2.fillOval(x, y, w, hH);
                    }
                    break;

                case INK_BLOB:
                    BufferedImage inkImg = (h.getVx() < 0) 
                        ? assets.getFlippedImage("ink_blob") 
                        : assets.getImage("ink_blob");
                    if (inkImg == null) inkImg = assets.getImage("ink_blob");
                    if (inkImg == null) inkImg = assets.getImage("mực đen");
                    if (inkImg != null) {
                        g2.drawImage(inkImg, x, y, w, hH, null);
                    } else {
                        g2.setColor(new Color(20, 20, 30, 200));
                        g2.fillOval(x, y, w, hH);
                    }
                    break;
            }
        }
    }

    private void renderItems(Graphics2D g2) {
        for (ItemEntity it : engine.getItems()) {
            int x = (int) it.getX();
            int y = (int) it.getY();
            int w = (int) it.getWidth();
            int h = (int) it.getHeight();

            if (it.getType() == ItemEntity.Type.BA_KHI) {
                BufferedImage bkImg = assets.getImage("ba_khi");
                // Hào quang vàng nhấp nháy
                g2.setColor(new Color(255, 215, 0, 150));
                g2.fillOval(x - 5, y - 5, w + 10, h + 10);
                if (bkImg != null) g2.drawImage(bkImg, x, y, w, h, null);
            } else if (it.getType() == ItemEntity.Type.SKILL_DROP) {
                // Bong bóng kỹ năng rơi PvP
                g2.setColor(new Color(0, 240, 255, 120));
                g2.fillOval(x - 4, y - 4, w + 8, h + 8);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2));
                g2.drawOval(x - 4, y - 4, w + 8, h + 8);

                // Vẽ icon kỹ năng bên trong
                if (it.getDroppedSkill() != null) {
                    BufferedImage sIcon = assets.getImage(it.getDroppedSkill().getIconKey());
                    if (sIcon != null) {
                        g2.drawImage(sIcon, x + 3, y + 3, w - 6, h - 6, null);
                    }
                }
            }
        }
    }

    private void renderPlayers(Graphics2D g2) {
        // 1. Vẽ Player 1
        if (engine.getPlayer1() != null && !engine.getPlayer1().isDead()) {
            renderSinglePlayer(g2, engine.getPlayer1(), new Color(0, 210, 255));
        }

        // 2. Vẽ Player 2 (PVP)
        if (engine.getPlayer2() != null && !engine.getPlayer2().isDead()) {
            renderSinglePlayer(g2, engine.getPlayer2(), new Color(255, 100, 100));
        }

        // 3. Vẽ AI Bot (PVE)
        if (engine.getAiBot() != null && !engine.getAiBot().getPlayerEntity().isDead()) {
            renderSinglePlayer(g2, engine.getAiBot().getPlayerEntity(), new Color(255, 70, 70));
        }
    }

    /**
     * Tính kích thước vẽ chuẩn hóa cho nhân vật người chơi.
     * Đảm bảo mọi nhân vật cùng cấp có diện tích hiển thị tương đương,
     * và phản ánh đúng tương quan kích thước với cá game (cùng level thì player to hơn cá ~15%).
     * Trả về {drawW, drawH} theo tỷ lệ 3:2 chuẩn.
     */
    private int[] getPlayerDrawSize(Player p) {
        int effSize = p.getEffectiveSizeLevel();
        // Kích thước cơ sở: mỗi cấp tăng đều 40px chiều rộng, duy trì tỷ lệ 3:2
        // Size 1: 96x64  (tương đương cá lv1: 64x52, player lớn hơn ~15%)
        // Size 2: 130x86  (tương đương cá lv2: 88x64)
        // Size 3: 168x112 (tương đương cá lv3: 112x80)
        // Size 4: 210x140 (tương đương cá lv4: 136x96)
        int baseW, baseH;
        switch (effSize) {
            case 1:  baseW = 96;  baseH = 64;  break;
            case 2:  baseW = 130; baseH = 86;  break;
            case 3:  baseW = 168; baseH = 112; break;
            case 4:
            default: baseW = 210; baseH = 140; break;
        }
        return new int[]{baseW, baseH};
    }

    private void renderSinglePlayer(Graphics2D g2, Player p, Color tagColor) {

        // ─── TRƯỜNG HỢP ĐẶC BIỆT: CÁ ÉP ĐANG KÝ SINH VÀO VẬT CHỦ ───────────
        // Khi đang ký sinh, cá ép "ẩn bên trong" vật chủ → không vẽ sprite của nó.
        // Toàn bộ thông tin đã được hiển thị trên con cá vật chủ (trong renderFishes).
        // Chỉ vẽ nhãn trạng thái nổi theo vị trí vật chủ.
        if (p.isRemoraAttached() && p.getAttachedFish() != null) {
            FishEntity host = p.getAttachedFish();
            int hx = (int) host.getCenterX();
            int hy = (int) (host.getY() - 30);

            // Nhãn đếm ngược thời gian ký sinh + điểm tích lũy
            double timeLeft = p.getRemoraAttachTimer();
            String tag = String.format("🪸 KÝ SINH %.1fs  +%dđ [%s]",
                timeLeft, p.getScoreEarnedDuringAttach(), p.getName());
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            FontMetrics fm2 = g2.getFontMetrics();
            int tx = hx - fm2.stringWidth(tag) / 2;
            // Đổ bóng đen
            g2.setColor(Color.BLACK);
            g2.drawString(tag, tx + 1, hy + 1);
            // Chữ xanh lá rực
            g2.setColor(new Color(0, 255, 120));
            g2.drawString(tag, tx, hy);
            return; // Không vẽ gì thêm cho cá ép khi đang ký sinh
        }
        // ─────────────────────────────────────────────────────────────────────

        int[] drawSize = getPlayerDrawSize(p);
        int drawW = drawSize[0];
        int drawH = drawSize[1];
        // Căn chỉnh vị trí vẽ theo center của logic bounding box
        int x = (int)(p.getCenterX() - drawW / 2.0);
        int y = (int)(p.getCenterY() - drawH / 2.0);
        int w = drawW;
        int h = drawH;

        // 1. Hào quang Bá Khí (Vàng rực rỡ)
        if (p.isBaKhiActive()) {
            g2.setColor(new Color(255, 215, 0, 140));
            g2.fillOval(x - 12, y - 12, w + 24, h + 24);
        }

        // 2. Khiên Mai Rùa Kim Cương
        if (p.isTurtleShieldActive()) {
            g2.setColor(new Color(0, 255, 180, 120));
            g2.setStroke(new BasicStroke(4));
            g2.drawOval(x - 10, y - 10, w + 20, h + 20);
        }

        // 3. Vòng tròn xung điện Cá Đuối Điện — đúng phạm vi thực tế (320px)
        if (p.isElectricShockActive()) {
            int cx = (int) p.getCenterX();
            int cy = (int) p.getCenterY();
            int r  = (int) GameEngine.ELECTRIC_RANGE; // 320px

            // Lớp nền: hào quang nhạt bên trong vòng
            long tickFill = System.currentTimeMillis() / 120;
            int fillAlpha = (tickFill % 2 == 0) ? 28 : 14;
            g2.setColor(new Color(0, 200, 255, fillAlpha));
            g2.fillOval(cx - r, cy - r, r * 2, r * 2);

            // Viền ngoài: vòng tròn điện nhấp nháy
            long tickBorder = System.currentTimeMillis() / 80;
            int borderAlpha = (tickBorder % 2 == 0) ? 230 : 140;
            g2.setColor(new Color(0, 220, 255, borderAlpha));
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);

            // Viền trong: ring nhỏ thứ hai tạo hiệu ứng lồng điện
            int r2 = r - 8;
            g2.setColor(new Color(100, 240, 255, 60));
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawOval(cx - r2, cy - r2, r2 * 2, r2 * 2);

            // Icon sét nhỏ trên viền
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(new Color(0, 240, 255, 200));
            g2.drawString("⚡", cx - r - 10, cy);
            g2.drawString("⚡", cx + r - 6, cy);
        }

        // 4b. Hiệu ứng Phase Lao Đến của Cá Ép (màu cam vệt tốc độ cao)
        if (p.isRemoraChasing()) {
            long tick = System.currentTimeMillis() / 80;
            int alpha = (tick % 2 == 0) ? 220 : 100;
            g2.setColor(new Color(255, 160, 0, alpha));
            g2.setStroke(new BasicStroke(4));
            g2.drawOval(x - 12, y - 12, w + 24, h + 24);
            g2.setColor(new Color(255, 160, 0, 50));
            g2.fillOval(x - 12, y - 12, w + 24, h + 24);
        }

        // 4c. Hiệu ứng Fallback Cá Ép (tăng tốc siêu mạnh)
        if (p.isRemoraFallbackActive()) {
            long tick = System.currentTimeMillis() / 100;
            int alpha = (tick % 2 == 0) ? 200 : 120;
            g2.setColor(new Color(0, 200, 255, alpha));
            g2.setStroke(new BasicStroke(4));
            g2.drawOval(x - 15, y - 15, w + 30, h + 30);
            g2.setColor(new Color(0, 200, 255, 40));
            g2.fillOval(x - 15, y - 15, w + 30, h + 30);
        }

        // 4d. Hiệu ứng Trúng Độc Cá Độc (quầng độc tố tím nhấp nháy, giảm 50% tốc độ di chuyển)
        if (p.isPoisoned()) {
            long tick = System.currentTimeMillis() / 120;
            int alpha = (tick % 2 == 0) ? 170 : 80;
            g2.setColor(new Color(160, 32, 240, alpha));
            g2.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawOval(x - 10, y - 10, w + 20, h + 20);
            g2.setColor(new Color(140, 0, 210, 45));
            g2.fillOval(x - 10, y - 10, w + 20, h + 20);
        }

        // 4e. Hiệu ứng Bị Choáng (Stun - Vòng tròn sao vàng bất động)
        if (p.isStunned()) {
            long tick = System.currentTimeMillis() / 100;
            int alpha = (tick % 2 == 0) ? 230 : 130;
            g2.setColor(new Color(255, 230, 0, alpha));
            g2.setStroke(new BasicStroke(3));
            g2.drawOval(x - 8, y - 8, w + 16, h + 16);
        }

        // 5. Vẽ Sprite nhân vật (Có đổi sprite cắn ăn)
        // Sprite gốc quay sang TRÁI → cần đảo !facingLeft để đầu hướng đúng chiều bơi
        CharacterType cType = p.getCharacterType();
        String spriteKey = p.isBiting() ? cType.getEatSpriteKey() : cType.getSpriteKey();
        BufferedImage pImg = assets.getImageDirectional(spriteKey, !p.isFacingLeft());

        if (pImg != null) {
            g2.drawImage(pImg, x, y, w, h, null);
        } else {
            g2.setColor(tagColor);
            g2.fillOval(x, y, w, h);
        }

        // Tên & Cấp Size / Trạng thái đặc biệt trên đầu
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        String tag;
        if (p.isStunned()) {
            g2.setColor(new Color(255, 230, 0));
            tag = "💫 BỊ CHOÁNG! [" + p.getName() + "]";
        } else if (p.isPoisoned()) {
            g2.setColor(new Color(210, 80, 255));
            tag = "☠️ TRÚNG ĐỘC (-50% TỐC) [" + p.getName() + "]";
        } else if (p.isRemoraChasing()) {
            g2.setColor(new Color(255, 160, 0));
            tag = "🏃 ĐANG LAO ĐẾN! [" + p.getName() + "]";
        } else if (p.isRemoraFallbackActive()) {
            g2.setColor(new Color(0, 200, 255));
            tag = "⚡ SIÊU TỐC! [" + p.getName() + "]";
        } else {
            g2.setColor(tagColor);
            tag = p.getName() + " [Size " + p.getSizeLevel() + "]";
        }
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(tag, (int) (p.getCenterX() - fm.stringWidth(tag) / 2.0), y - 10);
    }

    private void renderParticles(Graphics2D g2) {
        for (Particle p : engine.getParticles()) {
            float alpha = (float) p.getAlpha();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            int px = (int) p.getX();
            int py = (int) p.getY();
            int s = (int) p.getSize();

            if (p.getType() == Particle.ParticleType.FLOATING_TEXT) {
                g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                g2.setColor(Color.BLACK);
                g2.drawString(p.getText(), px + 1, py + 1); // Đổ bóng
                g2.setColor(p.getColor());
                g2.drawString(p.getText(), px, py);
            } else if (p.getType() == Particle.ParticleType.BUBBLE) {
                g2.setColor(p.getColor());
                g2.drawOval(px, py, s, s);
                g2.fillOval(px + 2, py + 2, s / 3, s / 3);
            } else if (p.getType() == Particle.ParticleType.EXPLOSION_SPARK) {
                g2.setColor(p.getColor());
                g2.fillOval(px - s / 2, py - s / 2, s, s);
            } else if (p.getType() == Particle.ParticleType.INK_SMOKE) {
                int a = Math.max(0, Math.min(255, (int)(220 * alpha)));
                g2.setColor(new Color(15, 15, 25, a));
                g2.fillOval(px - s / 2, py - s / 2, s, s);
            }
        }
        g2.setComposite(AlphaComposite.SrcOver);
    }

    private void renderBlindOverlay(Graphics2D g2, int viewW, int viewH) {
        // Tối đen màn hình khi chạm phải mực (không hiển thị chữ)
        g2.setColor(new Color(5, 5, 10, 248));
        g2.fillRect(0, 0, viewW, viewH);
    }

    // =========================================================================
    // VẼ GIAO DIỆN HUD
    // =========================================================================

    private void renderHUD(Graphics2D g2, int viewW, int viewH) {
        Player p1 = engine.getPlayer1();
        if (p1 == null) return;

        // --- TOP-LEFT: P1 PROFILE, SIZE, MANA ---
        renderPlayerHUDCard(g2, p1, 20, 20, true);

        // --- TOP-RIGHT: P2 PROFILE (NẾU PVP) HOẶC BOT (NẾU PVE) ---
        if (engine.getCurrentMode() == GameEngine.GameMode.PVP && engine.getPlayer2() != null) {
            renderPlayerHUDCard(g2, engine.getPlayer2(), viewW - 325, 20, false);
        } else if (engine.getCurrentMode() == GameEngine.GameMode.PVE && engine.getAiBot() != null) {
            renderPlayerHUDCard(g2, engine.getAiBot().getPlayerEntity(), viewW - 325, 20, false);
        }

        // --- TOP-CENTER: ĐIỂM SỐ & BỘ ĐẾM THỜI GIAN ---
        renderTopCenterScore(g2, viewW);

        // --- TOP-RIGHT: NÚT TẠM DỪNG (PAUSE BUTTON) XUYÊN SUỐT MỌI CHẾ ĐỘ KHI ĐANG CHƠI ---
        if (engine.getState() == GameEngine.State.PLAYING) {
            renderPauseButton(g2, viewW);
        }

        // --- BOTTOM-RIGHT: NÚT KỸ NĂNG & PHÍM TẮT ---
        renderSkillButtons(g2, viewW, viewH);
    }

    private void renderPlayerHUDCard(Graphics2D g2, Player p, int x, int y, boolean isP1) {
        // Khung kính mờ
        g2.setColor(new Color(5, 20, 45, 210));
        g2.fillRoundRect(x, y, 250, 75, 20, 20);
        g2.setColor(new Color(0, 210, 255, 150));
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(x, y, 250, 75, 20, 20);

        // Icon Avatar nhân vật
        BufferedImage avatar = assets.getImage(p.getCharacterType().getSpriteKey());
        if (avatar != null) {
            g2.drawImage(avatar, x + 10, y + 10, 55, 55, null);
        }

        // Tên & Kích thước
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2.setColor(Color.WHITE);
        g2.drawString(p.getName() + " - " + p.getCharacterType().getDisplayName(), x + 75, y + 25);

        // Cấp Size
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.setColor(new Color(255, 215, 0));
        g2.drawString("SIZE: " + p.getSizeLevel() + " (Ăn: " + p.getFishEatenCount() + " cá)", x + 75, y + 42);

        // Thanh Mana (0.0 -> 5.0)
        int manaBarW = 160;
        int manaBarH = 12;
        g2.setColor(new Color(20, 30, 50));
        g2.fillRoundRect(x + 75, y + 50, manaBarW, manaBarH, 6, 6);

        double manaRatio = Math.min(1.0, p.getMana() / 5.0);
        int fillW = (int) (manaBarW * manaRatio);
        Color manaCol = p.canUseSkill() ? new Color(0, 255, 150) : new Color(0, 180, 240);
        g2.setColor(manaCol);
        g2.fillRoundRect(x + 75, y + 50, fillW, manaBarH, 6, 6);

        g2.setFont(new Font("Segoe UI", Font.BOLD, 9));
        g2.setColor(Color.WHITE);
        String manaTxt = p.canUseSkill() ? "MANA 100% [SẴN SÀNG!]" : String.format("MANA %.1f / 5", p.getMana());
        g2.drawString(manaTxt, x + 85, y + 60);

        // Số mạng & Buff cân bằng động (PvE)
        if (engine.getCurrentMode() == GameEngine.GameMode.PVE) {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(Color.PINK);
            String lifeTxt = engine.isPvePhase2Active() ? "⚔️ 1 MẠNG" : "❤️ x" + p.getLives();
            g2.drawString(lifeTxt, x + 185, y + 42);

            if (p.getDynamicSpeedMultiplier() > 1.05) {
                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                g2.setColor(new Color(0, 255, 200));
                g2.drawString("⚡ BUFF TỐC", x + 175, y + 25);
            }
        }
    }

    private void renderTopCenterScore(Graphics2D g2, int viewW) {
        int w = (engine.getCurrentMode() == GameEngine.GameMode.PVE) ? 330 : 260;
        int h = 48;
        int x = (viewW - w) / 2;
        int y = 15;

        g2.setColor(new Color(5, 20, 45, 220));
        g2.fillRoundRect(x, y, w, h, 24, 24);
        g2.setColor(new Color(255, 215, 0, 180));
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(x, y, w, h, 24, 24);

        g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g2.setColor(Color.WHITE);
        int score = engine.getPlayer1() != null ? engine.getPlayer1().getScore() : 0;
        g2.drawString("ĐIỂM: " + score, x + 16, y + 30);

        // Hiển thị bộ đếm giờ theo chế độ
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2.setColor(new Color(0, 240, 255));
        if (engine.getCurrentMode() == GameEngine.GameMode.HIGH_SCORE_TIMED || engine.getCurrentMode() == GameEngine.GameMode.PVP) {
            int secLeft = (int) engine.getModeTimer();
            g2.drawString(String.format("⏱️ %02d:%02d", secLeft / 60, secLeft % 60), x + 155, y + 30);
        } else if (engine.getCurrentMode() == GameEngine.GameMode.PVE) {
            int p1Eaten = engine.getPlayer1() != null ? engine.getPlayer1().getFishEatenCount() : 0;
            int botEaten = engine.getAiBot() != null ? engine.getAiBot().getEatenCount() : 0;
            int diff = p1Eaten - botEaten;
            String diffStr = "Diff: " + (diff > 0 ? "+" + diff : "" + diff);

            if (!engine.isPvePhase2Active()) {
                int secLeft = (int) engine.getPvePhaseTimer();
                g2.drawString(String.format("GĐ1: %02ds | %s", secLeft, diffStr), x + 125, y + 30);
            } else {
                g2.setColor(new Color(255, 70, 70));
                g2.drawString(String.format("⚔️ TỬ CHIẾN | %s", diffStr), x + 125, y + 30);
            }
        }
    }

    private void renderSkillButtons(Graphics2D g2, int viewW, int viewH) {
        Player p1 = engine.getPlayer1();
        if (p1 == null) return;

        int btnSize = 72;
        int bx = viewW - btnSize - 25;
        int by = viewH - btnSize - 25;

        // Vòng tròn nút kỹ năng P1
        boolean ready = p1.canUseSkill();
        g2.setColor(ready ? new Color(0, 255, 150, 200) : new Color(30, 40, 60, 200));
        g2.fillOval(bx, by, btnSize, btnSize);
        g2.setColor(ready ? Color.YELLOW : Color.GRAY);
        g2.setStroke(new BasicStroke(3));
        g2.drawOval(bx, by, btnSize, btnSize);

        // Icon kỹ năng P1
        BufferedImage sIcon = assets.getImage(p1.getCharacterType().getSkillIconKey());
        if (sIcon != null) {
            g2.drawImage(sIcon, bx + 6, by + 6, btnSize - 12, btnSize - 12, null);
        }

        // Phím tắt gợi ý P1
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.setColor(Color.WHITE);
        String keyHint = engine.getCurrentMode() == GameEngine.GameMode.PVP ? "Q" : "SPACE";
        g2.drawString(keyHint, bx + btnSize / 2 - 8, by + btnSize + 16);

        // Nếu là PvP: hiển thị slot kỹ năng P1 (E, C) và HUD kĩ năng P2 (O, U, N)
        if (engine.getCurrentMode() == GameEngine.GameMode.PVP) {
            // P1 slot — bên trái nút chính (góc dưới-phải)
            renderPvPSkillSlot(g2, bx - 60, by + 10, "E", p1.getPvPSlot1());
            renderPvPSkillSlot(g2, bx - 120, by + 10, "C", p1.getPvPSlot2());

            // P2 HUD — góc dưới-trái (đối xứng với P1)
            Player p2 = engine.getPlayer2();
            if (p2 != null) {
                int p2Bx = 25;
                int p2By = viewH - btnSize - 25;

                // Nút kỹ năng chính P2 (O)
                boolean p2Ready = p2.canUseSkill();
                g2.setColor(p2Ready ? new Color(0, 255, 150, 200) : new Color(30, 40, 60, 200));
                g2.fillOval(p2Bx, p2By, btnSize, btnSize);
                g2.setColor(p2Ready ? Color.YELLOW : Color.GRAY);
                g2.setStroke(new BasicStroke(3));
                g2.drawOval(p2Bx, p2By, btnSize, btnSize);

                BufferedImage p2Icon = assets.getImage(p2.getCharacterType().getSkillIconKey());
                if (p2Icon != null) {
                    g2.drawImage(p2Icon, p2Bx + 6, p2By + 6, btnSize - 12, btnSize - 12, null);
                }

                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                g2.setColor(Color.WHITE);
                g2.drawString("O", p2Bx + btnSize / 2 - 5, p2By + btnSize + 16);

                // P2 slot kỹ năng nhặt (U, N) — bên phải nút chính P2
                renderPvPSkillSlot(g2, p2Bx + btnSize + 12, p2By + 10, "U", p2.getPvPSlot1());
                renderPvPSkillSlot(g2, p2Bx + btnSize + 72, p2By + 10, "N", p2.getPvPSlot2());
            }
        }
    }

    private void renderPvPSkillSlot(Graphics2D g2, int x, int y, String key, SkillType skill) {
        int sz = 48;
        g2.setColor(new Color(10, 30, 60, 200));
        g2.fillOval(x, y, sz, sz);
        g2.setColor(skill != null ? new Color(0, 240, 255) : Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(2));
        g2.drawOval(x, y, sz, sz);

        if (skill != null) {
            BufferedImage icon = assets.getImage(skill.getIconKey());
            if (icon != null) g2.drawImage(icon, x + 4, y + 4, sz - 8, sz - 8, null);
        }

        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.setColor(Color.WHITE);
        g2.drawString(key, x + sz / 2 - 4, y + sz + 14);
    }

    private void renderPauseButton(Graphics2D g2, int viewW) {
        int size = 44;
        int bx = viewW - size - 15;
        int by = 15;

        // Nền nút dạng kính mờ biển sâu
        g2.setColor(new Color(5, 20, 45, 220));
        g2.fillRoundRect(bx, by, size, size, 12, 12);
        g2.setColor(new Color(0, 210, 255, 180));
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(bx, by, size, size, 12, 12);

        // Biểu tượng || (Pause icon)
        g2.setColor(Color.WHITE);
        int barW = 5;
        int barH = 18;
        int barY = by + (size - barH) / 2;
        g2.fillRoundRect(bx + 13, barY, barW, barH, 2, 2);
        g2.fillRoundRect(bx + 26, barY, barW, barH, 2, 2);
    }

    private boolean isInsidePauseButton(int mx, int my) {
        int size = 44;
        int bx = getWidth() - size - 15;
        int by = 15;
        return mx >= bx && mx <= bx + size && my >= by && my <= by + size;
    }

    private void renderMiniMap(Graphics2D g2, int viewW, int viewH) {
        int mapSize = 130;
        int mx = 20;
        int my = viewH - mapSize - 20;

        // Nền radar tròn
        g2.setColor(new Color(5, 15, 30, 210));
        g2.fillOval(mx, my, mapSize, mapSize);
        g2.setColor(new Color(0, 210, 255, 180));
        g2.setStroke(new BasicStroke(2));
        g2.drawOval(mx, my, mapSize, mapSize);

        // Vòng tròn radar
        g2.drawOval(mx + mapSize / 4, my + mapSize / 4, mapSize / 2, mapSize / 2);

        // Tỷ lệ quy đổi vị trí world -> radar
        double scaleX = (double) mapSize / GameEngine.MAP_WIDTH;
        double scaleY = (double) mapSize / GameEngine.MAP_HEIGHT;

        // Vẽ cá thường (chấm xanh lá)
        g2.setColor(new Color(0, 255, 100, 160));
        for (FishEntity f : engine.getFishes()) {
            int fx = mx + (int) (f.getCenterX() * scaleX);
            int fy = my + (int) (f.getCenterY() * scaleY);
            g2.fillRect(fx - 1, fy - 1, 2, 2);
        }

        // Vẽ bom (chấm đỏ)
        g2.setColor(Color.RED);
        for (HazardEntity h : engine.getHazards()) {
            int hx = mx + (int) (h.getCenterX() * scaleX);
            int hy = my + (int) (h.getCenterY() * scaleY);
            g2.fillRect(hx - 2, hy - 2, 4, 4);
        }

        // Vẽ Player 1 (chấm xanh dương lớn)
        Player p1 = engine.getPlayer1();
        if (p1 != null && !p1.isDead()) {
            g2.setColor(new Color(0, 240, 255));
            int px = mx + (int) (p1.getCenterX() * scaleX);
            int py = my + (int) (p1.getCenterY() * scaleY);
            g2.fillOval(px - 3, py - 3, 7, 7);
        }

        // Vẽ Player 2 / Bot (chấm đỏ cam lớn)
        Player p2 = engine.getCurrentMode() == GameEngine.GameMode.PVE ? 
            (engine.getAiBot() != null ? engine.getAiBot().getPlayerEntity() : null) : engine.getPlayer2();
        if (p2 != null && !p2.isDead()) {
            g2.setColor(Color.ORANGE);
            int px = mx + (int) (p2.getCenterX() * scaleX);
            int py = my + (int) (p2.getCenterY() * scaleY);
            g2.fillOval(px - 3, py - 3, 7, 7);
        }

        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g2.setColor(new Color(0, 210, 255));
        g2.drawString("RADAR", mx + mapSize / 2 - 16, my + 14);
    }

    // =========================================================================
    // INPUT LISTENERS & ĐIỀU KHIỂN
    // =========================================================================

    public void processPlayerInput() {
        // Player 1: WASD
        double p1Vx = 0, p1Vy = 0;
        if (p1Up) p1Vy -= 1;
        if (p1Down) p1Vy += 1;
        if (p1Left) p1Vx -= 1;
        if (p1Right) p1Vx += 1;
        // Chuẩn hóa vector để di chố chéo không nhanh hơn di chố thẳng
        double p1Len = Math.hypot(p1Vx, p1Vy);
        if (p1Len > 0) { p1Vx /= p1Len; p1Vy /= p1Len; }
        if (engine.getPlayer1() != null) {
            engine.getPlayer1().setMovement(p1Vx, p1Vy);
        }

        // Player 2 (PVP): IJKL
        if (engine.getCurrentMode() == GameEngine.GameMode.PVP && engine.getPlayer2() != null) {
            double p2Vx = 0, p2Vy = 0;
            if (p2Up) p2Vy -= 1;
            if (p2Down) p2Vy += 1;
            if (p2Left) p2Vx -= 1;
            if (p2Right) p2Vx += 1;
            double p2Len = Math.hypot(p2Vx, p2Vy);
            if (p2Len > 0) { p2Vx /= p2Len; p2Vy /= p2Len; }
            engine.getPlayer2().setMovement(p2Vx, p2Vy);
        }
    }

    public boolean handleKeyPressed(KeyEvent e) {
        int code = e.getKeyCode();

        // Tạm dừng: ESC hoặc P
        if (code == KeyEvent.VK_ESCAPE || code == KeyEvent.VK_P) {
            if (engine.getState() == GameEngine.State.PLAYING) {
                openPauseDialog();
            }
            return true;
        }

        boolean handled = false;

        // P1 điều khiển
        if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) { p1Up = true; handled = true; }
        if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) { p1Down = true; handled = true; }
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) { p1Left = true; handled = true; }
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) { p1Right = true; handled = true; }

        // P1 Kỹ năng: SPACE hoặc Q
        if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_Q) {
            if (engine.getPlayer1() != null) {
                engine.getPlayer1().triggerCharacterSkill(engine.getFishes(), GameEngine.MAP_WIDTH, GameEngine.MAP_HEIGHT);
            }
            handled = true;
        }
        // P1 Kỹ năng rơi PvP: E, C
        if (code == KeyEvent.VK_E && engine.getPlayer1() != null) {
            engine.getPlayer1().usePvPSlot1(engine.getFishes(), GameEngine.MAP_WIDTH, GameEngine.MAP_HEIGHT);
            handled = true;
        }
        if (code == KeyEvent.VK_C && engine.getPlayer1() != null) {
            engine.getPlayer1().usePvPSlot2(engine.getFishes(), GameEngine.MAP_WIDTH, GameEngine.MAP_HEIGHT);
            handled = true;
        }

        // P2 điều khiển (I, J, K, L)
        if (code == KeyEvent.VK_I) { p2Up = true; handled = true; }
        if (code == KeyEvent.VK_K) { p2Down = true; handled = true; }
        if (code == KeyEvent.VK_J) { p2Left = true; handled = true; }
        if (code == KeyEvent.VK_L) { p2Right = true; handled = true; }

        // P2 Kỹ năng: O
        if (code == KeyEvent.VK_O && engine.getPlayer2() != null) {
            engine.getPlayer2().triggerCharacterSkill(engine.getFishes(), GameEngine.MAP_WIDTH, GameEngine.MAP_HEIGHT);
            handled = true;
        }
        // P2 Kỹ năng rơi PvP: U, N
        if (code == KeyEvent.VK_U && engine.getPlayer2() != null) {
            engine.getPlayer2().usePvPSlot1(engine.getFishes(), GameEngine.MAP_WIDTH, GameEngine.MAP_HEIGHT);
            handled = true;
        }
        if (code == KeyEvent.VK_N && engine.getPlayer2() != null) {
            engine.getPlayer2().usePvPSlot2(engine.getFishes(), GameEngine.MAP_WIDTH, GameEngine.MAP_HEIGHT);
            handled = true;
        }

        if (handled) {
            processPlayerInput();
        }
        return handled;
    }

    public boolean handleKeyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        boolean handled = false;

        if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) { p1Up = false; handled = true; }
        if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) { p1Down = false; handled = true; }
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) { p1Left = false; handled = true; }
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) { p1Right = false; handled = true; }

        if (code == KeyEvent.VK_I) { p2Up = false; handled = true; }
        if (code == KeyEvent.VK_K) { p2Down = false; handled = true; }
        if (code == KeyEvent.VK_J) { p2Left = false; handled = true; }
        if (code == KeyEvent.VK_L) { p2Right = false; handled = true; }

        if (handled) {
            processPlayerInput();
        }
        return handled;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        handleKeyPressed(e);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        handleKeyReleased(e);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        requestFocusInWindow();
        if (isInsidePauseButton(e.getX(), e.getY())) {
            return;
        }

        // Click chuột để kích hoạt kỹ năng Player 1
        if (engine.getPlayer1() != null) {
            engine.getPlayer1().triggerCharacterSkill(engine.getFishes(), GameEngine.MAP_WIDTH, GameEngine.MAP_HEIGHT);
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (engine.getState() == GameEngine.State.PLAYING && isInsidePauseButton(e.getX(), e.getY())) {
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            return;
        } else {
            setCursor(Cursor.getDefaultCursor());
        }

        // Hỗ trợ bơi theo hướng chuột nếu không dùng phím
        if (!p1Up && !p1Down && !p1Left && !p1Right && engine.getPlayer1() != null) {
            double worldMouseX = e.getX() + cameraX;
            double worldMouseY = e.getY() + cameraY;
            double dx = worldMouseX - engine.getPlayer1().getCenterX();
            double dy = worldMouseY - engine.getPlayer1().getCenterY();
            double dist = Math.hypot(dx, dy);
            if (dist > 30) {
                engine.getPlayer1().setMovement(dx / dist, dy / dist);
            } else {
                engine.getPlayer1().setMovement(0, 0);
            }
        }
    }

    private void openPauseDialog() {
        resetInputStates();
        engine.pauseGame();
        PauseDialog dlg = new PauseDialog(parentFrame, new PauseDialog.PauseListener() {
            @Override
            public void onResume() {
                engine.resumeGame();
                resetInputStates();
                SwingUtilities.invokeLater(() -> requestFocusInWindow());
            }

            @Override
            public void onQuitToLobby() {
                engine.stopMatch();
                resetInputStates();
                if (parentFrame instanceof Main) {
                    ((Main) parentFrame).showLobby();
                }
            }
        });
        dlg.setVisible(true);
        if (engine.getState() == GameEngine.State.PLAYING) {
            resetInputStates();
            SwingUtilities.invokeLater(() -> requestFocusInWindow());
        }
    }

    @Override public void keyTyped(KeyEvent e) {}
    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        if (engine.getState() == GameEngine.State.PLAYING && isInsidePauseButton(e.getX(), e.getY())) {
            openPauseDialog();
        }
    }
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void mouseDragged(MouseEvent e) { mouseMoved(e); }
}
