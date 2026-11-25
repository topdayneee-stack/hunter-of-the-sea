package com.game.hunter.repository;

import com.game.hunter.entity.GameCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameCharacterRepository extends JpaRepository<GameCharacter, Integer> {
    Optional<GameCharacter> findByName(String name);
}
