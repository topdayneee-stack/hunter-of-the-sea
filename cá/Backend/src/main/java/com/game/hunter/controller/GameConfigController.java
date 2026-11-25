package com.game.hunter.controller;

import com.game.hunter.entity.GameConfig;
import com.game.hunter.service.GameConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/configs")
@CrossOrigin(origins = "*")
public class GameConfigController {

    @Autowired
    private GameConfigService configService;

    @GetMapping
    public List<GameConfig> getAllConfigs() {
        return configService.getAllConfigs();
    }

    @GetMapping("/{key}")
    public ResponseEntity<GameConfig> getConfigByKey(@PathVariable String key) {
        return configService.getConfigByKey(key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{key}")
    public ResponseEntity<GameConfig> updateConfig(@PathVariable String key, @RequestBody GameConfig newConfig) {
        return configService.updateConfig(key, newConfig)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
