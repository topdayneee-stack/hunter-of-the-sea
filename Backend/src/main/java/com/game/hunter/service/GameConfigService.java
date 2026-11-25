package com.game.hunter.service;

import com.game.hunter.entity.GameConfig;
import com.game.hunter.repository.GameConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class GameConfigService {

    @Autowired
    private GameConfigRepository configRepo;

    public List<GameConfig> getAllConfigs() {
        return configRepo.findAll();
    }

    public Optional<GameConfig> getConfigByKey(String key) {
        return configRepo.findById(key);
    }

    public Optional<GameConfig> updateConfig(String key, GameConfig newConfig) {
        return configRepo.findById(key).map(config -> {
            config.setConfigValue(newConfig.getConfigValue());
            if (newConfig.getDescription() != null) {
                config.setDescription(newConfig.getDescription());
            }
            config.setUpdatedAt(LocalDateTime.now());
            return configRepo.save(config);
        });
    }
}
