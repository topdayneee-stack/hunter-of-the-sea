package huntersea.model;

/**
 * Định nghĩa 7 nhân vật Thủy Thần và thuộc tính kĩ năng tự nhiên
 */
public enum CharacterType {
    SHRIMP(
        "Tôm", 
        "shrimp", 
        "shrimp_eat", 
        "skill_shrimp", 
        "Búng Lùi Dịch Chuyển", 
        "Dịch chuyển tức thời tới vùng an toàn: xa cá lớn và gần bầy cá nhỏ."
    ),
    SWORDFISH(
        "Cá Kiếm", 
        "swordfish", 
        "swordfish_eat", 
        "skill_swordfish", 
        "Lướt Thần Tốc", 
        "Tăng mạnh tốc độ bơi gấp 2.2 lần trong 3.5 giây để săn mồi hoặc tẩu thoát."
    ),
    CRAB(
        "Cua", 
        "crab", 
        "crab_eat", 
        "skill_crab", 
        "Kẹp Sắt Đoạt Mạng", 
        "Tung đôi càng kẹp bắt và nuốt trọn 2 con cá nhỏ gần nhất trong phạm vi."
    ),
    REMORA(
        "Cá Ép", 
        "remora", 
        "remora_eat", 
        "skill_remora", 
        "Ký Sinh Bám Dính", 
        "Tạm bám vào cá lớn, điều khiển cá chủ và cộng dồn kích thước trong 5s."
    ),
    OCTOPUS(
        "Bạch Tuộc", 
        "octopus", 
        "octopus_eat", 
        "skill_octopus", 
        "Vùng Mực Hắc Ám", 
        "Phun vùng mây mực: Bản thân bất tử và tăng tốc, cá khác bị làm chậm."
    ),
    ELECTRIC_RAY(
        "Cá Đuối Điện", 
        "ray", 
        "ray_eat", 
        "skill_ray", 
        "Xung Điện Định Kỳ", 
        "Phóng xung điện trong 3s, làm choáng 0.5s toàn bộ cá ở gần sau mỗi 1s."
    ),
    TURTLE(
        "Rùa Biển", 
        "turtle", 
        "turtle_eat", 
        "skill_turtle", 
        "Mai Rùa Bất Hoại", 
        "Bất tử trong 4s; cá lớn tấn công sẽ bị đẩy lùi mạnh và làm choáng 1s."
    );

    private final String displayName;
    private final String spriteKey;
    private final String eatSpriteKey;
    private final String skillIconKey;
    private final String skillName;
    private final String skillDesc;

    CharacterType(String displayName, String spriteKey, String eatSpriteKey, 
                  String skillIconKey, String skillName, String skillDesc) {
        this.displayName = displayName;
        this.spriteKey = spriteKey;
        this.eatSpriteKey = eatSpriteKey;
        this.skillIconKey = skillIconKey;
        this.skillName = skillName;
        this.skillDesc = skillDesc;
    }

    public String getDisplayName() { return displayName; }
    public String getSpriteKey() { return spriteKey; }
    public String getEatSpriteKey() { return eatSpriteKey; }
    public String getSkillIconKey() { return skillIconKey; }
    public String getSkillName() { return skillName; }
    public String getSkillDesc() { return skillDesc; }

    public int getDbId() {
        return ordinal() + 1;
    }

    public static CharacterType fromDbId(int id) {
        for (CharacterType ct : values()) {
            if (ct.getDbId() == id) return ct;
        }
        return SWORDFISH;
    }
}
