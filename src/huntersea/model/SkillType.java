package huntersea.model;

/**
 * Các loại kỹ năng trong trò chơi (kỹ năng gốc và kỹ năng nhặt trong PvP)
 */
public enum SkillType {
    SHRIMP_BLINK("Búng Dịch Chuyển", "skill_shrimp", 220.0, false),
    SWORD_DASH("Lướt Thần Tốc", "skill_swordfish", 320.0, true),
    CRAB_PINCER("Kẹp Sắt Đoạt Mạng", "skill_crab", 260.0, true),
    REMORA_ATTACH("Ký Sinh Bám Dính", "skill_remora", 280.0, true),
    OCTOPUS_INK("Vùng Mực Hắc Ám", "skill_octopus", 240.0, true),
    RAY_SHOCK("Xung Điện Định Kỳ", "skill_ray", 480.0, true),
    TURTLE_SHELL("Mai Rùa Bất Hoại", "skill_turtle", 220.0, true),
    VORTEX_PULL("Hố Đen Xoáy Nước", "skill_octopus", 360.0, true),
    POISON_STING("Gai Độc Tê Liệt", "skill_ray", 300.0, true),
    ROAR_FEAR("Tiếng Gầm Thủy Quái", "skill_turtle", 340.0, true);

    private final String name;
    private final String iconKey;
    private final double range;
    private final boolean offensive;

    SkillType(String name, String iconKey) {
        this(name, iconKey, 250.0, true);
    }

    SkillType(String name, String iconKey, double range, boolean offensive) {
        this.name = name;
        this.iconKey = iconKey;
        this.range = range;
        this.offensive = offensive;
    }

    public String getName() { return name; }
    public String getIconKey() { return iconKey; }
    public double getRange() { return range; }
    public boolean isOffensive() { return offensive; }

    public static SkillType fromCharacter(CharacterType character) {
        switch (character) {
            case SHRIMP: return SHRIMP_BLINK;
            case SWORDFISH: return SWORD_DASH;
            case CRAB: return CRAB_PINCER;
            case REMORA: return REMORA_ATTACH;
            case OCTOPUS: return OCTOPUS_INK;
            case ELECTRIC_RAY: return RAY_SHOCK;
            case TURTLE: return TURTLE_SHELL;
            default: return SWORD_DASH;
        }
    }
}
