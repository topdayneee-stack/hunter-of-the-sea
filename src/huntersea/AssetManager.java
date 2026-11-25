package huntersea;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Quản lý nạp và xử lý tài nguyên hình ảnh game Hunter of the Sea.
 * Tự động lọc nền trắng thành alpha trong suốt (Chroma Alpha),
 * tạo sprite lật trái/phải, và dự phòng đồ họa nếu thiếu ảnh.
 */
public class AssetManager {
    private static AssetManager instance;

    private final Map<String, BufferedImage> images = new HashMap<>();
    private final Map<String, BufferedImage> flippedImages = new HashMap<>();

    private AssetManager() {
        loadAllAssets();
    }

    public static synchronized AssetManager getInstance() {
        if (instance == null) {
            instance = new AssetManager();
        }
        return instance;
    }

    private void loadAllAssets() {
        File dir = new File(".");
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (!name.endsWith(".jpg") && !name.endsWith(".png")) continue;

            try {
                BufferedImage raw = ImageIO.read(f);
                if (raw == null) continue;

                // Xác định key dựa trên tên file
                String key = identifyKey(f.getName());
                if (key != null) {
                    boolean isBg = key.equals("lobby_bg") || key.equals("map") || key.equals("map_1");
                    boolean isSkillIcon = key.startsWith("skill_");

                    BufferedImage processed;
                    if (isBg) {
                        // Ảnh nền giữ nguyên
                        processed = raw;
                    } else if (isSkillIcon) {
                        // Icon kỹ năng: bo tròn đẹp mắt
                        processed = makeCircularIcon(raw);
                    } else {
                        // Sprite sinh vật, bom, bá khí: lọc nền trắng thành trong suốt
                        processed = removeWhiteBackground(raw);
                    }

                    images.put(key, processed);
                    images.put(f.getName(), processed);

                    // Lưu các alias cho Mực đen
                    if (key.equals("ink_blob")) {
                        images.put("muc_den", processed);
                        images.put("mực đen", processed);
                        images.put("mực đen ", processed);
                        images.put("squid", processed);
                    }

                    // Tạo bản lật ngang cho sprite
                    if (!isBg && !isSkillIcon) {
                        flippedImages.put(key, flipHorizontal(processed));
                        if (key.equals("ink_blob")) {
                            BufferedImage flipped = flipHorizontal(processed);
                            flippedImages.put("muc_den", flipped);
                            flippedImages.put("mực đen", flipped);
                            flippedImages.put("squid", flipped);
                        }
                    }

                    System.out.println("  [OK] " + f.getName() + " -> key: " + key);
                } else {
                    System.out.println("  [SKIP] " + f.getName() + " (không nhận diện được)");
                }
            } catch (Exception e) {
                System.err.println("Lỗi nạp ảnh: " + f.getName() + " - " + e.getMessage());
            }
        }

        System.out.println("Nạp thành công " + images.size() + " tài nguyên hình ảnh!");
    }

    /**
     * Nhận diện key chuẩn hóa cho từng file tài nguyên.
     * Dùng startsWith để tránh nhầm lẫn do các điều kiện contains quá rộng.
     */
    private String identifyKey(String rawName) {
        // Chuẩn hóa: chuyển về chữ thường, bỏ phần mở rộng
        String lower = rawName.toLowerCase();
        String noExt = lower.contains(".") ? lower.substring(0, lower.lastIndexOf('.')) : lower;

        // --- Backgrounds ---
        if (noExt.equals("lobby_bg"))        return "lobby_bg";
        if (noExt.equals("map 1") || noExt.equals("map_1")) return "map_1";
        if (noExt.equals("map"))             return "map";

        // --- Bom & Bá khí ---
        // "bom nổ" / "bom n?" — phải check trước "bom" đơn
        if (noExt.startsWith("bom") && noExt.length() > 3) return "bomb_explosion";
        if (noExt.equals("bom"))             return "bomb";
        // "bá khí" — bắt đầu bằng "b" và có "kh"
        if (noExt.startsWith("b") && noExt.contains("kh")) return "ba_khi";

        // --- Kỹ năng icons (tên bắt đầu bằng "kĩ năng" / "ki nang") ---
        // Phải kiểm tra TRƯỚC sprite nhân vật để không nhầm
        if (noExt.startsWith("k")) {
            // "kĩ năng bạch tuộc"
            if (noExt.contains("bạch") || noExt.contains("bach") || noExt.contains("tu"))
                return "skill_octopus";
            // "kĩ năng cua"
            if (noExt.contains("cua"))
                return "skill_crab";
            // "kĩ năng cá kiếm" / "ki nang ca kiem"
            if (noExt.contains("ki") && noExt.contains("m"))
                return "skill_swordfish";
            // "kĩ năng cá đuối điện"
            if (noExt.contains("du") || noExt.contains("đuối"))
                return "skill_ray";
            // "kĩ năng rùa"
            if (noExt.contains("rùa") || noExt.contains("rua"))
                return "skill_turtle";
            // "kĩ năng tôm"
            if (noExt.contains("tôm") || noExt.contains("tom"))
                return "skill_shrimp";
            // "kĩ năng cá ép"
            if (noExt.contains("ép") || noExt.contains("ep"))
                return "skill_remora";
        }

        // --- 7 loại cá thường (tên file chính xác: "cá 1", "cá 2", ...) ---
        for (int i = 1; i <= 7; i++) {
            if (noExt.equals("cá " + i) || noExt.equals("ca " + i)
                    || noExt.equals("c? " + i) || noExt.equals("c " + i)) {
                return "fish_" + i;
            }
        }

        // --- Cá độc (tên bắt đầu bằng "cá độc") ---
        if (noExt.startsWith("cá độc") || noExt.startsWith("ca doc")
                || noExt.startsWith("c? d?c")) return "poison_fish";

        // --- Mực đen (Chướng ngại vật phun mực mù màn hình / squid ink hazard) ---
        if (noExt.startsWith("mực đen") || noExt.startsWith("muc den")
                || noExt.startsWith("m?c den") || noExt.startsWith("m?c ?en")
                || noExt.startsWith("mực") || noExt.startsWith("muc")
                || noExt.startsWith("m?c")
                || (noExt.contains("mực") && noExt.contains("đen"))
                || (noExt.contains("muc") && noExt.contains("den"))) {
            return "ink_blob";
        }

        // --- Sprite nhân vật chính & trạng thái ăn ---
        // Phát hiện " ăn" (eat animation) — có DẤU CÁCH trước "ăn" để tránh nhầm
        boolean isEat = noExt.endsWith(" ăn") || noExt.endsWith(" an")
                || noExt.endsWith("_eat") || noExt.contains(" ăn ")
                || noExt.contains(" an ");

        // Tôm — bắt đầu bằng "tôm" hoặc "tom" hoặc "t?m"
        if (noExt.startsWith("tôm") || noExt.startsWith("tom") || noExt.startsWith("t?m")) {
            return isEat ? "shrimp_eat" : "shrimp";
        }
        // Cá Kiếm — phải check trước "cá ép" vì cùng bắt đầu bằng "cá"
        if (noExt.startsWith("cá kiếm") || noExt.startsWith("ca kiem")
                || noExt.startsWith("c? ki?m")) {
            return isEat ? "swordfish_eat" : "swordfish";
        }
        // Cá Ép
        if (noExt.startsWith("cá ép") || noExt.startsWith("ca ep")
                || noExt.startsWith("c? ?p") || noExt.startsWith("cá ep")) {
            return isEat ? "remora_eat" : "remora";
        }
        // Cá Đuối Điện
        if (noExt.startsWith("cá đuối") || noExt.startsWith("ca duoi")
                || noExt.startsWith("c? du?i")) {
            return isEat ? "ray_eat" : "ray";
        }
        // Bạch Tuộc
        if (noExt.startsWith("bạch") || noExt.startsWith("bach") || noExt.startsWith("b?ch")) {
            return isEat ? "octopus_eat" : "octopus";
        }
        // Cua
        if (noExt.startsWith("cua")) {
            return isEat ? "crab_eat" : "crab";
        }
        // Rùa Biển
        if (noExt.startsWith("rùa") || noExt.startsWith("rua") || noExt.startsWith("r?a")) {
            return isEat ? "turtle_eat" : "turtle";
        }

        return null;
    }

    /**
     * Tự động xóa nền trắng, biến các pixel màu trắng/gần trắng thành trong suốt
     */
    public BufferedImage removeWhiteBackground(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        // Quét ma trận pixel
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int a = (rgb >> 24) & 0xFF;

                // Nếu ảnh đã có alpha và trong suốt sẵn
                if (a < 50) {
                    dest.setRGB(x, y, 0);
                    continue;
                }

                // Phát hiện pixel nền trắng/xám sáng
                if (r >= 232 && g >= 232 && b >= 232) {
                    dest.setRGB(x, y, 0); // Trong suốt hoàn toàn
                } else if (r >= 210 && g >= 210 && b >= 210) {
                    // Viền mờ chống răng cưa (Anti-aliasing feathering)
                    int alpha = (int) (255 * (1.0 - ((r + g + b) / 3.0 - 210) / 22.0));
                    alpha = Math.max(0, Math.min(255, alpha));
                    dest.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
                } else {
                    dest.setRGB(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
                }
            }
        }
        return dest;
    }

    /**
     * Bo tròn icon kỹ năng thành hình tròn nghệ thuật
     */
    private BufferedImage makeCircularIcon(BufferedImage src) {
        int d = Math.min(src.getWidth(), src.getHeight());
        BufferedImage dest = new BufferedImage(d, d, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dest.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setClip(new java.awt.geom.Ellipse2D.Float(0, 0, d, d));
        g2.drawImage(src, 0, 0, d, d, null);
        g2.dispose();
        return dest;
    }

    /**
     * Lật ảnh theo trục ngang (sang trái/phải)
     */
    public BufferedImage flipHorizontal(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dest.createGraphics();
        AffineTransform at = AffineTransform.getScaleInstance(-1, 1);
        at.translate(-w, 0);
        g2.drawImage(src, at, null);
        g2.dispose();
        return dest;
    }

    /**
     * Lấy ảnh theo key
     */
    public BufferedImage getImage(String key) {
        return images.get(key);
    }

    /**
     * Lấy ảnh lật ngược theo key
     */
    public BufferedImage getFlippedImage(String key) {
        BufferedImage flipped = flippedImages.get(key);
        return (flipped != null) ? flipped : getImage(key);
    }

    /**
     * Lấy ảnh tùy theo hướng di chuyển (facingLeft = true thì lấy ảnh lật)
     */
    public BufferedImage getImageDirectional(String key, boolean facingLeft) {
        if (facingLeft) {
            return getFlippedImage(key);
        }
        return getImage(key);
    }

    public static void main(String[] args) {
        AssetManager am = AssetManager.getInstance();
        System.out.println("ink_blob loaded: " + (am.getImage("ink_blob") != null));
    }
}
