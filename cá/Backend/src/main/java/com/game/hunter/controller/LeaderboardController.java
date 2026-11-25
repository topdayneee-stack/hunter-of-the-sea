package com.game.hunter.controller;

import com.game.hunter.dto.ScoreSubmitRequest;
import com.game.hunter.entity.Leaderboard;
import com.game.hunter.service.LeaderboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboards")
@CrossOrigin(origins = "*")
public class LeaderboardController {

    @Autowired
    private LeaderboardService leaderboardService;

    @GetMapping
    public List<Leaderboard> getLeaderboard(
            @RequestParam(defaultValue = "time_limit") String mode,
            @RequestParam(defaultValue = "true") boolean top10) {
        return leaderboardService.getLeaderboard(mode, top10);
    }

    @GetMapping("/user/{userId}")
    public List<Leaderboard> getUserLeaderboard(@PathVariable Integer userId) {
        return leaderboardService.getUserLeaderboard(userId);
    }

    @PostMapping
    public ResponseEntity<?> submitScore(@RequestBody ScoreSubmitRequest request) {
        return leaderboardService.submitScore(request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }
}
