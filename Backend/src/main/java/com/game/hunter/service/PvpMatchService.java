package com.game.hunter.service;

import com.game.hunter.dto.PvpRecordRequest;
import com.game.hunter.entity.GameCharacter;
import com.game.hunter.entity.PvpMatchHistory;
import com.game.hunter.entity.User;
import com.game.hunter.repository.GameCharacterRepository;
import com.game.hunter.repository.PvpMatchHistoryRepository;
import com.game.hunter.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PvpMatchService {

    @Autowired
    private PvpMatchHistoryRepository pvpRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private GameCharacterRepository characterRepo;

    public List<PvpMatchHistory> getMatchHistory() {
        return pvpRepo.findAllByOrderByPlayedAtDesc();
    }

    public List<PvpMatchHistory> getUserMatchHistory(Integer userId) {
        return pvpRepo.findByPlayer1_UserIdOrPlayer2_UserIdOrderByPlayedAtDesc(userId, userId);
    }

    public Optional<PvpMatchHistory> recordMatch(PvpRecordRequest request) {
        Optional<User> p1 = userRepo.findById(request.getPlayer1Id());
        Optional<User> p2 = userRepo.findById(request.getPlayer2Id());
        Optional<User> winner = userRepo.findById(request.getWinnerId());
        Optional<GameCharacter> c1 = characterRepo.findById(request.getPlayer1CharacterId());
        Optional<GameCharacter> c2 = characterRepo.findById(request.getPlayer2CharacterId());

        if (p1.isEmpty() || p2.isEmpty() || winner.isEmpty() || c1.isEmpty() || c2.isEmpty()) {
            return Optional.empty();
        }

        PvpMatchHistory match = new PvpMatchHistory(
                p1.get(),
                c1.get(),
                p2.get(),
                c2.get(),
                winner.get(),
                request.getMatchDurationSeconds()
        );

        return Optional.of(pvpRepo.save(match));
    }
}
