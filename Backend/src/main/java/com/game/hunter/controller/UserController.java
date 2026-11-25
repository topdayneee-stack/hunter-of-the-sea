package com.game.hunter.controller;

import com.game.hunter.dto.UserLoginRequest;
import com.game.hunter.entity.GameCharacter;
import com.game.hunter.entity.User;
import com.game.hunter.entity.UserCharacter;
import com.game.hunter.service.UserCharacterService;
import com.game.hunter.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserCharacterService userCharacterService;

    @PostMapping("/login")
    public ResponseEntity<User> loginOrRegister(@RequestBody UserLoginRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(userService.loginOrRegister(request.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Integer id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/characters")
    public ResponseEntity<List<GameCharacter>> getUnlockedCharacters(@PathVariable Integer id) {
        if (userService.getUserById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(userCharacterService.getUnlockedCharacters(id));
    }

    @PostMapping("/{userId}/characters/{charId}")
    public ResponseEntity<?> unlockCharacter(@PathVariable Integer userId, @PathVariable Integer charId) {
        return userCharacterService.unlockCharacter(userId, charId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    @GetMapping("/{userId}/characters/{charId}/check")
    public ResponseEntity<?> checkUnlocked(@PathVariable Integer userId, @PathVariable Integer charId) {
        boolean unlocked = userCharacterService.isCharacterUnlocked(userId, charId);
        return ResponseEntity.ok(Map.of("userId", userId, "characterId", charId, "unlocked", unlocked));
    }
}
