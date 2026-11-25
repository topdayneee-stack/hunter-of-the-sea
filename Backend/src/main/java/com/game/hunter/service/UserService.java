package com.game.hunter.service;

import com.game.hunter.entity.User;
import com.game.hunter.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private UserCharacterService userCharacterService;

    public User loginOrRegister(String username) {
        String cleanUsername = username.trim();
        Optional<User> existing = userRepo.findByUsername(cleanUsername);
        if (existing.isPresent()) {
            return existing.get();
        }

        User newUser = userRepo.save(new User(cleanUsername));
        // Tự động tặng 2 nhân vật ban đầu cho tân thủ
        try {
            userCharacterService.unlockDefaultCharacters(newUser);
        } catch (Exception ignored) {}

        return newUser;
    }

    public Optional<User> getUserById(Integer id) {
        return userRepo.findById(id);
    }
}
