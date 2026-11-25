package com.game.hunter.service;

import com.game.hunter.entity.GameCharacter;
import com.game.hunter.entity.User;
import com.game.hunter.entity.UserCharacter;
import com.game.hunter.entity.UserCharacterId;
import com.game.hunter.repository.GameCharacterRepository;
import com.game.hunter.repository.UserCharacterRepository;
import com.game.hunter.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserCharacterService {

    @Autowired
    private UserCharacterRepository userCharRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private GameCharacterRepository charRepo;

    /**
     * Lấy danh sách các GameCharacter mà user đã mở khóa.
     */
    public List<GameCharacter> getUnlockedCharacters(Integer userId) {
        List<UserCharacter> list = userCharRepo.findByUser_UserId(userId);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(UserCharacter::getCharacter)
                .collect(Collectors.toList());
    }

    /**
     * Mở khóa nhân vật cho người chơi.
     */
    public Optional<UserCharacter> unlockCharacter(Integer userId, Integer characterId) {
        Optional<User> userOpt = userRepo.findById(userId);
        Optional<GameCharacter> charOpt = charRepo.findById(characterId);

        if (userOpt.isEmpty() || charOpt.isEmpty()) {
            return Optional.empty();
        }

        UserCharacterId id = new UserCharacterId(userId, characterId);
        if (userCharRepo.existsById(id)) {
            return userCharRepo.findById(id);
        }

        UserCharacter uc = new UserCharacter(userOpt.get(), charOpt.get());
        return Optional.of(userCharRepo.save(uc));
    }

    /**
     * Kiểm tra xem người chơi đã mở khóa nhân vật này chưa.
     */
    public boolean isCharacterUnlocked(Integer userId, Integer characterId) {
        return userCharRepo.existsById(new UserCharacterId(userId, characterId));
    }

    /**
     * Mở khóa 2 nhân vật mặc định (Tôm và Cá Kiếm) cho người chơi mới tạo.
     */
    public void unlockDefaultCharacters(User user) {
        List<GameCharacter> characters = charRepo.findAll();
        for (GameCharacter c : characters) {
            // Mở khóa Tôm và Cá Kiếm (hoặc 2 nhân vật đầu tiên nếu có)
            if (c.getName().equalsIgnoreCase("Tôm") || c.getName().equalsIgnoreCase("Cá Kiếm")) {
                UserCharacter uc = new UserCharacter(user, c);
                userCharRepo.save(uc);
            }
        }
    }
}
