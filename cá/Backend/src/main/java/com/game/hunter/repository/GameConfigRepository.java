package com.game.hunter.repository;

import com.game.hunter.entity.GameConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameConfigRepository extends JpaRepository<GameConfig, String> {
}
