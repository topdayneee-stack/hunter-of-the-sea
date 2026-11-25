package com.game.hunter.controller;

import com.game.hunter.dto.PvpRecordRequest;
import com.game.hunter.entity.PvpMatchHistory;
import com.game.hunter.service.PvpMatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pvp")
@CrossOrigin(origins = "*")
public class PvpMatchController {

    @Autowired
    private PvpMatchService pvpService;

    @GetMapping("/history")
    public List<PvpMatchHistory> getHistory() {
        return pvpService.getMatchHistory();
    }

    @GetMapping("/history/user/{userId}")
    public List<PvpMatchHistory> getUserHistory(@PathVariable Integer userId) {
        return pvpService.getUserMatchHistory(userId);
    }

    @PostMapping("/record")
    public ResponseEntity<?> recordMatch(@RequestBody PvpRecordRequest request) {
        return pvpService.recordMatch(request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }
}
