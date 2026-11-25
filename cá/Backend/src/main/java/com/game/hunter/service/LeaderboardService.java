package com.game.hunter.service;

import com.game.hunter.dto.ScoreSubmitRequest;
import com.game.hunter.entity.GameCharacter;
import com.game.hunter.entity.Leaderboard;
import com.game.hunter.entity.User;
import com.game.hunter.repository.GameCharacterRepository;
import com.game.hunter.repository.LeaderboardRepository;
import com.game.hunter.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LeaderboardService {

    @Autowired
    private LeaderboardRepository leaderboardRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private GameCharacterRepository characterRepo;

    public List<Leaderboard> getLeaderboard(String mode, boolean top10) {
        if (top10) {
            return leaderboardRepo.findTop10ByGameModeOrderByScoreDesc(mode);
        } else {
            return leaderboardRepo.findByGameModeOrderByScoreDesc(mode);
        }
    }

    public List<Leaderboard> getUserLeaderboard(Integer userId) {
        return leaderboardRepo.findByUser_UserIdOrderByScoreDesc(userId);
    }

    public Optional<Leaderboard> submitScore(ScoreSubmitRequest request) {
        // Kiểm tra xem User và Character có hợp lệ trong CSDL không
        Optional<User> userOpt = userRepo.findById(request.getUserId());
        Optional<GameCharacter> charOpt = characterRepo.findById(request.getCharacterId());

        if (userOpt.isEmpty() || charOpt.isEmpty()) {
            return Optional.empty();
        }

        Leaderboard entry = new Leaderboard(
                userOpt.get(),
                charOpt.get(),
                request.getScore(),
                request.getGameMode()
        );

        return Optional.of(leaderboardRepo.save(entry));
    }
}
