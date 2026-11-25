package com.game.hunter.config;

import com.game.hunter.entity.GameCharacter;
import com.game.hunter.entity.GameConfig;
import com.game.hunter.entity.User;
import com.game.hunter.entity.UserCharacter;
import com.game.hunter.repository.GameCharacterRepository;
import com.game.hunter.repository.GameConfigRepository;
import com.game.hunter.repository.UserCharacterRepository;
import com.game.hunter.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private GameCharacterRepository characterRepo;

    @Autowired
    private GameConfigRepository configRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private UserCharacterRepository userCharRepo;

    @Override
    public void run(String... args) {
        seedCharacters();
        seedConfigs();
        seedDefaultUsers();
    }

    private void seedCharacters() {
        if (characterRepo.count() > 0) return;

        List<GameCharacter> defaultChars = List.of(
                new GameCharacter("Tôm", 2, 2, new BigDecimal("4.50"), 
                        "Búng Lùi Dịch Chuyển", 
                        "Dịch chuyển tức thời tới vùng an toàn: xa cá lớn và gần bầy cá nhỏ.", 5),
                new GameCharacter("Cá Kiếm", 3, 2, new BigDecimal("4.80"), 
                        "Lướt Thần Tốc", 
                        "Tăng mạnh tốc độ bơi gấp 2.2 lần trong 3.5 giây để săn mồi hoặc tẩu thoát.", 5),
                new GameCharacter("Cua", 3, 3, new BigDecimal("3.80"), 
                        "Kẹp Sắt Đoạt Mạng", 
                        "Tung đôi càng kẹp bắt và nuốt trọn 2 con cá nhỏ gần nhất trong phạm vi.", 5),
                new GameCharacter("Cá Ép", 2, 2, new BigDecimal("4.20"), 
                        "Ký Sinh Bám Dính", 
                        "Tạm bám vào cá lớn, điều khiển cá chủ và cộng dồn kích thước trong 5s.", 5),
                new GameCharacter("Bạch Tuộc", 3, 3, new BigDecimal("3.90"), 
                        "Vùng Mực Hắc Ám", 
                        "Phun vùng mây mực: Bản thân bất tử và tăng tốc, cá khác bị làm chậm.", 5),
                new GameCharacter("Cá Đuối Điện", 3, 3, new BigDecimal("4.00"), 
                        "Xung Điện Định Kỳ", 
                        "Phóng xung điện trong 3s, làm choáng 0.5s toàn bộ cá ở gần sau mỗi 1s.", 5),
                new GameCharacter("Rùa Biển", 3, 3, new BigDecimal("3.50"), 
                        "Mai Rùa Bất Hoại", 
                        "Bất tử trong 4s; cá lớn tấn công sẽ bị đẩy lùi mạnh và làm choáng 1s.", 5)
        );

        characterRepo.saveAll(defaultChars);
        System.out.println(">>> [DataSeeder] Đã khởi tạo thành công 7 Thủy Thần mặc định!");
    }

    private void seedConfigs() {
        if (configRepo.count() > 0) return;

        List<GameConfig> defaultConfigs = List.of(
                new GameConfig("haki_buff_duration_sec", "5", "Thời gian hiệu lực tăng 1 cấp của viên bá khí (5 giây)"),
                new GameConfig("squid_ink_blind_sec", "2", "Thời gian đen màn hình khi ăn phải mực (2 giây)"),
                new GameConfig("eat_count_to_level_up", "10", "Số lượng cá nhỏ cần ăn để tăng 1 cấp kích thước"),
                new GameConfig("game_duration_timed_sec", "180", "Thời gian thi đấu chế độ 3 phút tính bằng giây"),
                new GameConfig("poison_fish_stun_sec", "2", "Thời gian làm chậm tốc độ di chuyển khi ăn phải cá độc (giây)"),
                new GameConfig("pve_phase1_duration_sec", "90", "Thời gian giai đoạn 1 của chế độ chơi với máy PvE")
        );

        configRepo.saveAll(defaultConfigs);
        System.out.println(">>> [DataSeeder] Đã khởi tạo thành công cấu hình game mặc định!");
    }

    private void seedDefaultUsers() {
        if (userRepo.count() > 0) return;

        User sampleUser = userRepo.save(new User("ThuyThuSanMoi"));
        List<GameCharacter> chars = characterRepo.findAll();
        for (GameCharacter c : chars) {
            if (c.getName().equals("Tôm") || c.getName().equals("Cá Kiếm")) {
                userCharRepo.save(new UserCharacter(sampleUser, c));
            }
        }
        System.out.println(">>> [DataSeeder] Đã tạo người chơi mẫu ThuyThuSanMoi với 2 nhân vật ban đầu!");
    }
}
