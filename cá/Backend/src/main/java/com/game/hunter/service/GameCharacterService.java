package com.game.hunter.service;

import com.game.hunter.entity.GameCharacter;
import com.game.hunter.repository.GameCharacterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GameCharacterService {

    @Autowired
    private GameCharacterRepository characterRepo;

    public List<GameCharacter> getAllCharacters() {
        return characterRepo.findAll();
    }

    public Optional<GameCharacter> getCharacterById(Integer id) {
        return characterRepo.findById(id);
    }
}
